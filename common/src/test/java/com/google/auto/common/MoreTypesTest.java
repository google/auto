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

import static com.google.common.truth.Truth.assertThat;
import static javax.lang.model.type.TypeKind.NONE;
import static javax.lang.model.type.TypeKind.VOID;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.testing.EquivalenceTester;
import com.google.testing.compile.CompilationRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@RunWith(JUnit4.class)
public class MoreTypesTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  @Test
  public void equivalence() {
    Types types = compilationRule.getTypes();
    Elements elements = compilationRule.getElements();
    TypeMirror objectType = elements.getTypeElement(Object.class.getCanonicalName()).asType();
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement mapElement = elements.getTypeElement(Map.class.getCanonicalName());
    TypeElement setElement = elements.getTypeElement(Set.class.getCanonicalName());
    TypeElement enumElement = elements.getTypeElement(Enum.class.getCanonicalName());
    TypeElement funkyBounds = elements.getTypeElement(FunkyBounds.class.getCanonicalName());
    DeclaredType mapOfObjectToObjectType =
        types.getDeclaredType(mapElement, objectType, objectType);
    TypeMirror mapType = mapElement.asType();
    WildcardType wildcard = types.getWildcardType(null, null);
    EquivalenceTester<TypeMirror> tester = EquivalenceTester.<TypeMirror>of(MoreTypes.equivalence())
        .addEquivalenceGroup(types.getNullType())
        .addEquivalenceGroup(types.getNoType(NONE))
        .addEquivalenceGroup(types.getNoType(VOID))
        .addEquivalenceGroup(objectType)
        .addEquivalenceGroup(stringType)
        .addEquivalenceGroup(funkyBounds.asType())
        // Enum<E extends Enum<E>>
        .addEquivalenceGroup(enumElement.asType())
        // Map<K, V>
        .addEquivalenceGroup(mapType)
        .addEquivalenceGroup(mapOfObjectToObjectType)
        // Map<?, ?>
        .addEquivalenceGroup(types.getDeclaredType(mapElement, wildcard, wildcard))
        // Map
        .addEquivalenceGroup(types.erasure(mapType), types.erasure(mapOfObjectToObjectType))
        .addEquivalenceGroup(types.getDeclaredType(mapElement, objectType, stringType))
        .addEquivalenceGroup(types.getDeclaredType(mapElement, stringType, objectType))
        .addEquivalenceGroup(types.getDeclaredType(mapElement, stringType, stringType))
        .addEquivalenceGroup(wildcard)
        // ? extends Object
        .addEquivalenceGroup(types.getWildcardType(objectType, null))
        // ? extends String
        .addEquivalenceGroup(types.getWildcardType(stringType, null))
        // ? super String
        .addEquivalenceGroup(types.getWildcardType(null, stringType))
        // Map<String, Map<String, Set<Object>>>
        .addEquivalenceGroup(types.getDeclaredType(mapElement, stringType,
            types.getDeclaredType(mapElement, stringType,
                types.getDeclaredType(setElement, objectType))))
        .addEquivalenceGroup(FAKE_ERROR_TYPE)
        ;

    for (TypeKind kind : TypeKind.values()) {
      if (kind.isPrimitive()) {
        PrimitiveType primitiveType = types.getPrimitiveType(kind);
        TypeMirror boxedPrimitiveType = types.boxedClass(primitiveType).asType();
        tester.addEquivalenceGroup(primitiveType, types.unboxedType(boxedPrimitiveType));
        tester.addEquivalenceGroup(boxedPrimitiveType);
        tester.addEquivalenceGroup(types.getArrayType(primitiveType));
        tester.addEquivalenceGroup(types.getArrayType(boxedPrimitiveType));
      }
    }

    ImmutableSet<Class<?>> testClasses = ImmutableSet.of(
        ExecutableElementsGroupA.class,
        ExecutableElementsGroupB.class,
        ExecutableElementsGroupC.class,
        ExecutableElementsGroupD.class,
        ExecutableElementsGroupE.class);
    for (Class<?> testClass : testClasses) {
      ImmutableList<TypeMirror> equivalenceGroup = FluentIterable.from(
          elements.getTypeElement(testClass.getCanonicalName()).getEnclosedElements())
              .transform(new Function<Element, TypeMirror>() {
                @Override public TypeMirror apply(Element input) {
                  return input.asType();
                }
              })
              .toList();
      tester.addEquivalenceGroup(equivalenceGroup);
    }

    tester.test();
  }

  @SuppressWarnings("unused")
  private static final class ExecutableElementsGroupA {
    ExecutableElementsGroupA() {}
    void a() {}
    public static void b() {}
  }

  @SuppressWarnings("unused")
  private static final class ExecutableElementsGroupB {
    ExecutableElementsGroupB(String s) {}
    void a(String s) {}
    public static void b(String s) {}
  }

  @SuppressWarnings("unused")
  private static final class ExecutableElementsGroupC {
    ExecutableElementsGroupC() throws Exception {}
    void a() throws Exception {}
    public static void b() throws Exception {}
  }

  @SuppressWarnings("unused")
  private static final class ExecutableElementsGroupD {
    ExecutableElementsGroupD() throws RuntimeException {}
    void a() throws RuntimeException {}
    public static void b() throws RuntimeException {}
  }

  @SuppressWarnings("unused")
  private static final class ExecutableElementsGroupE {
    <T> ExecutableElementsGroupE() {}
    <T> void a() {}
    public static <T> void b() {}
  }

  @SuppressWarnings("unused")
  private static final class FunkyBounds<T extends Number & Comparable<T>> {}

  @Test public void testReferencedTypes() {
    Elements elements = compilationRule.getElements();
    TypeElement testDataElement = elements
        .getTypeElement(ReferencedTypesTestData.class.getCanonicalName());
    ImmutableMap<String, VariableElement> fieldIndex =
        FluentIterable.from(ElementFilter.fieldsIn(testDataElement.getEnclosedElements()))
            .uniqueIndex(new Function<VariableElement, String>() {
              @Override public String apply(VariableElement input) {
                return input.getSimpleName().toString();
              }
            });

    TypeElement objectElement =
        elements.getTypeElement(Object.class.getCanonicalName());
    TypeElement stringElement =
        elements.getTypeElement(String.class.getCanonicalName());
    TypeElement integerElement =
        elements.getTypeElement(Integer.class.getCanonicalName());
    TypeElement setElement =
        elements.getTypeElement(Set.class.getCanonicalName());
    TypeElement mapElement =
        elements.getTypeElement(Map.class.getCanonicalName());
    TypeElement charSequenceElement =
        elements.getTypeElement(CharSequence.class.getCanonicalName());

    assertThat(MoreTypes.referencedTypes(fieldIndex.get("f1").asType()))
        .containsExactly(objectElement);
    assertThat(MoreTypes.referencedTypes(fieldIndex.get("f2").asType()))
        .containsExactly(setElement, stringElement);
    assertThat(MoreTypes.referencedTypes(fieldIndex.get("f3").asType()))
        .containsExactly(mapElement, stringElement, objectElement);
    assertThat(MoreTypes.referencedTypes(fieldIndex.get("f4").asType()))
        .containsExactly(integerElement);
    assertThat(MoreTypes.referencedTypes(fieldIndex.get("f5").asType()))
        .containsExactly(setElement);
    assertThat(MoreTypes.referencedTypes(fieldIndex.get("f6").asType()))
        .containsExactly(setElement, charSequenceElement);
    assertThat(MoreTypes.referencedTypes(fieldIndex.get("f7").asType()))
        .containsExactly(mapElement, stringElement, setElement, charSequenceElement);
    assertThat(MoreTypes.referencedTypes(fieldIndex.get("f8").asType()))
        .containsExactly(stringElement);
    assertThat(MoreTypes.referencedTypes(fieldIndex.get("f9").asType()))
        .containsExactly(stringElement);
    assertThat(MoreTypes.referencedTypes(fieldIndex.get("f10").asType())).isEmpty();
    assertThat(MoreTypes.referencedTypes(fieldIndex.get("f11").asType())).isEmpty();
    assertThat(MoreTypes.referencedTypes(fieldIndex.get("f12").asType()))
        .containsExactly(setElement, stringElement);
  }

  @SuppressWarnings("unused") // types used in compiler tests
  private static final class ReferencedTypesTestData {
    Object f1;
    Set<String> f2;
    Map<String, Object> f3;
    Integer f4;
    Set<?> f5;
    Set<? extends CharSequence> f6;
    Map<String, Set<? extends CharSequence>> f7;
    String[] f8;
    String[][] f9;
    int f10;
    int[] f11;
    Set<? super String> f12;
  }

  private static final ErrorType FAKE_ERROR_TYPE = new ErrorType() {
    @Override
    public TypeKind getKind() {
      return TypeKind.ERROR;
    }

    @Override
    public <R, P> R accept(TypeVisitor<R, P> v, P p) {
      return v.visitError(this, p);
    }

    @Override
    public List<? extends TypeMirror> getTypeArguments() {
      return ImmutableList.of();
    }

    @Override
    public TypeMirror getEnclosingType() {
      return null;
    }

    @Override
    public Element asElement() {
      return null;
    }

    // JDK8 Compatibility:

    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
      return null;
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
      return null;
    }

    public List<? extends AnnotationMirror> getAnnotationMirrors() {
      return null;
    }
  };
}
