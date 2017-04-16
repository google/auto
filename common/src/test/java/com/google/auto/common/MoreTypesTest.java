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
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.testing.EquivalenceTester;
import com.google.testing.compile.CompilationRule;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
    TypeElement container = elements.getTypeElement(Container.class.getCanonicalName());
    TypeElement contained = elements.getTypeElement(Container.Contained.class.getCanonicalName());
    TypeElement funkyBounds = elements.getTypeElement(FunkyBounds.class.getCanonicalName());
    TypeElement funkyBounds2 = elements.getTypeElement(FunkyBounds2.class.getCanonicalName());
    TypeElement funkierBounds = elements.getTypeElement(FunkierBounds.class.getCanonicalName());
    TypeMirror funkyBoundsVar = ((DeclaredType) funkyBounds.asType()).getTypeArguments().get(0);
    TypeMirror funkyBounds2Var = ((DeclaredType) funkyBounds2.asType()).getTypeArguments().get(0);
    TypeMirror funkierBoundsVar = ((DeclaredType) funkierBounds.asType()).getTypeArguments().get(0);
    DeclaredType mapOfObjectToObjectType =
        types.getDeclaredType(mapElement, objectType, objectType);
    TypeMirror mapType = mapElement.asType();
    DeclaredType setOfSetOfObject =
        types.getDeclaredType(setElement, types.getDeclaredType(setElement, objectType));
    DeclaredType setOfSetOfString =
        types.getDeclaredType(setElement, types.getDeclaredType(setElement, stringType));
    DeclaredType setOfSetOfSetOfObject = types.getDeclaredType(setElement, setOfSetOfObject);
    DeclaredType setOfSetOfSetOfString = types.getDeclaredType(setElement, setOfSetOfString);
    WildcardType wildcard = types.getWildcardType(null, null);
    DeclaredType containerOfObject = types.getDeclaredType(container, objectType);
    DeclaredType containerOfString = types.getDeclaredType(container, stringType);
    TypeMirror containedInObject = types.asMemberOf(containerOfObject, contained);
    TypeMirror containedInString = types.asMemberOf(containerOfString, contained);
    EquivalenceTester<TypeMirror> tester = EquivalenceTester.<TypeMirror>of(MoreTypes.equivalence())
        .addEquivalenceGroup(types.getNullType())
        .addEquivalenceGroup(types.getNoType(NONE))
        .addEquivalenceGroup(types.getNoType(VOID))
        .addEquivalenceGroup(objectType)
        .addEquivalenceGroup(stringType)
        .addEquivalenceGroup(containedInObject)
        .addEquivalenceGroup(containedInString)
        .addEquivalenceGroup(funkyBounds.asType())
        .addEquivalenceGroup(funkyBounds2.asType())
        .addEquivalenceGroup(funkierBounds.asType())
        .addEquivalenceGroup(funkyBoundsVar, funkyBounds2Var)
        .addEquivalenceGroup(funkierBoundsVar)
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
        .addEquivalenceGroup(setOfSetOfObject)
        .addEquivalenceGroup(setOfSetOfString)
        .addEquivalenceGroup(setOfSetOfSetOfObject)
        .addEquivalenceGroup(setOfSetOfSetOfString)
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
  private static final class Container<T> {
    private final class Contained {}
  }

  @SuppressWarnings("unused")
  private static final class FunkyBounds<T extends Number & Comparable<T>> {}

  @SuppressWarnings("unused")
  private static final class FunkyBounds2<T extends Number & Comparable<T>> {}

  @SuppressWarnings("unused")
  private static final class FunkierBounds<T extends Number & Comparable<T> & Cloneable> {}

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

  private static class Parent<T> {}
  private static class ChildA extends Parent<Number> {}
  private static class ChildB extends Parent<String> {}
  private static class GenericChild<T> extends Parent<T> {}

  @Test
  public void asElement_throws() {
    TypeMirror javaDotLang =
        compilationRule.getElements().getPackageElement("java.lang").asType();
    try {
      MoreTypes.asElement(javaDotLang);
      fail();
    } catch (IllegalArgumentException expected) {}

  }

  @Test
  public void asElement() {
    Elements elements = compilationRule.getElements();
    TypeElement stringElement = elements.getTypeElement("java.lang.String");
    assertThat(MoreTypes.asElement(stringElement.asType())).isEqualTo(stringElement);
    TypeParameterElement setParameterElement = Iterables.getOnlyElement(
        compilationRule.getElements().getTypeElement("java.util.Set").getTypeParameters());
    assertThat(MoreTypes.asElement(setParameterElement.asType())).isEqualTo(setParameterElement);
    // we don't test error types because those are very hard to get predictably
  }

  @Test
  public void testNonObjectSuperclass() {
    Types types = compilationRule.getTypes();
    Elements elements = compilationRule.getElements();
    TypeMirror numberType = elements.getTypeElement(Number.class.getCanonicalName()).asType();
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeMirror integerType = elements.getTypeElement(Integer.class.getCanonicalName()).asType();
    TypeElement parent = elements.getTypeElement(Parent.class.getCanonicalName());
    TypeElement childA = elements.getTypeElement(ChildA.class.getCanonicalName());
    TypeElement childB = elements.getTypeElement(ChildB.class.getCanonicalName());
    TypeElement genericChild = elements.getTypeElement(GenericChild.class.getCanonicalName());
    TypeMirror genericChildOfNumber = types.getDeclaredType(genericChild, numberType);
    TypeMirror genericChildOfInteger = types.getDeclaredType(genericChild, integerType);

    assertThat(MoreTypes.nonObjectSuperclass(types, elements, (DeclaredType) parent.asType()))
        .isAbsent();

    Optional<DeclaredType> parentOfChildA =
        MoreTypes.nonObjectSuperclass(types, elements, (DeclaredType) childA.asType());
    Optional<DeclaredType> parentOfChildB =
        MoreTypes.nonObjectSuperclass(types, elements, (DeclaredType) childB.asType());
    Optional<DeclaredType> parentOfGenericChild =
        MoreTypes.nonObjectSuperclass(types, elements, (DeclaredType) genericChild.asType());
    Optional<DeclaredType> parentOfGenericChildOfNumber =
        MoreTypes.nonObjectSuperclass(types, elements, (DeclaredType) genericChildOfNumber);
    Optional<DeclaredType> parentOfGenericChildOfInteger =
        MoreTypes.nonObjectSuperclass(types, elements, (DeclaredType) genericChildOfInteger);

    EquivalenceTester<TypeMirror> tester = EquivalenceTester.<TypeMirror>of(MoreTypes.equivalence())
          .addEquivalenceGroup(parentOfChildA.get(),
              types.getDeclaredType(parent, numberType),
              parentOfGenericChildOfNumber.get())
          .addEquivalenceGroup(parentOfChildB.get(), types.getDeclaredType(parent, stringType))
          .addEquivalenceGroup(parentOfGenericChild.get(), parent.asType())
          .addEquivalenceGroup(parentOfGenericChildOfInteger.get(),
              types.getDeclaredType(parent, integerType));

    tester.test();
  }
  
  @Test
  public void testAsMemberOf_variableElement() {
    Types types = compilationRule.getTypes();
    Elements elements = compilationRule.getElements();
    TypeMirror numberType = elements.getTypeElement(Number.class.getCanonicalName()).asType();
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeMirror integerType = elements.getTypeElement(Integer.class.getCanonicalName()).asType();

    TypeElement paramsElement = elements.getTypeElement(Params.class.getCanonicalName());
    VariableElement tParam = Iterables.getOnlyElement(Iterables.getOnlyElement(
        ElementFilter.methodsIn(paramsElement.getEnclosedElements())).getParameters());
    VariableElement tField =
        Iterables.getOnlyElement(ElementFilter.fieldsIn(paramsElement.getEnclosedElements())); 
    
    DeclaredType numberParams =
        (DeclaredType) elements.getTypeElement(NumberParams.class.getCanonicalName()).asType();
    DeclaredType stringParams =
        (DeclaredType) elements.getTypeElement(StringParams.class.getCanonicalName()).asType();
    TypeElement genericParams = elements.getTypeElement(GenericParams.class.getCanonicalName());
    DeclaredType genericParamsOfNumber = types.getDeclaredType(genericParams, numberType);
    DeclaredType genericParamsOfInteger = types.getDeclaredType(genericParams, integerType);
    
    TypeMirror fieldOfNumberParams = MoreTypes.asMemberOf(types, numberParams, tField);
    TypeMirror paramOfNumberParams = MoreTypes.asMemberOf(types, numberParams, tParam);
    TypeMirror fieldOfStringParams = MoreTypes.asMemberOf(types, stringParams, tField);
    TypeMirror paramOfStringParams = MoreTypes.asMemberOf(types, stringParams, tParam);
    TypeMirror fieldOfGenericOfNumber = MoreTypes.asMemberOf(types, genericParamsOfNumber, tField);
    TypeMirror paramOfGenericOfNumber = MoreTypes.asMemberOf(types, genericParamsOfNumber, tParam);
    TypeMirror fieldOfGenericOfInteger =
        MoreTypes.asMemberOf(types, genericParamsOfInteger, tField);
    TypeMirror paramOfGenericOfInteger =
        MoreTypes.asMemberOf(types, genericParamsOfInteger, tParam);

    EquivalenceTester<TypeMirror> tester = EquivalenceTester.<TypeMirror>of(MoreTypes.equivalence())
        .addEquivalenceGroup(fieldOfNumberParams, paramOfNumberParams, fieldOfGenericOfNumber,
            paramOfGenericOfNumber, numberType)
        .addEquivalenceGroup(fieldOfStringParams, paramOfStringParams, stringType)
        .addEquivalenceGroup(fieldOfGenericOfInteger, paramOfGenericOfInteger, integerType);
    tester.test();
  }
  
  private static class Params<T> {
    @SuppressWarnings("unused") T t;
    @SuppressWarnings("unused") void add(T t) {}
  }
  private static class NumberParams extends Params<Number> {}
  private static class StringParams extends Params<String> {}
  private static class GenericParams<T> extends Params<T> {}

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
