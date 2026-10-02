/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.markdown;

import java.util.Set;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionResult;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import org.jetbrains.annotations.NotNull;


/**
 * MarkdownSplitActionListener, tells {@link MarkdownSplitOrientationFixer} that a user is changing
 * an editor's orientation by the editor's toolbar, so that the change is not reverted.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-02 nsano initial version <br>
 */
public class MarkdownSplitActionListener implements AnActionListener {

    /** {@code TextEditorWithPreview}'s actions which may change the orientation */
    static final Set<String> ACTION_IDS = Set.of(
            "TextEditorWithPreview.SplitVertically",
            "TextEditorWithPreview.SplitHorizontally",
            // toggles the orientation when it is already selected
            "TextEditorWithPreview.Layout.EditorAndPreview"
    );

    /** actions are performed on the edt only */
    private static int changing;

    static boolean isUserChanging() {
        return changing > 0;
    }

    private static boolean isTarget(AnAction action) {
        String id = ActionManager.getInstance().getId(action);
        return id != null && ACTION_IDS.contains(id);
    }

    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
        if (isTarget(action)) changing++;
    }

    @Override
    public void afterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result) {
        if (isTarget(action) && changing > 0) changing--;
    }
}
