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

import static com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.BLAH;
import static com.google.auto.common.AnnotationMirrorsTest.SimpleEnum.FOO;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.Assert.fail;

import com.google.common.testing.EquivalenceTester;
import com.google.testing.compile.CompilationRule;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
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

  @Before public void setUp() {
    this.elements = compilationRule.getElements();
  }

  @interface SimpleAnnotation {}

  @SimpleAnnotation class SimplyAnnotated {}
  @SimpleAnnotation class AlsoSimplyAnnotated {}

  enum SimpleEnum {
    BLAH, FOO
  }

  @interface Outer {
    SimpleEnum value();
  }

  @Outer(BLAH) static class TestClassBlah {}
  @Outer(BLAH) static class TestClassBlah2 {}
  @Outer(FOO) static class TestClassFoo {}

  @interface DefaultingOuter {
    SimpleEnum value() default SimpleEnum.BLAH;
  }

  @DefaultingOuter class TestWithDefaultingOuterDefault {}
  @DefaultingOuter(BLAH) class TestWithDefaultingOuterBlah {}
  @DefaultingOuter(FOO) class TestWithDefaultingOuterFoo {}

  @interface AnnotatedOuter {
    DefaultingOuter value();
  }

  @AnnotatedOuter(@DefaultingOuter) class TestDefaultNestedAnnotated {}
  @AnnotatedOuter(@DefaultingOuter(BLAH)) class TestBlahNestedAnnotated {}
  @AnnotatedOuter(@DefaultingOuter(FOO)) class TestFooNestedAnnotated {}

  @interface OuterWithValueArray {
    DefaultingOuter[] value() default {};
  }

  @OuterWithValueArray class TestValueArrayWithDefault {}
  @OuterWithValueArray({}) class TestValueArrayWithEmpty {}

  @OuterWithValueArray({@DefaultingOuter}) class TestValueArrayWithOneDefault {}
  @OuterWithValueArray(@DefaultingOuter(BLAH)) class TestValueArrayWithOneBlah {}
  @OuterWithValueArray(@DefaultingOuter(FOO)) class TestValueArrayWithOneFoo {}

  @OuterWithValueArray({@DefaultingOuter(FOO), @DefaultingOuter})
  class TestValueArrayWithFooAndDefaultBlah {}
  @OuterWithValueArray({@DefaultingOuter(FOO), @DefaultingOuter(BLAH)})
  class TestValueArrayWithFooBlah {}
  @OuterWithValueArray({@DefaultingOuter(FOO), @DefaultingOuter(BLAH)})
  class TestValueArrayWithFooBlah2 {} // Different instances than on TestValueArrayWithFooBlah.
  @OuterWithValueArray({@DefaultingOuter(BLAH), @DefaultingOuter(FOO)})
  class TestValueArrayWithBlahFoo {}

  @Test public void testEquivalences() {
    EquivalenceTester<AnnotationMirror> tester =
        EquivalenceTester.of(AnnotationMirrors.equivalence());

    tester.addEquivalenceGroup(
        annotationOn(SimplyAnnotated.class),
        annotationOn(AlsoSimplyAnnotated.class));

    tester.addEquivalenceGroup(
        annotationOn(TestClassBlah.class),
        annotationOn(TestClassBlah2.class));

    tester.addEquivalenceGroup(
        annotationOn(TestClassFoo.class));

    tester.addEquivalenceGroup(
        annotationOn(TestWithDefaultingOuterDefault.class),
        annotationOn(TestWithDefaultingOuterBlah.class));

    tester.addEquivalenceGroup(
        annotationOn(TestWithDefaultingOuterFoo.class));

    tester.addEquivalenceGroup(
        annotationOn(TestDefaultNestedAnnotated.class),
        annotationOn(TestBlahNestedAnnotated.class));

    tester.addEquivalenceGroup(
        annotationOn(TestFooNestedAnnotated.class));

    tester.addEquivalenceGroup(
        annotationOn(TestValueArrayWithDefault.class),
        annotationOn(TestValueArrayWithEmpty.class));

    tester.addEquivalenceGroup(
        annotationOn(TestValueArrayWithOneDefault.class),
        annotationOn(TestValueArrayWithOneBlah.class));

    tester.addEquivalenceGroup(
        annotationOn(TestValueArrayWithOneFoo.class));

    tester.addEquivalenceGroup(
        annotationOn(TestValueArrayWithFooAndDefaultBlah.class),
        annotationOn(TestValueArrayWithFooBlah.class),
        annotationOn(TestValueArrayWithFooBlah2.class));

    tester.addEquivalenceGroup(
        annotationOn(TestValueArrayWithBlahFoo.class));

    tester.test();
  }

  @interface Stringy {
    String value() default "default";
  }

  @Stringy class StringyUnset {}
  @Stringy("foo") class StringySet {}

  @Test public void testGetDefaultValuesUnset() {
    assertThat(annotationOn(StringyUnset.class).getElementValues()).isEmpty();
    Iterable<AnnotationValue> values = AnnotationMirrors.getAnnotationValuesWithDefaults(
        annotationOn(StringyUnset.class)).values();
    String value = getOnlyElement(values).accept(new SimpleAnnotationValueVisitor6<String, Void>() {
          @Override public String visitString(String value, Void ignored) {
            return value;
          }
        }, null);
    assertThat(value).isEqualTo("default");
  }

  @Test public void testGetDefaultValuesSet() {
    Iterable<AnnotationValue> values = AnnotationMirrors.getAnnotationValuesWithDefaults(
        annotationOn(StringySet.class)).values();
    String value = getOnlyElement(values).accept(new SimpleAnnotationValueVisitor6<String, Void>() {
          @Override public String visitString(String value, Void ignored) {
            return value;
          }
        }, null);
    assertThat(value).isEqualTo("foo");
  }

  @Test public void testGetValueEntry() {
    Map.Entry<ExecutableElement, AnnotationValue> elementValue =
        AnnotationMirrors.getAnnotationElementAndValue(
            annotationOn(TestClassBlah.class), "value");
    assertThat(elementValue.getKey().getSimpleName().toString()).isEqualTo("value");
    assertThat(elementValue.getValue().getValue()).isInstanceOf(VariableElement.class);
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(
        annotationOn(TestClassBlah.class), "value");
    assertThat(value.getValue()).isInstanceOf(VariableElement.class);
  }

  @Test public void testGetValueEntryFailure() {
    try {
      AnnotationMirrors.getAnnotationValue(annotationOn(TestClassBlah.class), "a");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage(
          "@com.google.auto.common.AnnotationMirrorsTest.Outer does not define an element a()");
      return;
    }
    fail("Should have thrown.");
  }

  private AnnotationMirror annotationOn(Class<?> clazz) {
    return getOnlyElement(elements.getTypeElement(clazz.getCanonicalName()).getAnnotationMirrors());
  }

}
