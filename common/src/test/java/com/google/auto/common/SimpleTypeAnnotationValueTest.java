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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.testing.compile.CompilationRule;
import java.util.List;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.lang.model.util.Types;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SimpleTypeAnnotationValue}. */
@RunWith(JUnit4.class)
public class SimpleTypeAnnotationValueTest {
  @Rule public final CompilationRule compilation = new CompilationRule();
  private Types types;
  private Elements elements;
  private TypeMirror objectType;
  private PrimitiveType primitiveType;

  @Before
  public void setUp() {
    types = compilation.getTypes();
    elements = compilation.getElements();
    objectType = elements.getTypeElement(Object.class.getCanonicalName()).asType();
    primitiveType = types.getPrimitiveType(TypeKind.BOOLEAN);
  }

  @Test
  public void primitiveClass() {
    AnnotationValue annotationValue = SimpleTypeAnnotationValue.of(primitiveType);
    assertThat(annotationValue.getValue()).isEqualTo(primitiveType);
  }

  @Test
  public void arrays() {
    SimpleTypeAnnotationValue.of(types.getArrayType(objectType));
    SimpleTypeAnnotationValue.of(types.getArrayType(primitiveType));
  }

  @Test
  public void declaredType() {
    SimpleTypeAnnotationValue.of(objectType);
  }

  @Test
  public void visitorMethod() {
    SimpleTypeAnnotationValue.of(objectType)
        .accept(
            new SimpleAnnotationValueVisitor8<@Nullable Void, @Nullable Void>() {
              @Override
              public @Nullable Void visitType(TypeMirror typeMirror, @Nullable Void aVoid) {
                // do nothing, expected case
                return null;
              }

              @Override
              protected @Nullable Void defaultAction(Object o, @Nullable Void aVoid) {
                throw new AssertionError();
              }
            },
            null);
  }

  @Test
  public void parameterizedType() {
    try {
      SimpleTypeAnnotationValue.of(
          types.getDeclaredType(
              elements.getTypeElement(List.class.getCanonicalName()), objectType));
      fail("Expected an exception");
    } catch (IllegalArgumentException expected) {
    }
  }
}
