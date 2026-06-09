package com.optionalconverter;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConvertToOptionalChainAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(findChainAt(e) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        var chain = findChainAt(e);
        var project = e.getProject();
        if (chain == null || project == null) return;

        WriteCommandAction.runWriteCommandAction(project, OptionalChainConverter.ACTION_NAME, null,
                () -> OptionalChainConverter.convert(project, chain));
    }

    @Nullable
    private static OptionalChainConverter.ChainInfo findChainAt(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || !(file instanceof PsiJavaFile)) return null;

        var element = file.findElementAt(editor.getCaretModel().getOffset());
        return element != null ? OptionalChainConverter.findChain(element) : null;
    }
}
