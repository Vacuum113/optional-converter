package com.optionalconverter;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OptionalChainConverter {

    static final String ACTION_NAME = "Convert to Optional Chain";

    private OptionalChainConverter() {}

    record ChainInfo(PsiExpression root, List<PsiMethodCallExpression> mapCalls,
                     PsiMethodCallExpression outermost) {}

    @Nullable
    static ChainInfo findChain(@NotNull PsiElement element) {
        var outermost = findOutermostCall(element);
        return outermost != null ? analyzeChain(outermost) : null;
    }

    static void convert(@NotNull Project project, @NotNull ChainInfo chain) {
        String text = buildText(chain);
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiExpression expr = factory.createExpressionFromText(text, chain.outermost());
        PsiElement result = chain.outermost().replace(expr);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
    }

    private static PsiMethodCallExpression findOutermostCall(PsiElement element) {
        var call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
        if (call == null || hasArgs(call)) return null;

        var outermost = call;
        PsiElement parent = outermost.getParent();
        while (parent instanceof PsiReferenceExpression) {
            PsiElement grandParent = parent.getParent();
            if (grandParent instanceof PsiMethodCallExpression grandCall && !hasArgs(grandCall)) {
                outermost = grandCall;
                parent = outermost.getParent();
            } else {
                break;
            }
        }
        return outermost;
    }

    private static ChainInfo analyzeChain(PsiMethodCallExpression outermostCall) {
        List<PsiMethodCallExpression> calls = new ArrayList<>();
        PsiExpression current = outermostCall;

        while (current instanceof PsiMethodCallExpression call) {
            if (hasArgs(call)) break;
            calls.add(call);
            current = call.getMethodExpression().getQualifierExpression();
        }

        Collections.reverse(calls);

        PsiExpression root;
        List<PsiMethodCallExpression> mapCalls;

        if (current != null) {
            root = current;
            mapCalls = calls;
        } else {
            if (calls.size() < 2) return null;
            root = calls.get(0);
            mapCalls = List.copyOf(calls.subList(1, calls.size()));
        }

        if (mapCalls.isEmpty()) return null;

        if (root instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiClass) {
            return null;
        }

        return new ChainInfo(root, mapCalls, outermostCall);
    }

    private static String buildText(ChainInfo chain) {
        var sb = new StringBuilder();
        String method = isDefinitelyNotNull(chain.root()) ? "of" : "ofNullable";
        sb.append("java.util.Optional.").append(method).append("(")
                .append(chain.root().getText()).append(")");

        for (var call : chain.mapCalls()) {
            var methodExpr = call.getMethodExpression();
            String name = methodExpr.getReferenceName();
            PsiExpression qualifier = methodExpr.getQualifierExpression();
            String fqn = qualifier != null ? resolveTypeFqn(qualifier.getType()) : null;

            if (fqn != null) {
                sb.append(".map(").append(fqn).append("::").append(name).append(")");
            } else {
                sb.append(".map(v -> v.").append(name).append("())");
            }
        }

        sb.append(".orElse(null)");
        return sb.toString();
    }

    private static boolean isDefinitelyNotNull(PsiExpression expression) {
        if (expression == null) return false;

        if (expression instanceof PsiThisExpression
                || expression instanceof PsiSuperExpression
                || expression instanceof PsiNewExpression
                || expression instanceof PsiLiteralExpression) {
            return true;
        }

        if (expression instanceof PsiReferenceExpression refExpr) {
            PsiElement resolved = refExpr.resolve();

            if (resolved instanceof PsiVariable variable
                    && variable.hasModifierProperty(PsiModifier.FINAL)) {
                if (isDefinitelyNotNull(variable.getInitializer())) return true;
            }

            if (resolved instanceof PsiModifierListOwner owner) {
                return NullableNotNullManager.getInstance(expression.getProject()).isNotNull(owner, false);
            }
        }

        if (expression instanceof PsiMethodCallExpression call) {
            PsiMethod method = call.resolveMethod();
            if (method != null) {
                return NullableNotNullManager.getInstance(expression.getProject()).isNotNull(method, false);
            }
        }

        return false;
    }

    private static boolean hasArgs(PsiMethodCallExpression call) {
        return call.getArgumentList().getExpressions().length > 0;
    }

    private static String resolveTypeFqn(PsiType type) {
        if (!(type instanceof PsiClassType classType)) return null;
        PsiClass psiClass = classType.resolve();
        if (psiClass == null || psiClass instanceof PsiTypeParameter) return null;
        return psiClass.getQualifiedName();
    }
}
