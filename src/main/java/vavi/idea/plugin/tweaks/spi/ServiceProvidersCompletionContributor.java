/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.spi;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.keymap.KeymapUtil;
import org.jetbrains.annotations.NotNull;


/**
 * ServiceProvidersCompletionContributor, adds "Press ... to insert all" at the bottom of the completion popup
 * of a service loader file. no variants are added, they come from the platform.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-04 nsano initial version <br>
 * @see InsertAllServiceProvidersAction
 */
public class ServiceProvidersCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        if (!InsertAllServiceProvidersAction.isSpi(parameters.getOriginalFile())) return;
        String shortcut = KeymapUtil.getFirstKeyboardShortcutText(InsertAllServiceProvidersAction.ID);
        if (!shortcut.isEmpty()) {
            result.addLookupAdvertisement("Press " + shortcut + " to insert all");
        }
    }
}
