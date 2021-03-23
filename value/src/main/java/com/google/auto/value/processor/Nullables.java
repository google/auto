/*
 * Copyright 2021 Google LLC
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

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

class Nullables {
  /**
   * Returns the type of a {@code @Nullable} type-annotation, if one is found anywhere in the
   * signatures of the given methods.
   */
  static Optional<DeclaredType> nullableMentionedInMethods(Collection<ExecutableElement> methods) {
    return methods.stream()
        .flatMap(
            method ->
                Stream.concat(
                    Stream.of(method.getReturnType()),
                    method.getParameters().stream().map(Element::asType)))
        .map(Nullables::nullableIn)
        .filter(Optional::isPresent)
        .findFirst()
        .orElse(Optional.empty());
  }

  private static Optional<DeclaredType> nullableIn(TypeMirror type) {
    return new NullableFinder().visit(type);
  }

  private static Optional<DeclaredType> nullableIn(List<? extends AnnotationMirror> annotations) {
    return annotations.stream()
        .map(AnnotationMirror::getAnnotationType)
        .filter(t -> t.asElement().getSimpleName().contentEquals("Nullable"))
        .findFirst();
  }

  private static class NullableFinder extends SimpleTypeVisitor8<Optional<DeclaredType>, Void> {
    private final TypeMirrorSet visiting = new TypeMirrorSet();

    NullableFinder() {
      super(Optional.empty());
    }

    // Primitives can't be @Nullable so we don't check that.

    @Override
    public Optional<DeclaredType> visitDeclared(DeclaredType t, Void unused) {
      if (!visiting.add(t)) {
        return Optional.empty();
      }
      return nullableIn(t.getAnnotationMirrors())
          .map(Optional::of)
          .orElseGet(() -> visitAll(t.getTypeArguments()));
    }

    @Override
    public Optional<DeclaredType> visitTypeVariable(TypeVariable t, Void unused) {
      if (!visiting.add(t)) {
        return Optional.empty();
      }
      return nullableIn(t.getAnnotationMirrors())
          .map(Optional::of)
          .orElseGet(() -> visitAll(ImmutableList.of(t.getUpperBound(), t.getLowerBound())));
    }

    @Override
    public Optional<DeclaredType> visitArray(ArrayType t, Void unused) {
      return nullableIn(t.getAnnotationMirrors())
          .map(Optional::of)
          .orElseGet(() -> visit(t.getComponentType()));
    }

    @Override
    public Optional<DeclaredType> visitWildcard(WildcardType t, Void unused) {
      return nullableIn(t.getAnnotationMirrors())
          .map(Optional::of)
          .orElseGet(
              () ->
                  visitAll(
                      Stream.of(t.getExtendsBound(), t.getSuperBound())
                          .filter(Objects::nonNull)
                          .collect(toList())));
    }

    @Override
    public Optional<DeclaredType> visitIntersection(IntersectionType t, Void unused) {
      return nullableIn(t.getAnnotationMirrors())
          .map(Optional::of)
          .orElseGet(() -> visitAll(t.getBounds()));
    }

    private Optional<DeclaredType> visitAll(List<? extends TypeMirror> types) {
      return types.stream()
          .map(this::visit)
          .filter(Optional::isPresent)
          .findFirst()
          .orElse(Optional.empty());
    }
  }

  private Nullables() {}
}
