/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.tweaks.clamp;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import vavi.idea.plugin.tweaks.clamp.ClampMigration.Arg;


/**
 * UnsafeClampMigrationSuppressor, hides "Math.clamp() can be used" (MathClampMigration) where the replacement
 * the quick fix makes is not equivalent: a wrong value to clamp is offered, or {@code min <= max} is not proven
 * by the data flow (then {@code Math.clamp()} may throw). see {@link ClampMigration}.
 * <p>
 * the suppression also applies to "fix all" and code cleanup, which take the first offer.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-11-02 nsano initial version <br>
 */
public class UnsafeClampMigrationSuppressor implements InspectionSuppressor {

    static final String TOOL_ID = "MathClampMigration";

    @Override
    public boolean isSuppressedFor(PsiElement element, String toolId) {
        if (!TOOL_ID.equals(toolId)) {
            return false;
        }
        PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
        if (call == null) {
            return false;
        }
        String top = mathFunction(call, "min", "max");
        if (top == null) {
            return false;
        }
        String sub = top.equals("min") ? "max" : "min";
        CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(call.getArgumentList());
        if (result == null) {
            return false; // the ide doesn't report it
        }
        PsiExpression[] args = call.getArgumentList().getExpressions();
        Arg<PsiExpression> first = arg(result, args[0], sub);
        Arg<PsiExpression> second = arg(result, args[1], sub);
        if (!first.hasChildren() && !second.hasChildren()) {
            return false;
        }

        PsiExpression lhs = PsiUtil.skipParenthesizedExprUp(call.getParent()) instanceof PsiAssignmentExpression a ?
                a.getLExpression() : null;
        EquivalenceChecker checker = EquivalenceChecker.getCanonicalPsiEquivalence();
        Predicate<PsiExpression> assigned = e -> lhs != null && checker.expressionsAreEquivalent(e, lhs);
        BiPredicate<PsiExpression, PsiExpression> lessOrEqual = (lo, hi) -> lessOrEqual(result.getDfType(lo), result.getDfType(hi));

        return !ClampMigration.isSafe(top.equals("min"), first, second, assigned, lessOrEqual);
    }

    @Override
    public SuppressQuickFix[] getSuppressActions(PsiElement element, String toolId) {
        return SuppressQuickFix.EMPTY_ARRAY;
    }

    /** proven for any value by the data flow */
    static boolean lessOrEqual(DfType lo, DfType hi) {
        if (lo == DfType.TOP || hi == DfType.TOP || lo == DfType.BOTTOM || hi == DfType.BOTTOM) {
            return false;
        }
        if (lo instanceof DfConstantType<?> l && l.getValue() instanceof Number ln &&
                hi instanceof DfConstantType<?> h && h.getValue() instanceof Number hn) {
            return ln.doubleValue() <= hn.doubleValue(); // false for NaN
        }
        // no value of lo is greater than a value of hi
        return lo.meetRelation(RelationType.GT, hi) == DfType.BOTTOM;
    }

    /** as MathClampMigrationInspection builds its ClampInfo */
    private static Arg<PsiExpression> arg(CommonDataflow.DataflowResult result, PsiExpression argument, String sub) {
        PsiExpression e = PsiUtil.skipParenthesizedExprDown(argument);
        if (e == null) {
            e = argument;
        }
        if (e instanceof PsiMethodCallExpression c && mathFunction(c, sub) != null) {
            PsiExpression[] args = c.getArgumentList().getExpressions();
            return Arg.call(e, arg(result, args[0], sub), arg(result, args[1], sub));
        }
        boolean constant = result.getDfType(e) instanceof DfConstantType<?> c && c.getValue() != null;
        boolean staticMember = false;
        if (e.getReference() != null && e.getReference().resolve() instanceof PsiModifierListOwner owner) {
            PsiModifierList modifiers = owner.getModifierList();
            staticMember = modifiers != null && modifiers.hasModifierProperty(PsiModifier.STATIC);
        }
        return Arg.leaf(e, constant, staticMember);
    }

    /** @return the name if the call is a 2 args {@code java.lang.Math} method of the names, null if not */
    private static String mathFunction(PsiMethodCallExpression call, String... names) {
        String name = call.getMethodExpression().getReferenceName();
        if (name == null || call.getArgumentList().getExpressionCount() != 2) {
            return null;
        }
        for (String n : names) {
            if (n.equals(name)) {
                PsiMethod method = call.resolveMethod();
                PsiClass c = method != null ? method.getContainingClass() : null;
                return c != null && CommonClassNames.JAVA_LANG_MATH.equals(c.getQualifiedName()) ? n : null;
            }
        }
        return null;
    }
}
