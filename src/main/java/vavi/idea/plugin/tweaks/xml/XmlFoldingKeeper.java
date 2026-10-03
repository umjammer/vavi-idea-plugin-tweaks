/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.xml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * XmlFoldingKeeper, keeps folding states of tags in an xml file (e.g. pom.xml) when the file is closed and reopened.
 * <p>
 * the platform is supposed to restore them from the editor history, but it forgets them now and then.
 * so the states are recorded when an xml editor is closed, keyed by a tag path like {@code project[0]/build[0]/plugins[0]/plugin[2]}
 * (not by offsets, nor limited by the number of children unlike the platform's signatures),
 * and applied when the file is reopened, after the platform initialized the foldings of the editor.
 * only regions which differ from the record are changed, so nothing happens when the platform restored them.
 * <p>
 * records are kept in memory for the session, and consumed when the file is reopened.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-03 nsano initial version <br>
 */
public class XmlFoldingKeeper implements FileEditorManagerListener, FileEditorManagerListener.Before {

    private static final Logger logger = Logger.getInstance(XmlFoldingKeeper.class);

    /** {@code CodeFoldingManagerImpl}'s editor key, set when the editor's foldings are initialized (incl. restoring states) */
    private static final String FOLDINGS_INITIALIZED = "FOLDINGS_INITIALIZED";

    /** recorded states, file url → tag path → expanded */
    @Service(Service.Level.PROJECT)
    public static final class Records {
        final Map<String, Map<String, Boolean>> states = new ConcurrentHashMap<>();
    }

    private final Project project;

    public XmlFoldingKeeper(Project project) {
        this.project = project;
    }

    private Records records() {
        return project.getService(Records.class);
    }

    @Override
    public void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        TextEditor editor = textEditor(source, file);
        if (editor == null) return;
        Map<String, Boolean> states = new HashMap<>();
        ReadAction.run(() -> {
            Map<String, FoldRegion> regions = regions(editor.getEditor());
            if (regions != null) regions.forEach((path, region) -> states.put(path, region.isExpanded()));
        });
        if (!states.isEmpty()) {
            records().states.put(file.getUrl(), states);
        }
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        Map<String, Boolean> states = records().states.remove(file.getUrl());
        if (states == null) return;
        TextEditor editor = textEditor(source, file);
        if (editor == null) return;
        restoreWhenInitialized(editor, states);
    }

    /**
     * waits for the platform to initialize (and restore) the foldings, then applies the record.
     * the initialization doesn't always fire folding events (e.g. nothing to restore), so the daemon is also watched,
     * the folding pass is one of its passes.
     */
    private void restoreWhenInitialized(TextEditor textEditor, Map<String, Boolean> states) {
        Editor editor = textEditor.getEditor();
        if (isFoldingInitialized(editor)) {
            restore(textEditor, states);
            return;
        }
        Disposable disposable = Disposer.newDisposable("vavi.tweaks.xmlFoldingKeeper");
        Disposer.register(textEditor, disposable);
        boolean[] scheduled = new boolean[1];
        Runnable check = () -> {
            if (scheduled[0] || editor.isDisposed() || !isFoldingInitialized(editor)) return;
            scheduled[0] = true;
            // out of the batch operation or the daemon's callback
            ApplicationManager.getApplication().invokeLater(() -> {
                Disposer.dispose(disposable);
                restore(textEditor, states);
            }, project.getDisposed());
        };
        ((FoldingModelEx) editor.getFoldingModel()).addListener(new FoldingListener() {
            @Override
            public void onFoldProcessingEnd() {
                check.run();
            }
        }, disposable);
        project.getMessageBus().connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
            @Override
            public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
                if (fileEditors.contains(textEditor)) ApplicationManager.getApplication().invokeLater(check, project.getDisposed());
            }

            @Override
            public void daemonCanceled(@NotNull String reason, @NotNull Collection<? extends FileEditor> fileEditors) {
                if (fileEditors.contains(textEditor)) ApplicationManager.getApplication().invokeLater(check, project.getDisposed());
            }
        });
    }

    private void restore(TextEditor textEditor, Map<String, Boolean> states) {
        Editor editor = textEditor.getEditor();
        if (editor.isDisposed()) return;
        Map<String, FoldRegion> regions = ReadAction.compute(() -> regions(editor));
        if (regions == null) return;
        Map<String, Boolean> current = new HashMap<>();
        regions.forEach((path, region) -> current.put(path, region.isExpanded()));
        Map<String, Boolean> changes = changes(states, current);
        if (changes.isEmpty()) {
            logger.info("checked " + states.size() + " folding state(s), the platform restored them: " + textEditor.getFile().getPath());
            return;
        }
        editor.getFoldingModel().runBatchFoldingOperation(() -> changes.forEach((path, expanded) -> {
            FoldRegion region = regions.get(path);
            if (region.isValid()) region.setExpanded(expanded);
        }));
        logger.info("restored " + changes.size() + " folding state(s) the platform forgot: " + textEditor.getFile().getPath());
    }

    /**
     * @param recorded tag path → expanded, recorded when the file was closed
     * @param current tag path → expanded, of the reopened editor
     * @return tag path → expanded to set, regions not recorded or gone are left as they are
     */
    static Map<String, Boolean> changes(Map<String, Boolean> recorded, Map<String, Boolean> current) {
        Map<String, Boolean> changes = new HashMap<>();
        recorded.forEach((path, expanded) -> {
            Boolean now = current.get(path);
            if (now != null && now.booleanValue() != expanded.booleanValue()) changes.put(path, expanded);
        });
        return changes;
    }

    private static boolean isFoldingInitialized(Editor editor) {
        Key<?> key = Key.findKeyByName(FOLDINGS_INITIALIZED);
        if (key == null) {
            // not created yet (no editor has been initialized), or renamed
            return false;
        }
        return Boolean.TRUE.equals(editor.getUserData(key));
    }

    private static @Nullable TextEditor textEditor(FileEditorManager source, VirtualFile file) {
        for (FileEditor editor : source.getEditors(file)) {
            if (editor instanceof TextEditor e) return e;
        }
        return null;
    }

    /** @return tag path → fold region of the tag, null when the editor is not of an xml file */
    private @Nullable Map<String, FoldRegion> regions(Editor editor) {
        if (project.isDisposed() || editor.isDisposed()) return null;
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (!(psiFile instanceof XmlFile) || psiFile.getLanguage() != XMLLanguage.INSTANCE) return null;
        Map<String, FoldRegion> regions = new HashMap<>();
        for (FoldRegion region : editor.getFoldingModel().getAllFoldRegions()) {
            XmlTag tag = tagOf(psiFile, region);
            if (tag == null) continue;
            regions.putIfAbsent(path(tag), region);
        }
        return regions;
    }

    /**
     * a tag's region is from the end of the name (or the last attribute on the first line) to the end of the tag - 1,
     * see {@code XmlCodeFoldingBuilder#getRangeToFold}
     *
     * @return a tag folded by the region, null when the region is not of a tag (comments, attributes etc.)
     */
    static @Nullable XmlTag tagOf(PsiFile psiFile, FoldRegion region) {
        if (!region.isValid()) return null;
        PsiElement element = psiFile.findElementAt(region.getStartOffset());
        for (XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false); tag != null; tag = tag.getParentTag()) {
            TextRange range = tag.getTextRange();
            if (range.getStartOffset() < region.getStartOffset() && range.getEndOffset() - 1 == region.getEndOffset()) {
                return tag;
            }
        }
        return null;
    }

    /** @return e.g. {@code project[0]/build[0]/plugins[0]/plugin[2]}, an index is among the siblings of the same name */
    static String path(XmlTag tag) {
        List<String> segments = new ArrayList<>();
        for (XmlTag t = tag; t != null; t = t.getParentTag()) {
            segments.add(segment(t.getName(), siblingIndex(t)));
        }
        Collections.reverse(segments);
        return String.join("/", segments);
    }

    static String segment(String name, int index) {
        return name + "[" + index + "]";
    }

    private static int siblingIndex(XmlTag tag) {
        XmlTag parent = tag.getParentTag();
        if (parent == null) return 0;
        int index = 0;
        for (XmlTag sibling : parent.getSubTags()) {
            if (sibling == tag) return index;
            if (sibling.getName().equals(tag.getName())) index++;
        }
        return index;
    }
}
