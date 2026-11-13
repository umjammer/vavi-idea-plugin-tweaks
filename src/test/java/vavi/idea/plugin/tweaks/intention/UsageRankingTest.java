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

    /** offers all and applies one, like a popup */
    static void apply(UsageRanking ranking, List<Fix> offered, Fix applied) {
        offered.forEach(f -> ranking.offer(f.text(), f.family()));
        assertTrue(ranking.record(applied.text()));
    }

    @Test
    void mostUsedFirst() {
        Fix a = new Fix("Make method 'void'", "Make method void");
        Fix b = new Fix("Safe delete 'foo'", "Safe delete");
        Fix c = new Fix("Suppress warning", "Suppress");
        List<Fix> all = List.of(a, b, c);
        UsageRanking ranking = new UsageRanking();

        // unused keeps the original order
        assertEquals(all, ranking.sort(all, TEXT, FAMILY));

        apply(ranking, all, c);
        apply(ranking, all, b);
        apply(ranking, all, c);
        assertEquals(List.of(c, b, a), ranking.sort(all, TEXT, FAMILY));
    }

    @Test
    void familyCountsForDifferentTexts() {
        Fix a = new Fix("Make method 'void'", "Make method void");
        Fix b = new Fix("Safe delete 'foo'", "Safe delete");
        Fix b2 = new Fix("Safe delete 'bar'", "Safe delete");
        UsageRanking ranking = new UsageRanking();
        apply(ranking, List.of(b), b);

        // "bar" itself is never used, but its family is
        assertEquals(List.of(b2, a), ranking.sort(List.of(a, b2), TEXT, FAMILY));
    }

    @Test
    void familyPrecedesText() {
        // a fixed text vs. texts with a parameter
        Fix ignore = new Fix("Ignore vulnerable dependency", "Ignore vulnerable dependency");
        Fix replace2 = new Fix("Replace 2 vulnerable transitive dependencies", "Replace vulnerable transitive dependencies");
        Fix replace3 = new Fix("Replace 3 vulnerable transitive dependencies", "Replace vulnerable transitive dependencies");
        Fix replace4 = new Fix("Replace 4 vulnerable transitive dependencies", "Replace vulnerable transitive dependencies");
        UsageRanking ranking = new UsageRanking();
        apply(ranking, List.of(ignore, replace2), ignore);
        apply(ranking, List.of(ignore, replace2), ignore);
        apply(ranking, List.of(ignore, replace2), replace2);
        apply(ranking, List.of(ignore, replace3), replace3);
        apply(ranking, List.of(ignore, replace4), replace4);
        // ignore: text 2 family 2, replace2: text 1 family 3
        assertEquals(List.of(replace2, ignore), ranking.sort(List.of(ignore, replace2), TEXT, FAMILY));
    }

    @Test
    void textOrdersSameFamily() {
        Fix a = new Fix("A", "F1");
        Fix b1 = new Fix("B1", "F2");
        Fix b2 = new Fix("B2", "F2");
        List<Fix> all = List.of(a, b1, b2);
        UsageRanking ranking = new UsageRanking();
        apply(ranking, all, b1);
        apply(ranking, all, b2);
        apply(ranking, all, b2);
        // b2: family 3 text 2, b1: family 3 text 1
        assertEquals(List.of(b2, b1, a), ranking.sort(all, TEXT, FAMILY));
    }

    @Test
    void oldOfferIsNotRecorded() {
        long[] now = {0};
        UsageRanking ranking = new UsageRanking(() -> now[0]);
        ranking.offer("Copy", "Copy");
        now[0] = UsageRanking.OFFER_TIMEOUT;
        assertTrue(ranking.record("Copy"));
        ranking.offer("Copy", "Copy");
        now[0] += UsageRanking.OFFER_TIMEOUT + 1;
        assertFalse(ranking.record("Copy"));
        assertEquals(1, ranking.textCount("Copy"));
    }

    @Test
    void recordedOnceForAnOffer() {
        UsageRanking ranking = new UsageRanking();
        ranking.offer("Copy", "Copy");
        ranking.offer("Paste", "Paste");
        assertTrue(ranking.record("Copy"));
        // the same popup is not used twice, later commands with the same names are not intentions
        assertFalse(ranking.record("Copy"));
        assertFalse(ranking.record("Paste"));
        assertEquals(1, ranking.textCount("Copy"));
        assertEquals(0, ranking.textCount("Paste"));
    }
}
