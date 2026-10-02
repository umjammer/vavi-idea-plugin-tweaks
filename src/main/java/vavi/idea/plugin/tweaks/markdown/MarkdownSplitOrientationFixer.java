/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.markdown;

import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * MarkdownSplitOrientationFixer, makes the markdown editor/preview splitter follow
 * "Settings → Languages &amp; Frameworks → Markdown → Preview layout".
 * <p>
 * the platform ignores the setting in two ways.
 * <ul>
 *  <li>{@code TextEditorWithPreview} creates its splitter without applying the orientation
 *      given to the constructor, so the panes are always side by side</li>
 *  <li>{@code TextEditorWithPreview#setState} applies the orientation saved for each file
 *      in the workspace (default side by side), e.g. on opening or back/forward navigation</li>
 * </ul>
 * so the orientation is applied when a markdown file is opened, and restored whenever the splitter is changed
 * except by the editor's toolbar, see {@link #watch}.
 * <p>
 * note that markdown's "split vertically" means side by side,
 * and "split horizontally" means the preview below the editor.
 * <p>
 * the setting's default is changed to "split horizontally", once for each project, see {@link #applyDefault}.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-02 nsano initial version <br>
 */
public class MarkdownSplitOrientationFixer implements FileEditorManagerListener {

    private static final Logger logger = Logger.getInstance(MarkdownSplitOrientationFixer.class);

    static final String MARKDOWN_PLUGIN_ID = "org.intellij.plugins.markdown";
    static final String MARKDOWN_EDITOR = "org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview";
    static final String MARKDOWN_SETTINGS = "org.intellij.plugins.markdown.settings.MarkdownSettings";

    /** a client property on a splitter, marks the splitter is watched */
    private static final String WATCHED = "vavi.tweaks.markdownSplitOrientationWatched";

    /** a project property, marks the default is applied to the project */
    static final String DEFAULT_APPLIED = "vavi.tweaks.markdownSplitHorizontalDefaultApplied";

    private final Project project;

    public MarkdownSplitOrientationFixer(Project project) {
        this.project = project;
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        for (FileEditor editor : source.getEditors(file)) {
            if (editor instanceof TextEditorWithPreview e && MARKDOWN_EDITOR.equals(e.getClass().getName())) {
                fix(e);
            }
        }
    }

    /** applies the setting to the editor, and keeps it */
    private void fix(TextEditorWithPreview editor) {
        applyDefault(project);
        Boolean markdownVerticalSplit = markdownVerticalSplit(project);
        if (markdownVerticalSplit == null) return;
        // also creates the editor's ui
        editor.setVerticalSplit(splitterVertical(markdownVerticalSplit));

        Splitter splitter = UIUtil.findComponentOfType(editor.getComponent(), Splitter.class);
        if (splitter == null) {
            logger.warn("no splitter in " + editor);
            return;
        }
        watch(splitter, () -> {
            if (project.isDisposed()) return splitter.getOrientation();
            Boolean v = markdownVerticalSplit(project);
            return splitterVertical(v != null ? v : markdownVerticalSplit);
        }, MarkdownSplitActionListener::isUserChanging, editor::setVerticalSplit);
    }

    /** setter for an editor's orientation, to keep the editor's own flag in sync with its splitter */
    interface OrientationSetter {
        void set(boolean splitterVertical);
    }

    /** an orientation chosen by a user for an editor by the editor's toolbar */
    record Choice(boolean orientation, boolean settingAtChoice) {}

    /**
     * @param choice a user's choice for an editor, nullable
     * @param setting an orientation by the setting
     * @return an orientation to keep, a user's choice is dropped when the setting is changed after it
     */
    static boolean wanted(@Nullable Choice choice, boolean setting) {
        return choice != null && choice.settingAtChoice() == setting ? choice.orientation() : setting;
    }

    /**
     * restores a splitter's orientation whenever it is changed to another one than wanted.
     * <ul>
     *  <li>a change by a user (the editor's toolbar) is kept as the editor's choice</li>
     *  <li>a change by markdown when the setting is changed is kept</li>
     *  <li>others (restoring per file states etc.) are reverted</li>
     * </ul>
     *
     * @param setting an orientation by the setting
     * @param userChanging true while a user is changing the orientation
     */
    static void watch(Splitter splitter, BooleanSupplier setting, BooleanSupplier userChanging, OrientationSetter setter) {
        if (splitter.getClientProperty(WATCHED) != null) return;
        splitter.putClientProperty(WATCHED, Boolean.TRUE);
        Choice[] choice = new Choice[1];
        PropertyChangeListener l = e -> {
            boolean s = setting.getAsBoolean();
            if (userChanging.getAsBoolean()) {
                choice[0] = new Choice(splitter.getOrientation(), s);
                return;
            }
            boolean w = wanted(choice[0], s);
            if (w == s) choice[0] = null;
            if (splitter.getOrientation() != w) {
                setter.set(w);
            }
        };
        splitter.addPropertyChangeListener(Splitter.PROP_ORIENTATION, l);
    }

    /**
     * @param markdownVerticalSplit markdown's setting, true: "split vertically"
     * @return {@link Splitter#getOrientation()}, true: top and bottom
     */
    static boolean splitterVertical(boolean markdownVerticalSplit) {
        return !markdownVerticalSplit;
    }

    /**
     * sets markdown's setting to "split horizontally" once for a project.
     * markdown doesn't store the default value ("split vertically"), so a user's choice of it
     * cannot be told from the default, the project is marked instead, and later choices are kept.
     */
    private static void applyDefault(Project project) {
        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        if (properties.getBoolean(DEFAULT_APPLIED)) return;
        try {
            Class<?> c = markdownSettingsClass();
            Object settings = c.getMethod("getInstance", Project.class).invoke(null, project);
            c.getMethod("setVerticalSplit", boolean.class).invoke(settings, false);
            properties.setValue(DEFAULT_APPLIED, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logger.warn("cannot write markdown settings", e);
        }
    }

    /** @return markdown's "split vertically" setting, null when unavailable */
    private static @Nullable Boolean markdownVerticalSplit(Project project) {
        try {
            Class<?> c = markdownSettingsClass();
            Object settings = c.getMethod("getInstance", Project.class).invoke(null, project);
            Method isVerticalSplit = c.getMethod("isVerticalSplit");
            return (Boolean) isVerticalSplit.invoke(settings);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logger.warn("cannot read markdown settings", e);
            return null;
        }
    }

    private static Class<?> markdownSettingsClass() throws ClassNotFoundException {
        PluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(MARKDOWN_PLUGIN_ID));
        ClassLoader cl = plugin != null && plugin.getPluginClassLoader() != null ?
                plugin.getPluginClassLoader() : MarkdownSplitOrientationFixer.class.getClassLoader();
        return Class.forName(MARKDOWN_SETTINGS, true, cl);
    }
}
