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
package com.google.auto.common;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Correspondence;
import com.google.testing.compile.CompilationRule;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link AnnotationValues}. */
@RunWith(JUnit4.class)
public final class AnnotationValuesTest {

  private @interface InsideAnnotation {
    int value();
  }

  private static class GenericClass<T> {}

  private static class InsideClassA {}

  private static class InsideClassB {}

  private @interface MultiValueAnnotation {
    Class<InsideClassA> classValue();

    Class<?>[] classValues();

    Class<?> genericClassValue();

    InsideAnnotation insideAnnotationValue();

    InsideAnnotation[] insideAnnotationValues();

    String stringValue();

    String[] stringValues();

    Foo enumValue();

    Foo[] enumValues();

    int intValue();

    int[] intValues();

    long longValue();

    long[] longValues();

    byte byteValue();

    byte[] byteValues();

    short shortValue();

    short[] shortValues();

    float floatValue();

    float[] floatValues();

    double doubleValue();

    double[] doubleValues();

    boolean booleanValue();

    boolean[] booleanValues();

    char charValue();

    char[] charValues();
  }

  private enum Foo {
    BAR,
    BAZ,
    BAH;
  }

  @MultiValueAnnotation(
      classValue = InsideClassA.class,
      classValues = {InsideClassA.class, InsideClassB.class},
      genericClassValue = GenericClass.class,
      insideAnnotationValue = @InsideAnnotation(19),
      insideAnnotationValues = {@InsideAnnotation(20), @InsideAnnotation(21)},
      stringValue = "hello",
      stringValues = {"it's", "me"},
      enumValue = Foo.BAR,
      enumValues = {Foo.BAZ, Foo.BAH},
      intValue = 5,
      intValues = {1, 2},
      longValue = 6L,
      longValues = {3L, 4L},
      byteValue = (byte) 7,
      byteValues = {(byte) 8, (byte) 9},
      shortValue = (short) 10,
      shortValues = {(short) 11, (short) 12},
      floatValue = 13F,
      floatValues = {14F, 15F},
      doubleValue = 16D,
      doubleValues = {17D, 18D},
      booleanValue = true,
      booleanValues = {true, false},
      charValue = 'a',
      charValues = {'b', 'c'})
  private static class AnnotatedClass {}

  @Rule public final CompilationRule compilation = new CompilationRule();

  private Elements elements;
  private Types types;
  private AnnotationMirror annotationMirror;

  @Before
  public void setUp() {
    elements = compilation.getElements();
    types = compilation.getTypes();
    TypeElement annotatedClass = getTypeElement(AnnotatedClass.class);
    annotationMirror =
        MoreElements.getAnnotationMirror(annotatedClass, MultiValueAnnotation.class).get();
  }

  @Test
  public void getTypeMirror() {
    TypeElement insideClassA = getTypeElement(InsideClassA.class);
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "classValue");
    assertThat(AnnotationValues.getTypeMirror(value).asElement()).isEqualTo(insideClassA);
  }

  @Test
  public void getTypeMirrorGenericClass() {
    TypeElement genericClass = getTypeElement(GenericClass.class);
    AnnotationValue gvalue =
        AnnotationMirrors.getAnnotationValue(annotationMirror, "genericClassValue");
    assertThat(AnnotationValues.getTypeMirror(gvalue).asElement()).isEqualTo(genericClass);
  }

  @Test
  public void getTypeMirrors() {
    TypeMirror insideClassA = getTypeElement(InsideClassA.class).asType();
    TypeMirror insideClassB = getTypeElement(InsideClassB.class).asType();
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "classValues");
    ImmutableList<DeclaredType> valueElements = AnnotationValues.getTypeMirrors(value);
    assertThat(valueElements)
        .comparingElementsUsing(Correspondence.from(types::isSameType, "has Same Type"))
        .containsExactly(insideClassA, insideClassB)
        .inOrder();
  }

  @Test
  public void getAnnotationMirror() {
    TypeElement insideAnnotation = getTypeElement(InsideAnnotation.class);
    AnnotationValue value =
        AnnotationMirrors.getAnnotationValue(annotationMirror, "insideAnnotationValue");
    AnnotationMirror annotationMirror = AnnotationValues.getAnnotationMirror(value);
    assertThat(annotationMirror.getAnnotationType().asElement()).isEqualTo(insideAnnotation);
    assertThat(AnnotationMirrors.getAnnotationValue(annotationMirror, "value").getValue())
        .isEqualTo(19);
  }

  @Test
  public void getAnnotationMirrors() {
    TypeElement insideAnnotation = getTypeElement(InsideAnnotation.class);
    AnnotationValue value =
        AnnotationMirrors.getAnnotationValue(annotationMirror, "insideAnnotationValues");
    ImmutableList<AnnotationMirror> annotationMirrors =
        AnnotationValues.getAnnotationMirrors(value);
    ImmutableList<Element> valueElements =
        annotationMirrors.stream()
            .map(AnnotationMirror::getAnnotationType)
            .map(DeclaredType::asElement)
            .collect(toImmutableList());
    assertThat(valueElements).containsExactly(insideAnnotation, insideAnnotation);
    ImmutableList<Object> valuesStoredInAnnotation =
        annotationMirrors.stream()
            .map(
                annotationMirror ->
                    AnnotationMirrors.getAnnotationValue(annotationMirror, "value").getValue())
            .collect(toImmutableList());
    assertThat(valuesStoredInAnnotation).containsExactly(20, 21).inOrder();
  }

  @Test
  public void getString() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "stringValue");
    assertThat(AnnotationValues.getString(value)).isEqualTo("hello");
  }

  @Test
  public void getStrings() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "stringValues");
    assertThat(AnnotationValues.getStrings(value)).containsExactly("it's", "me").inOrder();
  }

  @Test
  public void getEnum() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "enumValue");
    assertThat(AnnotationValues.getEnum(value)).isEqualTo(value.getValue());
  }

  @Test
  public void getEnums() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "enumValues");
    assertThat(getEnumNames(AnnotationValues.getEnums(value)))
        .containsExactly(Foo.BAZ.name(), Foo.BAH.name())
        .inOrder();
  }

  @Test
  public void getAnnotationValues() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "intValues");
    ImmutableList<AnnotationValue> values = AnnotationValues.getAnnotationValues(value);
    assertThat(values)
        .comparingElementsUsing(Correspondence.transforming(AnnotationValue::getValue, "has value"))
        .containsExactly(1, 2)
        .inOrder();
  }

  @Test
  public void getInt() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "intValue");
    assertThat(AnnotationValues.getInt(value)).isEqualTo(5);
  }

  @Test
  public void getInts() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "intValues");
    assertThat(AnnotationValues.getInts(value)).containsExactly(1, 2).inOrder();
  }

  @Test
  public void getLong() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "longValue");
    assertThat(AnnotationValues.getLong(value)).isEqualTo(6L);
  }

  @Test
  public void getLongs() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "longValues");
    assertThat(AnnotationValues.getLongs(value)).containsExactly(3L, 4L).inOrder();
  }

  @Test
  public void getByte() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "byteValue");
    assertThat(AnnotationValues.getByte(value)).isEqualTo((byte) 7);
  }

  @Test
  public void getBytes() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "byteValues");
    assertThat(AnnotationValues.getBytes(value)).containsExactly((byte) 8, (byte) 9).inOrder();
  }

  @Test
  public void getShort() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "shortValue");
    assertThat(AnnotationValues.getShort(value)).isEqualTo((short) 10);
  }

  @Test
  public void getShorts() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "shortValues");
    assertThat(AnnotationValues.getShorts(value)).containsExactly((short) 11, (short) 12).inOrder();
  }

  @Test
  public void getFloat() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "floatValue");
    assertThat(AnnotationValues.getFloat(value)).isEqualTo(13F);
  }

  @Test
  public void getFloats() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "floatValues");
    assertThat(AnnotationValues.getFloats(value)).containsExactly(14F, 15F).inOrder();
  }

  @Test
  public void getDouble() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "doubleValue");
    assertThat(AnnotationValues.getDouble(value)).isEqualTo(16D);
  }

  @Test
  public void getDoubles() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "doubleValues");
    assertThat(AnnotationValues.getDoubles(value)).containsExactly(17D, 18D).inOrder();
  }

  @Test
  public void getBoolean() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "booleanValue");
    assertThat(AnnotationValues.getBoolean(value)).isTrue();
  }

  @Test
  public void getBooleans() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "booleanValues");
    assertThat(AnnotationValues.getBooleans(value)).containsExactly(true, false).inOrder();
  }

  @Test
  public void getChar() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "charValue");
    assertThat(AnnotationValues.getChar(value)).isEqualTo('a');
  }

  @Test
  public void getChars() {
    AnnotationValue value = AnnotationMirrors.getAnnotationValue(annotationMirror, "charValues");
    assertThat(AnnotationValues.getChars(value)).containsExactly('b', 'c').inOrder();
  }

  @Test
  public void toSourceString() {
    ImmutableMap<String, String> inputs =
        ImmutableMap.<String, String>builder()
            .put("classValue", "com.google.auto.common.AnnotationValuesTest.InsideClassA.class")
            .put(
                "classValues",
                "{com.google.auto.common.AnnotationValuesTest.InsideClassA.class,"
                    + " com.google.auto.common.AnnotationValuesTest.InsideClassB.class}")
            .put(
                "genericClassValue",
                "com.google.auto.common.AnnotationValuesTest.GenericClass.class")
            .put(
                "insideAnnotationValue",
                "@com.google.auto.common.AnnotationValuesTest.InsideAnnotation(19)")
            .put(
                "insideAnnotationValues",
                "{@com.google.auto.common.AnnotationValuesTest.InsideAnnotation(20),"
                    + " @com.google.auto.common.AnnotationValuesTest.InsideAnnotation(21)}")
            .put("stringValue", "\"hello\"")
            .put("stringValues", "{\"it\\'s\", \"me\"}")
            .put("enumValue", "com.google.auto.common.AnnotationValuesTest.Foo.BAR")
            .put(
                "enumValues",
                "{com.google.auto.common.AnnotationValuesTest.Foo.BAZ,"
                    + " com.google.auto.common.AnnotationValuesTest.Foo.BAH}")
            .put("intValue", "5")
            .put("intValues", "{1, 2}")
            .put("longValue", "6L")
            .put("longValues", "{3L, 4L}")
            .put("byteValue", "7")
            .put("byteValues", "{8, 9}")
            .put("shortValue", "10")
            .put("shortValues", "{11, 12}")
            .put("floatValue", "13.0F")
            .put("floatValues", "{14.0F, 15.0F}")
            .put("doubleValue", "16.0")
            .put("doubleValues", "{17.0, 18.0}")
            .put("booleanValue", "true")
            .put("booleanValues", "{true, false}")
            .put("charValue", "'a'")
            .put("charValues", "{'b', 'c'}")
            .build();
    inputs.forEach(
        (name, expected) ->
            assertThat(
                    AnnotationValues.toString(
                        AnnotationMirrors.getAnnotationValue(annotationMirror, name)))
                .isEqualTo(expected));
    assertThat(AnnotationMirrors.toString(annotationMirror))
        .isEqualTo(
            inputs.entrySet().stream()
                .map(e -> e.getKey() + " = " + e.getValue())
                .collect(
                    joining(
                        ", ",
                        "@com.google.auto.common.AnnotationValuesTest.MultiValueAnnotation(",
                        ")")));
  }

  private TypeElement getTypeElement(Class<?> clazz) {
    return elements.getTypeElement(clazz.getCanonicalName());
  }

  private static ImmutableList<String> getEnumNames(ImmutableList<VariableElement> values) {
    return values.stream()
        .map(VariableElement::getSimpleName)
        .map(Name::toString)
        .collect(toImmutableList());
  }
}
