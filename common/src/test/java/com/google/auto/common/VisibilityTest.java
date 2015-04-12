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

import com.google.testing.compile.CompilationRule;
import javax.lang.model.element.PackageElement;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.auto.common.Visibility.DEFAULT;
import static com.google.auto.common.Visibility.PRIVATE;
import static com.google.auto.common.Visibility.PROTECTED;
import static com.google.auto.common.Visibility.PUBLIC;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

@RunWith(JUnit4.class)
public class VisibilityTest {
  @Rule public CompilationRule compilation = new CompilationRule();

  @Test
  public void packageVisibility() {
    assertThat(Visibility.ofElement(compilation.getElements().getPackageElement("java.lang")))
        .isEqualTo(PUBLIC);
    assertThat(Visibility.ofElement(
        compilation.getElements().getPackageElement("com.google.auto.common")))
            .isEqualTo(PUBLIC);
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
    assertThat(Visibility.ofElement(
        compilation.getElements().getTypeElement("java.util.Map.Entry")))
            .isEqualTo(PUBLIC);
    assertThat(Visibility.ofElement(
        compilation.getElements().getTypeElement(PublicClass.class.getCanonicalName())))
            .isEqualTo(PUBLIC);
    assertThat(Visibility.ofElement(
        compilation.getElements().getTypeElement(ProtectedClass.class.getCanonicalName())))
            .isEqualTo(PROTECTED);
    assertThat(Visibility.ofElement(
        compilation.getElements().getTypeElement(DefaultClass.class.getCanonicalName())))
            .isEqualTo(DEFAULT);
    assertThat(Visibility.ofElement(
        compilation.getElements().getTypeElement(PrivateClass.class.getCanonicalName())))
            .isEqualTo(PRIVATE);
  }

  @Test
  public void effectiveClassVisibility() {
    assertThat(effectiveVisiblityOfClass(PublicClass.class)).isEqualTo(PUBLIC);
    assertThat(effectiveVisiblityOfClass(ProtectedClass.class)).isEqualTo(PROTECTED);
    assertThat(effectiveVisiblityOfClass(DefaultClass.class)).isEqualTo(DEFAULT);
    assertThat(effectiveVisiblityOfClass(PrivateClass.class)).isEqualTo(PRIVATE);

    assertThat(effectiveVisiblityOfClass(PublicClass.NestedPublicClass.class))
        .isEqualTo(PUBLIC);
    assertThat(effectiveVisiblityOfClass(PublicClass.NestedProtectedClass.class))
        .isEqualTo(PROTECTED);
    assertThat(effectiveVisiblityOfClass(PublicClass.NestedDefaultClass.class))
        .isEqualTo(DEFAULT);
    assertThat(effectiveVisiblityOfClass(PublicClass.NestedPrivateClass.class))
        .isEqualTo(PRIVATE);

    assertThat(effectiveVisiblityOfClass(ProtectedClass.NestedPublicClass.class))
        .isEqualTo(PROTECTED);
    assertThat(effectiveVisiblityOfClass(ProtectedClass.NestedProtectedClass.class))
        .isEqualTo(PROTECTED);
    assertThat(effectiveVisiblityOfClass(ProtectedClass.NestedDefaultClass.class))
        .isEqualTo(DEFAULT);
    assertThat(effectiveVisiblityOfClass(ProtectedClass.NestedPrivateClass.class))
        .isEqualTo(PRIVATE);

    assertThat(effectiveVisiblityOfClass(DefaultClass.NestedPublicClass.class))
        .isEqualTo(DEFAULT);
    assertThat(effectiveVisiblityOfClass(DefaultClass.NestedProtectedClass.class))
        .isEqualTo(DEFAULT);
    assertThat(effectiveVisiblityOfClass(DefaultClass.NestedDefaultClass.class))
        .isEqualTo(DEFAULT);
    assertThat(effectiveVisiblityOfClass(DefaultClass.NestedPrivateClass.class))
        .isEqualTo(PRIVATE);

    assertThat(effectiveVisiblityOfClass(PrivateClass.NestedPublicClass.class))
        .isEqualTo(PRIVATE);
    assertThat(effectiveVisiblityOfClass(PrivateClass.NestedProtectedClass.class))
        .isEqualTo(PRIVATE);
    assertThat(effectiveVisiblityOfClass(PrivateClass.NestedDefaultClass.class))
        .isEqualTo(PRIVATE);
    assertThat(effectiveVisiblityOfClass(PrivateClass.NestedPrivateClass.class))
        .isEqualTo(PRIVATE);
  }

  private Visibility effectiveVisiblityOfClass(Class<?> clazz) {
    return Visibility.effectiveVisibilityOfElement(
        compilation.getElements().getTypeElement(clazz.getCanonicalName()));
  }

  @Test
  public void visibilityFrom() {
    PackageElement samePackage = compilation.getElements().getPackageElement("com.google.auto.common");
    assertTrue(isVisibleFromClass(PublicClass.class, samePackage));
    assertTrue(isVisibleFromClass(ProtectedClass.class, samePackage));
    assertTrue(isVisibleFromClass(DefaultClass.class, samePackage));
    assertFalse(isVisibleFromClass(PrivateClass.class, samePackage));

    assertTrue(isVisibleFromClass(PublicClass.NestedPublicClass.class, samePackage));
    assertTrue(isVisibleFromClass(PublicClass.NestedProtectedClass.class, samePackage));
    assertTrue(isVisibleFromClass(PublicClass.NestedDefaultClass.class, samePackage));
    assertFalse(isVisibleFromClass(PublicClass.NestedPrivateClass.class, samePackage));

    assertTrue(isVisibleFromClass(ProtectedClass.NestedPublicClass.class, samePackage));
    assertTrue(isVisibleFromClass(ProtectedClass.NestedProtectedClass.class, samePackage));
    assertTrue(isVisibleFromClass(ProtectedClass.NestedDefaultClass.class, samePackage));
    assertFalse(isVisibleFromClass(ProtectedClass.NestedPrivateClass.class, samePackage));

    assertTrue(isVisibleFromClass(DefaultClass.NestedPublicClass.class, samePackage));
    assertTrue(isVisibleFromClass(DefaultClass.NestedProtectedClass.class, samePackage));
    assertTrue(isVisibleFromClass(DefaultClass.NestedDefaultClass.class, samePackage));
    assertFalse(isVisibleFromClass(DefaultClass.NestedPrivateClass.class, samePackage));

    assertFalse(isVisibleFromClass(PrivateClass.NestedPublicClass.class, samePackage));
    assertFalse(isVisibleFromClass(PrivateClass.NestedProtectedClass.class, samePackage));
    assertFalse(isVisibleFromClass(PrivateClass.NestedDefaultClass.class, samePackage));
    assertFalse(isVisibleFromClass(PrivateClass.NestedPrivateClass.class, samePackage));

    PackageElement otherPackage = compilation.getElements().getPackageElement("com.example");
    assertTrue(isVisibleFromClass(PublicClass.class, otherPackage));
    assertFalse(isVisibleFromClass(ProtectedClass.class, otherPackage));
    assertFalse(isVisibleFromClass(DefaultClass.class, otherPackage));
    assertFalse(isVisibleFromClass(PrivateClass.class, otherPackage));

    assertTrue(isVisibleFromClass(PublicClass.NestedPublicClass.class, otherPackage));
    assertFalse(isVisibleFromClass(PublicClass.NestedProtectedClass.class, otherPackage));
    assertFalse(isVisibleFromClass(PublicClass.NestedDefaultClass.class, otherPackage));
    assertFalse(isVisibleFromClass(PublicClass.NestedPrivateClass.class, otherPackage));

    assertFalse(isVisibleFromClass(ProtectedClass.NestedPublicClass.class, otherPackage));
    assertFalse(isVisibleFromClass(ProtectedClass.NestedProtectedClass.class, otherPackage));
    assertFalse(isVisibleFromClass(ProtectedClass.NestedDefaultClass.class, otherPackage));
    assertFalse(isVisibleFromClass(ProtectedClass.NestedPrivateClass.class, otherPackage));

    assertFalse(isVisibleFromClass(DefaultClass.NestedPublicClass.class, otherPackage));
    assertFalse(isVisibleFromClass(DefaultClass.NestedProtectedClass.class, otherPackage));
    assertFalse(isVisibleFromClass(DefaultClass.NestedDefaultClass.class, otherPackage));
    assertFalse(isVisibleFromClass(DefaultClass.NestedPrivateClass.class, otherPackage));

    assertFalse(isVisibleFromClass(PrivateClass.NestedPublicClass.class, otherPackage));
    assertFalse(isVisibleFromClass(PrivateClass.NestedProtectedClass.class, otherPackage));
    assertFalse(isVisibleFromClass(PrivateClass.NestedDefaultClass.class, otherPackage));
    assertFalse(isVisibleFromClass(PrivateClass.NestedPrivateClass.class, otherPackage));
  }

  private boolean isVisibleFromClass(Class<?> clazz, PackageElement from) {
    return Visibility.isVisibleFrom(
        compilation.getElements().getTypeElement(clazz.getCanonicalName()), from);
  }

  @Test
  public void visibilityFromSubclass() {
    PackageElement samePackage = compilation.getElements().getPackageElement("com.google.auto.common");
    assertTrue(isVisibleFromSubclass(PublicClass.class, samePackage));
    assertTrue(isVisibleFromSubclass(ProtectedClass.class, samePackage));
    assertTrue(isVisibleFromSubclass(DefaultClass.class, samePackage));
    assertFalse(isVisibleFromSubclass(PrivateClass.class, samePackage));

    assertTrue(isVisibleFromSubclass(PublicClass.NestedPublicClass.class, samePackage));
    assertTrue(isVisibleFromSubclass(PublicClass.NestedProtectedClass.class, samePackage));
    assertTrue(isVisibleFromSubclass(PublicClass.NestedDefaultClass.class, samePackage));
    assertFalse(isVisibleFromSubclass(PublicClass.NestedPrivateClass.class, samePackage));

    assertTrue(isVisibleFromSubclass(ProtectedClass.NestedPublicClass.class, samePackage));
    assertTrue(isVisibleFromSubclass(ProtectedClass.NestedProtectedClass.class, samePackage));
    assertTrue(isVisibleFromSubclass(ProtectedClass.NestedDefaultClass.class, samePackage));
    assertFalse(isVisibleFromSubclass(ProtectedClass.NestedPrivateClass.class, samePackage));

    assertTrue(isVisibleFromSubclass(DefaultClass.NestedPublicClass.class, samePackage));
    assertTrue(isVisibleFromSubclass(DefaultClass.NestedProtectedClass.class, samePackage));
    assertTrue(isVisibleFromSubclass(DefaultClass.NestedDefaultClass.class, samePackage));
    assertFalse(isVisibleFromSubclass(DefaultClass.NestedPrivateClass.class, samePackage));

    assertFalse(isVisibleFromSubclass(PrivateClass.NestedPublicClass.class, samePackage));
    assertFalse(isVisibleFromSubclass(PrivateClass.NestedProtectedClass.class, samePackage));
    assertFalse(isVisibleFromSubclass(PrivateClass.NestedDefaultClass.class, samePackage));
    assertFalse(isVisibleFromSubclass(PrivateClass.NestedPrivateClass.class, samePackage));

    PackageElement otherPackage = compilation.getElements().getPackageElement("com.example");
    assertTrue(isVisibleFromSubclass(PublicClass.class, otherPackage));
    assertTrue(isVisibleFromSubclass(ProtectedClass.class, otherPackage));
    assertFalse(isVisibleFromSubclass(DefaultClass.class, otherPackage));
    assertFalse(isVisibleFromSubclass(PrivateClass.class, otherPackage));

    assertTrue(isVisibleFromSubclass(PublicClass.NestedPublicClass.class, otherPackage));
    assertTrue(isVisibleFromSubclass(PublicClass.NestedProtectedClass.class, otherPackage));
    assertFalse(isVisibleFromSubclass(PublicClass.NestedDefaultClass.class, otherPackage));
    assertFalse(isVisibleFromSubclass(PublicClass.NestedPrivateClass.class, otherPackage));

    assertTrue(isVisibleFromSubclass(ProtectedClass.NestedPublicClass.class, otherPackage));
    assertTrue(isVisibleFromSubclass(ProtectedClass.NestedProtectedClass.class, otherPackage));
    assertFalse(isVisibleFromSubclass(ProtectedClass.NestedDefaultClass.class, otherPackage));
    assertFalse(isVisibleFromSubclass(ProtectedClass.NestedPrivateClass.class, otherPackage));

    assertFalse(isVisibleFromSubclass(DefaultClass.NestedPublicClass.class, otherPackage));
    assertFalse(isVisibleFromSubclass(DefaultClass.NestedProtectedClass.class, otherPackage));
    assertFalse(isVisibleFromSubclass(DefaultClass.NestedDefaultClass.class, otherPackage));
    assertFalse(isVisibleFromSubclass(DefaultClass.NestedPrivateClass.class, otherPackage));

    assertFalse(isVisibleFromSubclass(PrivateClass.NestedPublicClass.class, otherPackage));
    assertFalse(isVisibleFromSubclass(PrivateClass.NestedProtectedClass.class, otherPackage));
    assertFalse(isVisibleFromSubclass(PrivateClass.NestedDefaultClass.class, otherPackage));
    assertFalse(isVisibleFromSubclass(PrivateClass.NestedPrivateClass.class, otherPackage));
  }

  private boolean isVisibleFromSubclass(Class<?> clazz, PackageElement from) {
    return Visibility.isVisibleFromSubclass(
        compilation.getElements().getTypeElement(clazz.getCanonicalName()), from);
  }
}
