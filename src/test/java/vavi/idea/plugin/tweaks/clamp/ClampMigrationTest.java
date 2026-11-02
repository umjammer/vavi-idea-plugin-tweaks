/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.clamp;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import vavi.idea.plugin.tweaks.clamp.ClampMigration.Arg;
import vavi.idea.plugin.tweaks.clamp.ClampMigration.Bounds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * ClampMigrationTest, cases are what the ide's quick fix did in vavi-media-quicktime (fb96bda).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-11-02 nsano initial version <br>
 */
class ClampMigrationTest {

    static final Predicate<String> NOT_ASSIGNED = e -> false;

    /** constants are compared, other values are unknown */
    static final BiPredicate<String, String> CONSTANTS = (lo, hi) -> {
        try {
            return Double.parseDouble(lo) <= Double.parseDouble(hi);
        } catch (NumberFormatException e) {
            return false;
        }
    };

    static Arg<String> v(String e) {
        return Arg.leaf(e, false, false);
    }

    static Arg<String> c(String e) {
        return Arg.leaf(e, true, false);
    }

    static Arg<String> call(Arg<String> a, Arg<String> b) {
        return Arg.call("f(" + a.element() + ", " + b.element() + ")", a, b);
    }

    @Test
    void valueFirstOffered() {
        // Math.max(-max - 1, Math.min(max, Math.round(s * (max + 1))))
        Arg<String> first = v("-max - 1");
        Arg<String> second = call(v("max"), v("Math.round(s * (max + 1))"));
        List<String> targets = ClampMigration.targets(first, second, NOT_ASSIGNED);
        // the bound is offered first, the fix wrote Math.clamp(max, -max - 1, Math.round(s * (max + 1)))
        assertEquals(List.of("max", "Math.round(s * (max + 1))"), targets);
        assertEquals(new Bounds<>("max", "-max - 1", "Math.round(s * (max + 1))"),
                ClampMigration.bounds(false, first, second, "max"));
        assertFalse(ClampMigration.isSafe(false, first, second, NOT_ASSIGNED, CONSTANTS));
    }

    @Test
    void ambiguousEvenIfOrdered() {
        // Math.max(newSize, Math.min(capacity, data.length * 2)), newSize <= capacity is known
        Arg<String> first = v("newSize");
        Arg<String> second = call(v("capacity"), v("data.length * 2"));
        BiPredicate<String, String> le = (lo, hi) -> lo.equals("newSize") && hi.equals("capacity");
        // Math.clamp(data.length * 2, newSize, capacity) is fine, but Math.clamp(capacity, newSize, data.length * 2) is offered too
        assertFalse(ClampMigration.isSafe(false, first, second, NOT_ASSIGNED, le));
        BiPredicate<String, String> both = (lo, hi) -> lo.equals("newSize");
        assertTrue(ClampMigration.isSafe(false, first, second, NOT_ASSIGNED, both));
    }

    @Test
    void boundsNotProven() {
        // Math.min(data.length, Math.max(4, Bytes.getInt(data, 0) & 0x00ff_ffff)), throws for data shorter than 4
        Arg<String> first = v("data.length");
        Arg<String> second = call(c("4"), v("Bytes.getInt(data, 0) & 0x00ff_ffff"));
        assertEquals(List.of("Bytes.getInt(data, 0) & 0x00ff_ffff"), ClampMigration.targets(first, second, NOT_ASSIGNED));
        assertEquals(new Bounds<>("Bytes.getInt(data, 0) & 0x00ff_ffff", "4", "data.length"),
                ClampMigration.bounds(true, first, second, "Bytes.getInt(data, 0) & 0x00ff_ffff"));
        assertFalse(ClampMigration.isSafe(true, first, second, NOT_ASSIGNED, CONSTANTS));
    }

    @Test
    void constantBounds() {
        // Math.max(0, Math.min(88, index + IMA_INDEX[nibble]))
        Arg<String> first = c("0");
        Arg<String> second = call(c("88"), v("index + IMA_INDEX[nibble]"));
        assertEquals(new Bounds<>("index + IMA_INDEX[nibble]", "0", "88"),
                ClampMigration.bounds(false, first, second, "index + IMA_INDEX[nibble]"));
        assertTrue(ClampMigration.isSafe(false, first, second, NOT_ASSIGNED, CONSTANTS));
        // Math.min(Math.max(1, x), 0)
        assertFalse(ClampMigration.isSafe(true, call(c("1"), v("x")), c("0"), NOT_ASSIGNED, CONSTANTS));
    }

    @Test
    void assignedIsTheValue() {
        // c = Math.max(lo, Math.min(c, hi)), lo <= hi is known
        Arg<String> first = v("lo");
        Arg<String> second = call(v("c"), v("hi"));
        assertEquals(List.of("c"), ClampMigration.targets(first, second, "c"::equals));
        BiPredicate<String, String> le = (lo, hi) -> lo.equals("lo") && hi.equals("hi");
        assertTrue(ClampMigration.isSafe(false, first, second, "c"::equals, le));
    }

    @Test
    void staticMemberIsNotTheValue() {
        // Math.min(Codecs.codecLosslessQuality, Math.max(lo, s.spatial(10)))
        Arg<String> first = v("Codecs.codecLosslessQuality");
        Arg<String> second = call(v("lo"), Arg.leaf("s.spatial(10)", false, false));
        Arg<String> secondStatic = call(Arg.leaf("LO", false, true), v("s.spatial(10)"));
        assertEquals(List.of("lo", "s.spatial(10)"), ClampMigration.targets(first, second, NOT_ASSIGNED));
        assertEquals(List.of("s.spatial(10)"), ClampMigration.targets(first, secondStatic, NOT_ASSIGNED));
    }

    @Test
    void bothCalls() {
        // Math.min(Math.max(mt, 0), Math.max(0, m.duration() - 1))
        Arg<String> first = call(v("mt"), c("0"));
        Arg<String> second = call(c("0"), v("m.duration() - 1"));
        assertEquals(List.of("mt", "m.duration() - 1"), ClampMigration.targets(first, second, NOT_ASSIGNED));
        assertEquals(new Bounds<>("mt", "0", second.element()), ClampMigration.bounds(true, first, second, "mt"));
        Map<String, Double> mins = Map.of("0", 0d, second.element(), 0d); // max(0, ...) >= 0
        BiPredicate<String, String> le = (lo, hi) -> lo.equals("0") && mins.containsKey(hi);
        // m.duration() - 1 as the value: Math.clamp(m.duration() - 1, 0, Math.max(mt, 0)), not proven
        assertFalse(ClampMigration.isSafe(true, first, second, NOT_ASSIGNED, le));
    }

    @Test
    void nestedRestIsNotInSource() {
        // Math.max(lo, Math.min(Math.min(a, x), b)), x as the value: the fix writes Math.min(a, b)
        Arg<String> first = v("lo");
        Arg<String> second = call(call(v("a"), v("x")), v("b"));
        assertNull(ClampMigration.bounds(false, first, second, "x"));
        assertFalse(ClampMigration.isSafe(false, first, second, NOT_ASSIGNED, (lo, hi) -> true));
    }
}
