package io.github.soir20.mode.processingmode.ui;

import io.github.soir20.mode.processingmode.pdex.ErrorListener;
import processing.app.Language;
import processing.app.ui.Editor;
import processing.app.ui.EditorButton;
import processing.mode.java.JavaToolbar;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

public class HelpfulJavaToolbar extends JavaToolbar {
    private final ErrorListener LISTENER;
    private final Consumer<String> UPDATE_PAGE_ACTION;

    public HelpfulJavaToolbar(Editor editor, ErrorListener listener, Consumer<String> updatePageAction) {
        super(editor);
        LISTENER = listener;
        UPDATE_PAGE_ACTION = updatePageAction;
    }

    @Override
    public void addModeButtons(Box box, JLabel label) {
        EditorButton helpButton = new EditorButton(this, "/lib/toolbar/debug", Language.text("toolbar.debug")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                UPDATE_PAGE_ACTION.accept(LISTENER.getLastUrl());
            }
        };

        box.add(helpButton);
        addGap(box);
        super.addModeButtons(box, label);
    }
}
