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

public class HelpfulJavaEditor extends JavaEditor {
    private WebView webView;
    private ErrorListener listener;

    public HelpfulJavaEditor(Base base, String path, EditorState state, Mode mode) throws EditorException {
        super(base, path, state, mode);

        /* createToolbar is called in the constructor, so we have to let that method
           create the listener and then register it once the preprocessing service
           has also been created. */
        preprocessingService.registerListener(listener::updateAvailablePage);

    }

    public void setErrorPage(String url) {
        javafx.application.Platform.runLater(() -> {
            if (webView != null) {
                webView.getEngine().load(url);
            }
        });
    }

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

    @Override
    public EditorToolbar createToolbar() {
        listener = new ErrorListener();
        return new HelpfulJavaToolbar(this, listener, this::setErrorPage);
    }

    @Override
    public EditorFooter createFooter() {
        EditorFooter footer = super.createFooter();
        addEditorHints(footer);
        return footer;
    }

    private void addEditorHints(EditorFooter footer) {
        JFXPanel embedPanel = new JFXPanel();

        javafx.application.Platform.runLater(() -> {
            webView = new WebView();
            embedPanel.setScene(new Scene(webView));
        });

        footer.addPanel(embedPanel, "Hints", "/theme/footer/hint");
    }
}
