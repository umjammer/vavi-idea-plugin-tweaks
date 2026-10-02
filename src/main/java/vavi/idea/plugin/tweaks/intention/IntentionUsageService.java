/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.intention;

import java.util.HashMap;
import java.util.Map;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;


/**
 * IntentionUsageService, keeps {@link UsageRanking} over ide restarts.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-02 nsano initial version <br>
 */
@Service(Service.Level.APP)
@State(name = "VaviTweaksIntentionUsage", storages = @Storage("vaviTweaksIntentionUsage.xml"))
public final class IntentionUsageService implements PersistentStateComponent<IntentionUsageService.UsageState> {

    /** persisted form */
    public static class UsageState {
        public Map<String, Integer> texts = new HashMap<>();
        public Map<String, Integer> families = new HashMap<>();
    }

    private final UsageRanking ranking = new UsageRanking();

    public static IntentionUsageService getInstance() {
        return ApplicationManager.getApplication().getService(IntentionUsageService.class);
    }

    public UsageRanking getRanking() {
        return ranking;
    }

    @Override
    public UsageState getState() {
        UsageState state = new UsageState();
        state.texts.putAll(ranking.textCounts);
        state.families.putAll(ranking.familyCounts);
        return state;
    }

    @Override
    public void loadState(@NotNull UsageState state) {
        ranking.textCounts.clear();
        ranking.textCounts.putAll(state.texts);
        ranking.familyCounts.clear();
        ranking.familyCounts.putAll(state.families);
    }
}
