package com.example.plugin;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;


import javax.swing.*;
import java.util.Objects;

public class MethodGutterIconRenderer extends GutterIconRenderer {
    private final String methodName;
    private final Editor editor;
    private final RangeHighlighter highlighter;
    private final MethodHighlighter methodHighlighter;
    private final MethodMetadata metadata;

    public MethodGutterIconRenderer(String methodName, Editor editor, RangeHighlighter highlighter,
                                    MethodHighlighter methodHighlighter, MethodMetadata metadata) {
        this.methodName = methodName;
        this.editor = editor;
        this.highlighter = highlighter;
        this.methodHighlighter = methodHighlighter;
        this.metadata = metadata;
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return AllIcons.General.Warning; // Use IntelliJ's built-in warning icon
    }

    @Nullable
    @Override
    public String getTooltipText() {
        String joinedParams = String.join(", ", metadata.getParams());
        return String.format(
                "<html>Arguments <b>'%s'</b> from method <b>'%s'</b> have previously suffered from " +
                        "Default Argument Breaking Changes (DABCs)<br>in the version <b>'%s'</b> of the library. " +
                        "If you want to ignore this warning click the icon.</html>",
                joinedParams,
                methodName,
                metadata.getVersion()
        );
    }


    @Nullable
    @Override
    public AnAction getClickAction() {
        return new AnAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                // Ensure the highlighter is still valid
                if (highlighter.isValid()) {
                    MarkupModel markupModel = editor.getMarkupModel();
                    markupModel.removeHighlighter(highlighter); // Remove the highlight
                    methodHighlighter.ignoreWarning(methodName);
                }
            }
        };
    }

    @Override
    public Alignment getAlignment() {
        return Alignment.LEFT;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MethodGutterIconRenderer that = (MethodGutterIconRenderer) obj;
        return Objects.equals(methodName, that.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodName);
    }
}
