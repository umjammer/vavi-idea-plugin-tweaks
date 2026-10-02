/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.structure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * DisplayNameFileTreeElementTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-02 nsano initial version <br>
 */
class DisplayNameFileTreeElementTest {

    @Test
    @DisplayName("annotation name is the junit5 one")
    void annotationName() {
        assertEquals(DisplayName.class.getName(), DisplayNameFileTreeElement.DISPLAY_NAME);
    }

    @Test
    void location() {
        assertNull(DisplayNameFileTreeElement.location(null, null));
        assertEquals("↑Base", DisplayNameFileTreeElement.location(null, "↑Base"));
        assertEquals("adds two", DisplayNameFileTreeElement.location("adds two", null));
        assertEquals("adds two", DisplayNameFileTreeElement.location("adds two", ""));
        assertEquals("adds two ↑Base", DisplayNameFileTreeElement.location("adds two", "↑Base"));
    }
}
