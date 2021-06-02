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

import static com.google.auto.common.Visibility.DEFAULT;
import static com.google.auto.common.Visibility.PRIVATE;
import static com.google.auto.common.Visibility.PROTECTED;
import static com.google.auto.common.Visibility.PUBLIC;
import static com.google.common.truth.Truth.assertThat;

import com.google.testing.compile.CompilationRule;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VisibilityTest {
  @Rule public CompilationRule compilation = new CompilationRule();

  @Test
  public void packageVisibility() {
    assertThat(Visibility.ofElement(compilation.getElements().getPackageElement("java.lang")))
        .isEqualTo(PUBLIC);
    assertThat(
            Visibility.ofElement(
                compilation.getElements().getPackageElement("com.google.auto.common")))
        .isEqualTo(PUBLIC);
  }

  @Test
  public void moduleVisibility() throws IllegalAccessException, InvocationTargetException {
    Method getModuleElement;
    try {
      getModuleElement = Elements.class.getMethod("getModuleElement", CharSequence.class);
    } catch (NoSuchMethodException e) {
      // TODO(ronshapiro): rewrite this test without reflection once we're on Java 9
      return;
    }
    Element moduleElement =
        (Element) getModuleElement.invoke(compilation.getElements(), "java.base");
    assertThat(Visibility.ofElement(moduleElement)).isEqualTo(PUBLIC);
  }

  @SuppressWarnings("unused")
  public static class PublicClass {
    public static class NestedPublicClass {}

    protected static class NestedProtectedClass {}

    static class NestedDefaultClass {}

    private static class NestedPrivateClass {}
  }

  @SuppressWarnings("unused")
  protected static class ProtectedClass {
    public static class NestedPublicClass {}

    protected static class NestedProtectedClass {}

    static class NestedDefaultClass {}

    private static class NestedPrivateClass {}
  }

  @SuppressWarnings("unused")
  static class DefaultClass {
    public static class NestedPublicClass {}

    protected static class NestedProtectedClass {}

    static class NestedDefaultClass {}

    private static class NestedPrivateClass {}
  }

  @SuppressWarnings("unused")
  private static class PrivateClass {
    public static class NestedPublicClass {}

    protected static class NestedProtectedClass {}

    static class NestedDefaultClass {}

    private static class NestedPrivateClass {}
  }

  @Test
  public void classVisibility() {
    assertThat(Visibility.ofElement(compilation.getElements().getTypeElement("java.util.Map")))
        .isEqualTo(PUBLIC);
    assertThat(
            Visibility.ofElement(compilation.getElements().getTypeElement("java.util.Map.Entry")))
        .isEqualTo(PUBLIC);
    assertThat(
            Visibility.ofElement(
                compilation.getElements().getTypeElement(PublicClass.class.getCanonicalName())))
        .isEqualTo(PUBLIC);
    assertThat(
            Visibility.ofElement(
                compilation.getElements().getTypeElement(ProtectedClass.class.getCanonicalName())))
        .isEqualTo(PROTECTED);
    assertThat(
            Visibility.ofElement(
                compilation.getElements().getTypeElement(DefaultClass.class.getCanonicalName())))
        .isEqualTo(DEFAULT);
    assertThat(
            Visibility.ofElement(
                compilation.getElements().getTypeElement(PrivateClass.class.getCanonicalName())))
        .isEqualTo(PRIVATE);
  }

  @Test
  public void effectiveClassVisibility() {
    assertThat(effectiveVisiblityOfClass(PublicClass.class)).isEqualTo(PUBLIC);
    assertThat(effectiveVisiblityOfClass(ProtectedClass.class)).isEqualTo(PROTECTED);
    assertThat(effectiveVisiblityOfClass(DefaultClass.class)).isEqualTo(DEFAULT);
    assertThat(effectiveVisiblityOfClass(PrivateClass.class)).isEqualTo(PRIVATE);

    assertThat(effectiveVisiblityOfClass(PublicClass.NestedPublicClass.class)).isEqualTo(PUBLIC);
    assertThat(effectiveVisiblityOfClass(PublicClass.NestedProtectedClass.class))
        .isEqualTo(PROTECTED);
    assertThat(effectiveVisiblityOfClass(PublicClass.NestedDefaultClass.class)).isEqualTo(DEFAULT);
    assertThat(effectiveVisiblityOfClass(PublicClass.NestedPrivateClass.class)).isEqualTo(PRIVATE);

    assertThat(effectiveVisiblityOfClass(ProtectedClass.NestedPublicClass.class))
        .isEqualTo(PROTECTED);
    assertThat(effectiveVisiblityOfClass(ProtectedClass.NestedProtectedClass.class))
        .isEqualTo(PROTECTED);
    assertThat(effectiveVisiblityOfClass(ProtectedClass.NestedDefaultClass.class))
        .isEqualTo(DEFAULT);
    assertThat(effectiveVisiblityOfClass(ProtectedClass.NestedPrivateClass.class))
        .isEqualTo(PRIVATE);

    assertThat(effectiveVisiblityOfClass(DefaultClass.NestedPublicClass.class)).isEqualTo(DEFAULT);
    assertThat(effectiveVisiblityOfClass(DefaultClass.NestedProtectedClass.class))
        .isEqualTo(DEFAULT);
    assertThat(effectiveVisiblityOfClass(DefaultClass.NestedDefaultClass.class)).isEqualTo(DEFAULT);
    assertThat(effectiveVisiblityOfClass(DefaultClass.NestedPrivateClass.class)).isEqualTo(PRIVATE);

    assertThat(effectiveVisiblityOfClass(PrivateClass.NestedPublicClass.class)).isEqualTo(PRIVATE);
    assertThat(effectiveVisiblityOfClass(PrivateClass.NestedProtectedClass.class))
        .isEqualTo(PRIVATE);
    assertThat(effectiveVisiblityOfClass(PrivateClass.NestedDefaultClass.class)).isEqualTo(PRIVATE);
    assertThat(effectiveVisiblityOfClass(PrivateClass.NestedPrivateClass.class)).isEqualTo(PRIVATE);
  }

  private Visibility effectiveVisiblityOfClass(Class<?> clazz) {
    return Visibility.effectiveVisibilityOfElement(
        compilation.getElements().getTypeElement(clazz.getCanonicalName()));
  }
}
