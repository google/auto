/*
 * Copyright (C) 2013 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.auto.factory.gentest;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;

import javax.annotation.Nullable;

import org.truth0.FailureStrategy;
import org.truth0.TestVerb;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.SimpleTreeVisitor;

/**
 * A visitor that traverses a {@link Tree} in parallel with its argument to check that the trees are
 * the same.
 *
 * @author Gregory Kick
 */
/*
 * This should really just implement TreeVisitor this insulates against API changes in different
 * versions of Java.
 */
final class EqualityScanner extends SimpleTreeVisitor<Void, Tree> {
    private final TestVerb testVerb;

  EqualityScanner(FailureStrategy failureStrategy) {
    this.testVerb = new TestVerb(failureStrategy);
  }

  private <T extends Tree> T checkTypeAndCast(T reference, Tree tree) {
    Kind referenceKind = checkNotNull(reference).getKind();
    Kind treeKind = checkNotNull(tree).getKind();
    testVerb.that(referenceKind).is(treeKind);
    @SuppressWarnings("unchecked")
    T treeAsReferenceType = (T) tree;
    return treeAsReferenceType;
  }

  private void scan(@Nullable Tree reference, @Nullable Tree tree) {
    if (reference == null) {
      testVerb.that(tree).isNull();
    } else {
      reference.accept(this, tree);
    }
  }

  private void parallelScan(Iterable<? extends Tree> reference,
      Iterable<? extends Tree> trees) {
    Iterator<? extends Tree> referenceIterator = reference.iterator();
    Iterator<? extends Tree> treesIterator = trees.iterator();
    while (referenceIterator.hasNext() && treesIterator.hasNext()) {
      referenceIterator.next().accept(this, treesIterator.next());
    }
    testVerb.that(referenceIterator.hasNext()).is(treesIterator.hasNext());
  }

  @Override
  public Void visitAnnotation(AnnotationTree reference, Tree tree) {
    AnnotationTree other = checkTypeAndCast(reference, tree);
    scan(reference.getAnnotationType(), other.getAnnotationType());
    parallelScan(reference.getArguments(), other.getArguments());
    return null;
  }

  @Override
  public Void visitMethodInvocation(MethodInvocationTree reference, Tree tree) {
    MethodInvocationTree other = checkTypeAndCast(reference, tree);
    parallelScan(reference.getTypeArguments(), other.getTypeArguments());
    scan(reference.getMethodSelect(), other.getMethodSelect());
    parallelScan(reference.getArguments(), other.getArguments());
    return null;
  }

  @Override
  public Void visitAssert(AssertTree reference, Tree tree) {
    AssertTree other = checkTypeAndCast(reference, tree);
    scan(reference.getCondition(), other.getCondition());
    scan(reference.getDetail(), other.getDetail());
    return null;
  }

  @Override
  public Void visitAssignment(AssignmentTree reference, Tree tree) {
    AssignmentTree other = checkTypeAndCast(reference, tree);
    scan(reference.getVariable(), other.getVariable());
    scan(reference.getExpression(), other.getExpression());
    return null;
  }

  @Override
  public Void visitCompoundAssignment(CompoundAssignmentTree reference, Tree tree) {
    CompoundAssignmentTree other = checkTypeAndCast(reference, tree);
    scan(reference.getVariable(), other.getVariable());
    scan(reference.getExpression(), other.getExpression());
    return null;
  }

  @Override
  public Void visitBinary(BinaryTree reference, Tree tree) {
    BinaryTree other = checkTypeAndCast(reference, tree);
    scan(reference.getLeftOperand(), other.getLeftOperand());
    scan(reference.getRightOperand(), other.getRightOperand());
    return null;
  }

  @Override
  public Void visitBlock(BlockTree reference, Tree tree) {
    BlockTree other = checkTypeAndCast(reference, tree);
    testVerb.that(reference.isStatic()).is(other.isStatic());
    parallelScan(reference.getStatements(), other.getStatements());
    return null;
  }

  @Override
  public Void visitBreak(BreakTree reference, Tree tree) {
    BreakTree other = checkTypeAndCast(reference, tree);
    testVerb.that(reference.getLabel()).isEqualTo(other.getLabel());
    return null;
  }

  @Override
  public Void visitCase(CaseTree reference, Tree tree) {
    CaseTree other = checkTypeAndCast(reference, tree);
    scan(reference.getExpression(), other.getExpression());
    parallelScan(reference.getStatements(), other.getStatements());
    return null;
  }

  @Override
  public Void visitCatch(CatchTree reference, Tree tree) {
    CatchTree other = checkTypeAndCast(reference, tree);
    scan(reference.getParameter(), other.getParameter());
    scan(reference.getBlock(), other.getBlock());
    return null;
  }

  @Override
  public Void visitClass(ClassTree reference, Tree tree) {
    ClassTree other = checkTypeAndCast(reference, tree);
    scan(reference.getModifiers(), other.getModifiers());
    testVerb.that(reference.getSimpleName()).isEqualTo(other.getSimpleName());
    parallelScan(reference.getTypeParameters(), other.getTypeParameters());
    scan(reference.getExtendsClause(), other.getExtendsClause());
    parallelScan(reference.getImplementsClause(), other.getImplementsClause());
    parallelScan(reference.getMembers(), other.getMembers());
    return null;
  }

  @Override
  public Void visitConditionalExpression(ConditionalExpressionTree reference, Tree tree) {
    ConditionalExpressionTree other = checkTypeAndCast(reference, tree);
    scan(reference.getCondition(), other.getCondition());
    scan(reference.getTrueExpression(), other.getTrueExpression());
    scan(reference.getFalseExpression(), other.getFalseExpression());
    return null;
  }

  @Override
  public Void visitContinue(ContinueTree reference, Tree tree) {
    ContinueTree other = checkTypeAndCast(reference, tree);
    testVerb.that(reference.getLabel()).isEqualTo(other.getLabel());
    return null;
  }

  @Override
  public Void visitDoWhileLoop(DoWhileLoopTree reference, Tree tree) {
    DoWhileLoopTree other = checkTypeAndCast(reference, tree);
    scan(reference.getCondition(), other.getCondition());
    scan(reference.getStatement(), other.getStatement());
    return null;
  }

  @Override
  public Void visitErroneous(ErroneousTree reference, Tree tree) {
    ErroneousTree other = checkTypeAndCast(reference, tree);
    parallelScan(reference.getErrorTrees(), other.getErrorTrees());
    return null;
  }

  @Override
  public Void visitExpressionStatement(ExpressionStatementTree reference, Tree tree) {
    ExpressionStatementTree other = checkTypeAndCast(reference, tree);
    scan(reference.getExpression(), other.getExpression());
    return null;
  }

  @Override
  public Void visitEnhancedForLoop(EnhancedForLoopTree reference, Tree tree) {
    EnhancedForLoopTree other = checkTypeAndCast(reference, tree);
    scan(reference.getVariable(), other.getVariable());
    scan(reference.getExpression(), other.getExpression());
    scan(reference.getStatement(), other.getStatement());
    return null;
  }

  @Override
  public Void visitForLoop(ForLoopTree reference, Tree tree) {
    ForLoopTree other = checkTypeAndCast(reference, tree);
    parallelScan(reference.getInitializer(), other.getInitializer());
    scan(reference.getCondition(), other.getCondition());
    parallelScan(reference.getUpdate(), other.getUpdate());
    scan(reference.getStatement(), other.getStatement());
    return null;
  }

  @Override
  public Void visitIdentifier(IdentifierTree reference, Tree tree) {
    IdentifierTree other = checkTypeAndCast(reference, tree);
    testVerb.that(reference.getName()).isEqualTo(other.getName());
    return null;
  }

  @Override
  public Void visitIf(IfTree reference, Tree tree) {
    IfTree other = checkTypeAndCast(reference, tree);
    scan(reference.getCondition(), other.getCondition());
    scan(reference.getThenStatement(), other.getThenStatement());
    scan(reference.getElseStatement(), other.getElseStatement());
    return null;
  }

  @Override
  public Void visitImport(ImportTree reference, Tree tree) {
    ImportTree other = checkTypeAndCast(reference, tree);
    testVerb.that(reference.isStatic()).is(other.isStatic());
    scan(reference.getQualifiedIdentifier(), other.getQualifiedIdentifier());
    return null;
  }

  @Override
  public Void visitArrayAccess(ArrayAccessTree reference, Tree tree) {
    ArrayAccessTree other = checkTypeAndCast(reference, tree);
    scan(reference.getExpression(), other.getExpression());
    scan(reference.getIndex(), other.getIndex());
    return null;
  }

  @Override
  public Void visitLabeledStatement(LabeledStatementTree reference, Tree tree) {
    LabeledStatementTree other = checkTypeAndCast(reference, tree);
    testVerb.that(reference.getLabel()).isEqualTo(other.getLabel());
    scan(reference.getStatement(), other.getStatement());
    return null;
  }

  @Override
  public Void visitLiteral(LiteralTree reference, Tree tree) {
    LiteralTree other = checkTypeAndCast(reference, tree);
    testVerb.that(reference.getValue()).isEqualTo(other.getValue());
    return null;
  }

  @Override
  public Void visitMethod(MethodTree reference, Tree tree) {
    MethodTree other = checkTypeAndCast(reference, tree);
    scan(reference.getModifiers(), other.getModifiers());
    testVerb.that(reference.getName()).isEqualTo(other.getName());
    scan(reference.getReturnType(), other.getReturnType());
    parallelScan(reference.getTypeParameters(), other.getTypeParameters());
    parallelScan(reference.getParameters(), other.getParameters());
    parallelScan(reference.getThrows(), other.getThrows());
    scan(reference.getBody(), other.getBody());
    scan(reference.getDefaultValue(), other.getDefaultValue());
    return null;
  }

  @Override
  public Void visitModifiers(ModifiersTree reference, Tree tree) {
    ModifiersTree other = checkTypeAndCast(reference, tree);
    testVerb.that(reference.getFlags()).isEqualTo(other.getFlags());
    parallelScan(reference.getAnnotations(), other.getAnnotations());
    return null;
  }

  @Override
  public Void visitNewArray(NewArrayTree reference, Tree tree) {
    NewArrayTree other = checkTypeAndCast(reference, tree);
    scan(reference.getType(), other.getType());
    parallelScan(reference.getDimensions(), other.getDimensions());
    parallelScan(reference.getInitializers(), other.getInitializers());
    return null;
  }

  @Override
  public Void visitNewClass(NewClassTree reference, Tree tree) {
    NewClassTree other = checkTypeAndCast(reference, tree);
    scan(reference.getEnclosingExpression(), other.getEnclosingExpression());
    parallelScan(reference.getTypeArguments(), other.getTypeArguments());
    scan(reference.getIdentifier(), other.getIdentifier());
    parallelScan(reference.getArguments(), other.getArguments());
    scan(reference.getClassBody(), other.getClassBody());
    return null;
  }

  @Override
  public Void visitParenthesized(ParenthesizedTree reference, Tree tree) {
    ParenthesizedTree other = checkTypeAndCast(reference, tree);
    scan(reference.getExpression(), other.getExpression());
    return null;
  }

  @Override
  public Void visitReturn(ReturnTree reference, Tree tree) {
    ReturnTree other = checkTypeAndCast(reference, tree);
    scan(reference.getExpression(), other.getExpression());
    return null;
  }

  @Override
  public Void visitMemberSelect(MemberSelectTree reference, Tree tree) {
    MemberSelectTree other = checkTypeAndCast(reference, tree);
    scan(reference.getExpression(), other.getExpression());
    testVerb.that(reference.getIdentifier()).isEqualTo(other.getIdentifier());
    return null;
  }

  @Override
  public Void visitEmptyStatement(EmptyStatementTree reference, Tree tree) {
    checkTypeAndCast(reference, tree);
    return null;
  }

  @Override
  public Void visitSwitch(SwitchTree reference, Tree tree) {
    SwitchTree other = checkTypeAndCast(reference, tree);
    scan(reference.getExpression(), other.getExpression());
    parallelScan(reference.getCases(), other.getCases());
    return null;
  }

  @Override
  public Void visitSynchronized(SynchronizedTree reference, Tree tree) {
    SynchronizedTree other = checkTypeAndCast(reference, tree);
    scan(reference.getExpression(), other.getExpression());
    scan(reference.getBlock(), other.getBlock());
    return null;
  }

  @Override
  public Void visitThrow(ThrowTree reference, Tree tree) {
    ThrowTree other = checkTypeAndCast(reference, tree);
    scan(reference.getExpression(), other.getExpression());
    return null;
  }

  @Override
  public Void visitCompilationUnit(CompilationUnitTree reference, Tree tree) {
    CompilationUnitTree other = checkTypeAndCast(reference, tree);
    parallelScan(reference.getPackageAnnotations(), other.getPackageAnnotations());
    scan(reference.getPackageName(), other.getPackageName());
    parallelScan(reference.getImports(), other.getImports());
    parallelScan(reference.getTypeDecls(), other.getTypeDecls());
    // specifically don't check the JavaFileObject.  Those are supposed to be different.
    // LineMap is irrelevant
    return null;
  }

  @Override
  public Void visitTry(TryTree reference, Tree tree) {
    TryTree other = checkTypeAndCast(reference, tree);
    scan(reference.getBlock(), other.getBlock());
    parallelScan(reference.getCatches(), other.getCatches());
    scan(reference.getFinallyBlock(), other.getFinallyBlock());
    return null;
  }

  @Override
  public Void visitParameterizedType(ParameterizedTypeTree reference, Tree tree) {
    ParameterizedTypeTree other = checkTypeAndCast(reference, tree);
    scan(reference.getType(), other.getType());
    parallelScan(reference.getTypeArguments(), other.getTypeArguments());
    return null;
  }

  @Override
  public Void visitArrayType(ArrayTypeTree reference, Tree tree) {
    ArrayTypeTree other = checkTypeAndCast(reference, tree);
    scan(reference.getType(), other.getType());
    return null;
  }

  @Override
  public Void visitTypeCast(TypeCastTree reference, Tree tree) {
    TypeCastTree other = checkTypeAndCast(reference, tree);
    scan(reference.getType(), other.getType());
    scan(reference.getExpression(), other.getExpression());
    return null;
  }

  @Override
  public Void visitPrimitiveType(PrimitiveTypeTree reference, Tree tree) {
    PrimitiveTypeTree other = checkTypeAndCast(reference, tree);
    testVerb.that(reference.getPrimitiveTypeKind()).is(other.getPrimitiveTypeKind());
    return null;
  }

  @Override
  public Void visitTypeParameter(TypeParameterTree reference, Tree tree) {
    TypeParameterTree other = checkTypeAndCast(reference, tree);
    testVerb.that(reference.getName()).isEqualTo(other.getName());
    parallelScan(reference.getBounds(), other.getBounds());
    return null;
  }

  @Override
  public Void visitInstanceOf(InstanceOfTree reference, Tree tree) {
    InstanceOfTree other = checkTypeAndCast(reference, tree);
    scan(reference.getExpression(), other.getExpression());
    scan(reference.getType(), other.getType());
    return null;
  }

  @Override
  public Void visitUnary(UnaryTree reference, Tree tree) {
    UnaryTree other = checkTypeAndCast(reference, tree);
    scan(reference.getExpression(), other.getExpression());
    return null;
  }

  @Override
  public Void visitVariable(VariableTree reference, Tree tree) {
    VariableTree other = checkTypeAndCast(reference, tree);
    scan(reference.getModifiers(), other.getModifiers());
    testVerb.that(reference.getName()).is(other.getName());
    scan(reference.getType(), other.getType());
    scan(reference.getInitializer(), other.getInitializer());
    return null;
  }

  @Override
  public Void visitWhileLoop(WhileLoopTree reference, Tree tree) {
    WhileLoopTree other = checkTypeAndCast(reference, tree);
    scan(reference.getCondition(), other.getCondition());
    scan(reference.getStatement(), other.getStatement());
    return null;
  }

  @Override
  public Void visitWildcard(WildcardTree reference, Tree tree) {
    WildcardTree other = checkTypeAndCast(reference, tree);
    scan(reference.getBound(), other.getBound());
    return null;
  }

  @Override
  public Void visitOther(Tree reference, Tree tree) {
    throw new UnsupportedOperationException("cannot compare unknown trees");
  }
}
