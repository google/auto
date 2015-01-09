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
import static org.junit.Assert.fail;

import com.google.common.base.Optional;
import com.google.common.truth.Expect;
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
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

@RunWith(JUnit4.class)
public class MoreElementsTest {
  @Rule public CompilationRule compilation = new CompilationRule();
  @Rule public Expect expect = Expect.create();

  private PackageElement javaLangPackageElement;
  private TypeElement stringElement;

  @Before
  public void initializeTestElements() {
    Elements elements = compilation.getElements();
    this.javaLangPackageElement = elements.getPackageElement("java.lang");
    this.stringElement = elements.getTypeElement(String.class.getCanonicalName());
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
    assertThat(MoreElements.asType(typeElement)).isEqualTo(typeElement);
  }

  @Test public void asTypeElement_notATypeElement() {
    TypeElement typeElement =
        compilation.getElements().getTypeElement(String.class.getCanonicalName());
    for (ExecutableElement e : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
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

    expect.that(MoreElements.asType(documented.get().getAnnotationType().asElement())
        .getQualifiedName().toString()).isEqualTo(Documented.class.getCanonicalName());
    expect.that(MoreElements.asType(innerAnnotation.get().getAnnotationType().asElement())
        .getQualifiedName().toString()).isEqualTo(InnerAnnotation.class.getCanonicalName());
  }
}
