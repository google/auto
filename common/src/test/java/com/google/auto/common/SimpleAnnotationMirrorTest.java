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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.testing.compile.CompilationRule;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.TypeElement;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SimpleAnnotationMirror}. */
@RunWith(JUnit4.class)
public class SimpleAnnotationMirrorTest {
  @Rule public final CompilationRule compilation = new CompilationRule();

  @interface EmptyAnnotation {}

  @interface AnnotationWithDefault {
    int value() default 3;
  }

  @interface MultipleValues {
    int value1();

    int value2();
  }

  @Test
  public void emptyAnnotation() {
    TypeElement emptyAnnotation = getTypeElement(EmptyAnnotation.class);
    AnnotationMirror simpleAnnotation = SimpleAnnotationMirror.of(emptyAnnotation);
    assertThat(simpleAnnotation.getAnnotationType()).isEqualTo(emptyAnnotation.asType());
    assertThat(simpleAnnotation.getElementValues()).isEmpty();
  }

  @Test
  public void multipleValues() {
    TypeElement multipleValues = getTypeElement(MultipleValues.class);
    Map<String, AnnotationValue> values = new HashMap<>();
    values.put("value1", intValue(1));
    values.put("value2", intValue(2));
    assertThat(SimpleAnnotationMirror.of(multipleValues, values).getElementValues()).hasSize(2);
  }

  @Test
  public void extraValues() {
    TypeElement multipleValues = getTypeElement(MultipleValues.class);
    Map<String, AnnotationValue> values = new HashMap<>();
    values.put("value1", intValue(1));
    values.put("value2", intValue(2));
    values.put("value3", intValue(3));
    expectThrows(() -> SimpleAnnotationMirror.of(multipleValues, values));
  }

  @Test
  public void defaultValue() {
    TypeElement withDefaults = getTypeElement(AnnotationWithDefault.class);
    AnnotationMirror annotation = SimpleAnnotationMirror.of(withDefaults);
    assertThat(annotation.getElementValues()).hasSize(1);
    assertThat(getOnlyElement(annotation.getElementValues().values()).getValue()).isEqualTo(3);
  }

  @Test
  public void overriddenDefaultValue() {
    TypeElement withDefaults = getTypeElement(AnnotationWithDefault.class);
    AnnotationMirror annotation =
        SimpleAnnotationMirror.of(withDefaults, ImmutableMap.of("value", intValue(4)));
    assertThat(annotation.getElementValues()).hasSize(1);
    assertThat(getOnlyElement(annotation.getElementValues().values()).getValue()).isEqualTo(4);
  }

  @Test
  public void missingValues() {
    TypeElement multipleValues = getTypeElement(MultipleValues.class);
    expectThrows(() -> SimpleAnnotationMirror.of(multipleValues));
  }

  @Test
  public void notAnAnnotation() {
    TypeElement stringElement = getTypeElement(String.class);
    expectThrows(() -> SimpleAnnotationMirror.of(stringElement));
  }

  private TypeElement getTypeElement(Class<?> clazz) {
    return compilation.getElements().getTypeElement(clazz.getCanonicalName());
  }

  private static void expectThrows(Runnable throwingRunnable) {
    try {
      throwingRunnable.run();
      fail("Expected an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  private static AnnotationValue intValue(int value) {
    return new AnnotationValue() {
      @Override
      public Object getValue() {
        return value;
      }

      @Override
      public <R, P> R accept(AnnotationValueVisitor<R, P> annotationValueVisitor, P p) {
        return annotationValueVisitor.visitInt(value, p);
      }
    };
  }
}
