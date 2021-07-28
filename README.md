# helpful-java-mode
A Processing mode with more helpful error explanations for Processing 3.

# Build
helpful-java-mode uses Ant for builds. There are several build tasks available:
* `build` - builds your mode and puts the output in the "dist" folder
* `install` - builds your mode and places the output in your modes directory
* `run` - builds and installs your mode and then starts Processing
* `clean` - deletes the "build" and "dist" folders to clear all build output

A more complete explanation of the build options available is at the [original template](https://github.com/soir20/processing-mode-template). In particular, you must build the Processing source at least once before building the mode.

## IntelliJ Setup
IntelliJ requires some additional setup to recognize the imports from the Processing source code correctly in its editor.

You need to add the `app`, `core`, and `java` folders in the Processing source as modules for your mode project.
* File > New > **Module** from Existing Sources
* Select the folder you want to add from the Processing source (`app`, `core`, or `java`).
* Select "Create module from existing sources."
* Keep pressing "Next" while IntelliJ lists the libraries that will be imported.
* "Finish."
* Follow these steps again for the other folders.

If you already have the Processing source code set up for IntelliJ with modules, you can use the existing ones instead of overwriting them.

Finally, add the new modules as dependencies to your mode's module. You can do this by hovering over code marked as an error in IntelliJ and selecting "Add module as dependency." You can also add the dependencies manually:
* File > Project Structure > Modules
* Select your mode's module.
* Press the plus sign button (alt + insert) to add modules from Processing as dependencies.

You also need to add build.xml as an Ant build file:
* View > Tool Windows > Ant
* "Add Ant build file" in the tool window that opens.
* Choose "[build.xml](build.xml)" in the root of this repository.

# Run
After installing, the mode will be available to all Processing executables on your machine. Choose "Helpful Java" in the menu in the top right corner of Processing to enable the mode.