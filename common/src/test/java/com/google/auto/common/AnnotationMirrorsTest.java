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

import static com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.BLAH;
import static com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.FOO;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableSet;
import com.google.common.testing.EquivalenceTester;
import com.google.common.truth.Correspondence;
import com.google.testing.compile.CompilationRule;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link AnnotationMirrors}.
 */
@RunWith(JUnit4.class)
public class AnnotationMirrorsTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  private Elements elements;

  @Before
  public void setUp() {
    this.elements = compilationRule.getElements();
  }

  @interface SimpleAnnotation {}

  @SimpleAnnotation
  static class SimplyAnnotated {}

  @SimpleAnnotation
  static class AlsoSimplyAnnotated {}

  enum SimpleEnum {
    BLAH,
    FOO
  }

  @interface Outer {
    SimpleEnum value();
  }

  @Outer(BLAH)
  static class TestClassBlah {}

  @Outer(BLAH)
  static class TestClassBlah2 {}

  @Outer(FOO)
  static class TestClassFoo {}

  @interface DefaultingOuter {
    SimpleEnum value() default SimpleEnum.BLAH;
  }

  @DefaultingOuter
  static class TestWithDefaultingOuterDefault {}

  @DefaultingOuter(BLAH)
  static class TestWithDefaultingOuterBlah {}

  @DefaultingOuter(FOO)
  static class TestWithDefaultingOuterFoo {}

  @interface AnnotatedOuter {
    DefaultingOuter value();
  }

  @AnnotatedOuter(@DefaultingOuter)
  static class TestDefaultNestedAnnotated {}

  @AnnotatedOuter(@DefaultingOuter(BLAH))
  static class TestBlahNestedAnnotated {}

  @AnnotatedOuter(@DefaultingOuter(FOO))
  static class TestFooNestedAnnotated {}

  @interface OuterWithValueArray {
    DefaultingOuter[] value() default {};
  }

  @OuterWithValueArray
  static class TestValueArrayWithDefault {}

  @OuterWithValueArray({})
  static class TestValueArrayWithEmpty {}

  @OuterWithValueArray({@DefaultingOuter})
  static class TestValueArrayWithOneDefault {}

  @OuterWithValueArray(@DefaultingOuter(BLAH))
  static class TestValueArrayWithOneBlah {}

  @OuterWithValueArray(@DefaultingOuter(FOO))
  static class TestValueArrayWithOneFoo {}

  @OuterWithValueArray({@DefaultingOuter(FOO), @DefaultingOuter})
  class TestValueArrayWithFooAndDefaultBlah {}

  @OuterWithValueArray({@DefaultingOuter(FOO), @DefaultingOuter(BLAH)})
  class TestValueArrayWithFooBlah {}

  @OuterWithValueArray({@DefaultingOuter(FOO), @DefaultingOuter(BLAH)})
  class TestValueArrayWithFooBlah2 {} // Different instances than on TestValueArrayWithFooBlah.

  @OuterWithValueArray({@DefaultingOuter(BLAH), @DefaultingOuter(FOO)})
  class TestValueArrayWithBlahFoo {}

  @Test
  public void testEquivalences() {
    EquivalenceTester<AnnotationMirror> tester =
        EquivalenceTester.of(AnnotationMirrors.equivalence());

    tester.addEquivalenceGroup(
        annotationOn(SimplyAnnotated.class), annotationOn(AlsoSimplyAnnotated.class));

    tester.addEquivalenceGroup(
        annotationOn(TestClassBlah.class), annotationOn(TestClassBlah2.class));

    tester.addEquivalenceGroup(annotationOn(TestClassFoo.class));

    tester.addEquivalenceGroup(
        annotationOn(TestWithDefaultingOuterDefault.class),
        annotationOn(TestWithDefaultingOuterBlah.class));

    tester.addEquivalenceGroup(annotationOn(TestWithDefaultingOuterFoo.class));

    tester.addEquivalenceGroup(
        annotationOn(TestDefaultNestedAnnotated.class),
        annotationOn(TestBlahNestedAnnotated.class));

    tester.addEquivalenceGroup(annotationOn(TestFooNestedAnnotated.class));

    tester.addEquivalenceGroup(
        annotationOn(TestValueArrayWithDefault.class), annotationOn(TestValueArrayWithEmpty.class));

    tester.addEquivalenceGroup(
        annotationOn(TestValueArrayWithOneDefault.class),
        annotationOn(TestValueArrayWithOneBlah.class));

    tester.addEquivalenceGroup(annotationOn(TestValueArrayWithOneFoo.class));

    tester.addEquivalenceGroup(
        annotationOn(TestValueArrayWithFooAndDefaultBlah.class),
        annotationOn(TestValueArrayWithFooBlah.class),
        annotationOn(TestValueArrayWithFooBlah2.class));

    tester.addEquivalenceGroup(annotationOn(TestValueArrayWithBlahFoo.class));

    tester.test();
  }

  @interface Stringy {
    String value() default "default";
  }

  @Stringy
  static class StringyUnset {}

  @Stringy("foo")
  static class StringySet {}

  @Test
  public void testGetDefaultValuesUnset() {
    assertThat(annotationOn(StringyUnset.class).getElementValues()).isEmpty();
    Iterable<AnnotationValue> values =
        AnnotationMirrors.getAnnotationValuesWithDefaults(annotationOn(StringyUnset.class))
            .values();
    String value =
        getOnlyElement(values)
            .accept(
                new SimpleAnnotationValueVisitor6<String, Void>() {
                  @Override
                  public String visitString(String value, Void ignored) {
                    return value;
                  }
                },
                null);
    assertThat(value).isEqualTo("default");
  }

  @Test
  public void testGetDefaultValuesSet() {
    Iterable<AnnotationValue> values =
        AnnotationMirrors.getAnnotationValuesWithDefaults(annotationOn(StringySet.class)).values();
    String value =
        getOnlyElement(values)
            .accept(
                new SimpleAnnotationValueVisitor6<String, Void>() {
                  @Override
                  public String visitString(String value, Void ignored) {
                    return value;
                  }
                },
                null);
    assertThat(value).isEqualTo("foo");
  }

  @Test
  public void testGetValueEntry() {
    Map.Entry<ExecutableElement, AnnotationValue> elementValue =
        AnnotationMirrors.getAnnotationElementAndValue(annotationOn(TestClassBlah.class), "value");
    assertThat(elementValue.getKey().getSimpleName().toString()).isEqualTo("value");
    assertThat(elementValue.getValue().getValue()).isInstanceOf(VariableElement.class);
    AnnotationValue value =
        AnnotationMirrors.getAnnotationValue(annotationOn(TestClassBlah.class), "value");
    assertThat(value.getValue()).isInstanceOf(VariableElement.class);
  }

  @Test
  public void testGetValueEntryFailure() {
    try {
      AnnotationMirrors.getAnnotationValue(annotationOn(TestClassBlah.class), "a");
    } catch (IllegalArgumentException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "@com.google.auto.common.AnnotationMirrorsTest.Outer does not define an element a()");
      return;
    }
    fail("Should have thrown.");
  }

  private AnnotationMirror annotationOn(Class<?> clazz) {
    return getOnlyElement(elements.getTypeElement(clazz.getCanonicalName()).getAnnotationMirrors());
  }

  @Retention(RetentionPolicy.RUNTIME)
  private @interface AnnotatingAnnotation {}

  @AnnotatingAnnotation
  @Retention(RetentionPolicy.RUNTIME)
  private @interface AnnotatedAnnotation1 {}

  @AnnotatingAnnotation
  @Retention(RetentionPolicy.RUNTIME)
  private @interface AnnotatedAnnotation2 {}

  @Retention(RetentionPolicy.RUNTIME)
  private @interface NotAnnotatedAnnotation {}

  @AnnotatedAnnotation1
  @NotAnnotatedAnnotation
  @AnnotatedAnnotation2
  private static final class AnnotatedClass {}

  @Test
  public void getAnnotatedAnnotations() {
    TypeElement element = elements.getTypeElement(AnnotatedClass.class.getCanonicalName());

    // Test Class API
    getAnnotatedAnnotationsAsserts(
        AnnotationMirrors.getAnnotatedAnnotations(element, AnnotatingAnnotation.class));

    // Test String API
    String annotatingAnnotationName = AnnotatingAnnotation.class.getCanonicalName();
    getAnnotatedAnnotationsAsserts(
        AnnotationMirrors.getAnnotatedAnnotations(element, annotatingAnnotationName));

    // Test TypeElement API
    TypeElement annotatingAnnotationElement = elements.getTypeElement(annotatingAnnotationName);
    getAnnotatedAnnotationsAsserts(
        AnnotationMirrors.getAnnotatedAnnotations(element, annotatingAnnotationElement));
  }

  @Test
  public void toSourceString() {
    assertThat(AnnotationMirrors.toString(annotationOn(AlsoSimplyAnnotated.class)))
        .isEqualTo("@com.google.auto.common.AnnotationMirrorsTest.SimpleAnnotation");
    assertThat(AnnotationMirrors.toString(annotationOn(SimplyAnnotated.class)))
        .isEqualTo("@com.google.auto.common.AnnotationMirrorsTest.SimpleAnnotation");
    assertThat(AnnotationMirrors.toString(annotationOn(StringySet.class)))
        .isEqualTo("@com.google.auto.common.AnnotationMirrorsTest.Stringy(\"foo\")");
    assertThat(AnnotationMirrors.toString(annotationOn(StringyUnset.class)))
        .isEqualTo("@com.google.auto.common.AnnotationMirrorsTest.Stringy");
    assertThat(AnnotationMirrors.toString(annotationOn(TestBlahNestedAnnotated.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.AnnotatedOuter(@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.BLAH))");
    assertThat(AnnotationMirrors.toString(annotationOn(TestClassBlah2.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.Outer(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.BLAH)");
    assertThat(AnnotationMirrors.toString(annotationOn(TestClassBlah.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.Outer(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.BLAH)");
    assertThat(AnnotationMirrors.toString(annotationOn(TestClassFoo.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.Outer(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.FOO)");
    assertThat(AnnotationMirrors.toString(annotationOn(TestDefaultNestedAnnotated.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.AnnotatedOuter(@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter)");
    assertThat(AnnotationMirrors.toString(annotationOn(TestFooNestedAnnotated.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.AnnotatedOuter(@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.FOO))");
    assertThat(AnnotationMirrors.toString(annotationOn(TestValueArrayWithBlahFoo.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.OuterWithValueArray({@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.BLAH),"
                + " @com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.FOO)})");
    assertThat(AnnotationMirrors.toString(annotationOn(TestValueArrayWithDefault.class)))
        .isEqualTo("@com.google.auto.common.AnnotationMirrorsTest.OuterWithValueArray");
    assertThat(AnnotationMirrors.toString(annotationOn(TestValueArrayWithEmpty.class)))
        .isEqualTo("@com.google.auto.common.AnnotationMirrorsTest.OuterWithValueArray({})");
    assertThat(AnnotationMirrors.toString(annotationOn(TestValueArrayWithFooAndDefaultBlah.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.OuterWithValueArray({@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.FOO),"
                + " @com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter})");
    assertThat(AnnotationMirrors.toString(annotationOn(TestValueArrayWithFooBlah2.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.OuterWithValueArray({@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.FOO),"
                + " @com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.BLAH)})");
    assertThat(AnnotationMirrors.toString(annotationOn(TestValueArrayWithFooBlah.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.OuterWithValueArray({@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.FOO),"
                + " @com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.BLAH)})");
    assertThat(AnnotationMirrors.toString(annotationOn(TestValueArrayWithOneBlah.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.OuterWithValueArray(@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.BLAH))");
    assertThat(AnnotationMirrors.toString(annotationOn(TestValueArrayWithOneDefault.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.OuterWithValueArray(@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter)");
    assertThat(AnnotationMirrors.toString(annotationOn(TestValueArrayWithOneFoo.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.OuterWithValueArray(@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.FOO))");
    assertThat(AnnotationMirrors.toString(annotationOn(TestWithDefaultingOuterBlah.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.BLAH)");
    assertThat(AnnotationMirrors.toString(annotationOn(TestWithDefaultingOuterDefault.class)))
        .isEqualTo("@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter");
    assertThat(AnnotationMirrors.toString(annotationOn(TestWithDefaultingOuterFoo.class)))
        .isEqualTo(
            "@com.google.auto.common.AnnotationMirrorsTest.DefaultingOuter(com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.FOO)");
  }

  private void getAnnotatedAnnotationsAsserts(
      ImmutableSet<? extends AnnotationMirror> annotatedAnnotations) {
    assertThat(annotatedAnnotations)
        .comparingElementsUsing(
            Correspondence.transforming(
                (AnnotationMirror a) -> MoreTypes.asTypeElement(a.getAnnotationType()), "has type"))
        .containsExactly(
            elements.getTypeElement(AnnotatedAnnotation1.class.getCanonicalName()),
            elements.getTypeElement(AnnotatedAnnotation2.class.getCanonicalName()));
  }
}
