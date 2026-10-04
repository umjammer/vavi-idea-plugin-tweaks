/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.spi;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * InsertAllServiceProvidersActionTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-04 nsano initial version <br>
 */
class InsertAllServiceProvidersActionTest {

    @Test
    void entries() {
        List<String> lines = List.of(
                "# comment",
                "  foo.A  ",
                "foo.B # trailing comment",
                "",
                "foo.Outer$C");
        assertEquals(Set.of("foo.A", "foo.B", "foo.Outer$C"), InsertAllServiceProvidersAction.entries(lines));
    }

    @Test
    void missing() {
        List<String> candidates = List.of("foo.C", "foo.A", "foo.Outer$B", "foo.A", "bar.D");
        assertEquals(List.of("bar.D", "foo.C", "foo.Outer$B"),
                InsertAllServiceProvidersAction.missing(candidates, Set.of("foo.A"), ""));
    }

    @Test
    void missingWithPrefix() {
        List<String> candidates = List.of("foo.C", "foo.A", "foo.Outer$B", "bar.D");
        assertEquals(List.of("foo.C", "foo.Outer$B"),
                InsertAllServiceProvidersAction.missing(candidates, Set.of("foo.A"), "foo."));
    }
}
