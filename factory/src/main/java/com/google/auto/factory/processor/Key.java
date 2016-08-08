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
package com.google.auto.factory.processor;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.factory.processor.Mirrors.isProvider;
import static com.google.auto.factory.processor.Mirrors.unwrapOptionalEquivalence;
import static com.google.auto.factory.processor.Mirrors.wrapOptionalInEquivalence;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * A value object for types and qualifiers.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class Key {

  abstract TypeMirror type();
  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> qualifierWrapper();

  Optional<AnnotationMirror> qualifier() {
    return unwrapOptionalEquivalence(qualifierWrapper());
  }

  /**
   * Constructs a key based on the type {@code type} and any {@link Qualifier}s in {@code
   * annotations}.
   *
   * <p>If {@code type} is a {@code Provider<T>}, the returned {@link Key}'s {@link #type()} is
   * {@code T}. If {@code type} is a primitive, the returned {@link Key}'s {@link #type()} is the
   * corresponding {@linkplain Types#boxedClass(PrimitiveType) boxed type}.
   *
   * <p>For example:
   * <table>
   *   <tr><th>Input type                <th>{@code Key.type()}
   *   <tr><td>{@code String}            <td>{@code String}
   *   <tr><td>{@code Provider<String>}  <td>{@code String}
   *   <tr><td>{@code int}               <td>{@code Integer}
   * </table>
   */
  static Key create(
      TypeMirror type, Iterable<? extends AnnotationMirror> annotations, Types types) {
    ImmutableSet.Builder<AnnotationMirror> qualifiers = ImmutableSet.builder();
    for (AnnotationMirror annotation : annotations) {
      if (isAnnotationPresent(annotation.getAnnotationType().asElement(), Qualifier.class)) {
        qualifiers.add(annotation);
      }
    }

    // TODO(gak): check for only one qualifier rather than using the first
    Optional<AnnotationMirror> qualifier = FluentIterable.from(qualifiers.build()).first();

    TypeMirror keyType =
        isProvider(type)
            ? MoreTypes.asDeclared(type).getTypeArguments().get(0)
            : boxedType(type, types);
    return new AutoValue_Key(
        keyType, wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), qualifier));
  }

  /**
   * If {@code type} is a primitive type, returns the boxed equivalent; otherwise returns
   * {@code type}.
   */
  private static TypeMirror boxedType(TypeMirror type, Types types) {
    return type.getKind().isPrimitive()
        ? types.boxedClass(MoreTypes.asPrimitiveType(type)).asType()
        : type;
  }

  @Override
  public String toString() {
    String typeQualifiedName = MoreTypes.asTypeElement(type()).toString();
    return qualifier().isPresent()
        ? qualifier().get() + "/" + typeQualifiedName
        : typeQualifiedName;
  }
}
