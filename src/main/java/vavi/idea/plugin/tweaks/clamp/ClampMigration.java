/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.clamp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;


/**
 * ClampMigration, the replacement the ide's "Math.clamp() can be used" quick fix (MathClampMigrationInspection,
 * new in 2026.1) makes for a combination of {@code Math.max()}/{@code Math.min()}, modeled without psi.
 * <p>
 * the quick fix is wrong in two ways (both reproduced here as they are, to know what the fix will write):
 * <ul>
 *  <li>"brave mode" is on by default (the description says the opposite), then every non-constant leaf
 *      of the nested call is offered as the value to clamp, in the source order. for
 *      {@code Math.max(lo, Math.min(hi, x))} the first offer is {@code hi}, then {@code Math.clamp(hi, lo, x)}.</li>
 *  <li>it never proves {@code min <= max}, but {@code Math.clamp()} throws {@link IllegalArgumentException}
 *      if not, where the {@code max(min())} returned a value. (e.g. {@code Math.min(d.length, Math.max(4, n))}
 *      for an array shorter than 4)</li>
 * </ul>
 * {@link #isSafe} tells whether every replacement the quick fix may make is equivalent.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-11-02 nsano initial version <br>
 */
public final class ClampMigration {

    private ClampMigration() {}

    /**
     * an argument of a {@code Math.max()}/{@code Math.min()} call.
     *
     * @param element  the argument expression
     * @param constant the data flow knows its value
     * @param staticMember it refers a static member
     * @param children the arguments if it is a nested call of the other function (min in max, max in min), empty if not
     */
    public record Arg<T>(T element, boolean constant, boolean staticMember, List<Arg<T>> children) {

        public static <T> Arg<T> leaf(T element, boolean constant, boolean staticMember) {
            return new Arg<>(element, constant, staticMember, List.of());
        }

        public static <T> Arg<T> call(T element, Arg<T> first, Arg<T> second) {
            return new Arg<>(element, false, false, List.of(first, second));
        }

        public boolean hasChildren() {
            return !children.isEmpty();
        }

        Stream<Arg<T>> leaves() {
            return hasChildren() ? children.stream().flatMap(Arg::leaves) : Stream.of(this);
        }

        boolean contains(T target) {
            return element.equals(target) || children.stream().anyMatch(c -> c.contains(target));
        }
    }

    /** bounds of a replacement {@code Math.clamp(value, min, max)} */
    public record Bounds<T>(T value, T min, T max) {}

    /**
     * @param assigned whether the expression is the left side of the assignment the call is the right side of
     * @return values the quick fix offers to clamp, empty if the ide doesn't report the call
     */
    public static <T> List<T> targets(Arg<T> first, Arg<T> second, Predicate<T> assigned) {
        List<Arg<T>> candidates = Stream.of(first, second).filter(Arg::hasChildren)
                .flatMap(Arg::leaves).distinct().toList();
        List<Predicate<Arg<T>>> filters = List.of(a -> !a.constant(), a -> !a.staticMember());
        for (Predicate<Arg<T>> filter : filters) {
            candidates = candidates.stream().filter(filter).toList();
            for (Arg<T> a : candidates) {
                if (assigned.test(a.element())) {
                    return List.of(a.element());
                }
            }
            if (candidates.size() <= 1) {
                break;
            }
        }
        return candidates.stream().map(Arg::element).toList();
    }

    /**
     * @param topIsMin the outer call is {@code Math.min()}
     * @return the bounds of the replacement for the target, null if the quick fix writes an expression
     *         not in the source (e.g. a new {@code Math.min()} call of the rest)
     */
    public static <T> Bounds<T> bounds(boolean topIsMin, Arg<T> first, Arg<T> second, T target) {
        Arg<T> inner = first.contains(target) ? first : second;
        Arg<T> outer = inner == first ? second : first;
        T innerBound = bound(inner, target);
        T outerBound = bound(outer, target);
        if (innerBound == null || outerBound == null) {
            return null;
        }
        // max(lo, min(hi, x)), min(hi, max(lo, x))
        return topIsMin ? new Bounds<>(target, innerBound, outerBound) : new Bounds<>(target, outerBound, innerBound);
    }

    /** the expression of the arg without the target, null if it is not an expression in the source */
    private static <T> T bound(Arg<T> arg, T target) {
        if (!arg.hasChildren()) {
            return arg.element();
        }
        List<Arg<T>> rest = new ArrayList<>();
        for (Arg<T> c : arg.children()) {
            if (!c.element().equals(target)) {
                rest.add(c);
            }
        }
        if (rest.size() == 1) {
            return bound(rest.getFirst(), target);
        }
        return arg.contains(target) ? null : arg.element();
    }

    /**
     * @param assigned       see {@link #targets}
     * @param lessOrEqual    whether the first is proven less than or equal to the second for any value
     * @return false if any replacement the quick fix offers for the call is not equivalent,
     *         true if all are, or the call is not reported
     */
    public static <T> boolean isSafe(boolean topIsMin, Arg<T> first, Arg<T> second, Predicate<T> assigned, BiPredicate<T, T> lessOrEqual) {
        for (T target : targets(first, second, assigned)) {
            Bounds<T> b = bounds(topIsMin, first, second, target);
            if (b == null || !lessOrEqual.test(b.min(), b.max())) {
                return false;
            }
        }
        return true;
    }
}
