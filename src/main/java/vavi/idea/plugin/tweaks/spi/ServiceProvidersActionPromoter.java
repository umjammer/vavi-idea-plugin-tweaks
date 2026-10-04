/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.spi;

import java.util.List;

import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;


/**
 * ServiceProvidersActionPromoter, runs {@link InsertAllServiceProvidersAction} before other actions sharing
 * the shortcut (e.g. "Start New Line" on shift enter). the action is enabled only
 * in the completion popup of a service loader file, so the others work elsewhere.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-04 nsano initial version <br>
 */
public class ServiceProvidersActionPromoter implements ActionPromoter {

    @Override
    public List<AnAction> promote(List<? extends AnAction> actions, DataContext context) {
        return actions.stream().filter(a -> a instanceof InsertAllServiceProvidersAction).map(a -> (AnAction) a).toList();
    }
}
