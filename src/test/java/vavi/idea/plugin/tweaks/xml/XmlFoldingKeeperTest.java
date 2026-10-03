/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.xml;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * XmlFoldingKeeperTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-03 nsano initial version <br>
 */
class XmlFoldingKeeperTest {

    @Test
    void changes() {
        Map<String, Boolean> recorded = Map.of(
                "project[0]/build[0]", false,
                "project[0]/profiles[0]/profile[1]", false,
                "project[0]/dependencies[0]", true,
                "project[0]/gone[0]", false);
        Map<String, Boolean> current = Map.of(
                "project[0]/build[0]", true, // forgotten
                "project[0]/profiles[0]/profile[1]", false, // restored by the platform
                "project[0]/dependencies[0]", false, // collapsed by default, expanded by a user
                "project[0]/new[0]", true);
        Map<String, Boolean> changes = XmlFoldingKeeper.changes(recorded, current);
        assertEquals(Map.of("project[0]/build[0]", false, "project[0]/dependencies[0]", true), changes);
    }

    @Test
    void nothingToChange() {
        Map<String, Boolean> states = Map.of("project[0]/build[0]", false);
        assertTrue(XmlFoldingKeeper.changes(states, states).isEmpty());
    }

    @Test
    void segment() {
        assertEquals("plugin[2]", XmlFoldingKeeper.segment("plugin", 2));
    }
}
