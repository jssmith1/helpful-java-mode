package io.github.soir20.mode.helpfuljava;

import io.github.soir20.mode.helpfuljava.ui.HelpfulJavaEditor;
import processing.app.Base;
import processing.app.Mode;
import processing.app.ui.Editor;
import processing.app.ui.EditorException;
import processing.app.ui.EditorState;
import processing.mode.java.JavaMode;

import java.io.File;

/**
 * The main class that contains information about your mode. You can extend
 * {@link Mode} instead if you don't want to make a Java mode.
 * @author soir20
 */
public class HelpfulJavaMode extends JavaMode {
    public HelpfulJavaMode(Base base, File folder) {
        super(base, folder);
    }

    /**
     * Gets the display name of this mode.
     * @return the display name of this mode
     */
    @Override
    public String getTitle() {
        return "Helpful Java";
    }

    /**
     * Creates the editor for this mode.
     * @param base      the base class for the main Processing app
     * @param path      the path to the currently-open document
     * @param state     whether the editor is reopening a sketch or creating a new one
     * @return the editor for this mode
     * @throws EditorException an issue creating the editor
     */
    @Override
    public Editor createEditor(Base base, String path, EditorState state) throws EditorException {
        return new HelpfulJavaEditor(base, path, state, this);
    }

    /**
     * Retrieve the ClassLoader for JavaMode. This is used by the compiler to load
     * ECJ classes. Thanks to Ben Fry. Thanks to Joel Moniz for updating this for
     * Processing 3.0.
     * @return the class loader from java mode
     */
    @Override
    public ClassLoader getClassLoader() {
        final String JAVA_MODE_NAME = JavaMode.class.getName();

        for (Mode mode : base.getModeList()) {
            if (mode.getClass().getName().equals(JAVA_MODE_NAME)) {
                return mode.getClassLoader();
            }
        }
        
        /* If we return null here, Processing will throw an exception when the compiler is run,
           obscuring the cause of the problem. We'll give a descriptive error message instead. */
        throw new IllegalStateException("Java mode doesn't seem to be loaded. Can't compile sketches.");

    }

}
