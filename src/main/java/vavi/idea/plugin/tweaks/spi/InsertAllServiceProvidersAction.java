/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.spi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * InsertAllServiceProvidersAction, in the completion popup of a service loader file ({@code META-INF/services/foo.Bar}),
 * inserts all implementations of the service at once, instead of the selected one.
 * <p>
 * candidates are the classes in the production sources of the file's module which {@link java.util.ServiceLoader}
 * can load (public concrete classes, static nested ones included, having a public no-arg constructor
 * or a public static {@code provider()} method), matching the text typed in the line.
 * classes already listed in the file are skipped.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-04 nsano initial version <br>
 */
public class InsertAllServiceProvidersAction extends AnAction {

    public static final String ID = "vavi.tweaks.InsertAllServiceProviders";

    /** the language id of {@code com.intellij.spi.SPIFileType} (java-impl) */
    static final String SPI_LANGUAGE_ID = "SPI";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        Project project = e.getProject();
        boolean enabled = editor != null && project != null && !DumbService.isDumb(project);
        if (enabled) {
            LookupEx lookup = LookupManager.getActiveLookup(editor);
            enabled = lookup != null && lookup.isCompletion() && isSpi(lookup.getPsiFile());
        }
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        Project project = e.getProject();
        if (editor == null || project == null) return;
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (!isSpi(file)) return;

        LookupEx lookup = LookupManager.getActiveLookup(editor);
        if (lookup != null) lookup.hideLookup(true);

        Document document = editor.getDocument();
        int line = document.getLineNumber(editor.getCaretModel().getOffset());
        TextRange lineRange = new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line));
        String typed = document.getText(new TextRange(lineRange.getStartOffset(), editor.getCaretModel().getOffset())).trim();
        List<String> others = new ArrayList<>(document.getText().lines().toList());
        if (line < others.size()) others.remove(line);

        List<String> candidates = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> ReadAction.compute(() -> candidates(project, file)), "Searching service providers", true, project);
        if (candidates == null) return;
        List<String> names = missing(candidates, entries(others), typed);
        if (names.isEmpty()) {
            HintManager.getInstance().showInformationHint(editor, "No more service providers of " + file.getName());
            return;
        }

        String text = String.join("\n", names);
        WriteCommandAction.runWriteCommandAction(project, "Insert All Service Providers", null, () -> {
            document.replaceString(lineRange.getStartOffset(), lineRange.getEndOffset(), text);
            editor.getCaretModel().moveToOffset(lineRange.getStartOffset() + text.length());
        }, file);
    }

    static boolean isSpi(@Nullable PsiFile file) {
        return file != null && SPI_LANGUAGE_ID.equals(file.getLanguage().getID());
    }

    /** @return jvm names of loadable implementations in the production sources of the file's module */
    private static List<String> candidates(Project project, PsiFile file) {
        PsiClass service = JavaPsiFacade.getInstance(project).findClass(file.getName(), file.getResolveScope());
        if (service == null) return List.of();

        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        VirtualFile vf = file.getVirtualFile();
        Module module = vf != null ? index.getModuleForFile(vf) : null;
        GlobalSearchScope scope = module != null ? module.getModuleScope(false) : GlobalSearchScope.projectScope(project);

        List<String> result = new ArrayList<>();
        ClassInheritorsSearch.search(service, scope, true).forEach(c -> {
            VirtualFile cf = c.getContainingFile() != null ? c.getContainingFile().getVirtualFile() : null;
            if (cf == null || !index.isInSourceContent(cf) || index.isInTestSourceContent(cf)) return true;
            if (!isLoadable(c)) return true;
            String name = ClassUtil.getJVMClassName(c);
            if (name != null) result.add(name);
            return true;
        });
        return result;
    }

    /** @return true when {@link java.util.ServiceLoader} can instantiate the class */
    private static boolean isLoadable(PsiClass c) {
        if (c.getQualifiedName() == null) return false; // anonymous, local
        if (c.isInterface() || c.isAnnotationType()) return false;
        for (PsiClass o = c; o != null; o = o.getContainingClass()) {
            if (!o.hasModifierProperty(PsiModifier.PUBLIC)) return false;
            PsiClass outer = o.getContainingClass();
            if (outer != null && !o.hasModifierProperty(PsiModifier.STATIC) &&
                    !o.isInterface() && !o.isEnum() && !o.isRecord() && !outer.isInterface()) return false; // inner class
        }
        for (PsiMethod m : c.findMethodsByName("provider", false)) {
            if (m.hasModifierProperty(PsiModifier.PUBLIC) && m.hasModifierProperty(PsiModifier.STATIC) &&
                    !m.hasParameters()) return true;
        }
        if (c.isEnum() || c.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
        PsiMethod[] constructors = c.getConstructors();
        if (constructors.length == 0) return !c.isRecord() || c.getRecordComponents().length == 0;
        for (PsiMethod m : constructors) {
            if (m.hasModifierProperty(PsiModifier.PUBLIC) && !m.hasParameters()) return true;
        }
        return false;
    }

    /** @return class names listed in lines of a service loader file, comments and blanks are skipped */
    static Set<String> entries(Collection<String> lines) {
        Set<String> result = new HashSet<>();
        for (String line : lines) {
            int p = line.indexOf('#');
            String name = (p >= 0 ? line.substring(0, p) : line).trim();
            if (!name.isEmpty()) result.add(name);
        }
        return result;
    }

    /** @return sorted candidates which match the typed prefix and are not listed yet */
    static List<String> missing(Collection<String> candidates, Set<String> existing, String typed) {
        CamelHumpMatcher matcher = typed.isEmpty() ? null : new CamelHumpMatcher(typed, false);
        Set<String> result = new TreeSet<>();
        for (String name : candidates) {
            if (existing.contains(name)) continue;
            if (matcher != null && !matcher.prefixMatches(name)) continue;
            result.add(name);
        }
        return new ArrayList<>(result);
    }
}
