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

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.testing.compile.CompilationRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

@RunWith(JUnit4.class)
public class MoreElementsTest {
  @Rule public CompilationRule compilation = new CompilationRule();

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
    ASSERT.that(javaLangPackageElement).isEqualTo(javaLangPackageElement);
    ASSERT.that(MoreElements.getPackage(stringElement)).isEqualTo(javaLangPackageElement);
    for (Element childElement : stringElement.getEnclosedElements()) {
      ASSERT.that(MoreElements.getPackage(childElement)).isEqualTo(javaLangPackageElement);
    }
  }

  @Test
  public void asPackage() {
    ASSERT.that(MoreElements.asPackage(javaLangPackageElement)).is(javaLangPackageElement);
  }

  @Test
  public void asPackage_illegalArgument() {
    try {
      MoreElements.asPackage(stringElement);
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Test
  public void asType() {
    ASSERT.that(MoreElements.asType(stringElement)).is(stringElement);
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
      ASSERT.that(MoreElements.asVariable(variableElement)).is(variableElement);
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
      ASSERT.that(MoreElements.asExecutable(methodElement)).is(methodElement);
    }
    for (Element methodElement
        : ElementFilter.constructorsIn(stringElement.getEnclosedElements())) {
      ASSERT.that(MoreElements.asExecutable(methodElement)).is(methodElement);
    }
  }

  @Test
  public void asExecutable_illegalArgument() {
    try {
      MoreElements.asExecutable(javaLangPackageElement);
      fail();
    } catch (IllegalArgumentException expected) {}
  }
}
