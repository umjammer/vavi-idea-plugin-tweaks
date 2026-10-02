/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.intention;

import java.util.ArrayList;
import java.util.List;

import com.intellij.codeInsight.daemon.impl.quickfix.JavaIntentionsOrderProvider;
import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching;
import com.intellij.codeInsight.intention.impl.IntentionsOrderProvider;
import org.jetbrains.annotations.NotNull;


/**
 * UsageIntentionsOrderProvider, puts most used intentions (quick fixes) first.
 * <p>
 * the order is used by both the alt+enter popup and the top fix in the problem tooltip.
 * the original order is applied first by the java's provider, then reordered by usages.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-02 nsano initial version <br>
 */
public class UsageIntentionsOrderProvider implements IntentionsOrderProvider {

    private final IntentionsOrderProvider delegate = new JavaIntentionsOrderProvider();

    @Override
    public @NotNull List<IntentionActionWithTextCaching> getSortedIntentions(@NotNull CachedIntentions context,
                                                                           @NotNull List<IntentionActionWithTextCaching> intentions) {
        List<IntentionActionWithTextCaching> sorted = delegate.getSortedIntentions(context, intentions);
        UsageRanking ranking = IntentionUsageService.getInstance().getRanking();
        sorted.forEach(i -> ranking.offer(i.getText(), familyName(i)));
        return new ArrayList<>(ranking.sort(sorted, IntentionActionWithTextCaching::getText, UsageIntentionsOrderProvider::familyName));
    }

    private static String familyName(IntentionActionWithTextCaching i) {
        try {
            return i.getAction().getFamilyName();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
