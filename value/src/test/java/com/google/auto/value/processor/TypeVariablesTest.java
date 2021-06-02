/*
 * Copyright 2019 Google LLC
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
package com.google.auto.value.processor;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Expect;
import com.google.testing.compile.CompilationRule;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TypeVariablesTest {
  @ClassRule public static final CompilationRule compilationRule = new CompilationRule();
  @Rule public final Expect expect = Expect.create();

  private static Elements elementUtils;
  private static Types typeUtils;

  @BeforeClass
  public static void setUpClass() {
    elementUtils = compilationRule.getElements();
    typeUtils = compilationRule.getTypes();
  }

  abstract static class Source1 {
    abstract String getFoo();
  }

  abstract static class Target1 {}

  @Test
  public void noTypeParameters() {
    TypeElement source1 = elementUtils.getTypeElement(Source1.class.getCanonicalName());
    TypeElement target1 = elementUtils.getTypeElement(Target1.class.getCanonicalName());
    List<ExecutableElement> sourceMethods = ElementFilter.methodsIn(source1.getEnclosedElements());
    Map<ExecutableElement, TypeMirror> types =
        TypeVariables.rewriteReturnTypes(elementUtils, typeUtils, sourceMethods, source1, target1);
    assertThat(types).containsExactly(sourceMethods.get(0), sourceMethods.get(0).getReturnType());
  }

  abstract static class Source2<T> {
    abstract List<T> getFoo();
  }

  abstract static class Target2<T> {
    abstract void setFoo(List<T> list);
  }

  @Test
  public void simpleTypeParameter() {
    TypeElement source2 = elementUtils.getTypeElement(Source2.class.getCanonicalName());
    TypeElement target2 = elementUtils.getTypeElement(Target2.class.getCanonicalName());
    List<ExecutableElement> sourceMethods = ElementFilter.methodsIn(source2.getEnclosedElements());
    Map<ExecutableElement, TypeMirror> types =
        TypeVariables.rewriteReturnTypes(elementUtils, typeUtils, sourceMethods, source2, target2);
    List<ExecutableElement> targetMethods = ElementFilter.methodsIn(target2.getEnclosedElements());
    TypeMirror setFooParameter = targetMethods.get(0).getParameters().get(0).asType();
    ExecutableElement getFoo = sourceMethods.get(0);
    TypeMirror originalGetFooReturn = getFoo.getReturnType();
    TypeMirror rewrittenGetFooReturn = types.get(getFoo);
    assertThat(typeUtils.isAssignable(setFooParameter, originalGetFooReturn)).isFalse();
    assertThat(typeUtils.isAssignable(setFooParameter, rewrittenGetFooReturn)).isTrue();
  }

  abstract static class Source3<T extends Comparable<T>, U> {
    abstract Map<T, ? extends U> getFoo();
  }

  abstract static class Target3<T extends Comparable<T>, U> {
    abstract void setFoo(Map<T, ? extends U> list);
  }

  @Test
  public void hairyTypeParameters() {
    TypeElement source3 = elementUtils.getTypeElement(Source3.class.getCanonicalName());
    TypeElement target3 = elementUtils.getTypeElement(Target3.class.getCanonicalName());
    List<ExecutableElement> sourceMethods = ElementFilter.methodsIn(source3.getEnclosedElements());
    Map<ExecutableElement, TypeMirror> types =
        TypeVariables.rewriteReturnTypes(elementUtils, typeUtils, sourceMethods, source3, target3);
    List<ExecutableElement> targetMethods = ElementFilter.methodsIn(target3.getEnclosedElements());
    TypeMirror setFooParameter = targetMethods.get(0).getParameters().get(0).asType();
    ExecutableElement getFoo = sourceMethods.get(0);
    TypeMirror originalGetFooReturn = getFoo.getReturnType();
    TypeMirror rewrittenGetFooReturn = types.get(getFoo);
    assertThat(typeUtils.isAssignable(setFooParameter, originalGetFooReturn)).isFalse();
    assertThat(typeUtils.isAssignable(setFooParameter, rewrittenGetFooReturn)).isTrue();
  }

  abstract static class Outer<T, U extends T> {
    abstract Map<T, U> getFoo();

    abstract List<? extends T> getBar();

    abstract static class Inner<T, U extends T> {
      abstract void setFoo(Map<T, U> foo);

      abstract void setBar(List<? extends T> bar);
    }
  }

  @Test
  public void nestedClasses() {
    TypeElement outer = elementUtils.getTypeElement(Outer.class.getCanonicalName());
    TypeElement inner = elementUtils.getTypeElement(Outer.Inner.class.getCanonicalName());
    List<ExecutableElement> outerMethods = ElementFilter.methodsIn(outer.getEnclosedElements());
    Map<ExecutableElement, TypeMirror> types =
        TypeVariables.rewriteReturnTypes(elementUtils, typeUtils, outerMethods, outer, inner);
    List<ExecutableElement> innerMethods = ElementFilter.methodsIn(inner.getEnclosedElements());
    ExecutableElement getFoo = methodNamed(outerMethods, "getFoo");
    ExecutableElement getBar = methodNamed(outerMethods, "getBar");
    ExecutableElement setFoo = methodNamed(innerMethods, "setFoo");
    ExecutableElement setBar = methodNamed(innerMethods, "setBar");
    TypeMirror setFooParameter = setFoo.getParameters().get(0).asType();
    TypeMirror originalGetFooReturn = getFoo.getReturnType();
    TypeMirror rewrittenGetFooReturn = types.get(getFoo);
    assertThat(typeUtils.isAssignable(setFooParameter, originalGetFooReturn)).isFalse();
    assertThat(typeUtils.isAssignable(setFooParameter, rewrittenGetFooReturn)).isTrue();
    TypeMirror setBarParameter = setBar.getParameters().get(0).asType();
    TypeMirror originalGetBarReturn = getBar.getReturnType();
    TypeMirror rewrittenGetBarReturn = types.get(getBar);
    assertThat(typeUtils.isAssignable(setBarParameter, originalGetBarReturn)).isFalse();
    assertThat(typeUtils.isAssignable(setBarParameter, rewrittenGetBarReturn)).isTrue();
  }

  @Test
  public void canAssignStaticMethodResult() {
    TypeElement immutableMap = elementUtils.getTypeElement(ImmutableMap.class.getCanonicalName());
    TypeElement string = elementUtils.getTypeElement(String.class.getCanonicalName());
    TypeElement integer = elementUtils.getTypeElement(Integer.class.getCanonicalName());
    TypeElement number = elementUtils.getTypeElement(Number.class.getCanonicalName());
    TypeMirror immutableMapStringNumber =
        typeUtils.getDeclaredType(immutableMap, string.asType(), number.asType());
    TypeMirror immutableMapStringInteger =
        typeUtils.getDeclaredType(immutableMap, string.asType(), integer.asType());
    TypeElement map = elementUtils.getTypeElement(Map.class.getCanonicalName());
    TypeMirror erasedMap = typeUtils.erasure(map.asType());
    // If the target type is ImmutableMap<String, Number> then we should be able to use
    //   static <K, V> ImmutableMap<K, V> ImmutableMap.copyOf(Map<? extends K, ? extends V>)
    // with a parameter of type ImmutableMap<String, Integer>.
    List<ExecutableElement> immutableMapMethods =
        ElementFilter.methodsIn(immutableMap.getEnclosedElements());
    ExecutableElement copyOf = methodNamed(immutableMapMethods, "copyOf", erasedMap);
    expect
        .that(
            TypeVariables.canAssignStaticMethodResult(
                copyOf, immutableMapStringInteger, immutableMapStringNumber, typeUtils))
        .isTrue();
    expect
        .that(
            TypeVariables.canAssignStaticMethodResult(
                copyOf, immutableMapStringNumber, immutableMapStringInteger, typeUtils))
        .isFalse();
  }

  private static ExecutableElement methodNamed(List<ExecutableElement> methods, String name) {
    return methods.stream().filter(m -> m.getSimpleName().contentEquals(name)).findFirst().get();
  }

  private static ExecutableElement methodNamed(
      List<ExecutableElement> methods, String name, TypeMirror erasedParameterType) {
    return methods.stream()
        .filter(m -> m.getSimpleName().contentEquals(name))
        .filter(m -> m.getParameters().size() == 1)
        .filter(
            m ->
                typeUtils.isSameType(
                    erasedParameterType, typeUtils.erasure(m.getParameters().get(0).asType())))
        .findFirst()
        .get();
  }
}
