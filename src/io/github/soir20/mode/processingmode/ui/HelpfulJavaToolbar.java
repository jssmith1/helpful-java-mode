package io.github.soir20.mode.processingmode.ui;

import processing.app.Language;
import processing.app.ui.Editor;
import processing.app.ui.EditorButton;
import processing.mode.java.JavaToolbar;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class HelpfulJavaToolbar extends JavaToolbar {
    public HelpfulJavaToolbar(Editor editor) {
        super(editor);
    }

    @Override
    public void addModeButtons(Box box, JLabel label) {
        EditorButton helpButton = new EditorButton(this, "/lib/toolbar/debug", Language.text("toolbar.debug")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("Hello world");
            }
        };

        box.add(helpButton);
        addGap(box);
        super.addModeButtons(box, label);
    }
}
