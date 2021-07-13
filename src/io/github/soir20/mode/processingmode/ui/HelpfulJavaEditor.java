package io.github.soir20.mode.processingmode.ui;

import io.github.soir20.mode.processingmode.pdex.ErrorURLAssembler;
import io.github.soir20.mode.processingmode.pdex.ErrorListener;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import processing.app.Base;
import processing.app.Mode;
import processing.app.SketchException;
import processing.app.ui.EditorException;
import processing.app.ui.EditorFooter;
import processing.app.ui.EditorState;
import processing.app.ui.EditorToolbar;
import processing.mode.java.JavaEditor;

import java.util.Optional;

/**
 * A {@link JavaEditor} with an additional tab to display compiler error hints.
 * @author soir20
 */
public class HelpfulJavaEditor extends JavaEditor {
    private JFXPanel hintsPanel;
    private WebView webView;
    private ErrorListener listener;

    /**
     * Creates a new editor.
     * @param base      base class for main Processing app
     * @param path      path of the currently-open file
     * @param state     whether the editor has a new sketch or is reopening an old sketch
     * @param mode      the mode Processing is currently in
     * @throws EditorException if there is an issue creating the editor
     */
    public HelpfulJavaEditor(Base base, String path, EditorState state, Mode mode) throws EditorException {
        super(base, path, state, mode);

        // Set the default error page but keep the first tab as the console
        setErrorPage(listener.getLastUrl());
        footer.setPanel(console);

        /* createToolbar is called in the constructor, so we have to let that method
           create the listener and then register it once the preprocessing service
           has also been created. */
        preprocessingService.registerListener(listener::updateAvailablePage);
    }

    /**
     * Sets the page currently displayed in the hints tab
     * and makes the hints tab the active tab.
     * @param url       the URL to display
     */
    public void setErrorPage(String url) {
        footer.setPanel(hintsPanel);
        javafx.application.Platform.runLater(() -> {
            if (!url.equals(webView.getEngine().getLocation())) {
                webView.getEngine().load(url);
            }
        });
    }

    /**
     * Updates the available (but not yet shown) error page for on-run errors.
     * @param err       the exception that occurred when trying to run the sketch
     */
    @Override
    public void statusError(Exception err) {
        super.statusError(err);

        if (!(err instanceof SketchException)) {
            return;
        }

        // Get the error page URL
        ErrorURLAssembler urlAssembler = new ErrorURLAssembler(true);
        SketchException sketchErr = (SketchException) err;
        String message = err.getMessage();
        Optional<String> optionalURL = Optional.empty();

        // Not all errors have a line and column
        int line = Math.max(sketchErr.getCodeLine(), 0);
        int column = Math.max(sketchErr.getCodeColumn(), 0);

        String textAboveError = textarea.getText(
                0,
                textarea.getLineStartOffset(line) + column
        );
        if (message.equals("expecting EOF, found '}'") && textAboveError != null) {
            optionalURL = urlAssembler.getClosingCurlyBraceURL(textAboveError);
        } else if (message.startsWith("expecting DOT")) {
            optionalURL = urlAssembler.getIncorrectVarDeclarationURL(textarea, sketchErr);
        } else if (message.equals("It looks like you're mixing \"active\" and \"static\" modes.") && textAboveError != null) {
            optionalURL = urlAssembler.getIncorrectMethodDeclarationURL(textAboveError);
        } else if (message.startsWith("unexpected token:")) {
            String token = message.substring(message.indexOf(':') + 1).trim();
            optionalURL = urlAssembler.getUnexpectedTokenURL(token);
        }

        optionalURL.ifPresent(listener::updateAvailablePage);
    }

    /**
     * Creates the toolbar for this editor. Called during construction. This is
     * marked as public in the base class but should not be called anywhere else.
     * @return the toolbar for this editor
     */
    @Override
    public EditorToolbar createToolbar() {
        listener = new ErrorListener();
        return new HelpfulJavaToolbar(this, listener, this::setErrorPage);
    }

    /**
     * Creates the footer for this editor. Called during construction. This is
     * marked as public in the base class but should not be called anywhere else.
     * @return the footer for this editor
     */
    @Override
    public EditorFooter createFooter() {
        EditorFooter footer = super.createFooter();
        addEditorHints(footer);
        return footer;
    }

    /**
     * Adds the hints tab to this editor's footer.
     * @param footer    the footer to add the tab to
     */
    private void addEditorHints(EditorFooter footer) {
        hintsPanel = new JFXPanel();
        footer.addPanel(hintsPanel, "Hints", "/theme/footer/hint");

        javafx.application.Platform.runLater(() -> {
            webView = new WebView();
            hintsPanel.setScene(new Scene(webView));
        });
    }

}
