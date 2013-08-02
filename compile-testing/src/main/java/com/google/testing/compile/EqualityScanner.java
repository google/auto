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
package com.google.testing.compile;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
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
final class EqualityScanner extends SimpleTreeVisitor<Boolean, Tree> {
  private <T extends Tree> Optional<T> checkTypeAndCast(T reference, Tree tree) {
    Kind referenceKind = checkNotNull(reference).getKind();
    Kind treeKind = checkNotNull(tree).getKind();
    if (referenceKind == treeKind) {
      @SuppressWarnings("unchecked") // checked by Kind
      T treeAsReferenceType = (T) tree;
      return Optional.of(treeAsReferenceType);
    } else {
      return Optional.absent();
    }
  }

  private boolean scan(@Nullable Tree reference, @Nullable Tree tree) {
    return (reference == null) ? (tree == null) : reference.accept(this, tree);
  }

  private boolean parallelScan(Iterable<? extends Tree> reference,
      Iterable<? extends Tree> trees) {
    Iterator<? extends Tree> referenceIterator = reference.iterator();
    Iterator<? extends Tree> treesIterator = trees.iterator();
    while (referenceIterator.hasNext() && treesIterator.hasNext()) {
      if (!referenceIterator.next().accept(this, treesIterator.next())) {
        return false;
      }
    }
    return (referenceIterator.hasNext() == treesIterator.hasNext());
  }

  @Override
  public Boolean visitAnnotation(AnnotationTree reference, Tree tree) {
    Optional<AnnotationTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getAnnotationType(), other.get().getAnnotationType())
        && parallelScan(reference.getArguments(), other.get().getArguments());
  }

  @Override
  public Boolean visitMethodInvocation(MethodInvocationTree reference, Tree tree) {
    Optional<MethodInvocationTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && parallelScan(reference.getTypeArguments(), other.get().getTypeArguments())
        && scan(reference.getMethodSelect(), other.get().getMethodSelect())
        && parallelScan(reference.getArguments(), other.get().getArguments());
  }

  @Override
  public Boolean visitAssert(AssertTree reference, Tree tree) {
    Optional<AssertTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getCondition(), other.get().getCondition())
        && scan(reference.getDetail(), other.get().getDetail());
  }

  @Override
  public Boolean visitAssignment(AssignmentTree reference, Tree tree) {
    Optional<AssignmentTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getVariable(), other.get().getVariable())
        && scan(reference.getExpression(), other.get().getExpression());
  }

  @Override
  public Boolean visitCompoundAssignment(CompoundAssignmentTree reference, Tree tree) {
    Optional<CompoundAssignmentTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getVariable(), other.get().getVariable())
        && scan(reference.getExpression(), other.get().getExpression());
  }

  @Override
  public Boolean visitBinary(BinaryTree reference, Tree tree) {
    Optional<BinaryTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getLeftOperand(), other.get().getLeftOperand())
        && scan(reference.getRightOperand(), other.get().getRightOperand());
  }

  @Override
  public Boolean visitBlock(BlockTree reference, Tree tree) {
    Optional<BlockTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && (reference.isStatic() == other.get().isStatic())
        && parallelScan(reference.getStatements(), other.get().getStatements());
  }

  @Override
  public Boolean visitBreak(BreakTree reference, Tree tree) {
    Optional<BreakTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent() && reference.getLabel().contentEquals(other.get().getLabel());
  }

  @Override
  public Boolean visitCase(CaseTree reference, Tree tree) {
    Optional<CaseTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getExpression(), other.get().getExpression())
        && parallelScan(reference.getStatements(), other.get().getStatements());
  }

  @Override
  public Boolean visitCatch(CatchTree reference, Tree tree) {
    Optional<CatchTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getParameter(), other.get().getParameter())
        && scan(reference.getBlock(), other.get().getBlock());
  }

  @Override
  public Boolean visitClass(ClassTree reference, Tree tree) {
    Optional<ClassTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getModifiers(), other.get().getModifiers())
        && reference.getSimpleName().contentEquals(other.get().getSimpleName())
        && parallelScan(reference.getTypeParameters(), other.get().getTypeParameters())
        && scan(reference.getExtendsClause(), other.get().getExtendsClause())
        && parallelScan(reference.getImplementsClause(), other.get().getImplementsClause())
        && parallelScan(reference.getMembers(), other.get().getMembers());
  }

  @Override
  public Boolean visitConditionalExpression(ConditionalExpressionTree reference, Tree tree) {
    Optional<ConditionalExpressionTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getCondition(), other.get().getCondition())
        && scan(reference.getTrueExpression(), other.get().getTrueExpression())
        && scan(reference.getFalseExpression(), other.get().getFalseExpression());
  }

  @Override
  public Boolean visitContinue(ContinueTree reference, Tree tree) {
    Optional<ContinueTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && reference.getLabel().contentEquals(other.get().getLabel());
  }

  @Override
  public Boolean visitDoWhileLoop(DoWhileLoopTree reference, Tree tree) {
    Optional<DoWhileLoopTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getCondition(), other.get().getCondition())
        && scan(reference.getStatement(), other.get().getStatement());
  }

  @Override
  public Boolean visitErroneous(ErroneousTree reference, Tree tree) {
    Optional<ErroneousTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && parallelScan(reference.getErrorTrees(), other.get().getErrorTrees());
  }

  @Override
  public Boolean visitExpressionStatement(ExpressionStatementTree reference, Tree tree) {
    Optional<ExpressionStatementTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getExpression(), other.get().getExpression());
  }

  @Override
  public Boolean visitEnhancedForLoop(EnhancedForLoopTree reference, Tree tree) {
    Optional<EnhancedForLoopTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getVariable(), other.get().getVariable())
        && scan(reference.getExpression(), other.get().getExpression())
        && scan(reference.getStatement(), other.get().getStatement());
  }

  @Override
  public Boolean visitForLoop(ForLoopTree reference, Tree tree) {
    Optional<ForLoopTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && parallelScan(reference.getInitializer(), other.get().getInitializer())
        && scan(reference.getCondition(), other.get().getCondition())
        && parallelScan(reference.getUpdate(), other.get().getUpdate())
        && scan(reference.getStatement(), other.get().getStatement());

  }

  @Override
  public Boolean visitIdentifier(IdentifierTree reference, Tree tree) {
    Optional<IdentifierTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && reference.getName().contentEquals(other.get().getName());
  }

  @Override
  public Boolean visitIf(IfTree reference, Tree tree) {
    Optional<IfTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getCondition(), other.get().getCondition())
        && scan(reference.getThenStatement(), other.get().getThenStatement())
        && scan(reference.getElseStatement(), other.get().getElseStatement());
  }

  @Override
  public Boolean visitImport(ImportTree reference, Tree tree) {
    Optional<ImportTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && (reference.isStatic() == other.get().isStatic())
        && scan(reference.getQualifiedIdentifier(), other.get().getQualifiedIdentifier());
  }

  @Override
  public Boolean visitArrayAccess(ArrayAccessTree reference, Tree tree) {
    Optional<ArrayAccessTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getExpression(), other.get().getExpression())
        && scan(reference.getIndex(), other.get().getIndex());
  }

  @Override
  public Boolean visitLabeledStatement(LabeledStatementTree reference, Tree tree) {
    Optional<LabeledStatementTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && reference.getLabel().contentEquals(other.get().getLabel())
        && scan(reference.getStatement(), other.get().getStatement());
  }

  @Override
  public Boolean visitLiteral(LiteralTree reference, Tree tree) {
    Optional<LiteralTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && reference.getValue().equals(other.get().getValue());
  }

  @Override
  public Boolean visitMethod(MethodTree reference, Tree tree) {
    Optional<MethodTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getModifiers(), other.get().getModifiers())
        && reference.getName().contentEquals(other.get().getName())
        && scan(reference.getReturnType(), other.get().getReturnType())
        && parallelScan(reference.getTypeParameters(), other.get().getTypeParameters())
        && parallelScan(reference.getParameters(), other.get().getParameters())
        && parallelScan(reference.getThrows(), other.get().getThrows())
        && scan(reference.getBody(), other.get().getBody())
        && scan(reference.getDefaultValue(), other.get().getDefaultValue());
  }

  @Override
  public Boolean visitModifiers(ModifiersTree reference, Tree tree) {
    Optional<ModifiersTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && reference.getFlags().equals(other.get().getFlags())
        && parallelScan(reference.getAnnotations(), other.get().getAnnotations());
  }

  @Override
  public Boolean visitNewArray(NewArrayTree reference, Tree tree) {
    Optional<NewArrayTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getType(), other.get().getType())
        && parallelScan(reference.getDimensions(), other.get().getDimensions())
        && parallelScan(reference.getInitializers(), other.get().getInitializers());
  }

  @Override
  public Boolean visitNewClass(NewClassTree reference, Tree tree) {
    Optional<NewClassTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getEnclosingExpression(), other.get().getEnclosingExpression())
        && parallelScan(reference.getTypeArguments(), other.get().getTypeArguments())
        && scan(reference.getIdentifier(), other.get().getIdentifier())
        && parallelScan(reference.getArguments(), other.get().getArguments())
        && scan(reference.getClassBody(), other.get().getClassBody());
  }

  @Override
  public Boolean visitParenthesized(ParenthesizedTree reference, Tree tree) {
    Optional<ParenthesizedTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getExpression(), other.get().getExpression());
  }

  @Override
  public Boolean visitReturn(ReturnTree reference, Tree tree) {
    Optional<ReturnTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getExpression(), other.get().getExpression());
  }

  @Override
  public Boolean visitMemberSelect(MemberSelectTree reference, Tree tree) {
    Optional<MemberSelectTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getExpression(), other.get().getExpression())
        && reference.getIdentifier().contentEquals(other.get().getIdentifier());
  }

  @Override
  public Boolean visitEmptyStatement(EmptyStatementTree reference, Tree tree) {
    return checkTypeAndCast(reference, tree).isPresent();
  }

  @Override
  public Boolean visitSwitch(SwitchTree reference, Tree tree) {
    Optional<SwitchTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getExpression(), other.get().getExpression())
        && parallelScan(reference.getCases(), other.get().getCases());
  }

  @Override
  public Boolean visitSynchronized(SynchronizedTree reference, Tree tree) {
    Optional<SynchronizedTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getExpression(), other.get().getExpression())
        && scan(reference.getBlock(), other.get().getBlock());
  }

  @Override
  public Boolean visitThrow(ThrowTree reference, Tree tree) {
    Optional<ThrowTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getExpression(), other.get().getExpression());
  }

  @Override
  public Boolean visitCompilationUnit(CompilationUnitTree reference, Tree tree) {
    Optional<CompilationUnitTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && parallelScan(reference.getPackageAnnotations(), other.get().getPackageAnnotations())
        && scan(reference.getPackageName(), other.get().getPackageName())
        && parallelScan(reference.getImports(), other.get().getImports())
        && parallelScan(reference.getTypeDecls(), other.get().getTypeDecls());
    // specifically don't check the JavaFileObject.  Those are supposed to be different.
    // LineMap is irrelevant
  }

  @Override
  public Boolean visitTry(TryTree reference, Tree tree) {
    Optional<TryTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getBlock(), other.get().getBlock())
        && parallelScan(reference.getCatches(), other.get().getCatches())
        && scan(reference.getFinallyBlock(), other.get().getFinallyBlock());
  }

  @Override
  public Boolean visitParameterizedType(ParameterizedTypeTree reference, Tree tree) {
    Optional<ParameterizedTypeTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getType(), other.get().getType())
        && parallelScan(reference.getTypeArguments(), other.get().getTypeArguments());
  }

  @Override
  public Boolean visitArrayType(ArrayTypeTree reference, Tree tree) {
    Optional<ArrayTypeTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getType(), other.get().getType());
  }

  @Override
  public Boolean visitTypeCast(TypeCastTree reference, Tree tree) {
    Optional<TypeCastTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getType(), other.get().getType())
        && scan(reference.getExpression(), other.get().getExpression());
  }

  @Override
  public Boolean visitPrimitiveType(PrimitiveTypeTree reference, Tree tree) {
    Optional<PrimitiveTypeTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && (reference.getPrimitiveTypeKind() == other.get().getPrimitiveTypeKind());
  }

  @Override
  public Boolean visitTypeParameter(TypeParameterTree reference, Tree tree) {
    Optional<TypeParameterTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && reference.getName().contentEquals(other.get().getName())
        && parallelScan(reference.getBounds(), other.get().getBounds());
  }

  @Override
  public Boolean visitInstanceOf(InstanceOfTree reference, Tree tree) {
    Optional<InstanceOfTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getExpression(), other.get().getExpression())
        && scan(reference.getType(), other.get().getType());
  }

  @Override
  public Boolean visitUnary(UnaryTree reference, Tree tree) {
    Optional<UnaryTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getExpression(), other.get().getExpression());
  }

  @Override
  public Boolean visitVariable(VariableTree reference, Tree tree) {
    Optional<VariableTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getModifiers(), other.get().getModifiers())
        && reference.getName().contentEquals(other.get().getName())
        && scan(reference.getType(), other.get().getType())
        && scan(reference.getInitializer(), other.get().getInitializer());
  }

  @Override
  public Boolean visitWhileLoop(WhileLoopTree reference, Tree tree) {
    Optional<WhileLoopTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getCondition(), other.get().getCondition())
        && scan(reference.getStatement(), other.get().getStatement());
  }

  @Override
  public Boolean visitWildcard(WildcardTree reference, Tree tree) {
    Optional<WildcardTree> other = checkTypeAndCast(reference, tree);
    return other.isPresent()
        && scan(reference.getBound(), other.get().getBound());
  }

  @Override
  public Boolean visitOther(Tree reference, Tree tree) {
    throw new UnsupportedOperationException("cannot compare unknown trees");
  }
}
