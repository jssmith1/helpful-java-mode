package io.github.soir20.mode.helpfuljava.ui;

import io.github.soir20.mode.helpfuljava.pdex.ErrorListener;
import processing.app.ui.Editor;
import processing.app.ui.EditorButton;
import processing.mode.java.JavaToolbar;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

/**
 * A toolbar with a special help button for compiler errors. Otherwise, it is
 * the same as the {@link JavaToolbar}.
 * @author soir20
 */
public class HelpfulJavaToolbar extends JavaToolbar {
    private final ErrorListener LISTENER;
    private final Consumer<String> UPDATE_PAGE_ACTION;
    private EditorButton helpButton;

    private String openErrorUrl;

    /**
     * Creates a new editor toolbar.
     * @param editor            the editor to attach the toolbar to
     * @param listener          the listener that keeps track of the available error page
     * @param updatePageAction  updates the displayed error page on click
     */
    public HelpfulJavaToolbar(Editor editor, ErrorListener listener, Consumer<String> updatePageAction) {
        super(editor);

        // We have to repaint the button to make sure the highlight is shown
        LISTENER = listener;
        LISTENER.addListener((newErrorUrl) -> helpButton.repaint());

        UPDATE_PAGE_ACTION = updatePageAction;
    }

    /**
     * Adds all the buttons to the toolbar.
     * @param box       the box containing all the buttons
     * @param label     the label that shows the button descriptions
     */
    @Override
    public void addModeButtons(Box box, JLabel label) {
        helpButton = new EditorButton(this, "/theme/toolbar/help", "See hints for compiler errors") {
            private final Image HIGHLIGHT_IMAGE = mode.loadImageX("theme/toolbar/help-highlighted");

            @Override
            public void actionPerformed(ActionEvent event) {
                openErrorUrl = LISTENER.getLastUrl();
                UPDATE_PAGE_ACTION.accept(openErrorUrl);
            }

            @Override
            public void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);

                // This can be called even when the error page hasn't updated, so double check if page updated
                if (LISTENER.hasPage() && !LISTENER.getLastUrl().equals(openErrorUrl)) {
                    graphics.drawImage(HIGHLIGHT_IMAGE, 0, 0, getWidth(), getHeight(), this);
                }

            }
        };

        box.add(helpButton);
        addGap(box);
        super.addModeButtons(box, label);
    }

}
