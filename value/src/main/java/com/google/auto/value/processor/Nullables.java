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

import com.google.auto.common.MoreTypes;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
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
   * If set to a non-empty string, defines which {@code @Nullable} type annotation should be used by
   * default. If set to an empty string, does not insert {@code @Nullable} unless it is referenced
   * in the {@code @AutoValue} methods. If unset, defaults to {@value #DEFAULT_NULLABLE}.
   */
  static final String NULLABLE_OPTION = "com.google.auto.value.NullableTypeAnnotation";

  // We write this using .concat in order to hide it from rewriting rules.
  private static final String DEFAULT_NULLABLE = "org".concat(".jspecify.nullness.Nullable");

  private final Optional<AnnotationMirror> defaultNullable;

  Nullables(ProcessingEnvironment processingEnv) {
    // -Afoo without `=` sets "foo" to null in the getOptions() map.
    String nullableOption =
        Strings.nullToEmpty(
            processingEnv.getOptions().getOrDefault(NULLABLE_OPTION, DEFAULT_NULLABLE));
    this.defaultNullable =
        (!nullableOption.isEmpty()
                && processingEnv.getSourceVersion().ordinal() >= SourceVersion.RELEASE_8.ordinal())
            ? Optional.ofNullable(processingEnv.getElementUtils().getTypeElement(nullableOption))
                .map(t -> annotationMirrorOf(MoreTypes.asDeclared(t.asType())))
            : Optional.empty();
  }

  private static AnnotationMirror annotationMirrorOf(DeclaredType annotationType) {
    return new AnnotationMirror() {
      @Override
      public DeclaredType getAnnotationType() {
        return annotationType;
      }

      @Override
      public ImmutableMap<? extends ExecutableElement, ? extends AnnotationValue>
          getElementValues() {
        return ImmutableMap.of();
      }
    };
  }

  /**
   * Returns the type of a {@code @Nullable} type-annotation, if one is found anywhere in the
   * signatures of the given methods.
   */
  @VisibleForTesting
  static Optional<AnnotationMirror> nullableMentionedInMethods(
      Collection<ExecutableElement> methods) {
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

  /**
   * Returns the type of an appropriate {@code @Nullable} type-annotation, given a set of methods
   * that are known to be in the same compilation as the code being generated. If one of those
   * methods contains an appropriate {@code @Nullable} annotation on a parameter or return type,
   * this method will return that. Otherwise, if the <a href="http://jspecify.org">JSpecify</a>
   * {@code @Nullable} is available, this method will return it. Otherwise, this method will return
   * an empty {@code Optional}.
   */
  Optional<AnnotationMirror> appropriateNullableGivenMethods(
      Collection<ExecutableElement> methods) {
    return nullableMentionedInMethods(methods).map(Optional::of).orElse(defaultNullable);
  }

  private static Optional<AnnotationMirror> nullableIn(TypeMirror type) {
    return new NullableFinder().visit(type);
  }

  private static Optional<AnnotationMirror> nullableIn(
      List<? extends AnnotationMirror> annotations) {
    return annotations.stream()
        .filter(a -> a.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable"))
        .map(a -> (AnnotationMirror) a) // get rid of the pesky wildcard
        .findFirst();
  }

  private static class NullableFinder extends SimpleTypeVisitor8<Optional<AnnotationMirror>, Void> {
    private final TypeMirrorSet visiting = new TypeMirrorSet();

    NullableFinder() {
      super(Optional.empty());
    }

    // Primitives can't be @Nullable so we don't check that.

    @Override
    public Optional<AnnotationMirror> visitDeclared(DeclaredType t, Void unused) {
      if (!visiting.add(t)) {
        return Optional.empty();
      }
      return nullableIn(t.getAnnotationMirrors())
          .map(Optional::of)
          .orElseGet(() -> visitAll(t.getTypeArguments()));
    }

    @Override
    public Optional<AnnotationMirror> visitTypeVariable(TypeVariable t, Void unused) {
      if (!visiting.add(t)) {
        return Optional.empty();
      }
      return nullableIn(t.getAnnotationMirrors())
          .map(Optional::of)
          .orElseGet(() -> visitAll(ImmutableList.of(t.getUpperBound(), t.getLowerBound())));
    }

    @Override
    public Optional<AnnotationMirror> visitArray(ArrayType t, Void unused) {
      return nullableIn(t.getAnnotationMirrors())
          .map(Optional::of)
          .orElseGet(() -> visit(t.getComponentType()));
    }

    @Override
    public Optional<AnnotationMirror> visitWildcard(WildcardType t, Void unused) {
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
    public Optional<AnnotationMirror> visitIntersection(IntersectionType t, Void unused) {
      return nullableIn(t.getAnnotationMirrors())
          .map(Optional::of)
          .orElseGet(() -> visitAll(t.getBounds()));
    }

    private Optional<AnnotationMirror> visitAll(List<? extends TypeMirror> types) {
      return types.stream()
          .map(this::visit)
          .filter(Optional::isPresent)
          .findFirst()
          .orElse(Optional.empty());
    }
  }
}
