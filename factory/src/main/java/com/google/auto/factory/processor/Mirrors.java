/*
 * Copyright 2013 Google LLC
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

import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableMap;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor6;

final class Mirrors {
  private Mirrors() {}

  static Name getQualifiedName(DeclaredType type) {
    return type.asElement()
        .accept(
            new SimpleElementVisitor6<Name, Void>() {
              @Override
              protected Name defaultAction(Element e, Void p) {
                throw new AssertionError("DeclaredTypes should be TypeElements");
              }

              @Override
              public Name visitType(TypeElement e, Void p) {
                return e.getQualifiedName();
              }
            },
            null);
  }

  /** {@code true} if {@code type} is a {@link Provider}. */
  static boolean isProvider(TypeMirror type) {
    return MoreTypes.isType(type) && MoreTypes.isTypeOf(Provider.class, type);
  }

  /**
   * Returns an annotation value map  with {@link String} keys instead of {@link ExecutableElement}
   * instances.
   */
  static ImmutableMap<String, AnnotationValue> simplifyAnnotationValueMap(
      Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValueMap) {
    ImmutableMap.Builder<String, AnnotationValue> builder = ImmutableMap.builder();
    for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotationValueMap.entrySet()) {
      builder.put(entry.getKey().getSimpleName().toString(), entry.getValue());
    }
    return builder.build();
  }

  /**
   * Get the {@link AnnotationMirror} for the type {@code annotationType} present on the given
   * {@link Element} if it exists.
   */
  static Optional<AnnotationMirror> getAnnotationMirror(
      Element element, Class<? extends Annotation> annotationType) {
    String annotationName = annotationType.getName();
    return element.getAnnotationMirrors().stream()
        .filter(a -> getQualifiedName(a.getAnnotationType()).contentEquals(annotationName))
        .<AnnotationMirror>map(x -> x) // get rid of wildcard <? extends AnnotationMirror>
        .findFirst();
  }

  /**
   * Wraps an {@link Optional} of a type in an {@code Optional} of a {@link Equivalence.Wrapper} for
   * that type.
   */
  // TODO(ronshapiro): this is used in AutoFactory and Dagger, consider moving it into auto-common.
  static <T> Optional<Equivalence.Wrapper<T>> wrapOptionalInEquivalence(
      Equivalence<T> equivalence, Optional<T> optional) {
    return optional.map(equivalence::wrap);
  }

  /**
   * Unwraps an {@link Optional} of a {@link Equivalence.Wrapper} into an {@code Optional} of the
   * underlying type.
   */
  static <T> Optional<T> unwrapOptionalEquivalence(
      Optional<Equivalence.Wrapper<T>> wrappedOptional) {
    return wrappedOptional.map(Equivalence.Wrapper::get);
  }
}
