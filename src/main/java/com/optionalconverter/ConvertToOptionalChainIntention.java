package com.optionalconverter;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

public class ConvertToOptionalChainIntention extends PsiElementBaseIntentionAction {

    @Override
    public @NotNull String getText() {
        return OptionalChainConverter.ACTION_NAME;
    }

    @Override
    public @NotNull String getFamilyName() {
        return OptionalChainConverter.ACTION_NAME;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        return element.getContainingFile() instanceof PsiJavaFile
                && OptionalChainConverter.findChain(element) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        var chain = OptionalChainConverter.findChain(element);
        if (chain != null) {
            OptionalChainConverter.convert(project, chain);
        }
    }
}
