package sparta.checkers;

/*>>>
 import checkers.compilermsgs.quals.*;
 */

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javacutils.AnnotationUtils;
import javacutils.TreeUtils;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import sparta.checkers.quals.DependentPermissions;
import sparta.checkers.quals.FlowPermission;
import sparta.checkers.quals.IExtra;
import sparta.checkers.quals.IntentExtras;
import sparta.checkers.quals.MayRequiredPermissions;
import sparta.checkers.quals.RequiredPermissions;
import sparta.checkers.quals.Sink;
import sparta.checkers.quals.Source;
import checkers.basetype.BaseTypeChecker;
import checkers.source.Result;
import checkers.types.AnnotatedTypeMirror;
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType;
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType;
import checkers.units.quals.A;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacScope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

public class IntentVisitor extends FlowVisitor {

    private static List<String> PUTEXTRA_SIGNATURES = (List<String>) Arrays
            .asList(new String[] { "putCharSequenceArrayListExtra", "putExtra",
                    "putIntegerArrayListExtra", "putParcelableArrayListExtra",
                    "putStringArrayListExtra" });
    private static List<String> GETEXTRA_SIGNATURES = (List<String>) Arrays
            .asList(new String[] { "getStringExtra", "getStringArrayListExtra",
                    "getStringArrayExtra", "getShortExtra",
                    "getShortArrayExtra", "getSerializableExtra",
                    "getParcelableExtra", "getParcelableArrayListExtra",
                    "getParcelableArrayExtra", "getLongExtra",
                    "getLongArrayExtra", "getIntegerArrayListExtra",
                    "getIntExtra", "getIntArrayExtra", "getFloatExtra",
                    "getFloatArrayExtra", "getDoubleExtra",
                    "getDoubleArrayExtra", "getCharSequenceExtra",
                    "getCharSequenceArrayListExtra",
                    "getCharSequenceArrayExtra", "getCharExtra",
                    "getCharArrayExtra", "getByteExtra", "getByteArrayExtra",
                    "getBundleExtra", "getBooleanExtra", "getBooleanArrayExtra" });

    public IntentVisitor(BaseTypeChecker checker) {
        super(checker);
    }

    @Override
    protected void checkMethodInvocability(AnnotatedExecutableType method,
            MethodInvocationTree node) {
        // TODO: this string receiverClassName is temporary.
        // Something similar to the correct solution is commented below
        String receiverClassName = method.getReceiverType().getUnderlyingType()
                .asElement().getSimpleName().toString();
        // CHECK IF SUBTYPE of Intent, instead of equals Intent.
        // The code below does not work because getClass() returns a Symbol, and
        // not the actual class.
        // Need to find out a way to get the actual class from an Element
        // if (method.getReceiverType().getUnderlyingType()
        // .asElement().getClass().isAssignableFrom(android.content.Intent.class))
        // {

        if (receiverClassName.equals("Intent")) {
            checkGetExtraOrPutExtraMethods(method, node);
            return;
        }
        // if (method.getReceiverType().getUnderlyingType()
        // .asElement().getClass().isAssignableFrom(android.app.Activity.class))
        // {
        if (receiverClassName.equals("Activity")) {
            checkSendIntent(method, node);
            return;
        }
        super.checkMethodInvocability(method, node);
    }

    private void checkSendIntent(AnnotatedExecutableType method,
            MethodInvocationTree node) {
        String methodName = method.getElement().getSimpleName().toString();
        if (methodName.equals("startActivity")) {
            startActivityTypeCheck(method, node);
        }

    }

    private void startActivityTypeCheck(AnnotatedExecutableType method,
            MethodInvocationTree node) {
        if (node.getArguments().isEmpty()) {
            //Still need to figure out a better way to treat that. Defensive programming.
            throw new RuntimeException();
        }
        ExpressionTree intentObject = node.getArguments().get(0);
        AnnotatedTypeMirror rhs = atypeFactory.getAnnotatedType(intentObject);
        AnnotationMirror rhsIntentExtras = rhs
                .getAnnotation(IntentExtras.class);
        List<MethodInvocationTree> getIntentCalls = getAllReceiversIntents(node);
        if (getIntentCalls.isEmpty())
            return;
        for (MethodInvocationTree getIntentCall : getIntentCalls) {
            AnnotatedTypeMirror lhs = atypeFactory
                    .getAnnotatedType(getIntentCall);
            AnnotationMirror lhsIntentExtras = lhs
                    .getAnnotation(IntentExtras.class);
            IntentChecker intentChecker = (IntentChecker) checker;
            if (!intentChecker.isCopyableTo(rhsIntentExtras, lhsIntentExtras,
                    atypeFactory)) {
                checker.report(Result.failure("send.intent"), node);
            }
        }
    }

    /**
     * Get MethodSymbol based on class name, method name, and parameter length.
     * 
     * @return the corresponding method symbol or <code>null</code> if the
     *         method is not unique or cannot be determined
     */
    private Symbol getMethodSymbolFor(String className, String methodName,
            int paramLength, Env<AttrContext> env) {
        Context context = ((JavacProcessingEnvironment) checker
                .getProcessingEnvironment()).getContext();
        Resolve resolve = Resolve.instance(context);
        Names names = Names.instance(context);

        Symbol result = null;
        Method loadClass;
        try {
            loadClass = Resolve.class.getDeclaredMethod("loadClass", Env.class,
                    Name.class);
            loadClass.setAccessible(true);
            Symbol symClass;
            symClass = (Symbol) loadClass.invoke(resolve, env,
                    names.fromString(className));
            if (!symClass.exists()) {
                return result;
            }

            // TODO: We have to iterate over all super classes as the method(s)
            // we are looking for might be declared there
            for (Symbol s : symClass.getEnclosedElements()) {
                // Check all member methods
                if (s.getKind() == ElementKind.METHOD) {
                    // Check for method name and number of arguments
                    if (names.fromString(methodName).equals(s.name)
                            && ((MethodSymbol) s).getParameters().size() == paramLength) {
                        if (result != null) {
                            // Method name and parameter length is not unique in
                            // given class
                            result = null;
                            break;// outer;
                        }
                        result = s;
                    }
                }
            }
        } catch (IllegalAccessException | IllegalArgumentException
                | NoSuchMethodException | SecurityException
                | InvocationTargetException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * This method receives a tree for a sendIntent(intent) call and returns the
     * Component name with its action,category and parameter, if any. The format
     * is "Component(Action,[Category1,Category2],Data)". If there is none of
     * these elements, returns only "Component".
     * 
     * @param tree
     *            the sendIntent() tree
     * @return intent being sent in a String format, with Action,Category and
     *         Data, if any.
     */
    private String resolveIntentFilters(MethodInvocationTree tree) {
        String senderString = "";
        ClassTree classTree = TreeUtils.enclosingClass(getCurrentPath());
        senderString += classTree.getSimpleName().toString();
        AnnotationMirror am = atypeFactory.getAnnotatedType(
                tree.getArguments().get(0)).getAnnotation(IntentExtras.class);
        String action = AnnotationUtils.getElementValue(am, "action",
                String.class, true);
        List<String> categories = AnnotationUtils.getElementValueArray(am,
                "categories", String.class, true);
        String data = AnnotationUtils.getElementValue(am, "data", String.class,
                true);
        if (action.length() == 0 && categories.size() == 0
                && data.length() == 0) {
            return senderString;
        } else {
            // action
            senderString += "(" + action;
            // categories
            if (!categories.isEmpty()) {
                senderString += ",[";
                for (String category : categories) {
                    senderString += category + ",";
                }
                senderString = senderString.substring(0,
                        senderString.length() - 1); // removing last comma
                senderString += "]";
            }
            // data
            if (data.length() > 0) {
                senderString += "," + data;
            }
            senderString += ")";
        }
        return senderString;
    }

    /**
     * This method is responsible for obtaining the list of all getIntent()
     * methods from all possible receivers from a certain sendIntent() call.
     * Currently this implementation returns all *Components*, but instead
     * should return all the *Activities*. There is no getIntent() method in
     * Service and BroadcastReceiver.
     * 
     * @param tree
     *            the Tree of the sendIntent() call
     * @return A list of getIntent() methods
     */

    private List<MethodInvocationTree> getAllReceiversIntents(
            MethodInvocationTree tree) {

        JCMethodInvocation methodInvocation = (JCMethodInvocation) tree;
        Context context = ((JavacProcessingEnvironment) checker
                .getProcessingEnvironment()).getContext();
        // Return the sender intent in the String format. Check method @Javadoc.
        String senderString = resolveIntentFilters(tree);
        IntentChecker intentChecker = (IntentChecker) checker;

        // Getting the receivers components in the String format from the Intent
        // policy
        Set<String> receiversStrList = intentChecker
                .getReceiversFromSender(senderString);
        List<MethodInvocationTree> receiversList = new ArrayList<MethodInvocationTree>();
        // Here, we have the intent receivers set in the String format, and we
        // need to obtain
        // a list of it in the MethodInvocationTree format. The code to do that
        // is below.
        // TODO: Most of the code below is reused from the Reflection checker.
        // Refactor this so that both the Intent checker and the Intent checker
        // use the same code.

        // make is used to select the getIntent() method on the loop below.
        TreeMaker make = TreeMaker.instance(context);
        TreePath path = atypeFactory.getPath(tree);
        JavacScope scope = (JavacScope) trees.getScope(path);
        Env<AttrContext> env = scope.getEnv();
        String methodName = "getIntent";
        int paramLength = 0;
        // Get receiver, which is always the first argument of the invoke method
        JCExpression receiver = methodInvocation.args.head;
        // The remaining list contains the arguments
        com.sun.tools.javac.util.List<JCExpression> args = methodInvocation.args.tail;

        // Resolve the Symbol for the current method
        for (String receiverStr : receiversStrList) {
            Symbol symbol = getMethodSymbolFor(receiverStr, methodName,
                    paramLength, env);
            if (symbol != null) {
                JCExpression newMethod = make.Select(receiver, symbol);
                // Build method invocation tree depending on the number of
                // parameters
                JCMethodInvocation syntTree = paramLength > 0 ? make.App(
                        newMethod, args) : make.App(newMethod);

                // add method invocation tree to the list of possible methods
                receiversList.add(syntTree);
            } else {
                // If getIntent() method was not overriden in the Activity, a
                // warning should be raised.
                checker.report(
                        Result.failure("getintent.not.found", receiverStr),
                        tree);
            }
        }
        return receiversList;
    }

    /**
     * This method is called everytime we are visiting a method from the Intent
     * class. If the method being visited is a putExtra or getExtra call, we
     * type check it.
     * 
     * @param method
     *            Annotations on the method
     * @param node
     *            The tree of the method call
     */

    private void checkGetExtraOrPutExtraMethods(AnnotatedExecutableType method,
            MethodInvocationTree node) {
        if (node.getArguments().isEmpty()) {
            // If gets here, its not a getExtra nor putExtra.
            return;
        }
        String methodName = method.getElement().getSimpleName().toString();
        // The first element from putExtra and getExtra calls are keys.
        String keyName = node.getArguments().get(0).toString();
        // Removing the "" from the key. "key" -> key
        keyName = keyName.substring(1, keyName.length() - 1);
        ExpressionTree receiver = TreeUtils.getReceiverTree(node);
        AnnotatedTypeMirror receiverType = atypeFactory
                .getAnnotatedType(receiver);
        if (PUTEXTRA_SIGNATURES.contains(methodName)) {
            putExtraTypeCheck(node, keyName, receiver, receiverType);
        } else if (GETEXTRA_SIGNATURES.contains(methodName)) {
            getExtraTypeCheck(method, node, keyName, receiver, receiverType);
        }
    }

    /**
     * Method used to change the return type annotations of a
     * intent.getExtra(...) method call (@Source and @Sink), to the annotations
     * that can be found in the intent's @IntentExtras annotation.
     * 
     * @param method
     *            Actual getExtra method whose return type annotation will be
     *            updated
     * @param node
     *            Tree of the getExtra() method call, used only to show where a
     *            warning is being raised, in case it is.
     * @param keyName
     *            The key parameter of the getExtra(...) call. Ex:
     *            getExtra("key").
     * @param receiver
     *            Receiver component name in the String format. Ex: ActivityA
     * @param receiverType
     *            Annotations of the receiver calling intent.getExtra(...).
     */

    private void getExtraTypeCheck(AnnotatedExecutableType method,
            MethodInvocationTree node, String keyName, ExpressionTree receiver,
            AnnotatedTypeMirror receiverType) {
        if (receiverType.hasAnnotation(IntentExtras.class)) {
            AnnotationMirror receiverIntentAnnotation = receiverType
                    .getAnnotation(IntentExtras.class);
            // Getting list of iExtras from IntentExtras
            List<AnnotationMirror> iExtrasList = AnnotationUtils
                    .getElementValueArray(receiverIntentAnnotation, "value",
                            AnnotationMirror.class, true);

            for (AnnotationMirror iExtra : iExtrasList) {
                String key = AnnotationUtils.getElementValue(iExtra, "key",
                        String.class, true);
                if (key.equals(keyName)) {
                    // Now that we found the @IExtra that containts the key in
                    // the
                    // @IntentExtras annotation,
                    // we need to change the return type of getExtra to the
                    // @Source and @Sink annotations from this @IExtra element
                    Set<FlowPermission> annotatedSources = new HashSet<FlowPermission>(
                            AnnotationUtils.getElementValueEnumArray(iExtra,
                                    "source", FlowPermission.class, true));
                    Set<FlowPermission> annotatedSinks = new HashSet<FlowPermission>(
                            AnnotationUtils.getElementValueEnumArray(iExtra,
                                    "sink", FlowPermission.class, true));

                    AnnotationMirror sourceAnnotation = atypeFactory
                            .createAnnoFromSource(annotatedSources);
                    AnnotationMirror sinkAnnotation = atypeFactory
                            .createAnnoFromSink(annotatedSinks);
                    method.clearAnnotations();

                    AnnotatedTypeMirror methodReturnType = method
                            .getReturnType();
                    // Clearing and adding the new annotations
                    methodReturnType.clearAnnotations();
                    methodReturnType.addAnnotation(sourceAnnotation);
                    methodReturnType.addAnnotation(sinkAnnotation);

                    if (!methodReturnType.hasAnnotation(IntentExtras.class)) {
                        methodReturnType
                                .addAnnotation(((IntentChecker) checker).EMPTYINTENTEXTRAS);
                    }
                    return;
                }
            }
        }
        // If key could not be found in the @IntentExtras, raise a warning.
        checker.report(
                Result.failure("intent.key.notfound", keyName,
                        receiver.toString()), node);
    }

    /**
     * Method to type check the putExtra call. In every putExtra("key",value)
     * call the "key" need to be found in one of the @IExtra elements from the @IntentExtras
     * of the intent, and the @Source and @Sink from the @IExtra need to be a
     * supertype of the @Source and @Sink of value. TODO In the future, we want
     * to add inference to the putExtra() call. If the key cannot be found in
     * the @IntentExtras annotation, intent.putExtra("key",value) should update
     * the intent's @IntentExtra annotation adding a new @IExtra with key "key"
     * and @Source and @Sink of value.
     * 
     * @param node
     *            The tree of the putExtra method call
     * @param keyName
     *            The key of the element being added to the intent bundle
     * @param receiver
     *            Receiver component name in the String format. Ex: ActivityA
     * @param receiverType
     *            Annotations of the receiver calling intent.putExtra(...).
     */

    private void putExtraTypeCheck(MethodInvocationTree node, String keyName,
            ExpressionTree receiver, AnnotatedTypeMirror receiverType) {
        if (receiverType.hasAnnotation(IntentExtras.class)) {
            AnnotationMirror receiverIntentAnnotation = receiverType
                    .getAnnotation(IntentExtras.class);
            // Getting annotations of the value being added to the intent
            // bundle.
            ExpressionTree value = node.getArguments().get(1);
            AnnotatedTypeMirror valueType = atypeFactory
                    .getAnnotatedType(value);

            // Getting list of iExtras from IntentExtras
            List<AnnotationMirror> iExtrasList = AnnotationUtils
                    .getElementValueArray(receiverIntentAnnotation, "value",
                            AnnotationMirror.class, true);

            boolean found = false;
            for (AnnotationMirror iExtra : iExtrasList) {
                String key = AnnotationUtils.getElementValue(iExtra, "key",
                        String.class, true);
                // First we check if the key is already on the @IntentExtras
                // annotation.
                if (key.equals(keyName)) {
                    // If it is, the @IExtra flow of the annotation needs to be
                    // a supertype
                    // of the flow of the object being inserted on the hashmap.
                    found = true;
                    Set<FlowPermission> annotatedSources = new HashSet<FlowPermission>(
                            AnnotationUtils.getElementValueEnumArray(iExtra,
                                    "source", FlowPermission.class, true));
                    Set<FlowPermission> annotatedSinks = new HashSet<FlowPermission>(
                            AnnotationUtils.getElementValueEnumArray(iExtra,
                                    "sink", FlowPermission.class, true));

                    // Creating a type with the @Source and @Sink from value in
                    // putExtra(key,value).
                    AnnotationMirror sourceAnnotation = atypeFactory
                            .createAnnoFromSource(annotatedSources);
                    AnnotationMirror sinkAnnotation = atypeFactory
                            .createAnnoFromSink(annotatedSinks);
                    // annotatedType is a dummy type containing the @Source and
                    // @Sink
                    // from the IExtra, only to be used in the isSubType() call.
                    AnnotatedTypeMirror annotatedType = AnnotatedTypeMirror
                            .createType(valueType.getUnderlyingType(),
                                    atypeFactory);

                    annotatedType.addAnnotation(sourceAnnotation);
                    annotatedType.addAnnotation(sinkAnnotation);
                    annotatedType
                            .addAnnotation(((IntentChecker) checker).EMPTYINTENTEXTRAS);

                    // Check if the value being added to the intent bundle is a
                    // subtype
                    // of what the @IntentExtras annotation contains.
                    if (!atypeFactory.getTypeHierarchy().isSubtype(valueType,
                            annotatedType)) {
                        checker.report(Result.failure(
                                "intent.check.notcompatible",
                                receiver.toString(), keyName,
                                sourceAnnotation.toString(),
                                sinkAnnotation.toString(),
                                valueType.getAnnotation(Source.class),
                                valueType.getAnnotation(Sink.class)), node);
                    }
                    return;
                }
            }
            checker.report(
                    Result.failure("intent.key.notfound", keyName,
                            receiver.toString()), node);

            // TODO: Inference on putExtra
            // If key couldn't be found, add it to the @IntentExtras with the
            // flow of the object being inserted on the hashmap.
            // if(!found) {
            // IntentChecker intentChecker = (IntentChecker) checker;
            // AnnotationMirror newIExtra =
            // intentChecker.createIExtraAnno(keyName,
            // valueType.getAnnotation(Source.class),
            // valueType.getAnnotation(Sink.class));
            // AnnotationMirror newIntentExtras =
            // intentChecker.addIExtraToIntentExtras(receiverIntentAnnotation,
            // newIExtra);
            // receiverType.replaceAnnotation(newIntentExtras);
            // System.out.println();
            // }
        }
    }

    /**
     * For some reason, the FlowPermission[] passed to @Source or @Sink is
     * annotated and causes a type error. TODO: we should figure out why this is
     * happening in the first place and try to fix it.
     */
    @Override
    public Void visitAnnotation(AnnotationTree node, Void p) {
        List<? extends ExpressionTree> args = node.getArguments();
        if (args.isEmpty()) {
            // Nothing to do if there are no annotation arguments.
            return null;
        }

        Element anno = TreeInfo.symbol((JCTree) node.getAnnotationType());
        if (anno.toString().equals(Sink.class.getName())
                || anno.toString().equals(Source.class.getName())
                || anno.toString().equals(RequiredPermissions.class.getName())
                || anno.toString().equals(
                        MayRequiredPermissions.class.getName())
                || anno.toString().equals(DependentPermissions.class.getName())
                || anno.toString().equals(IntentExtras.class.getName())
                || anno.toString().equals(IExtra.class.getName())) {
            return null;
        }
        return super.visitAnnotation(node, p);
    }

}
