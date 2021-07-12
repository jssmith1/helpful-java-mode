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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ErrorListener {
    private final ErrorURLAssembler URL_ASSEMBLER;
    private String lastUrl;

    public ErrorListener() {
        URL_ASSEMBLER = new ErrorURLAssembler(true);
        lastUrl = URL_ASSEMBLER.getDefaultUrl();
    }

    public String getLastUrl() {
        return lastUrl;
    }

    public void updateAvailablePage(PreprocessedSketch sketch) {
        IProblem[] compilerErrors = sketch.compilationUnit.getProblems();
        lastUrl = Arrays.stream(compilerErrors).filter(
                (error) -> sketch.mapJavaToSketch(error) != PreprocessedSketch.SketchInterval.BEFORE_START
        ).map(
                (error) -> getErrorPageUrl(error, sketch.compilationUnit)
        ).filter(Optional::isPresent).findFirst().orElse(Optional.empty()).orElse(URL_ASSEMBLER.getDefaultUrl());
    }

    public void updateAvailablePage(String url) {
        lastUrl = url;
    }

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
                return URL_ASSEMBLER.getParamMismatchURL(problemNode);
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
                return URL_ASSEMBLER.getMethodCallWrongTypeURL(problemArguments[0], problemArguments[1], problemNode);
        }

        return Optional.empty();
    }

}
