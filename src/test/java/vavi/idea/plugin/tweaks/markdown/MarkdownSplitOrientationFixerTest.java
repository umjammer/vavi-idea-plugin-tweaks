/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.markdown;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.intellij.openapi.ui.Splitter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * MarkdownSplitOrientationFixerTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-02 nsano initial version <br>
 */
class MarkdownSplitOrientationFixerTest {

    @Test
    void mapping() {
        // markdown "split vertically" (default) is side by side
        assertFalse(MarkdownSplitOrientationFixer.splitterVertical(true));
        // markdown "split horizontally" is the preview below the editor
        assertTrue(MarkdownSplitOrientationFixer.splitterVertical(false));
    }

    @Test
    void restoresUnwantedChange() {
        Splitter splitter = new Splitter(true);
        AtomicBoolean wanted = new AtomicBoolean(true);
        AtomicInteger set = new AtomicInteger();
        MarkdownSplitOrientationFixer.watch(splitter, wanted::get, () -> false, v -> {
            set.incrementAndGet();
            splitter.setOrientation(v);
        });

        // e.g. setState() with a stale per file orientation
        splitter.setOrientation(false);
        assertTrue(splitter.getOrientation());
        assertEquals(1, set.get());

        // e.g. the setting is changed, markdown changes the splitter
        wanted.set(false);
        splitter.setOrientation(false);
        assertFalse(splitter.getOrientation());
        assertEquals(1, set.get());
    }

    @Test
    void watchedOnce() {
        Splitter splitter = new Splitter(false);
        AtomicInteger set = new AtomicInteger();
        MarkdownSplitOrientationFixer.watch(splitter, () -> false, () -> false, v -> { set.incrementAndGet(); splitter.setOrientation(v); });
        MarkdownSplitOrientationFixer.watch(splitter, () -> false, () -> false, v -> { set.incrementAndGet(); splitter.setOrientation(v); });
        splitter.setOrientation(true);
        assertFalse(splitter.getOrientation());
        assertEquals(1, set.get());
    }

    @Test
    void wanted() {
        assertTrue(MarkdownSplitOrientationFixer.wanted(null, true));
        assertFalse(MarkdownSplitOrientationFixer.wanted(null, false));
        // a user's choice under the same setting
        assertFalse(MarkdownSplitOrientationFixer.wanted(new MarkdownSplitOrientationFixer.Choice(false, true), true));
        // the setting is changed after the choice
        assertFalse(MarkdownSplitOrientationFixer.wanted(new MarkdownSplitOrientationFixer.Choice(true, true), false));
    }

    @Test
    void keepsUserChange() {
        Splitter splitter = new Splitter(true);
        AtomicBoolean setting = new AtomicBoolean(true);
        AtomicBoolean user = new AtomicBoolean();
        AtomicInteger set = new AtomicInteger();
        MarkdownSplitOrientationFixer.watch(splitter, setting::get, user::get, v -> {
            set.incrementAndGet();
            splitter.setOrientation(v);
        });

        // the editor's toolbar
        user.set(true);
        splitter.setOrientation(false);
        user.set(false);
        assertFalse(splitter.getOrientation());
        assertEquals(0, set.get());

        // e.g. back/forward navigation restores a stale per file orientation, the user's choice is kept
        splitter.setOrientation(true);
        assertFalse(splitter.getOrientation());
        assertEquals(1, set.get());

        // the setting is changed after the choice (the splitter is already the same)
        setting.set(false);
        // a stale per file orientation is reverted to the setting, not the user's choice
        splitter.setOrientation(true);
        assertFalse(splitter.getOrientation());
        assertEquals(2, set.get());

        // the setting is changed, markdown changes the splitter, it's kept
        setting.set(true);
        splitter.setOrientation(true);
        assertTrue(splitter.getOrientation());
        assertEquals(2, set.get());
    }
}
