/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.structure;

import java.util.Collection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.java.JavaClassTreeElement;
import com.intellij.ide.structureView.impl.java.JavaFileTreeElement;
import com.intellij.ide.structureView.impl.java.PsiMethodTreeElement;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * DisplayNameFileTreeElement, replaces java's class and method elements by the ones showing {@code @DisplayName}.
 * <p>
 * elements are subclassed (not wrapped) because java's filters, sorters and groupers
 * check element classes.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-02 nsano initial version <br>
 */
class DisplayNameFileTreeElement extends JavaFileTreeElement {

    static final String DISPLAY_NAME = "org.junit.jupiter.api.DisplayName";

    DisplayNameFileTreeElement(PsiClassOwner file) {
        super(file);
    }

    @Override
    public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
        return replace(super.getChildrenBase());
    }

    /** replaces java's plain elements by ours */
    static Collection<StructureViewTreeElement> replace(Collection<StructureViewTreeElement> elements) {
        return elements.stream().map(e -> {
            // exact classes only, subclasses like JavaAnonymousClassTreeElement have their own presentations
            if (e.getClass() == JavaClassTreeElement.class) {
                JavaClassTreeElement c = (JavaClassTreeElement) e;
                PsiClass psi = c.getElement();
                if (psi != null) return (StructureViewTreeElement) new DisplayNameClassTreeElement(psi, c.isInherited());
            } else if (e.getClass() == PsiMethodTreeElement.class) {
                PsiMethodTreeElement m = (PsiMethodTreeElement) e;
                PsiMethod psi = m.getElement();
                if (psi != null) return new DisplayNameMethodTreeElement(psi, m.isInherited());
            }
            return e;
        }).toList();
    }

    /** @return {@code @DisplayName} value, null when not annotated */
    static @Nullable String displayName(@Nullable PsiModifierListOwner owner) {
        if (owner == null || owner.getModifierList() == null) return null;
        PsiAnnotation annotation = owner.getModifierList().findAnnotation(DISPLAY_NAME);
        if (annotation == null) return null;
        String value = AnnotationUtil.getStringAttributeValue(annotation, "value");
        return value == null || value.isBlank() ? null : value;
    }

    /** joins a display name and an original location string */
    static @Nullable String location(@Nullable String displayName, @Nullable String original) {
        if (displayName == null) return original;
        if (original == null || original.isEmpty()) return displayName;
        return displayName + " " + original;
    }

    /** class element, for {@code @Nested} classes */
    static class DisplayNameClassTreeElement extends JavaClassTreeElement {

        DisplayNameClassTreeElement(PsiClass c, boolean inherited) {
            super(c, inherited);
        }

        @Override
        public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
            return replace(super.getChildrenBase());
        }

        @Override
        public String getLocationString() {
            return location(displayName(getElement()), super.getLocationString());
        }

        @Override
        public boolean isSearchInLocationString() {
            return true;
        }
    }

    /** method element */
    static class DisplayNameMethodTreeElement extends PsiMethodTreeElement {

        DisplayNameMethodTreeElement(PsiMethod m, boolean inherited) {
            super(m, inherited);
        }

        @Override
        public String getLocationString() {
            return location(displayName(getElement()), super.getLocationString());
        }

        @Override
        public boolean isSearchInLocationString() {
            return true;
        }
    }
}
