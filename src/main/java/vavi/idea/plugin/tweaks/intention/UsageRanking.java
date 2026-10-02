/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.intention;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


/**
 * UsageRanking, counts how many times each intention (quick fix) was applied,
 * and sorts intentions by the counts.
 * <p>
 * an intention is counted by both its text (e.g. "Make method 'void'") and its family name
 * (e.g. "Make method void"), the text precedes the family.
 * <p>
 * only texts offered recently by {@link #offer} are counted, so that commands other
 * than intentions (typing etc.) are not recorded.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-02 nsano initial version <br>
 */
public class UsageRanking {

    /** max size of offered texts to remember */
    private static final int MAX_OFFERED = 1000;

    /** key: text, value: count */
    final Map<String, Integer> textCounts = new ConcurrentHashMap<>();

    /** key: family name, value: count */
    final Map<String, Integer> familyCounts = new ConcurrentHashMap<>();

    /** key: offered text, value: family name (or "" when unknown) */
    private final Map<String, String> offered = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_OFFERED;
        }
    };

    /** remembers an intention shown to a user */
    public void offer(String text, String familyName) {
        if (text == null || text.isEmpty()) return;
        synchronized (offered) {
            offered.put(text, familyName == null ? "" : familyName);
        }
    }

    /**
     * counts an applied intention.
     *
     * @param text a command name, an intention's text
     * @return true when counted
     */
    public boolean record(String text) {
        if (text == null) return false;
        String family;
        synchronized (offered) {
            family = offered.get(text);
        }
        if (family == null) return false;
        textCounts.merge(text, 1, Integer::sum);
        if (!family.isEmpty()) familyCounts.merge(family, 1, Integer::sum);
        return true;
    }

    public int textCount(String text) {
        return text == null ? 0 : textCounts.getOrDefault(text, 0);
    }

    public int familyCount(String family) {
        return family == null ? 0 : familyCounts.getOrDefault(family, 0);
    }

    /**
     * sorts by usages, descending. the sort is stable, so unused ones keep the original order.
     *
     * @param text extracts a text from an item
     * @param family extracts a family name from an item
     * @return a new sorted list
     */
    public <T> List<T> sort(List<T> items, Function<T, String> text, Function<T, String> family) {
        Comparator<T> c = Comparator.<T>comparingInt(i -> textCount(text.apply(i)))
                .thenComparingInt(i -> familyCount(family.apply(i)))
                .reversed();
        return items.stream().sorted(c).toList();
    }
}
