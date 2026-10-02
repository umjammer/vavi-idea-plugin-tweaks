/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.intention;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * UsageRankingTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-02 nsano initial version <br>
 */
class UsageRankingTest {

    record Fix(String text, String family) {}

    static final Function<Fix, String> TEXT = Fix::text;
    static final Function<Fix, String> FAMILY = Fix::family;

    @Test
    void notOfferedIsNotRecorded() {
        UsageRanking ranking = new UsageRanking();
        assertFalse(ranking.record("Typing"));
        assertEquals(0, ranking.textCount("Typing"));
    }

    @Test
    void mostUsedFirst() {
        Fix a = new Fix("Make method 'void'", "Make method void");
        Fix b = new Fix("Safe delete 'foo'", "Safe delete");
        Fix c = new Fix("Suppress warning", "Suppress");
        UsageRanking ranking = new UsageRanking();
        List.of(a, b, c).forEach(f -> ranking.offer(f.text(), f.family()));

        // unused keeps the original order
        assertEquals(List.of(a, b, c), ranking.sort(List.of(a, b, c), TEXT, FAMILY));

        assertTrue(ranking.record(c.text()));
        assertTrue(ranking.record(b.text()));
        assertTrue(ranking.record(c.text()));
        assertEquals(List.of(c, b, a), ranking.sort(List.of(a, b, c), TEXT, FAMILY));
    }

    @Test
    void familyCountsForDifferentTexts() {
        Fix a = new Fix("Make method 'void'", "Make method void");
        Fix b = new Fix("Safe delete 'foo'", "Safe delete");
        Fix b2 = new Fix("Safe delete 'bar'", "Safe delete");
        UsageRanking ranking = new UsageRanking();
        ranking.offer(b.text(), b.family());
        ranking.record(b.text());

        // "bar" itself is never used, but its family is
        assertEquals(List.of(b2, a), ranking.sort(List.of(a, b2), TEXT, FAMILY));
    }

    @Test
    void textPrecedesFamily() {
        Fix a = new Fix("A", "F1");
        Fix b1 = new Fix("B1", "F2");
        Fix b2 = new Fix("B2", "F2");
        UsageRanking ranking = new UsageRanking();
        List.of(a, b1, b2).forEach(f -> ranking.offer(f.text(), f.family()));
        ranking.record("A");
        ranking.record("B1");
        ranking.record("B2");
        // a: text 1 family 1, b2: text 1 family 2, b1: text 1 family 2
        assertEquals(List.of(b1, b2, a), ranking.sort(List.of(a, b1, b2), TEXT, FAMILY));
        ranking.record("A");
        assertEquals(List.of(a, b1, b2), ranking.sort(List.of(a, b1, b2), TEXT, FAMILY));
    }
}
