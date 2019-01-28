package com.bleeker.netbeans.builder;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.Modifier;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.ModificationResult;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.spi.editor.codegen.CodeGenerator;
import org.netbeans.spi.editor.codegen.CodeGeneratorContextProvider;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Code generator used to insert a builder into a java class.
 *
 * @author D3X573
 */
public class BuilderCodeGenerator implements CodeGenerator {

    private static final Logger logger = Logger.getLogger(BuilderCodeGenerator.class.getName());

    private static final boolean BUILDER_REF_CONSTRUCTOR = true;
    private static final boolean CREATE_GETTERS = true;

    private final JTextComponent textComp;

    /**
     * Constructor
     *
     * @param context containing JTextComponent and possibly other items
     *                registered by {@link CodeGeneratorContextProvider}
     */
    private BuilderCodeGenerator(final Lookup context) { // Good practice is not to save Lookup outside ctor
        textComp = context.lookup(JTextComponent.class);
    }

    /**
     * The name which will be inserted inside Insert Code dialog
     */
    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(BuilderCodeGenerator.class, "BuilderCodeGenerator_name");
    }

    /**
     * This will be invoked when user chooses this Generator from Insert Code
     * dialog
     */
    @Override
    public void invoke() {
        try {
            final Document doc = textComp.getDocument();
            final JavaSource javaSource = JavaSource.forDocument(doc);
            final CancellableTask<WorkingCopy> task = new CancellableTask<WorkingCopy>() {

                @Override
                public void run(final WorkingCopy workingCopy) throws IOException {
                    workingCopy.toPhase(Phase.RESOLVED);
                    final CompilationUnitTree compilationUnitTree = workingCopy.getCompilationUnit();
                    final TreeMaker treeMaker = workingCopy.getTreeMaker();
                    for (final Tree typeDecl : compilationUnitTree.getTypeDecls()) {
                        if (Tree.Kind.CLASS == typeDecl.getKind()
                            || Tree.Kind.INTERFACE == typeDecl.getKind()) {
                            final ClassTree classTree = (ClassTree) typeDecl;
                            final List<Tree> newMembersTree = new ArrayList<>();
                            final List<Tree> newVariablesTree = new ArrayList<>();
                            // assemble the new class
                            buildNewFields(treeMaker, classTree, newVariablesTree);
                            newMembersTree.addAll(newVariablesTree);
                            buildNewConstructor(treeMaker, newMembersTree);
                            buildNewFactory(treeMaker, newMembersTree);
                            if (CREATE_GETTERS) {
                                buildNewGetters(treeMaker, classTree, newVariablesTree, newMembersTree);
                            }
                            buildNewSetters(treeMaker, classTree, newVariablesTree, newMembersTree);
                            buildNewBuilder(treeMaker, classTree, newVariablesTree, newMembersTree);
                            buildNewClass(treeMaker, classTree, newMembersTree, workingCopy);
                        }
                    }
                }

                @Override
                public void cancel() {
                }
            };
            final ModificationResult result = javaSource.runModificationTask(task);
            result.commit();
        } catch (final Exception ex) {
            logger.log(Level.WARNING, "", ex);
        }
    }

    private void buildNewBuilder(final TreeMaker treeMaker, final ClassTree classTree, final List<Tree> newVariablesTree, final List<Tree> newMembersTree) {
        // create the build method
        final MethodTree newBuildMethodTree = treeMaker.Method(
                treeMaker.Modifiers(EnumSet.of(Modifier.PUBLIC)),
                "build",
                treeMaker.Type(classTree.getSimpleName().toString()),
                Collections.<TypeParameterTree>emptyList(),
                Collections.<VariableTree>emptyList(),
                Collections.<ExpressionTree>emptyList(),
                "{" + MessageFormat.format("return new {0}{1}({2});",
                                           classTree.getSimpleName().toString(),
                                           classTree.getKind() == Tree.Kind.CLASS ? "" : "Impl",
                                           BUILDER_REF_CONSTRUCTOR ? "this" : buildListString(newVariablesTree)) + "}",
                null);
        newMembersTree.add(newBuildMethodTree);
    }

    private void buildNewGetters(final TreeMaker treeMaker, final ClassTree classTree, final List<Tree> newVariablesTree, final List<Tree> newMembersTree) {
        // and create new builder getters
        for (final Tree tree : newVariablesTree) {
            if (Tree.Kind.VARIABLE == tree.getKind()) {
                final VariableTree variableTree = (VariableTree) tree;
                final ModifiersTree modifiersTree = variableTree.getModifiers();
                // we only care about instance variables
                if (modifiersTree.getFlags().contains(Modifier.STATIC)) {
                    continue;
                }
                // create a getter method
                final MethodTree newGetterMethodTree = treeMaker.Method(
                        treeMaker.Modifiers(EnumSet.of(Modifier.PUBLIC)),
                        variableTree.getName().toString(),
                        variableTree.getType(),
                        Collections.<TypeParameterTree>emptyList(),
                        Collections.<VariableTree>emptyList(),
                        Collections.<ExpressionTree>emptyList(),
                        "{" + MessageFormat.format("return this.{0};", variableTree.getName().toString()) + "}",
                        null);
                newMembersTree.add(newGetterMethodTree);
            }
        }
    }

    private void buildNewSetters(final TreeMaker treeMaker, final ClassTree classTree, final List<Tree> newVariablesTree, final List<Tree> newMembersTree) {
        // and create new builder setters
        for (final Tree tree : newVariablesTree) {
            if (Tree.Kind.VARIABLE == tree.getKind()) {
                final VariableTree variableTree = (VariableTree) tree;
                final ModifiersTree modifiersTree = variableTree.getModifiers();
                // we only care about instance variables
                if (modifiersTree.getFlags().contains(Modifier.STATIC)) {
                    continue;
                }
                // create the parameter
                final VariableTree newParameterTree = treeMaker.Variable(
                        treeMaker.Modifiers(EnumSet.of(Modifier.FINAL)),
                        variableTree.getName().toString(),
                        variableTree.getType(),
                        null);
                // create a setter method
                final MethodTree newSetterMethodTree = treeMaker.Method(
                        treeMaker.Modifiers(EnumSet.of(Modifier.PUBLIC)),
                        variableTree.getName().toString(),
                        treeMaker.Type("Builder"),
                        Collections.<TypeParameterTree>emptyList(),
                        Collections.<VariableTree>singletonList(newParameterTree),
                        Collections.<ExpressionTree>emptyList(),
                        "{" + MessageFormat.format("this.{0} = {0};return this;", variableTree.getName().toString()) + "}",
                        null);
                newMembersTree.add(newSetterMethodTree);
            }
        }
    }

    private void buildNewFactory(final TreeMaker treeMaker, final List<Tree> newMembersTree) {
        // create the static factory method
        final MethodTree newFactoryConstructorMethodTree = treeMaker.Method(
                treeMaker.Modifiers(EnumSet.of(Modifier.PUBLIC, Modifier.STATIC)),
                "construct",
                treeMaker.Type("Builder"),
                Collections.<TypeParameterTree>emptyList(),
                Collections.<VariableTree>emptyList(),
                Collections.<ExpressionTree>emptyList(),
                "{return new Builder();}",
                null);
        newMembersTree.add(newFactoryConstructorMethodTree);
    }

    private void buildNewConstructor(final TreeMaker treeMaker, final List<Tree> newMembersTree) {
        // create the constructor
        final MethodTree newConstructorMethodTree = treeMaker.Constructor(
                treeMaker.Modifiers(EnumSet.of(Modifier.PROTECTED)),
                Collections.<TypeParameterTree>emptyList(),
                Collections.<VariableTree>emptyList(),
                Collections.<ExpressionTree>emptyList(),
                "{}");
        newMembersTree.add(newConstructorMethodTree);
    }

    private void buildNewFields(final TreeMaker treeMaker, final ClassTree classTree, final List<Tree> newVariablesTree) {
        if (Tree.Kind.CLASS == classTree.getKind()) {
            // get the variables from the existing class
            for (final Tree tree : classTree.getMembers()) {
                if (Tree.Kind.VARIABLE == tree.getKind()) {
                    final VariableTree variableTree = (VariableTree) tree;
                    // we only care about instance variables
                    final ModifiersTree modifiersTree = variableTree.getModifiers();
                    if (modifiersTree.getFlags().contains(Modifier.STATIC)) {
                        continue;
                    }
                    // create a field
                    final VariableTree newFieldTree = treeMaker.Variable(
                            treeMaker.Modifiers(EnumSet.of(Modifier.PROTECTED)),
                            variableTree.getName().toString(),
                            variableTree.getType(),
                            null);
                    newVariablesTree.add(newFieldTree);
                }
            }
        } else if (Tree.Kind.INTERFACE == classTree.getKind()) {
            // get the variables from the interface getters and setters
            for (final Tree tree : classTree.getMembers()) {
                if (Tree.Kind.METHOD == tree.getKind()) {
                    final MethodTree methodTree = (MethodTree) tree;
                    // we only care about getters
                    final String prefix;
                    if (methodTree.getName().toString().startsWith("get")) {
                        prefix = "get";
                    } else if (methodTree.getName().toString().startsWith("is")) {
                        prefix = "is";
                    } else {
                        continue;
                    }
                    // we only care about getters with no parameters
                    if (!(methodTree.getParameters() == null || methodTree.getParameters().isEmpty())) {
                        continue;
                    }
                    // we only care about instance variables
                    final ModifiersTree modifiersTree = methodTree.getModifiers();
                    if (modifiersTree.getFlags().contains(Modifier.STATIC)) {
                        continue;
                    }
                    // create a field
                    final VariableTree newFieldTree = treeMaker.Variable(
                            treeMaker.Modifiers(EnumSet.of(Modifier.PROTECTED)),
                            Character.toLowerCase(methodTree.getName().toString().charAt(prefix.length())) + methodTree.getName().toString().substring(prefix.length() + 1),
                            methodTree.getReturnType(),
                            null);
                    newVariablesTree.add(newFieldTree);
                }
            }
        }
    }

    private void buildNewClass(final TreeMaker treeMaker, final ClassTree classTree, final List<Tree> newMembersTree, final WorkingCopy workingCopy) {
        // create the new builder internal class
        ClassTree newClassTree = treeMaker.Class(
                treeMaker.Modifiers(EnumSet.of(Modifier.PUBLIC, Modifier.STATIC)),
                "Builder",
                Collections.<TypeParameterTree>emptyList(),
                null,
                Collections.<Tree>emptyList(),
                Collections.<Tree>emptyList());
        // add all of the members to the new class
        for (final Tree tree : newMembersTree) {
            newClassTree = treeMaker.addClassMember(newClassTree, tree);
        }
        // modify and save the existing class
        final ClassTree modifiedClass = treeMaker.addClassMember(classTree, newClassTree);
        workingCopy.rewrite(classTree, modifiedClass);
    }

    private String buildListString(final List<Tree> newVariablesTree) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < newVariablesTree.size(); i++) {
            sb.append(((VariableTree) newVariablesTree.get(i)).getName().toString());
            if (i < newVariablesTree.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    @MimeRegistration(mimeType = "text/x-java", service = CodeGenerator.Factory.class)
    public static class Factory implements CodeGenerator.Factory {

        @Override
        public List<? extends CodeGenerator> create(Lookup context) {
            return Collections.singletonList(new BuilderCodeGenerator(context));
        }
    }
}
