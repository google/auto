/*
 * Copyright 2014 Google LLC
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
package com.google.auto.value.processor;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

/**
 * Handling of undefined types. When we see an undefined type, it might genuinely be undefined, or
 * it might be a type whose source code will be generated later on as part of the same compilation.
 * If we encounter an undefined type in a place where we need to know the type, we throw {@link
 * MissingTypeException}. We then catch that and defer processing for the current class until the
 * next annotation-processing "round". If the missing class has been generated in the meanwhile, we
 * may now be able to complete processing. After a round has completed without generating any new
 * source code, if there are still missing types then we report an error.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
final class MissingTypes {
  private MissingTypes() {}

  /**
   * Exception thrown in the specific case where processing of a class was abandoned because it
   * required types that the class references to be present and they were not. This case is handled
   * specially because it is possible that those types might be generated later during annotation
   * processing, so we should reattempt the processing of the class in a later annotation processing
   * round.
   */
  @SuppressWarnings("serial")
  static class MissingTypeException extends RuntimeException {
    MissingTypeException(ErrorType missingType) {
      // Although it is not specified as such, in practice ErrorType.toString() is the type name
      // that appeared in the source code. Showing it here can help in debugging issues with
      // deferral.
      super(missingType == null ? "" : missingType.toString());
    }
  }

  /**
   * Check that the return type and parameter types of the given method are all defined, and arrange
   * to defer processing until the next round if not.
   *
   * @throws MissingTypeException if the return type or a parameter type of the given method is
   *     undefined
   */
  static void deferIfMissingTypesIn(ExecutableElement method) {
    MISSING_TYPE_VISITOR.check(method.getReturnType());
    for (VariableElement param : method.getParameters()) {
      MISSING_TYPE_VISITOR.check(param.asType());
    }
  }

  private static final MissingTypeVisitor MISSING_TYPE_VISITOR = new MissingTypeVisitor();

  private static class MissingTypeVisitor extends SimpleTypeVisitor8<Void, TypeMirrorSet> {
    // Avoid infinite recursion for a type like `Enum<E extends Enum<E>>` by remembering types that
    // we have already seen on this visit. Recursion has to go through a declared type, such as Enum
    // in this example, so in principle it should be enough to check only in visitDeclared. However
    // Eclipse has a quirk where the second E in `Enum<E extends Enum<E>>` is not the same as the
    // first, and if you ask for its bounds you will get another `Enum<E>` with a third E. So we
    // also check in visitTypeVariable. TypeMirrorSet does consider that all these E variables are
    // the same so infinite recursion is avoided.
    void check(TypeMirror type) {
      type.accept(this, new TypeMirrorSet());
    }

    @Override
    public Void visitError(ErrorType t, TypeMirrorSet visiting) {
      throw new MissingTypeException(t);
    }

    @Override
    public Void visitArray(ArrayType t, TypeMirrorSet visiting) {
      return t.getComponentType().accept(this, visiting);
    }

    @Override
    public Void visitDeclared(DeclaredType t, TypeMirrorSet visiting) {
      if (visiting.add(t)) {
        visitAll(t.getTypeArguments(), visiting);
      }
      return null;
    }

    @Override
    public Void visitTypeVariable(TypeVariable t, TypeMirrorSet visiting) {
      if (visiting.add(t)) {
        t.getLowerBound().accept(this, visiting);
        t.getUpperBound().accept(this, visiting);
      }
      return null;
    }

    @Override
    public Void visitWildcard(WildcardType t, TypeMirrorSet visiting) {
      if (t.getSuperBound() != null) {
        t.getSuperBound().accept(this, visiting);
      }
      if (t.getExtendsBound() != null) {
        t.getExtendsBound().accept(this, visiting);
      }
      return null;
    }

    @Override
    public Void visitIntersection(IntersectionType t, TypeMirrorSet visiting) {
      return visitAll(t.getBounds(), visiting);
    }

    private Void visitAll(List<? extends TypeMirror> types, TypeMirrorSet visiting) {
      for (TypeMirror type : types) {
        type.accept(this, visiting);
      }
      return null;
    }
  }
}
