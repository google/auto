/*
 * Copyright 2017 Google LLC
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

import static com.google.auto.common.MoreStreams.toImmutableMap;
import static com.google.common.base.Preconditions.checkArgument;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A simple implementation of the {@link AnnotationMirror} interface.
 *
 * <p>This type implements {@link #equals(Object)} and {@link #hashCode()} using {@link
 * AnnotationMirrors#equivalence} in accordance with the {@link AnnotationMirror} spec. Some {@link
 * AnnotationMirror}s, however, do not correctly implement equals, you should always compare them
 * using {@link AnnotationMirrors#equivalence} anyway.
 */
public final class SimpleAnnotationMirror implements AnnotationMirror {
  private final TypeElement annotationType;
  private final ImmutableMap<String, ? extends AnnotationValue> namedValues;
  private final ImmutableMap<ExecutableElement, ? extends AnnotationValue> elementValues;

  private SimpleAnnotationMirror(
      TypeElement annotationType, Map<String, ? extends AnnotationValue> namedValues) {
    checkArgument(
        annotationType.getKind().equals(ElementKind.ANNOTATION_TYPE),
        "annotationType must be an annotation: %s",
        annotationType);
    Map<String, AnnotationValue> values = new LinkedHashMap<>();
    Map<String, AnnotationValue> unusedValues = new LinkedHashMap<>(namedValues);
    List<String> missingMembers = new ArrayList<>();
    for (ExecutableElement method : methodsIn(annotationType.getEnclosedElements())) {
      String memberName = method.getSimpleName().toString();
      if (unusedValues.containsKey(memberName)) {
        values.put(memberName, unusedValues.remove(memberName));
      } else if (method.getDefaultValue() != null) {
        values.put(memberName, method.getDefaultValue());
      } else {
        missingMembers.add(memberName);
      }
    }

    checkArgument(
        unusedValues.isEmpty(),
        "namedValues has entries for members that are not in %s: %s",
        annotationType,
        unusedValues);
    checkArgument(
        missingMembers.isEmpty(), "namedValues is missing entries for: %s", missingMembers);

    this.annotationType = annotationType;
    this.namedValues = ImmutableMap.copyOf(namedValues);
    this.elementValues =
        methodsIn(annotationType.getEnclosedElements()).stream()
            .collect(toImmutableMap(e -> e, e -> values.get(e.getSimpleName().toString())));
  }

  /**
   * An object representing an {@linkplain ElementKind#ANNOTATION_TYPE annotation} instance. If
   * {@code annotationType} has any annotation members, they must have default values.
   */
  public static AnnotationMirror of(TypeElement annotationType) {
    return of(annotationType, ImmutableMap.of());
  }

  /**
   * An object representing an {@linkplain ElementKind#ANNOTATION_TYPE annotation} instance. If
   * {@code annotationType} has any annotation members, they must either be present in {@code
   * namedValues} or have default values.
   */
  public static AnnotationMirror of(
      TypeElement annotationType, Map<String, ? extends AnnotationValue> namedValues) {
    return new SimpleAnnotationMirror(annotationType, namedValues);
  }

  @Override
  public DeclaredType getAnnotationType() {
    return MoreTypes.asDeclared(annotationType.asType());
  }

  @Override
  public Map<ExecutableElement, ? extends AnnotationValue> getElementValues() {
    return elementValues;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("@").append(annotationType.getQualifiedName());
    if (!namedValues.isEmpty()) {
      builder
          .append('(')
          .append(Joiner.on(", ").withKeyValueSeparator(" = ").join(namedValues))
          .append(')');
    }
    return builder.toString();
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof AnnotationMirror
        && AnnotationMirrors.equivalence().equivalent(this, (AnnotationMirror) other);
  }

  @Override
  public int hashCode() {
    return AnnotationMirrors.equivalence().hash(this);
  }
}
