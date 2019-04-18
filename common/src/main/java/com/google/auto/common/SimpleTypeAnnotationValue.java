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

import static com.google.common.base.Preconditions.checkArgument;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.type.TypeMirror;

/**
 * A simple implementation of the {@link AnnotationValue} interface for a class literal, e.g. an
 * annotation member of type {@code Class<?>} or {@code Class<? extends Foo>}.
 */
public final class SimpleTypeAnnotationValue implements AnnotationValue {
  private final TypeMirror value;

  private SimpleTypeAnnotationValue(TypeMirror value) {
    checkArgument(
        value.getKind().isPrimitive()
            || value.getKind().equals(DECLARED)
            || value.getKind().equals(ARRAY),
        "value must be a primitive, array, or declared type, but was %s (%s)",
        value.getKind(),
        value);
    if (value.getKind().equals(DECLARED)) {
      checkArgument(
          MoreTypes.asDeclared(value).getTypeArguments().isEmpty(),
          "value must not be a parameterized type: %s",
          value);
    }
    this.value = value;
  }

  /**
   * An object representing an annotation value instance.
   *
   * @param value a primitive, array, or non-parameterized declared type
   */
  public static AnnotationValue of(TypeMirror value) {
    return new SimpleTypeAnnotationValue(value);
  }

  @Override
  public TypeMirror getValue() {
    return value;
  }

  @Override
  public String toString() {
    return value + ".class";
  }

  @Override
  public <R, P> R accept(AnnotationValueVisitor<R, P> visitor, P parameter) {
    return visitor.visitType(getValue(), parameter);
  }
}
