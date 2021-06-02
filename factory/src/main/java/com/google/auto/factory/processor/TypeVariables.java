/*
 * Copyright 2019 Google LLC
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
package com.google.auto.factory.processor;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

final class TypeVariables {
  private TypeVariables() {}

  static ImmutableSet<TypeVariable> getReferencedTypeVariables(TypeMirror type) {
    checkNotNull(type);
    return type.accept(ReferencedTypeVariables.INSTANCE, new HashSet<>());
  }

  private static final class ReferencedTypeVariables
      extends SimpleTypeVisitor8<ImmutableSet<TypeVariable>, Set<Element>> {

    private static final ReferencedTypeVariables INSTANCE = new ReferencedTypeVariables();

    ReferencedTypeVariables() {
      super(ImmutableSet.of());
    }

    @Override
    public ImmutableSet<TypeVariable> visitArray(ArrayType t, Set<Element> visited) {
      return t.getComponentType().accept(this, visited);
    }

    @Override
    public ImmutableSet<TypeVariable> visitDeclared(DeclaredType t, Set<Element> visited) {
      if (!visited.add(t.asElement())) {
        return ImmutableSet.of();
      }
      ImmutableSet.Builder<TypeVariable> typeVariables = ImmutableSet.builder();
      for (TypeMirror typeArgument : t.getTypeArguments()) {
        typeVariables.addAll(typeArgument.accept(this, visited));
      }
      return typeVariables.build();
    }

    @Override
    public ImmutableSet<TypeVariable> visitTypeVariable(TypeVariable t, Set<Element> visited) {
      if (!visited.add(t.asElement())) {
        return ImmutableSet.of();
      }
      ImmutableSet.Builder<TypeVariable> typeVariables = ImmutableSet.builder();
      typeVariables.add(t);
      typeVariables.addAll(t.getLowerBound().accept(this, visited));
      typeVariables.addAll(t.getUpperBound().accept(this, visited));
      return typeVariables.build();
    }

    @Override
    public ImmutableSet<TypeVariable> visitUnion(UnionType t, Set<Element> visited) {
      ImmutableSet.Builder<TypeVariable> typeVariables = ImmutableSet.builder();
      for (TypeMirror unionType : t.getAlternatives()) {
        typeVariables.addAll(unionType.accept(this, visited));
      }
      return typeVariables.build();
    }

    @Override
    public ImmutableSet<TypeVariable> visitIntersection(IntersectionType t, Set<Element> visited) {
      ImmutableSet.Builder<TypeVariable> typeVariables = ImmutableSet.builder();
      for (TypeMirror intersectionType : t.getBounds()) {
        typeVariables.addAll(intersectionType.accept(this, visited));
      }
      return typeVariables.build();
    }

    @Override
    public ImmutableSet<TypeVariable> visitWildcard(WildcardType t, Set<Element> visited) {
      ImmutableSet.Builder<TypeVariable> typeVariables = ImmutableSet.builder();
      TypeMirror extendsBound = t.getExtendsBound();
      if (extendsBound != null) {
        typeVariables.addAll(extendsBound.accept(this, visited));
      }
      TypeMirror superBound = t.getSuperBound();
      if (superBound != null) {
        typeVariables.addAll(superBound.accept(this, visited));
      }
      return typeVariables.build();
    }
  }
}
