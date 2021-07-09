package io.github.soir20.mode.processingmode.ui;

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

import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

public class HelpfulJavaEditor extends JavaEditor {
    private WebView webView;

    public HelpfulJavaEditor(Base base, String path, EditorState state, Mode mode) throws EditorException {
        super(base, path, state, mode);

        splitPane.setUI(new BasicSplitPaneUI() {
            public BasicSplitPaneDivider createDefaultDivider() {
                status = new HelpfulJavaEditorStatus(this, HelpfulJavaEditor.this);
                return status;
            }

            // This is copied from processing.app.ui.Editor because we cannot access it directly
            @Override
            public void finishDraggingTo(int location) {
                super.finishDraggingTo(location);
                // JSplitPane issue: if you only make the lower component visible at
                // the last minute, its minimum size is ignored.
                if (location > splitPane.getMaximumDividerLocation()) {
                    splitPane.setDividerLocation(splitPane.getMaximumDividerLocation());
                }
            }

        });
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

        footer.addPanel(embedPanel, "Hints", "/theme/footer/hint");
    }
}
