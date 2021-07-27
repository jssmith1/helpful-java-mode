package io.github.soir20.mode.processingmode.pdex;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import processing.mode.java.pdex.ASTUtils;
import processing.mode.java.pdex.PreprocessedSketch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Keeps track of the last error URL when an error is detected.
 * @author soir20
 */
public class ErrorListener {
    private final List<Consumer<String>> LISTENERS;
    private final ErrorURLAssembler URL_ASSEMBLER;
    private String lastUrl;

    /**
     * Creates a new listener.
     * @param urlAssembler  the assembler for error URLs
     */
    public ErrorListener(ErrorURLAssembler urlAssembler) {
        LISTENERS = new ArrayList<>();
        URL_ASSEMBLER = urlAssembler;
        lastUrl = URL_ASSEMBLER.getDefaultUrl();
    }

    /**
     * Adds a listener for when the error page changes.
     * @param listener      a listener that fires when the error page changes
     *                      with the new page as its parameter
     */
    public void addListener(Consumer<String> listener) {
        LISTENERS.add(listener);
    }

    /**
     * Determines whether an error page is available (and is not the default page).
     * @return whether an error page is available
     */
    public boolean hasPage() {
        return !lastUrl.equals(URL_ASSEMBLER.getDefaultUrl()) && !lastUrl.startsWith(URL_ASSEMBLER.getBaseUrl() + "?");
    }

    /**
     * Gets the last error URL sent to the listener. If no URLs have been sent,
     * the default one from the URL assembler is returned. URLs are already
     * marked as embedded.
     * @return the last error URL sent to the listener
     */
    public String getLastUrl() {
        return lastUrl;
    }

    /**
     * Sets the available page during preprocessing. Serves as a listener for
     * the {@link processing.mode.java.pdex.PreprocessingService}. Fires all listeners.
     * @param sketch        the preprocessed sketch
     */
    public void updateAvailablePage(PreprocessedSketch sketch) {
        IProblem[] compilerErrors = sketch.compilationUnit.getProblems();
        updateAvailablePage(
                Arrays.stream(compilerErrors).filter(
                        (error) -> sketch.mapJavaToSketch(error) != PreprocessedSketch.SketchInterval.BEFORE_START
                ).map(
                        (error) -> getErrorPageUrl(error, sketch.compilationUnit)
                ).filter(Optional::isPresent).findFirst().orElse(Optional.empty()).orElse(URL_ASSEMBLER.getDefaultUrl())
        );
    }

    /**
     * Sets the available page directly and fires all listeners.
     * @param url       the new available page
     */
    public void updateAvailablePage(String url) {
        if (!lastUrl.equals(url)) {
            lastUrl = url;
            LISTENERS.forEach((listener) -> listener.accept(lastUrl));
        }
    }

    /**
     * Gets the URL for an error page based on a preprocessed compiler error.
     * @param compilerError     the compiler error
     * @param ast               the abstract syntax tree root
     * @return the URL for the matching error page or an empty if the error is unknown
     */
    private Optional<String> getErrorPageUrl(IProblem compilerError, ASTNode ast) {
        String[] problemArguments = compilerError.getArguments();
        ASTNode problemNode = ASTUtils.getASTNodeAt(
                ast,
                compilerError.getSourceStart(),
                compilerError.getSourceEnd()
        );

        switch (compilerError.getID()) {
            case IProblem.MustDefineEitherDimensionExpressionsOrInitializer:
                return URL_ASSEMBLER.getArrDimURL(problemNode);
            case IProblem.IllegalDimension:
                return URL_ASSEMBLER.getTwoDimArrURL(problemNode);
            case IProblem.CannotDefineDimensionExpressionsWithInit:
                return URL_ASSEMBLER.getTwoInitializerArrURL(problemNode);
            case IProblem.UndefinedMethod:
                return URL_ASSEMBLER.getMissingMethodURL(problemNode);
            case IProblem.ParameterMismatch:
                return URL_ASSEMBLER.getParamMismatchURL(problemArguments[0], problemNode);
            case IProblem.ShouldReturnValue:
                return URL_ASSEMBLER.getMissingReturnURL(problemNode);
            case IProblem.TypeMismatch:
            case IProblem.ReturnTypeMismatch:
                String providedType = problemArguments[0];
                String requiredType = problemArguments[1];
                return URL_ASSEMBLER.getTypeMismatchURL(providedType, requiredType, problemNode);
            case IProblem.UndefinedType:
                return URL_ASSEMBLER.getMissingTypeURL(problemArguments[0], problemNode);
            case IProblem.UnresolvedVariable:
                return URL_ASSEMBLER.getMissingVarURL(problemArguments[0], problemNode);
            case IProblem.UninitializedLocalVariable:
                return URL_ASSEMBLER.getUninitializedVarURL(problemArguments[0], problemNode);
            case IProblem.StaticMethodRequested:
                return URL_ASSEMBLER.getStaticErrorURL(problemArguments[0], problemArguments[1], problemNode);
            case IProblem.UndefinedField:
            case IProblem.UndefinedName:
                return URL_ASSEMBLER.getVariableDeclaratorsURL(problemNode);
            case IProblem.ParsingErrorInsertToComplete:
                List<String> argsList = Arrays.asList(problemArguments);

                // Handle incorrect variable declaration
                if (argsList.contains("VariableDeclarators")) {
                    return URL_ASSEMBLER.getVariableDeclaratorsURL(problemNode);
                }

                ASTNode parent = problemNode.getParent();
                ASTNode grandparent = problemNode.getParent().getParent();
                if (parent instanceof ArrayCreation || grandparent instanceof ArrayAccess || argsList.contains("Dimensions")
                        || (parent instanceof FieldDeclaration && ((FieldDeclaration) parent).getType().isArrayType())) {
                    return URL_ASSEMBLER.getIncorrectVarDeclarationURL(problemNode);
                }

                /* Incorrect control structures almost always have one of these statements as the
                   problem node, its parent, or its grandparent. Use reflection here instead of regular
                   instanceof to make the code more concise and readable. */
                Class<?>[] statementClasses = {ForStatement.class, TryStatement.class, DoStatement.class,
                        SwitchStatement.class, IfStatement.class, EnhancedForStatement.class, WhileStatement.class};
                ASTNode[] nearbyNodes = {problemNode, parent, grandparent};
                for (ASTNode node : nearbyNodes) {
                    for (Class<?> statementClass : statementClasses) {
                        if (statementClass.isInstance(node)) {

                          /* Issues with control structures are most likely integer-related,
                             and the type isn't usually given in the problem arguments. */
                            return URL_ASSEMBLER.getUnexpectedTokenURL("int");

                        }
                    }
                }

                break;
            case IProblem.ParsingErrorDeleteToken:
                return URL_ASSEMBLER.getUnexpectedTokenURL(problemArguments[0]);
            case IProblem.NoMessageSendOnBaseType:
            case IProblem.NoMessageSendOnArrayType:
                return URL_ASSEMBLER.getMethodCallWrongTypeURL(problemArguments[0], problemArguments[1], problemNode);
        }

        return Optional.empty();
    }

}
