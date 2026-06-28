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

import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.testing.compile.CompilationRule;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.RandomAccess;
import java.util.SortedMap;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link MoreTypes#isTypeOf(Class, TypeMirror)}. */
@RunWith(JUnit4.class)
public class MoreTypesIsTypeOfTest {

  @Rule public CompilationRule compilationRule = new CompilationRule();

  private Elements elementUtils;
  private Types typeUtils;

  @Before
  public void setUp() {
    this.elementUtils = compilationRule.getElements();
    this.typeUtils = compilationRule.getTypes();
  }

  @Test
  public void isTypeOf_primitiveAndBoxedPrimitiveTypes() {
    class PrimitiveTypeInfo {
      final Class<?> CLASS_TYPE;
      final Class<?> BOXED_CLASS_TYPE;
      final TypeKind TYPE_KIND;

      PrimitiveTypeInfo(Class<?> classType, Class<?> boxedClassType, TypeKind typeKind) {
        this.CLASS_TYPE = classType;
        this.BOXED_CLASS_TYPE = boxedClassType;
        this.TYPE_KIND = typeKind;
      }
    }
    final List<PrimitiveTypeInfo> primitivesTypeInfo =
        ImmutableList.of(
            new PrimitiveTypeInfo(Byte.TYPE, Byte.class, TypeKind.BYTE),
            new PrimitiveTypeInfo(Short.TYPE, Short.class, TypeKind.SHORT),
            new PrimitiveTypeInfo(Integer.TYPE, Integer.class, TypeKind.INT),
            new PrimitiveTypeInfo(Long.TYPE, Long.class, TypeKind.LONG),
            new PrimitiveTypeInfo(Float.TYPE, Float.class, TypeKind.FLOAT),
            new PrimitiveTypeInfo(Double.TYPE, Double.class, TypeKind.DOUBLE),
            new PrimitiveTypeInfo(Boolean.TYPE, Boolean.class, TypeKind.BOOLEAN),
            new PrimitiveTypeInfo(Character.TYPE, Character.class, TypeKind.CHAR));

    for (boolean isBoxedI : new boolean[] {false, true}) {
      for (int i = 0; i < primitivesTypeInfo.size(); i++) { // For the Class<?> arg
        Class<?> clazz =
            isBoxedI
                ? primitivesTypeInfo.get(i).BOXED_CLASS_TYPE
                : primitivesTypeInfo.get(i).CLASS_TYPE;

        for (boolean isBoxedJ : new boolean[] {false, true}) {
          for (int j = 0; j < primitivesTypeInfo.size(); j++) { // For the TypeMirror arg
            TypeKind typeKind = primitivesTypeInfo.get(j).TYPE_KIND;
            TypeMirror typeMirror =
                isBoxedJ
                    ? typeUtils.boxedClass(typeUtils.getPrimitiveType(typeKind)).asType()
                    : typeUtils.getPrimitiveType(typeKind);

            String message =
                "Mirror:\t" + typeMirror.toString() + "\nClass:\t" + clazz.getCanonicalName();
            if (isBoxedI == isBoxedJ && i == j) {
              assertWithMessage(message).that(MoreTypes.isTypeOf(clazz, typeMirror)).isTrue();
            } else {
              assertWithMessage(message).that(MoreTypes.isTypeOf(clazz, typeMirror)).isFalse();
            }
          }
        }
      }
    }
  }

  @Test
  public void isTypeOf_voidAndPseudoVoidTypes() {
    TypeMirror voidType = typeUtils.getNoType(TypeKind.VOID);
    TypeMirror pseudoVoidType = getTypeElementFor(Void.class).asType();

    assertWithMessage("Mirror:\t" + voidType + "\nClass:\t" + Void.TYPE.getCanonicalName())
        .that(MoreTypes.isTypeOf(Void.TYPE, voidType))
        .isTrue();
    assertWithMessage("Mirror:\t" + pseudoVoidType + "\nClass:\t" + Void.TYPE.getCanonicalName())
        .that(MoreTypes.isTypeOf(Void.TYPE, pseudoVoidType))
        .isFalse();

    assertWithMessage("Mirror:\t" + voidType + "\nClass:\t" + Void.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(Void.class, voidType))
        .isFalse();
    assertWithMessage("Mirror:\t" + pseudoVoidType + "\nClass:\t" + Void.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(Void.class, pseudoVoidType))
        .isTrue();
  }

  @Test
  public void isTypeOf_arrayType() {
    TypeMirror type = typeUtils.getArrayType(getTypeElementFor(String.class).asType());
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + String[].class.getCanonicalName())
        .that(MoreTypes.isTypeOf(String[].class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + Integer[].class.getCanonicalName())
        .that(MoreTypes.isTypeOf(Integer[].class, type))
        .isFalse();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + int[].class.getCanonicalName())
        .that(MoreTypes.isTypeOf(int[].class, type))
        .isFalse();

    type = typeUtils.getArrayType(typeUtils.getPrimitiveType(TypeKind.INT));
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + String[].class.getCanonicalName())
        .that(MoreTypes.isTypeOf(String[].class, type))
        .isFalse();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + Integer[].class.getCanonicalName())
        .that(MoreTypes.isTypeOf(Integer[].class, type))
        .isFalse();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + int[].class.getCanonicalName())
        .that(MoreTypes.isTypeOf(int[].class, type))
        .isTrue();
  }

  @Test
  // ArrayList is a list because its parent implements List (checking interface and direct ancestry)
  public void isTypeOf_listLineage() {
    TypeMirror type =
        typeUtils.getDeclaredType(
            getTypeElementFor(ArrayList.class), getTypeElementFor(String.class).asType());
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + ArrayList.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(ArrayList.class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + List.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(List.class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + String.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(String.class, type))
        .isFalse();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + LinkedList.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(LinkedList.class, type))
        .isFalse();

    type = typeUtils.getArrayType(type); // ArrayList<String>[]
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + ArrayList[].class.getCanonicalName())
        .that(MoreTypes.isTypeOf(ArrayList[].class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + List[].class.getCanonicalName())
        .that(MoreTypes.isTypeOf(List[].class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + LinkedList[].class.getCanonicalName())
        .that(MoreTypes.isTypeOf(LinkedList[].class, type))
        .isFalse();
  }

  @Test
  // NavigableMap implements SortedMap and SortedMap implements Map (checking interface ancestry)
  public void isTypeOf_mapLineage() {
    TypeMirror type = getTypeElementFor(SortedMap.class).asType();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + SortedMap.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(SortedMap.class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + Map.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(Map.class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + NavigableMap.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(NavigableMap.class, type))
        .isFalse();

    // Testing ancestor that is not a direct parent
    type = getTypeElementFor(NavigableMap.class).asType();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + Map.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(Map.class, type))
        .isTrue();
  }

  @Test
  public void isTypeOf_wildcardCapture() {
    TypeMirror type =
        typeUtils.getWildcardType(
            getTypeElementFor(SortedMap.class).asType(), null); // ? extends SortedMap
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + SortedMap.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(SortedMap.class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + Map.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(Map.class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + NavigableMap.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(NavigableMap.class, type))
        .isFalse();
  }

  private interface TestType {
    @SuppressWarnings("unused")
    <T extends SortedMap<Number, String>> T method0();

    @SuppressWarnings("unused")
    <RANDOM_ACCESS_LIST extends List<?> & RandomAccess> void method1(
        RANDOM_ACCESS_LIST randomAccessList);
  }

  @Test
  public void isTypeOf_declaredType() {
    TypeMirror TestTypeTypeMirror = getTypeElementFor(TestType.class).asType();
    assertTrue(MoreTypes.isType(TestTypeTypeMirror));
    assertWithMessage(
            "Mirror:\t" + TestTypeTypeMirror + "\nClass:\t" + TestType.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(TestType.class, TestTypeTypeMirror))
        .isTrue();
    assertWithMessage(
            "Mirror:\t" + TestTypeTypeMirror + "\nClass:\t" + String.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(String.class, TestTypeTypeMirror))
        .isFalse();
  }

  @Test
  public void isTypeOf_typeParameterCapture() {
    assertTrue(MoreTypes.isType(getTypeElementFor(TestType.class).asType()));

    // Getting the type parameter of method0
    ExecutableElement executableElement =
        MoreElements.asExecutable(getTypeElementFor(TestType.class).getEnclosedElements().get(0));
    TypeMirror type = Iterables.getOnlyElement(executableElement.getTypeParameters()).asType();

    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + SortedMap.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(SortedMap.class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + Map.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(Map.class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + NavigableMap.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(NavigableMap.class, type))
        .isFalse();

    // Getting parameter type of method1 and checking for intersection type
    executableElement =
        MoreElements.asExecutable(getTypeElementFor(TestType.class).getEnclosedElements().get(1));
    type = Iterables.getOnlyElement(executableElement.getParameters()).asType();

    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + List.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(List.class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + RandomAccess.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(RandomAccess.class, type))
        .isTrue();
    assertWithMessage("Mirror:\t" + type + "\nClass:\t" + ArrayList.class.getCanonicalName())
        .that(MoreTypes.isTypeOf(ArrayList.class, type))
        .isFalse();
  }

  @Test
  public void isTypeOf_fail() {
    assertFalse(
        MoreTypes.isType(getTypeElementFor(TestType.class).getEnclosedElements().get(0).asType()));
    TypeMirror method1Type =
        getTypeElementFor(TestType.class).getEnclosedElements().get(1).asType();
    try {
      MoreTypes.isTypeOf(List.class, method1Type);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  /* Utility method(s) */
  private TypeElement getTypeElementFor(Class<?> clazz) {
    return elementUtils.getTypeElement(clazz.getCanonicalName());
  }
}
