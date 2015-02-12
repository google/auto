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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Expect;
import com.google.common.truth.Truth;
import com.google.testing.compile.CompilationRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

@RunWith(JUnit4.class)
public class MoreElementsTest {
  @Rule public CompilationRule compilation = new CompilationRule();
  @Rule public Expect expect = Expect.create();

  private PackageElement javaLangPackageElement;
  private TypeElement stringElement;
  private TypeElement moreElementsTestClass;

  @Before
  public void initializeTestElements() {
    Elements elements = compilation.getElements();
    this.javaLangPackageElement = elements.getPackageElement("java.lang");
    this.stringElement = elements.getTypeElement(String.class.getCanonicalName());
    this.moreElementsTestClass = elements.getTypeElement(MoreElementsTestClazz.class.getCanonicalName());
  }

  @Test
  public void getPackage() {
    assertThat(javaLangPackageElement).isEqualTo(javaLangPackageElement);
    assertThat(MoreElements.getPackage(stringElement)).isEqualTo(javaLangPackageElement);
    for (Element childElement : stringElement.getEnclosedElements()) {
      assertThat(MoreElements.getPackage(childElement)).isEqualTo(javaLangPackageElement);
    }
  }

  @Test
  public void asPackage() {
    assertThat(MoreElements.asPackage(javaLangPackageElement))
        .isEqualTo(javaLangPackageElement);
  }

  @Test
  public void asPackage_illegalArgument() {
    try {
      MoreElements.asPackage(stringElement);
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Test public void asTypeElement() {
    Element typeElement =
        compilation.getElements().getTypeElement(String.class.getCanonicalName());
    assertTrue(MoreElements.isType(typeElement));
    assertThat(MoreElements.asType(typeElement)).isEqualTo(typeElement);
  }

  @Test public void asTypeElement_notATypeElement() {
    TypeElement typeElement =
        compilation.getElements().getTypeElement(String.class.getCanonicalName());
    for (ExecutableElement e : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
      assertFalse(MoreElements.isType(e));
      try {
        MoreElements.asType(e);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }
  }

  @Test
  public void asType() {
    assertThat(MoreElements.asType(stringElement)).isEqualTo(stringElement);
  }

  @Test
  public void asType_illegalArgument() {
    assertFalse(MoreElements.isType(javaLangPackageElement));
    try {
      MoreElements.asType(javaLangPackageElement);
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Test
  public void asVariable() {
    for (Element variableElement : ElementFilter.fieldsIn(stringElement.getEnclosedElements())) {
      assertThat(MoreElements.asVariable(variableElement)).isEqualTo(variableElement);
    }
  }

  @Test
  public void asVariable_illegalArgument() {
    try {
      MoreElements.asVariable(javaLangPackageElement);
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Test
  public void asExecutable() {
    for (Element methodElement : ElementFilter.methodsIn(stringElement.getEnclosedElements())) {
      assertThat(MoreElements.asExecutable(methodElement)).isEqualTo(methodElement);
    }
    for (Element methodElement
        : ElementFilter.constructorsIn(stringElement.getEnclosedElements())) {
      assertThat(MoreElements.asExecutable(methodElement)).isEqualTo(methodElement);
    }
  }

  @Test
  public void asExecutable_illegalArgument() {
    try {
      MoreElements.asExecutable(javaLangPackageElement);
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Retention(RetentionPolicy.RUNTIME)
  private @interface InnerAnnotation {}

  @Documented
  @InnerAnnotation
  private @interface AnnotatedAnnotation {}

  @Test
  public void isAnnotationPresent() {
    TypeElement annotatedAnnotationElement =
        compilation.getElements().getTypeElement(AnnotatedAnnotation.class.getCanonicalName());
    assertThat(MoreElements.isAnnotationPresent(annotatedAnnotationElement, Documented.class))
        .isTrue();
    assertThat(MoreElements.isAnnotationPresent(annotatedAnnotationElement, InnerAnnotation.class))
        .isTrue();
    assertThat(MoreElements.isAnnotationPresent(annotatedAnnotationElement, SuppressWarnings.class))
        .isFalse();
  }

  @Test
  public void getAnnotationMirror() {
    TypeElement element =
        compilation.getElements().getTypeElement(AnnotatedAnnotation.class.getCanonicalName());

    Optional<AnnotationMirror> documented =
        MoreElements.getAnnotationMirror(element, Documented.class);
    Optional<AnnotationMirror> innerAnnotation =
        MoreElements.getAnnotationMirror(element, InnerAnnotation.class);
    Optional<AnnotationMirror> suppressWarnings =
        MoreElements.getAnnotationMirror(element, SuppressWarnings.class);

    expect.that(documented).isPresent();
    expect.that(innerAnnotation).isPresent();
    expect.that(suppressWarnings).isAbsent();

    Element annotationElement = documented.get().getAnnotationType().asElement();
    expect.that(MoreElements.isType(annotationElement)).isTrue();
    expect.that(MoreElements.asType(annotationElement).getQualifiedName().toString())
        .isEqualTo(Documented.class.getCanonicalName());

    annotationElement = innerAnnotation.get().getAnnotationType().asElement();
    expect.that(MoreElements.isType(annotationElement)).isTrue();
    expect.that(MoreElements.asType(annotationElement).getQualifiedName().toString())
        .isEqualTo(InnerAnnotation.class.getCanonicalName());
  }

  //Used from test
  @SuppressWarnings("unused")
  // Defining a base type so we can make sure its fields / methods are not included in our queries
  private static class MoreElementsBaseTestClazz {

    private static Object basePrivateStaticField;
    protected static Object baseProtectedStaticField;
    public static Object basePublicStaticField;
    static Object basePackageProtectedStaticField;

    private static void basePrivateStaticMethod() {}
    protected static void baseProtectedStaticMethod() {}
    public static void basePublicStaticMethod() {}
    static void basePackageProtectedStaticMethod() {}

    private Object basePrivateField;
    protected Object baseProtectedField;
    public Object basePublicField;
    Object basePackageProtectedField;

    private void basePrivateMethod() {}
    protected void baseProtectedMethod() {}
    public void basePublicMethod() {}
    void basePackageProtectedMethod() {}
  }

  @SuppressWarnings("unused")
  private static class MoreElementsTestClazz extends MoreElementsBaseTestClazz {

    static {
      // A broken implementation of MoreElements.geMethods might include clinit
      privateStaticField = new Object();
    }
    private static Object privateStaticField;
    protected static Object protectedStaticField;
    public static Object publicStaticField;
    static Object packageProtectedStaticField;


    private static void privateStaticMethod() {}
    protected static void protectedStaticMethod() {}
    public static void publicStaticMethod() {}
    static void packageProtectedStaticMethod() {}

    public MoreElementsTestClazz() {}
    public MoreElementsTestClazz(Object o) {}

    // Fields are initialized so that we create init calls in the class
    // These might show up as methods if MoreElements.getMethods is broken
    private Object privateField = new Object();
    protected Object protectedField = new Object();
    public Object publicField = new Object();
    Object packageProtectedField = new Object();

    private void privateMethod() {}
    protected void protectedMethod() {}
    public void publicMethod() {}
    void packageProtectedMethod() {}
  }

  private static Function<Element, String> ELEMENT_TOSTRING_TRANSFORMER =
      new Function<Element, String>() {
        @Override public String apply(Element input) {
          return input.getSimpleName().toString();
        }
      };

  @Test
  public void testGetMethods() {
    ImmutableList<String> methodsAsString = FluentIterable.from(
        MoreElements.getMethods(moreElementsTestClass)).transform(ELEMENT_TOSTRING_TRANSFORMER)
        .toList();

    Truth.assertThat(methodsAsString).containsExactly("privateStaticMethod",
        "protectedStaticMethod", "publicStaticMethod", "packageProtectedStaticMethod",
        "privateMethod", "protectedMethod", "publicMethod", "packageProtectedMethod").inOrder();
  }

  @Test
  public void testGetInstanceMethods() {
    ImmutableList<String> methodsAsString = FluentIterable.from(
        MoreElements.getIntanceMethods(moreElementsTestClass)).transform(
        ELEMENT_TOSTRING_TRANSFORMER).toList();

    Truth.assertThat(methodsAsString).containsExactly("privateMethod", "protectedMethod",
        "publicMethod", "packageProtectedMethod").inOrder();
  }

  @Test
  public void testGetStaticMethods() {
    ImmutableList<String> methodsAsString = FluentIterable.from(
        MoreElements.getStaticMethods(moreElementsTestClass)).transform(
        ELEMENT_TOSTRING_TRANSFORMER).toList();

    Truth.assertThat(methodsAsString).containsExactly("privateStaticMethod",
        "protectedStaticMethod", "publicStaticMethod", "packageProtectedStaticMethod").inOrder();
  }

  @Test
  public void testGetFields() {
    ImmutableList<String> methodsAsString = FluentIterable.from(
        MoreElements.getFields(moreElementsTestClass)).transform(ELEMENT_TOSTRING_TRANSFORMER)
        .toList();

    Truth.assertThat(methodsAsString).containsExactly("privateStaticField", "protectedStaticField",
        "publicStaticField", "packageProtectedStaticField", "privateField", "protectedField",
        "publicField", "packageProtectedField").inOrder();
  }

  @Test
  public void testGetInstanceFields() {
    ImmutableList<String> methodsAsString = FluentIterable.from(
        MoreElements.getInstanceFields(moreElementsTestClass)).transform(
        ELEMENT_TOSTRING_TRANSFORMER).toList();

    Truth.assertThat(methodsAsString).containsExactly("privateField", "protectedField",
        "publicField", "packageProtectedField").inOrder();
  }

  @Test
  public void testGetStaticFields() {
    ImmutableList<String> methodsAsString = FluentIterable.from(
        MoreElements.getStaticFields(moreElementsTestClass)).transform(ELEMENT_TOSTRING_TRANSFORMER)
        .toList();

    Truth.assertThat(methodsAsString).containsExactly("privateStaticField", "protectedStaticField",
        "publicStaticField", "packageProtectedStaticField").inOrder();
  }
}
