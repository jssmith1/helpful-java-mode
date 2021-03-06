package io.github.soir20.mode.helpfuljava.pdex;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import processing.app.SketchException;
import processing.app.syntax.JEditTextArea;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Creates URLs for errors based on the AST.
 * @author soir20
 */
public class ErrorURLAssembler {
    private static final String URL = "http://139.147.9.247/";
    private final Map<String, String> GLOBAL_PARAMS;

    /**
     * Creates a new URL assembler.
     * @param embedded      whether the pages will be embedded
     * @param fontSize      the base font size in points to use in the page
     */
    public ErrorURLAssembler(boolean embedded, int fontSize) {
        GLOBAL_PARAMS = new HashMap<>();
        if (embedded) {
            GLOBAL_PARAMS.put("embed", "true");
            setFontSize(fontSize);
        }
    }

    /**
     * Sets the base font size to use in the page.
     * @param newSize       the new base font size to use in the page
     */
    public void setFontSize(int newSize) {
        GLOBAL_PARAMS.put("fontsize", String.valueOf(newSize));
    }

    /**
     * Gets the base URL for all pages without parameters.
     * @return  the base URL without parameters
     */
    public String getBaseUrl() {
        return URL;
    }

    /**
     * Gets the default URL for the reference.
     * @return the default URL for the reference
     */
    public String getDefaultUrl() {
        return URL + getGlobalParams(true);
    }

    /**
     * Gets the URL for an extra right curly brace.
     * @param textAboveError    all text in the editor at and above the
     *                          line with the extra brace
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getClosingCurlyBraceURL(String textAboveError) {

        // We want to find a block before the extraneous brace
        int endIndex = textAboveError.lastIndexOf('}');
        int rightBraceIndex = textAboveError.lastIndexOf('}', endIndex - 1);
        int leftBraceIndex = findMatchingBrace(textAboveError, rightBraceIndex);

        int startIndex = textAboveError.lastIndexOf('\n', leftBraceIndex);
        if (startIndex > 0) {
            startIndex = textAboveError.lastIndexOf('\n', startIndex - 1);
        }
        String mismatchedSnippet = textAboveError.substring(startIndex + 1, leftBraceIndex + 1)
                + "\n  /* your code */\n" + textAboveError.substring(rightBraceIndex, endIndex + 1);
        String correctedSnippet = mismatchedSnippet.substring(0, mismatchedSnippet.length() - 1);

        try {
            mismatchedSnippet = URLEncoder.encode(mismatchedSnippet, "UTF-8");
            correctedSnippet = URLEncoder.encode(correctedSnippet, "UTF-8");
        } catch (UnsupportedEncodingException err) {
            return Optional.empty();
        }

        return Optional.of(URL + "extraneousclosingcurlybrace?original=" + mismatchedSnippet
                + "&fixed=" + correctedSnippet + getGlobalParams(false));
    }

    /**
     * Gets the URL for an incorrect variable declaration.
     * @param textArea      text area for the file that contains the error
     * @param exception     incorrect declaration exception from compilation
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getIncorrectVarDeclarationURL(JEditTextArea textArea, SketchException exception) {
        int errorIndex = textArea.getLineStartOffset(exception.getCodeLine()) + exception.getCodeColumn() - 1;
        String code = textArea.getText();
        String declarationStatement = code.substring(errorIndex);
        int statementEndIndex = declarationStatement.indexOf(';', errorIndex);
        if (statementEndIndex >= 0) {
            declarationStatement = declarationStatement.substring(0, statementEndIndex);
        }

        List<String> declaredArrays = getDeclaredArrays(declarationStatement);

        String pattern = "\\s*[\\w\\d$]+\\s*=\\s*(new\\s*[\\w\\d$]+\\s*\\[\\d+]|\\{.*})\\s*[,;]";
        Optional<String> firstInvalidDeclarationOptional =
                declaredArrays.stream().filter((declaration) -> !declaration.matches(pattern)).findFirst();

        if (!firstInvalidDeclarationOptional.isPresent()) {
            return Optional.empty();
        }

        String firstInvalidDeclaration = firstInvalidDeclarationOptional.get();
        String arrName = firstInvalidDeclaration.trim().split("[^\\w\\d$]")[0];

        // Get array type
        String beforeErrorText = code.substring(0, errorIndex);
        int currentIndex = beforeErrorText.length() - 1;

        boolean hasIdentifierEnded = false;
        StringBuilder arrType = new StringBuilder();
        while (currentIndex >= 0 && !hasIdentifierEnded) {
            String currentChar = beforeErrorText.substring(currentIndex, currentIndex + 1);

            if (!currentChar.matches("[\\s\\[\\]]")) {
                arrType.insert(0, currentChar);
            } else if (arrType.length() > 0) {
                hasIdentifierEnded = true;
            }

            currentIndex--;
        }

        return Optional.of(URL + "incorrectvariabledeclaration?typename=" + trimType(arrType.toString())
                + "&foundname=" + arrName + getGlobalParams(false));
    }

    /**
     * Gets the URL for an incorrect variable declaration.
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getIncorrectVarDeclarationURL(ASTNode problemNode) {
        Optional<VariableDeclarationFragment> fragmentOptional = findDeclarationFragment(problemNode);
        if (!fragmentOptional.isPresent()) {
            return Optional.empty();
        }

        VariableDeclarationFragment fragment = fragmentOptional.get();

        String arrName = fragment.getName().toString();
        String arrType = trimType(getElementType(fragment.resolveBinding().getType().toString()));

        return Optional.of(URL + "incorrectvariabledeclaration?typename=" + arrType
                + "&foundname=" + arrName + getGlobalParams(false));
    }

    /**
     * Gets the URL for an incorrect method declaration.
     * @param textAboveError      all text in the editor at and above the
     *                            line with error
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getIncorrectMethodDeclarationURL(String textAboveError) {
        int lastOpenParenthesisIndex = textAboveError.lastIndexOf('(');

        int currentCharIndex = lastOpenParenthesisIndex;
        char currentChar;
        do {
            currentCharIndex--;
            currentChar = textAboveError.charAt(currentCharIndex);
        } while (currentCharIndex > 0 && Character.isJavaIdentifierPart(currentChar));

        /* The method name starts one character ahead of the current one if
           we didn't reach the start of the string */
        if (currentCharIndex > 0) currentCharIndex++;

        String methodName = textAboveError.substring(currentCharIndex, lastOpenParenthesisIndex);

        return Optional.of(URL + "incorrectmethoddeclaration?methodname=" + methodName + getGlobalParams(false));
    }

    /**
     * Gets the URL for a missing array dimension.
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getArrDimURL(ASTNode problemNode) {
        Optional<VariableDeclarationFragment> fragmentOptional = findDeclarationFragment(problemNode);
        if (!fragmentOptional.isPresent()) {
            return Optional.empty();
        }

        String arrType = trimType(problemNode.toString());
        String arrName = fragmentOptional.get().getName().toString();

        return Optional.of(URL + "incorrectdimensionexpression1?typename=" + arrType
                + "&arrname=" + arrName
                + getGlobalParams(false));
    }

    /**
     * Gets the URL when the first of two array dimensions is missing.
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getTwoDimArrURL(ASTNode problemNode) {
        ASTNode parent = problemNode.getParent();
        Optional<VariableDeclarationFragment> fragmentOptional = findDeclarationFragment(problemNode);
        if (!(parent instanceof ArrayCreation) || !fragmentOptional.isPresent()) {
            return Optional.empty();
        }

        String arrType = trimType(getElementType(((ArrayCreation) parent).getType().toString()));
        String arrName = fragmentOptional.get().getName().toString();

        return Optional.of(URL + "incorrectdimensionexpression2?typename=" + arrType
                + "&arrname=" + arrName
                + getGlobalParams(false));
    }

    /**
     * Gets the URL for the use of two array initializers at once.
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getTwoInitializerArrURL(ASTNode problemNode) {
        ASTNode parent = problemNode.getParent();
        Optional<VariableDeclarationFragment> fragmentOptional = findDeclarationFragment(problemNode);
        if (!(parent instanceof ArrayCreation) || !fragmentOptional.isPresent()) {
            return Optional.empty();
        }

        String arrType = trimType(getElementType(((ArrayCreation) parent).getType().toString()));
        String arrName = fragmentOptional.get().getName().toString();

        return Optional.of(URL + "incorrectdimensionexpression3?typename=" + arrType
                + "&arrname=" + arrName
                + getGlobalParams(false));
    }

    /**
     * Gets the URL for a missing method.
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getMissingMethodURL(ASTNode problemNode) {
        ASTNode parent = problemNode.getParent();
        if (!(parent instanceof MethodInvocation)) {
            return Optional.empty();
        }

        MethodInvocation invocation = (MethodInvocation) problemNode.getParent();
        List<String> providedParams = ((List<?>) invocation.arguments()).stream()
                .map(Object::toString).collect(Collectors.toList());
        List<String> providedParamTypes = ((List<?>) invocation.arguments()).stream().map(
                (param) -> trimType(getClosestExpressionType(((ASTNode) param).getParent()))
        ).collect(Collectors.toList());
        String methodName = invocation.getName().toString();

        String returnType = getClosestExpressionType(invocation.getParent());
        String dummyCorrectName = "correctName";

        String encodedParams;
        String encodedTypes;
        try {
            encodedParams = URLEncoder.encode(String.join(",", providedParams), "UTF-8");
            encodedTypes = URLEncoder.encode(String.join(",", providedParamTypes), "UTF-8");
        } catch (UnsupportedEncodingException err) {
            return Optional.empty();
        }

        return Optional.of(URL + "methodnotfound?methodname=" + methodName
                + "&correctmethodname=" + dummyCorrectName
                + "&typename=" + trimType(returnType)
                + "&providedparams=" + encodedParams
                + "&providedtypes=" + encodedTypes
                + getGlobalParams(false));
    }

    /**
     * Gets the URL for a parameter mismatch in a method call.
     * @param fileName          sketch where the method resides
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getParamMismatchURL(String fileName, ASTNode problemNode) {
        ASTNode parent = problemNode.getParent();
        if (!(parent instanceof MethodInvocation)) {
            return Optional.empty();
        }

        MethodInvocation invocation = (MethodInvocation) parent;
        List<String> providedParamTypes = ((List<?>) invocation.arguments()).stream().map(
                (param) -> trimType(getClosestExpressionType((ASTNode) param))
        ).collect(Collectors.toList());
        List<String> requiredParamTypes = Arrays.stream(
                invocation.resolveMethodBinding().getParameterTypes()
        ).map((binding) -> trimType(binding.getName())).collect(Collectors.toList());

        String methodName = invocation.getName().toString();
        String methodReturnType = invocation.resolveMethodBinding().getReturnType().toString();

        String encodedProvidedTypes;
        String encodedRequiredTypes;
        try {
            encodedProvidedTypes = URLEncoder.encode(String.join(",", providedParamTypes), "UTF-8");
            encodedRequiredTypes = URLEncoder.encode(String.join(",", requiredParamTypes), "UTF-8");
        } catch (UnsupportedEncodingException err) {
            return Optional.empty();
        }

        return Optional.of(URL + "parametermismatch?classname=" + fileName
                + "&methodname=" + methodName
                + "&methodtypename=" + methodReturnType
                + "&providedtypes=" + encodedProvidedTypes
                + "&requiredtypes=" + encodedRequiredTypes
                + getGlobalParams(false));
    }

    /**
     * Gets the URL for a missing return statement in a method.
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getMissingReturnURL(ASTNode problemNode) {
        ASTNode parent = problemNode.getParent();
        if (!(parent instanceof MethodDeclaration)) {
            return Optional.empty();
        }

        MethodDeclaration declaration = (MethodDeclaration) parent;
        List<String> requiredParamTypes = Arrays.stream(
                declaration.resolveBinding().getParameterTypes()
        ).map((binding) -> trimType(binding.getName())).collect(Collectors.toList());
        String methodName = declaration.getName().toString();
        String methodReturnType = trimType(declaration.getReturnType2().toString());

        String encodedTypes;
        try {
            encodedTypes = URLEncoder.encode(String.join(",", requiredParamTypes), "UTF-8");
        } catch (UnsupportedEncodingException err) {
            return Optional.empty();
        }

        return Optional.of(URL + "returnmissing?methodname=" + methodName
                + "&typename=" + methodReturnType
                + "&requiredtypes=" + encodedTypes
                + getGlobalParams(false));
    }

    /**
     * Gets the URL for a mismatch between a variable's type and its assigned value.
     * @param providedType      the type provided by the programmer
     * @param requiredType      the type required by the method
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getTypeMismatchURL(String providedType, String requiredType, ASTNode problemNode) {
        Optional<VariableDeclarationFragment> declaration = findDeclarationFragment(problemNode);
        String varName = declaration.map((fragment) -> fragment.getName().toString()).orElse("example");
        return Optional.of(URL + "typemismatch?typeonename=" + trimType(providedType)
                + "&typetwoname=" + trimType(requiredType)
                + "&varname=" + varName
                + getGlobalParams(false));
    }

    /**
     * Gets the URL for a missing type.
     * @param missingType       name of the missing type
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getMissingTypeURL(String missingType, ASTNode problemNode) {
        String varName = "example";

        // All variables in the statement will be the same type, so use the first as an example
        Optional<VariableDeclarationFragment> fragmentOptional = findDeclarationFragment(problemNode);
        if (fragmentOptional.isPresent()) {
            varName = fragmentOptional.get().getName().toString();
        }


        String dummyCorrectName = "CorrectName";

        return Optional.of(URL + "typenotfound?classname=" + trimType(missingType)
                + "&correctclassname=" + dummyCorrectName
                + "&varname=" + varName
                + getGlobalParams(false));
    }

    /**
     * Gets the URL for a missing variable.
     * @param varName           name of the missing variable
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getMissingVarURL(String varName, ASTNode problemNode) {
        String varType = trimType(getClosestExpressionType(varName, problemNode.getParent()));
        return Optional.of(URL + "variablenotfound?classname=" + varType + "&varname=" + varName + getGlobalParams(false));
    }

    /**
     * Gets the URL for an uninitialized variable.
     * @param varName           name of the uninitialized variable
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getUninitializedVarURL(String varName, ASTNode problemNode) {
        String params = "?varname=" + varName;
        String type = getClosestExpressionType(varName, problemNode.getParent());
        params += "&typename=" + trimType(type);

        return Optional.of(URL + "variablenotinit" + params + getGlobalParams(false));
    }

    /**
     * Gets the URL for an unexpected type name.
     * @param typeName      the unexpected type name
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getUnexpectedTokenURL(String typeName) {
        if (!couldBeType(typeName)) {
            return Optional.empty();
        }

        return Optional.of(URL + "unexpectedtoken?typename=" + trimType(typeName) + getGlobalParams(false));
    }

    /**
     * Gets the URL for a non-static method call in a static context.
     * @param fileName          name of the file where the error is located
     * @param nonStaticMethod   name of the non-static method
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getStaticErrorURL(String fileName, String nonStaticMethod, ASTNode problemNode) {
        String params = "?methodname=" + nonStaticMethod;

        Optional<MethodDeclaration> declaration = findClosestNode(problemNode, MethodDeclaration.class);
        Optional<MethodInvocation> invocation = findClosestNode(problemNode, MethodInvocation.class);

        if (declaration.isPresent()) {
            params += "&staticmethodname=" + declaration.get().getName().toString();
            params += "&staticmethodreturntype=" + declaration.get().getReturnType2().toString();
        }

        if (invocation.isPresent()) {
            params += "&methodreturntype=" + invocation.get().resolveMethodBinding().getReturnType().toString();
        }

        params += "&filename=" + fileName;

        return Optional.of(URL + "nonstaticfromstatic" + params + getGlobalParams(false));
    }

    /**
     * Gets the URL for a VariableDeclarators error.
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getVariableDeclaratorsURL(ASTNode problemNode) {
        String methodName = problemNode.toString();

        ASTNode parent = problemNode.getParent();
        if (parent instanceof QualifiedName) {
            methodName = parent.toString();
        }

        String typeName = getClosestExpressionType(problemNode.getParent());
        String params = "?methodonename=" + methodName + "&typename=" + typeName;

        return Optional.of(URL + "syntaxerrorvariabledeclarators" + params + getGlobalParams(false));
    }

    /**
     * Gets the URL for a VariableDeclarators error.
     * @param type              the type of variable the method was invoked on
     * @param methodName        the name of the method that was invoked
     * @param problemNode       node of the AST where the problem occurred
     * @return the URL with path and parameters for the corresponding page
     */
    public Optional<String> getMethodCallWrongTypeURL(String type, String methodName, ASTNode problemNode) {
        String variableName = problemNode.toString();

        String returnType = "void";
        Optional<MethodInvocation> incorrectInvocation = findClosestNode(problemNode, MethodInvocation.class);

        if (incorrectInvocation.isPresent()) {
            ASTNode invocationParent = incorrectInvocation.get().getParent();

            /* An expression statement seems to be a good indicator that there is no return type.
               It's not extremely important for the return type to be correct since it is not the
               crux of the compiler error. */
            if (!(invocationParent instanceof ExpressionStatement) ||
                    ((ExpressionStatement) invocationParent).getExpression().resolveTypeBinding() != null) {
                returnType = getClosestExpressionType(incorrectInvocation.get().getParent());
            }

        }

        return Optional.of(URL + "methodcallonwrongtype?methodname=" + methodName
                + "&returntype=" + returnType
                + "&typename=" + trimType(type)
                + "&varname" + variableName
                + getGlobalParams(false));
    }

    /**
     * Trims a qualified name to its simple name.
     * @param type      the original (possibly qualified) name of the type
     * @return the type's simple name
     */
    private String trimType(String type) {
        if (type.length() == 0) {
            return "";
        }

        return type.substring(type.lastIndexOf('.') + 1);
    }

    /**
     * Gets an element type from an array type.
     * @param arrayType     the array type to get the element type from
     * @return the type of element in the array
     */
    private String getElementType(String arrayType) {
        return arrayType.replaceFirst("\\[]", "");
    }

    /**
     * Finds the index for a brace matching the one at the index provided.
     * @param code          code to search in
     * @param startIndex    index where the brace to find the match for is
     * @return the index of the matching brace or -1 if there is no matching brace
     */
    private int findMatchingBrace(String code, int startIndex) {
        AtomicInteger previousIndex = new AtomicInteger(startIndex);
        int neededLeftBraces = 0;

        char startChar = code.charAt(startIndex);
        boolean findRightBrace;
        Runnable moveToNextIndex;

        // Count the initial brace
        if (startChar == '{' || startChar == '(') {
            neededLeftBraces--;
            findRightBrace = true;
            moveToNextIndex = previousIndex::getAndIncrement;
        } else if (startChar == '}' || startChar == ')') {
            neededLeftBraces++;
            findRightBrace = false;
            moveToNextIndex = previousIndex::getAndDecrement;
        } else {
            throw new IllegalArgumentException("Character at index "
                    + startIndex + " is not a brace or parenthesis.");
        }

        // Find the matching brace
        while (neededLeftBraces != 0 && previousIndex.get() > 0 && previousIndex.get() < code.length() - 1) {
            moveToNextIndex.run();

            char nextChar = code.charAt(previousIndex.get());
            if (nextChar == '{' || nextChar == '(') {
                neededLeftBraces--;
            } else if (nextChar == '}' || nextChar == ')') {
                neededLeftBraces++;
            }
        }

        char lastCharacter = code.charAt(previousIndex.get());
        boolean isMatchingRight = findRightBrace && (lastCharacter == '}' || lastCharacter == ')');
        boolean isMatchingLeft = !findRightBrace && (lastCharacter == '{' || lastCharacter == '(');
        if (!isMatchingLeft && !isMatchingRight) {
            return -1;
        }

        return previousIndex.get();
    }

    /**
     * Extracts array declarations from a declaration statement.
     * @param declarationStatement  the statement to extract from
     * @return the individual declarations of all arrays in the statement
     *         of the form (identifier = new Type[size],)
     */
    private List<String> getDeclaredArrays(String declarationStatement) {
        List<String> declaredArrays = new ArrayList<>();
        int currentIndex = 0;
        int lastCommaIndex = -1;
        while (currentIndex < declarationStatement.length()) {
            char currentChar = declarationStatement.charAt(currentIndex);
            if (currentChar == '{') {

                // Skip array initializers
                int matchingBraceIndex = findMatchingBrace(declarationStatement, currentIndex);
                currentIndex = matchingBraceIndex == -1 ? declarationStatement.length() - 1 : matchingBraceIndex;

            } else if (currentChar == ',') {
                declaredArrays.add(declarationStatement.substring(lastCommaIndex + 1, currentIndex + 1));
                lastCommaIndex = currentIndex;
                currentIndex++;
            } else {
                currentIndex++;
            }
        }

        declaredArrays.add(declarationStatement.substring(lastCommaIndex + 1));

        return declaredArrays;
    }

    /**
     * Finds the closest node of a particular type.
     * @param problemNode       the node to search up from
     * @param nodeClass         the class of the node to find
     * @param <T> node type to find
     * @return the closest node of that class or empty
     */
    private <T> Optional<T> findClosestNode(ASTNode problemNode, Class<T> nodeClass) {
        ASTNode node = problemNode;
        while (node != null) {
            if (nodeClass.isInstance(node)) {
                return Optional.of(nodeClass.cast(node));
            }
            node = node.getParent();
        }

        return Optional.empty();
    }

    /**
     * Finds the closest {@link VariableDeclarationFragment} to the problem node.
     * @param problemNode       problem node where error occurred
     * @return the closest declaration fragment to the problem node
     */
    private Optional<VariableDeclarationFragment> findDeclarationFragment(ASTNode problemNode) {
        ASTNode node = problemNode;
        while (node != null) {
            if (node instanceof VariableDeclarationFragment) {
                return Optional.of((VariableDeclarationFragment) node);
            }

            if (node instanceof FieldDeclaration) {
                FieldDeclaration fieldDeclaration = (FieldDeclaration) node;
                return Optional.of((VariableDeclarationFragment) fieldDeclaration.fragments().get(0));
            }

            if (node instanceof VariableDeclarationStatement) {
                VariableDeclarationStatement declarationStatement = (VariableDeclarationStatement) node;
                return Optional.of((VariableDeclarationFragment) declarationStatement.fragments().get(0));
            }

            node = node.getParent();
        }

        return Optional.empty();
    }

    /**
     * Gets the expression closest to the error.
     * @param problemNode       the node where the error occurred
     * @return the type of the variable missing; defaults to "Object"
     */
    private String getClosestExpressionType(ASTNode problemNode) {

        // The empty string will simply be ignored by methods that use it
        return getClosestExpressionType("", problemNode);

    }

    /**
     * Gets the expression closest to the error.
     * @param missingVar        the name of the missing variable
     * @param problemNode       the node where the error occurred
     * @return the type of the variable missing; defaults to "Object"
     */
    private String getClosestExpressionType(String missingVar, ASTNode problemNode) {
        Map<Class<?>, BiFunction<String, ASTNode, String>> typeGetters = new HashMap<>();
        typeGetters.put(PrefixExpression.class, this::getTypeFromPrefixExpression);
        typeGetters.put(InfixExpression.class, this::getTypeFromInfixExpression);
        typeGetters.put(PostfixExpression.class, this::getTypeFromPostfixExpression);
        typeGetters.put(ConditionalExpression.class, this::getTypeFromConditionalExpression);
        typeGetters.put(InstanceofExpression.class, this::getTypeFromInstanceOf);
        typeGetters.put(VariableDeclarationFragment.class, this::getTypeFromVarDeclaration);
        typeGetters.put(ArrayCreation.class, this::getTypeFromArrayCreation);
        typeGetters.put(ArrayAccess.class, this::getTypeFromArrayAccess);
        typeGetters.put(ArrayInitializer.class, this::getTypeFromArrayInitializer);
        typeGetters.put(CastExpression.class, this::getTypeFromCastExpression);
        typeGetters.put(MethodInvocation.class, this::getTypeFromMethodInvocation);
        typeGetters.put(Assignment.class, this::getTypeFromAssignment);
        typeGetters.put(ExpressionStatement.class, this::getTypeFromExpressionStatement);
        typeGetters.put(CharacterLiteral.class, (name, node) -> "char");
        typeGetters.put(BooleanLiteral.class, (name, node) -> "boolean");
        typeGetters.put(NumberLiteral.class, (name, node) -> ((NumberLiteral) node).resolveTypeBinding().getName());
        typeGetters.put(StringLiteral.class, (name, node) -> "String");
        typeGetters.put(NullLiteral.class, (name, node) -> "Object");

        Set<Class<?>> supportedExpressions = typeGetters.keySet();

        ASTNode node = problemNode;
        while (node != null) {
            for (Class<?> expressionType : supportedExpressions) {
                if (expressionType.isInstance(node)) {
                    return typeGetters.get(expressionType).apply(missingVar, node);
                }
            }
            node = node.getParent();
        }

        return "Object";
    }

    /**
     * Gets the type of a missing variable from a prefix expression.
     * @param varName           name of the missing variable
     * @param prefixExpression  expression closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromPrefixExpression(String varName, ASTNode prefixExpression) {
        PrefixExpression prefix = (PrefixExpression) prefixExpression;

        PrefixExpression.Operator[] booleanOperators = {PrefixExpression.Operator.NOT};

        if (Arrays.asList(booleanOperators).contains(prefix.getOperator())) {
            return "boolean";
        } else {

            /* Some of the other operators can also apply to floating point values,
               but they all apply to integers. It's safer to assume the value is an integer. */
            return "int";

        }
    }

    /**
     * Gets the type of a missing variable from an infix expression.
     * @param varName           name of the missing variable
     * @param infixExpression   expression closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromInfixExpression(String varName, ASTNode infixExpression) {
        InfixExpression infix = (InfixExpression) infixExpression;

        // Guess the type based on the other operand
        if (infix.getLeftOperand() != null && infix.getLeftOperand().resolveTypeBinding() != null) {
            return infix.getLeftOperand().resolveTypeBinding().getName();
        } else if (infix.getRightOperand() != null && infix.getRightOperand().resolveTypeBinding() != null) {
            return infix.getRightOperand().resolveTypeBinding().getName();
        }

        InfixExpression.Operator[] booleanOperators = {
                InfixExpression.Operator.CONDITIONAL_OR, InfixExpression.Operator.CONDITIONAL_AND
        };
        InfixExpression.Operator[] numericalOperators = {
                InfixExpression.Operator.TIMES, InfixExpression.Operator.DIVIDE, InfixExpression.Operator.REMAINDER,
                InfixExpression.Operator.PLUS, InfixExpression.Operator.MINUS,
                InfixExpression.Operator.LEFT_SHIFT,
                InfixExpression.Operator.RIGHT_SHIFT_SIGNED, InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED,
                InfixExpression.Operator.LESS, InfixExpression.Operator.GREATER,
                InfixExpression.Operator.LESS_EQUALS, InfixExpression.Operator.GREATER_EQUALS,
                InfixExpression.Operator.XOR, InfixExpression.Operator.OR, InfixExpression.Operator.AND
        };

        // Guess the type based on the operator
        if (Arrays.asList(booleanOperators).contains(infix.getOperator())) {
            return "boolean";
        } else if (Arrays.asList(numericalOperators).contains(infix.getOperator())) {
            return "int";
        }

        // Assume it's a boolean if we can't find out any info about the type
        return "boolean";

    }

    /**
     * Gets the type of a missing variable from a postfix expression.
     * @param varName             name of the missing variable
     * @param postfixExpression   expression closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromPostfixExpression(String varName, ASTNode postfixExpression) {

        // The only two postfix operators are increment and decrement
        return "int";

    }

    /**
     * Gets the type of a missing variable from a conditional expression.
     * @param varName                   name of the missing variable
     * @param conditionalExpression     expression closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromConditionalExpression(String varName, ASTNode conditionalExpression) {
        return "boolean";
    }

    /**
     * Gets the type of a missing variable from an instanceOf expression.
     * @param varName           name of the missing variable
     * @param instanceOf        expression closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromInstanceOf(String varName, ASTNode instanceOf) {
        return "Object";
    }

    /**
     * Gets the type of a missing variable from a variable declaration expression.
     * @param varName           name of the missing variable
     * @param varDeclaration    expression closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromVarDeclaration(String varName, ASTNode varDeclaration) {
        VariableDeclarationFragment declaration = (VariableDeclarationFragment) varDeclaration;
        return declaration.resolveBinding().getType().getName();
    }

    /**
     * Gets the type of a missing variable from a method invocation expression.
     * @param varName           name of the missing variable
     * @param methodInvocation  expression closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromMethodInvocation(String varName, ASTNode methodInvocation) {
        MethodInvocation invocation = (MethodInvocation) methodInvocation;
        if (invocation.resolveMethodBinding() == null) {
            return "Object";
        }

        List<String> requiredParamTypes = Arrays.stream(
                invocation.resolveMethodBinding().getParameterTypes()
        ).map(ITypeBinding::getName).collect(Collectors.toList());
        List<String> providedParams = ((List<?>) invocation.arguments()).stream()
                .map(Object::toString).collect(Collectors.toList());

        int paramIndex = providedParams.indexOf(varName);

        return paramIndex >= 0 ? requiredParamTypes.get(paramIndex) : "Object";
    }

    /**
     * Gets the type of a missing variable from an array creation expression.
     * @param varName           name of the missing variable
     * @param arrayCreation     expression closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromArrayCreation(String varName, ASTNode arrayCreation) {
        return "int";
    }

    /**
     * Gets the type of a missing variable from an array access expression.
     * @param varName           name of the missing variable
     * @param arrayAccess       expression closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromArrayAccess(String varName, ASTNode arrayAccess) {
        return "int";
    }

    /**
     * Gets the type of a missing variable from an array initializer expression.
     * @param varName               name of the missing variable
     * @param arrayInitializer      expression closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromArrayInitializer(String varName, ASTNode arrayInitializer) {
        ArrayInitializer initializer = (ArrayInitializer) arrayInitializer;
        if (initializer.resolveTypeBinding() == null) {
            return "Object";
        }

        return getElementType(initializer.resolveTypeBinding().getName());
    }

    /**
     * Gets the type of a missing variable from a cast expression.
     * @param varName           name of the missing variable
     * @param castExpression    expression closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromCastExpression(String varName, ASTNode castExpression) {
        CastExpression cast = (CastExpression) castExpression;
        return cast.getType().toString();
    }

    /**
     * Gets the type of a missing variable from an assignment expression.
     * @param varName                   name of the missing variable
     * @param assignmentExpression      expression closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromAssignment(String varName, ASTNode assignmentExpression) {
        Assignment assignment = (Assignment) assignmentExpression;
        if (assignment.resolveTypeBinding() == null) {
            return "Object";
        }

        return assignment.resolveTypeBinding().getName();
    }

    /**
     * Gets the type of a missing variable from an expression statement.
     * @param varName                   name of the missing variable
     * @param expressionStatement       expression statement closest to error
     * @return the type of the missing variable
     */
    private String getTypeFromExpressionStatement(String varName, ASTNode expressionStatement) {
        ExpressionStatement statement = (ExpressionStatement) expressionStatement;
        if (statement.getExpression() == null || statement.getExpression().resolveTypeBinding() == null) {
            return "Object";
        }

        return statement.getExpression().resolveTypeBinding().getName();
    }

    /**
     * Checks if a token could be a type.
     * @param token     the token to check
     * @return whether this token could be a type
     */
    private boolean couldBeType(String token) {
        char[] chars = token.toCharArray();
        return IntStream.range(0, chars.length).allMatch(
                index -> Character.isJavaIdentifierPart(chars[index])
        );
    }

    /**
     * Gets the global parameters as URL query parameters.
     * @param first     whether the global parameters are the first parameters 
     *                  in the query
     * @return the global parameters as a URL query string
     */
    private String getGlobalParams(boolean first) {
        String connectedParams = GLOBAL_PARAMS.entrySet().stream().map(
                (entry) -> entry.getKey() + "=" + entry.getValue()
        ).collect(Collectors.joining("&"));

        String firstChar = first ? "?" : "&";
        return firstChar + connectedParams;
    }

}
