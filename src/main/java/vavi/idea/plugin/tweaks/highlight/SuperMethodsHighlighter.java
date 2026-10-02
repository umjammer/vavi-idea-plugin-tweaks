/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.highlight;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * SuperMethodsHighlighter, when a class or an interface in {@code extends} or {@code implements}
 * of a java class is clicked, highlights the methods of the class which override or implement
 * the clicked one's methods.
 * <p>
 * highlights are removed by the next click, escape or a text change.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-02 nsano initial version <br>
 */
public class SuperMethodsHighlighter implements EditorMouseListener {

    private static final Key<List<RangeHighlighter>> HIGHLIGHTERS = Key.create("vavi.tweaks.superMethodsHighlighters");

    @Override
    public void mouseClicked(@NotNull EditorMouseEvent event) {
        Editor editor = event.getEditor();
        Project project = editor.getProject();
        if (project == null || project.isDisposed()) return;

        clear(editor, project);

        MouseEvent me = event.getMouseEvent();
        if (event.getArea() != EditorMouseEventArea.EDITING_AREA || me.getButton() != MouseEvent.BUTTON1 ||
                me.getClickCount() != 1 || me.isPopupTrigger() ||
                me.isShiftDown() || me.isControlDown() || me.isMetaDown() || me.isAltDown()) return;
        if (DumbService.isDumb(project)) return;

        int offset = event.getOffset();
        ReadAction.nonBlocking(() -> find(project, editor, offset))
                .expireWhen(() -> editor.isDisposed() || project.isDisposed())
                .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState(), targets -> {
                    if (targets == null || targets.isEmpty() || editor.isDisposed()) return;
                    List<RangeHighlighter> highlighters = new ArrayList<>();
                    HighlightManager.getInstance(project).addOccurrenceHighlights(editor,
                            targets.toArray(PsiElement[]::new), EditorColors.SEARCH_RESULT_ATTRIBUTES, true, highlighters);
                    editor.putUserData(HIGHLIGHTERS, highlighters);
                    var statusBar = WindowManager.getInstance().getStatusBar(project);
                    if (statusBar != null) {
                        statusBar.setInfo(targets.size() + " related method(s) highlighted (press Escape to remove)");
                    }
                })
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    /** removes previous highlights */
    private static void clear(Editor editor, Project project) {
        List<RangeHighlighter> highlighters = editor.getUserData(HIGHLIGHTERS);
        if (highlighters == null) return;
        editor.putUserData(HIGHLIGHTERS, null);
        HighlightManager manager = HighlightManager.getInstance(project);
        highlighters.forEach(h -> manager.removeSegmentHighlighter(editor, h));
    }

    /** @return method name identifiers to highlight, null when the offset is not a super type reference */
    private static @Nullable List<PsiElement> find(Project project, Editor editor, int offset) {
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (!(file instanceof PsiJavaFile)) return null;

        PsiElement leaf = file.findElementAt(offset);
        PsiJavaCodeReferenceElement ref = PsiTreeUtil.getParentOfType(leaf, PsiJavaCodeReferenceElement.class);
        if (ref == null) return null;
        // the outermost reference in a reference list, e.g. "Map.Entry<K, V>" not "K"
        while (ref.getParent() instanceof PsiJavaCodeReferenceElement parent) ref = parent;
        if (!(ref.getParent() instanceof PsiReferenceList list)) return null;
        if (!(list.getParent() instanceof PsiClass clazz)) return null;
        if (list != clazz.getExtendsList() && list != clazz.getImplementsList()) return null;
        if (!(ref.resolve() instanceof PsiClass superClass)) return null;

        List<PsiElement> targets = new ArrayList<>();
        for (PsiMethod method : clazz.getMethods()) {
            if (method.isConstructor()) continue;
            if (method.findSuperMethods(superClass).length > 0 && method.getNameIdentifier() != null) {
                targets.add(method.getNameIdentifier());
            }
        }
        return targets;
    }
}
