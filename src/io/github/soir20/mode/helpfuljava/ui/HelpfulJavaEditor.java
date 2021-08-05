package io.github.soir20.mode.helpfuljava.ui;

import io.github.soir20.mode.helpfuljava.pdex.ErrorURLAssembler;
import io.github.soir20.mode.helpfuljava.pdex.ErrorListener;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import processing.app.Base;
import processing.app.Mode;
import processing.app.Preferences;
import processing.app.Problem;
import processing.app.SketchException;
import processing.app.ui.EditorException;
import processing.app.ui.EditorFooter;
import processing.app.ui.EditorState;
import processing.app.ui.EditorToolbar;
import processing.app.ui.Toolkit;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.mode.java.JavaEditor;
import processing.mode.java.pdex.PreprocessedSketch;

import java.awt.*;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static processing.mode.java.JavaMode.errorCheckEnabled;

/**
 * A {@link JavaEditor} with an additional tab to display compiler error hints.
 * @author soir20
 */
public class HelpfulJavaEditor extends JavaEditor {
    private JFXPanel hintsPanel;
    private WebView webView;

    private ErrorURLAssembler urlAssembler;
    private ErrorListener listener;
    private Consumer<PreprocessedSketch> preprocErrorPageHandler;
    private ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> scheduledUiUpdate;

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

        // If we don't set this, JavaFX will shut down when we change modes and stop loading error pages.
        Platform.setImplicitExit(false);

        // Set the default error page but keep the first tab as the console
        setErrorPage(listener.getLastUrl());
        footer.setPanel(console);

        /* createToolbar is called in the constructor, so we have to let that method
           create the listener and then register it once the preprocessing service
           has also been created. */
        updateListenerRegistration();

        // Manually unload the page so its unload hooks are triggered on close
        Runtime.getRuntime().addShutdownHook(new Thread(() -> setErrorPageSilently("")));

    }

    /**
     * Sets the page currently displayed in the hints tab.
     * and makes the hints tab the active tab.
     * @param url       the URL to display
     */
    public void setErrorPage(String url) {
        footer.setPanel(hintsPanel);
        setErrorPageSilently(url);
    }

    /**
     * Sets the page currently displayed in the hints tab without changing tab.
     * and makes the hints tab the active tab.
     * @param url       the URL to display
     */
    public void setErrorPageSilently(String url) {
        Platform.runLater(() -> {
            if (!url.equals(webView.getEngine().getLocation())) {
                webView.getEngine().load(url);
            }
        });
    }

    /**
     * Checks for an error page update when the editor status bar is updated.
     */
    @Override
    public void updateEditorStatus() {
        super.updateEditorStatus();
        Problem currentProblem = findProblem(textarea.getCaretLine());
        if (currentProblem != null) {
            listener.updateAvailablePage(currentProblem);
        }
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
        urlAssembler = new ErrorURLAssembler(true, 12);
        listener = new ErrorListener(urlAssembler);
        scheduler = Executors.newSingleThreadScheduledExecutor();

        final int DELAY = 650;
        preprocErrorPageHandler = (sketch) -> {
            stopHelpButtonUpdate();
            Runnable uiUpdater = () -> EventQueue.invokeLater(() -> listener.updateAvailablePage(sketch));
            scheduledUiUpdate = scheduler.schedule(uiUpdater, DELAY, TimeUnit.MILLISECONDS);
        };
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
     * Updates the user's preferences and the listener registration.
     */
    @Override
    protected void applyPreferences() {
        super.applyPreferences();
        updateListenerRegistration();

        /* The font size is smaller because of a different DPI in Java apps than Windows. See
        https://stackoverflow.com/questions/28659052/font-size-in-jtextpane-looks-smaller-then-in-other-applications */
        double dpiAdjustment = 1;
        if (PApplet.platform == PConstants.WINDOWS) {
            dpiAdjustment = 72.0 / 96;
        }

        urlAssembler.setFontSize(Toolkit.zoom(
                (int) Math.round(Preferences.getInteger("console.font.size") * dpiAdjustment)
        ));

        if (!listener.hasPage()) {
            listener.updateAvailablePage(urlAssembler.getDefaultUrl());
            setErrorPageSilently(listener.getLastUrl());
        }
    }

    /**
     * Registers the listener if error checking is enabled and unregisters it otherwise.
     * Does nothing if the error checking preference has not changed.
     */
    private void updateListenerRegistration() {

        // Some methods (like applyPreferences) are called before the preprocessing service has been initialized
        if (preprocessingService == null) {
            return;
        }

        if (errorCheckEnabled) {
            preprocessingService.registerListener(preprocErrorPageHandler);
        } else {
            preprocessingService.unregisterListener(preprocErrorPageHandler);
            stopHelpButtonUpdate();
        }
    }

    /**
     * Cancels the next help button UI update.
     */
    private void stopHelpButtonUpdate() {
        if (scheduledUiUpdate != null) {
            scheduledUiUpdate.cancel(true);
        }
    }

    /**
     * Adds the hints tab to this editor's footer.
     * @param footer    the footer to add the tab to
     */
    private void addEditorHints(EditorFooter footer) {
        hintsPanel = new JFXPanel();
        footer.addPanel(hintsPanel, "Hints", "/theme/footer/hint");

        Platform.runLater(() -> {
            webView = new WebView();
            hintsPanel.setScene(new Scene(webView));
        });
    }

}
