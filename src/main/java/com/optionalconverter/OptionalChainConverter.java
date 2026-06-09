package com.optionalconverter;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OptionalChainConverter {

    static final String ACTION_NAME = "Convert to Optional Chain";

    private OptionalChainConverter() {}

    static final class ChainInfo {
        private final PsiExpression root;
        private final List<PsiMethodCallExpression> mapCalls;
        private final PsiMethodCallExpression outermost;

        ChainInfo(PsiExpression root, List<PsiMethodCallExpression> mapCalls, PsiMethodCallExpression outermost) {
            this.root = root;
            this.mapCalls = mapCalls;
            this.outermost = outermost;
        }

        PsiExpression root() { return root; }
        List<PsiMethodCallExpression> mapCalls() { return mapCalls; }
        PsiMethodCallExpression outermost() { return outermost; }
    }

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
            if (grandParent instanceof PsiMethodCallExpression && !hasArgs((PsiMethodCallExpression) grandParent)) {
                outermost = (PsiMethodCallExpression) grandParent;
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

        while (current instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) current;
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

        if (root instanceof PsiReferenceExpression
                && ((PsiReferenceExpression) root).resolve() instanceof PsiClass) {
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

        if (expression instanceof PsiReferenceExpression) {
            PsiElement resolved = ((PsiReferenceExpression) expression).resolve();

            if (resolved instanceof PsiLocalVariable) {
                if (!hasWriteUsages((PsiLocalVariable) resolved)
                        && isDefinitelyNotNull(((PsiVariable) resolved).getInitializer())) {
                    return true;
                }
            } else if (resolved instanceof PsiVariable
                    && ((PsiVariable) resolved).hasModifierProperty(PsiModifier.FINAL)) {
                if (isDefinitelyNotNull(((PsiVariable) resolved).getInitializer())) return true;
            }

            if (resolved instanceof PsiModifierListOwner) {
                return NullableNotNullManager.getInstance(expression.getProject())
                        .isNotNull((PsiModifierListOwner) resolved, false);
            }
        }

        if (expression instanceof PsiMethodCallExpression) {
            PsiMethod method = ((PsiMethodCallExpression) expression).resolveMethod();
            if (method != null) {
                return NullableNotNullManager.getInstance(expression.getProject()).isNotNull(method, false);
            }
        }

        return false;
    }

    private static boolean hasWriteUsages(PsiLocalVariable variable) {
        for (PsiReference ref : ReferencesSearch.search(variable).findAll()) {
            PsiElement element = ref.getElement();
            PsiElement parent = element.getParent();
            if (parent instanceof PsiAssignmentExpression
                    && PsiTreeUtil.isAncestor(((PsiAssignmentExpression) parent).getLExpression(), element, false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasArgs(PsiMethodCallExpression call) {
        return call.getArgumentList().getExpressions().length > 0;
    }

    private static String resolveTypeFqn(PsiType type) {
        if (!(type instanceof PsiClassType)) return null;
        PsiClass psiClass = ((PsiClassType) type).resolve();
        if (psiClass == null || psiClass instanceof PsiTypeParameter) return null;
        return psiClass.getQualifiedName();
    }
}
