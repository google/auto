/*
 * Copyright 2014 Google LLC
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
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.Iterables;
import com.google.testing.compile.CompilationRule;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link MoreTypes#isTypeOf}.
 */
@RunWith(JUnit4.class)
public class MoreTypesIsTypeOfTest {

  @Rule public CompilationRule compilationRule = new CompilationRule();

  private Elements elements;

  @Before
  public void setUp() {
    this.elements = compilationRule.getElements();
  }

  private interface TestType {}

  @Test
  public void isTypeOf_declaredType() {
    assertTrue(MoreTypes.isType(typeElementFor(TestType.class).asType()));
    assertWithMessage("mirror represents the TestType")
        .that(MoreTypes.isTypeOf(TestType.class, typeElementFor(TestType.class).asType()))
        .isTrue();
    assertWithMessage("mirror does not represent a String")
        .that(MoreTypes.isTypeOf(String.class, typeElementFor(TestType.class).asType()))
        .isFalse();
  }

  private interface ArrayType {
    String[] array();
  }

  @Test
  public void isTypeOf_arrayType() {
    assertTrue(MoreTypes.isType(typeElementFor(ArrayType.class).asType()));
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(ArrayType.class));
    assertWithMessage("array mirror represents an array Class object")
        .that(MoreTypes.isTypeOf(String[].class, type))
        .isTrue();
  }

  private interface PrimitiveBoolean {
    boolean method();
  }

  @Test
  public void isTypeOf_primitiveBoolean() {
    assertTrue(MoreTypes.isType(typeElementFor(PrimitiveBoolean.class).asType()));
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveBoolean.class));
    assertWithMessage("mirror of a boolean").that(MoreTypes.isTypeOf(Boolean.TYPE, type)).isTrue();
  }

  private interface PrimitiveByte {
    byte method();
  }

  @Test
  public void isTypeOf_primitiveByte() {
    assertTrue(MoreTypes.isType(typeElementFor(PrimitiveByte.class).asType()));
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveByte.class));
    assertWithMessage("mirror of a byte").that(MoreTypes.isTypeOf(Byte.TYPE, type)).isTrue();
  }

  private interface PrimitiveChar {
    char method();
  }

  @Test
  public void isTypeOf_primitiveChar() {
    assertTrue(MoreTypes.isType(typeElementFor(PrimitiveChar.class).asType()));
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveChar.class));
    assertWithMessage("mirror of a char").that(MoreTypes.isTypeOf(Character.TYPE, type)).isTrue();
  }

  private interface PrimitiveDouble {
    double method();
  }

  @Test
  public void isTypeOf_primitiveDouble() {
    assertTrue(MoreTypes.isType(typeElementFor(PrimitiveDouble.class).asType()));
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveDouble.class));
    assertWithMessage("mirror of a double").that(MoreTypes.isTypeOf(Double.TYPE, type)).isTrue();
  }

  private interface PrimitiveFloat {
    float method();
  }

  @Test
  public void isTypeOf_primitiveFloat() {
    assertTrue(MoreTypes.isType(typeElementFor(PrimitiveFloat.class).asType()));
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveFloat.class));
    assertWithMessage("mirror of a float").that(MoreTypes.isTypeOf(Float.TYPE, type)).isTrue();
  }

  private interface PrimitiveInt {
    int method();
  }

  @Test
  public void isTypeOf_primitiveInt() {
    assertTrue(MoreTypes.isType(typeElementFor(PrimitiveInt.class).asType()));
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveInt.class));
    assertWithMessage("mirror of a int").that(MoreTypes.isTypeOf(Integer.TYPE, type)).isTrue();
  }

  private interface PrimitiveLong {
    long method();
  }

  @Test
  public void isTypeOf_primitiveLong() {
    assertTrue(MoreTypes.isType(typeElementFor(PrimitiveLong.class).asType()));
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveLong.class));
    assertWithMessage("mirror of a long").that(MoreTypes.isTypeOf(Long.TYPE, type)).isTrue();
  }

  private interface PrimitiveShort {
    short method();
  }

  @Test
  public void isTypeOf_primitiveShort() {
    assertTrue(MoreTypes.isType(typeElementFor(PrimitiveShort.class).asType()));
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveShort.class));
    assertWithMessage("mirror of a short").that(MoreTypes.isTypeOf(Short.TYPE, type)).isTrue();
  }

  private interface PrimitiveVoid {
    void method();
  }

  @Test
  public void isTypeOf_primitiveVoid() {
    assertTrue(MoreTypes.isType(typeElementFor(PrimitiveVoid.class).asType()));
    TypeMirror primitive = extractReturnTypeFromHolder(typeElementFor(PrimitiveVoid.class));
    assertWithMessage("mirror of a void").that(MoreTypes.isTypeOf(Void.TYPE, primitive)).isTrue();
  }

  private interface DeclaredVoid {
    Void method();
  }

  @Test
  public void isTypeOf_declaredVoid() {
    assertTrue(MoreTypes.isType(typeElementFor(DeclaredVoid.class).asType()));
    TypeMirror declared = extractReturnTypeFromHolder(typeElementFor(DeclaredVoid.class));
    assertWithMessage("mirror of a void").that(MoreTypes.isTypeOf(Void.class, declared)).isTrue();
  }

  @Test
  public void isTypeOf_fail() {
    assertFalse(
        MoreTypes.isType(
            getOnlyElement(typeElementFor(DeclaredVoid.class).getEnclosedElements()).asType()));
    TypeMirror method =
        getOnlyElement(typeElementFor(DeclaredVoid.class).getEnclosedElements()).asType();
    try {
      MoreTypes.isTypeOf(String.class, method);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  // Utility methods for this test.

  private TypeMirror extractReturnTypeFromHolder(TypeElement typeElement) {
    Element element = Iterables.getOnlyElement(typeElement.getEnclosedElements());
    TypeMirror arrayType = MoreElements.asExecutable(element).getReturnType();
    return arrayType;
  }

  private TypeElement typeElementFor(Class<?> clazz) {
    return elements.getTypeElement(clazz.getCanonicalName());
  }
}
