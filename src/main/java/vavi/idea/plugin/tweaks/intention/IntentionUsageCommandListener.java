/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.intention;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import org.jetbrains.annotations.NotNull;


/**
 * IntentionUsageCommandListener, counts applied intentions.
 * <p>
 * the platform has no listener for applied intentions, but an intention is executed
 * as a command named by its text.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-02 nsano initial version <br>
 */
public class IntentionUsageCommandListener implements CommandListener {

    @Override
    public void commandStarted(@NotNull CommandEvent event) {
        IntentionUsageService.getInstance().getRanking().record(event.getCommandName());
    }
}
