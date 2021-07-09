package io.github.soir20.mode.processingmode;

import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import processing.app.Base;
import processing.app.Mode;
import processing.app.Problem;
import processing.app.ui.EditorException;
import processing.app.ui.EditorFooter;
import processing.app.ui.EditorState;
import processing.mode.java.JavaEditor;

public class HelpfulJavaEditor extends JavaEditor {
    private WebView webView;

    protected HelpfulJavaEditor(Base base, String path, EditorState state, Mode mode) throws EditorException {
        super(base, path, state, mode);
    }

    @Override
    public void updateEditorStatus() {
        super.updateEditorStatus();

        Problem currentProblem = findProblem(textarea.getCaretLine());
        javafx.application.Platform.runLater(() -> {
            if (webView != null && currentProblem != null) {
                //webView.getEngine().load(currentProblem.getMatchingRefURL());
            }
        });
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

        footer.addPanel(embedPanel, "Hints", "/lib/footer/hint");
    }
}
