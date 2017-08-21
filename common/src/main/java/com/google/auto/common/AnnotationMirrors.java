/*
 * Copyright (C) 2014 Google, Inc.
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
package com.google.auto.common;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Equivalence;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

/**
 * A utility class for working with {@link AnnotationMirror} instances.
 *
 * @author Gregory Kick
 */
public final class AnnotationMirrors {
  private static final Equivalence<AnnotationMirror> ANNOTATION_MIRROR_EQUIVALENCE =
      new Equivalence<AnnotationMirror>() {
        @Override
        protected boolean doEquivalent(AnnotationMirror left, AnnotationMirror right) {
          return MoreTypes.equivalence().equivalent(left.getAnnotationType(),
              right.getAnnotationType()) && AnnotationValues.equivalence().pairwise().equivalent(
              getAnnotationValuesWithDefaults(left).values(),
              getAnnotationValuesWithDefaults(right).values());
        }
        @Override
        protected int doHash(AnnotationMirror annotation) {
          DeclaredType type = annotation.getAnnotationType();
          Iterable<AnnotationValue> annotationValues =
              getAnnotationValuesWithDefaults(annotation).values();
          return Arrays.hashCode(new int[] {MoreTypes.equivalence().hash(type),
              AnnotationValues.equivalence().pairwise().hash(annotationValues)});
        }
    };

  /**
   * Returns an {@link Equivalence} for {@link AnnotationMirror} as some implementations
   * delegate equality tests to {@link Object#equals} whereas the documentation explicitly
   * states that instance/reference equality is not the proper test.
   */
  public static Equivalence<AnnotationMirror> equivalence() {
    return ANNOTATION_MIRROR_EQUIVALENCE;
  }

  /**
   * Returns the {@link AnnotationMirror}'s map of {@link AnnotationValue} indexed by
   * {@link ExecutableElement}, supplying default values from the annotation if the
   * annotation property has not been set.  This is equivalent to
   * {@link Elements#getElementValuesWithDefaults(AnnotationMirror)} but can be called
   * statically without an {@link Elements} instance.
   */
  public static Map<ExecutableElement, AnnotationValue> getAnnotationValuesWithDefaults(
      AnnotationMirror annotation) {
    Map<ExecutableElement, AnnotationValue> values = Maps.newLinkedHashMap();
    Map<? extends ExecutableElement, ? extends AnnotationValue> declaredValues =
        annotation.getElementValues();
    for (ExecutableElement method :
        ElementFilter.methodsIn(annotation.getAnnotationType().asElement().getEnclosedElements())) {
      // Must iterate and put in this order, to ensure consistency in generated code.
      if (declaredValues.containsKey(method)) {
        values.put(method, declaredValues.get(method));
      } else if (method.getDefaultValue() != null) {
        values.put(method, method.getDefaultValue());
      } else {
        throw new IllegalStateException(
            "Unset annotation value without default should never happen: "
            + MoreElements.asType(method.getEnclosingElement()).getQualifiedName()
            + '.' + method.getSimpleName() + "()");
      }
    }
    return values;
  }

  /**
   * Returns an {@link AnnotationValue} for the named element if such an element was
   * either declared in the usage represented by the provided {@link AnnotationMirror}, or if
   * such an element was defined with a default.
   *
   * @throws IllegalArgumentException if no element is defined with the given elementName.
   */
  public static AnnotationValue getAnnotationValue(
      AnnotationMirror annotationMirror, String elementName) {
    return getAnnotationElementAndValue(annotationMirror, elementName).getValue();
  }

  /**
   * Returns a {@link ExecutableElement} and its associated {@link AnnotationValue} if such
   * an element was either declared in the usage represented by the provided
   * {@link AnnotationMirror}, or if such an element was defined with a default.
   *
   * @throws IllegalArgumentException if no element is defined with the given elementName.
   */
  public static Map.Entry<ExecutableElement, AnnotationValue> getAnnotationElementAndValue(
      AnnotationMirror annotationMirror, final String elementName) {
    checkNotNull(annotationMirror);
    checkNotNull(elementName);
    for (Map.Entry<ExecutableElement, AnnotationValue> entry :
        getAnnotationValuesWithDefaults(annotationMirror).entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals(elementName)) {
        return entry;
      }
    }
    throw new IllegalArgumentException(String.format("@%s does not define an element %s()",
        MoreElements.asType(annotationMirror.getAnnotationType().asElement()).getQualifiedName(),
        elementName));
  }

  /**
   * Returns all {@linkplain AnnotationMirror annotations} that are present on the given
   * {@link Element} which are themselves annotated with {@code annotationType}.
   */
  public static ImmutableSet<? extends AnnotationMirror> getAnnotatedAnnotations(Element element,
      final Class<? extends Annotation> annotationType) {
    List<? extends AnnotationMirror> annotations = element.getAnnotationMirrors();
    return FluentIterable.from(annotations)
        .filter(new Predicate<AnnotationMirror>() {
          @Override public boolean apply(AnnotationMirror input) {
            return isAnnotationPresent(input.getAnnotationType().asElement(), annotationType);
          }
        })
        .toSet();
  }

  private AnnotationMirrors() {}
}
