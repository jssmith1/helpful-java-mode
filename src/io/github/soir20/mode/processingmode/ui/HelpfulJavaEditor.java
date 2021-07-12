package io.github.soir20.mode.processingmode.ui;

import io.github.soir20.mode.processingmode.pdex.PreprocessedErrorListener;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import processing.app.Base;
import processing.app.Mode;
import processing.app.ui.EditorException;
import processing.app.ui.EditorFooter;
import processing.app.ui.EditorState;
import processing.app.ui.EditorToolbar;
import processing.mode.java.JavaEditor;

public class HelpfulJavaEditor extends JavaEditor {
    private WebView webView;
    private PreprocessedErrorListener listener;

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
    public EditorToolbar createToolbar() {
        listener = new PreprocessedErrorListener();
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
