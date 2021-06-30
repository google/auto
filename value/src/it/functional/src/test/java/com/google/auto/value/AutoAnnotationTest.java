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
package com.google.auto.value;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.auto.value.annotations.Empty;
import com.google.auto.value.annotations.GwtArrays;
import com.google.auto.value.annotations.StringValues;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.SerializableTester;
import java.io.ObjectStreamClass;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author emcmanus@google.com (Éamonn McManus) */
@RunWith(JUnit4.class)
public class AutoAnnotationTest {
  @AutoAnnotation
  private static StringValues newStringValues(String[] value) {
    return new AutoAnnotation_AutoAnnotationTest_newStringValues(value);
  }

  @Empty
  @StringValues("oops")
  static class AnnotatedClass {}

  @Test
  public void testSimple() {
    StringValues expectedStringValues = AnnotatedClass.class.getAnnotation(StringValues.class);
    StringValues actualStringValues = newStringValues(new String[] {"oops"});
    StringValues otherStringValues = newStringValues(new String[] {});
    new EqualsTester()
        .addEqualityGroup(expectedStringValues, actualStringValues)
        .addEqualityGroup(otherStringValues)
        .testEquals();
  }

  @Test
  public void testEqualsParameterAnnotation() throws ReflectiveOperationException {
    assume()
        .that(Double.parseDouble(StandardSystemProperty.JAVA_SPECIFICATION_VERSION.value()))
        .isAtLeast(8.0);
    Class<? extends Annotation> jspecifyNullable;
    try {
      // We write this using .concat in order to hide it from rewriting rules.
      jspecifyNullable =
          Class.forName("org".concat(".jspecify.nullness.Nullable")).asSubclass(Annotation.class);
    } catch (ClassNotFoundException e) {
      throw new AssumptionViolatedException("No JSpecify @Nullable available", e);
    }
    @SuppressWarnings("GetClassOnAnnotation") // yes, I really want the implementation class
    Class<? extends StringValues> autoAnnotationImpl = newStringValues(new String[0]).getClass();
    Method equals = autoAnnotationImpl.getDeclaredMethod("equals", Object.class);
    // The remaining faffing around with reflection is there because we have a Google-internal test
    // that runs this code with -source 7 -target 7. We're really just doing this:
    //   assertThat(equals.getAnnotatedParameterTypes()[0].isAnnotationPresent(jspecifyNullable))
    //      .isTrue();
    Method getAnnotatedParameterTypes = Method.class.getMethod("getAnnotatedParameterTypes");
    Object[] annotatedParameterTypes = (Object[]) getAnnotatedParameterTypes.invoke(equals);
    Method isAnnotationPresent =
        annotatedParameterTypes[0].getClass().getMethod("isAnnotationPresent", Class.class);
    assertThat(isAnnotationPresent.invoke(annotatedParameterTypes[0], jspecifyNullable))
        .isEqualTo(true);
  }

  @Test
  public void testArraysAreCloned() {
    String[] array = {"Jekyll"};
    StringValues stringValues = newStringValues(array);
    array[0] = "Hyde";
    assertThat(stringValues.value()).asList().containsExactly("Jekyll");
    stringValues.value()[0] = "Hyde";
    assertThat(stringValues.value()[0]).isEqualTo("Jekyll");
  }

  @Test
  public void testGwtArraysAreCloned() {
    String[] strings = {"Jekyll"};
    int[] ints = {2, 3, 5};
    GwtArrays arrays = newGwtArrays(strings, ints);
    assertThat(arrays.strings()).asList().containsExactly("Jekyll");
    assertThat(arrays.ints()).asList().containsExactly(2, 3, 5).inOrder();
    strings[0] = "Hyde";
    ints[0] = -1;
    assertThat(arrays.strings()).asList().containsExactly("Jekyll");
    assertThat(arrays.ints()).asList().containsExactly(2, 3, 5).inOrder();
  }

  @AutoAnnotation
  private static GwtArrays newGwtArrays(String[] strings, int[] ints) {
    return new AutoAnnotation_AutoAnnotationTest_newGwtArrays(strings, ints);
  }

  @AutoAnnotation
  private static StringValues newStringValuesVarArgs(String... value) {
    return new AutoAnnotation_AutoAnnotationTest_newStringValuesVarArgs(value);
  }

  @Test
  public void testSimpleVarArgs() {
    StringValues expectedStringValues = AnnotatedClass.class.getAnnotation(StringValues.class);
    StringValues actualStringValues = newStringValuesVarArgs("oops");
    StringValues otherStringValues = newStringValuesVarArgs(new String[] {});
    new EqualsTester()
        .addEqualityGroup(expectedStringValues, actualStringValues)
        .addEqualityGroup(otherStringValues)
        .testEquals();
  }

  @AutoAnnotation
  private static Empty newEmpty() {
    return new AutoAnnotation_AutoAnnotationTest_newEmpty();
  }

  @Test
  public void testEmpty() {
    Empty expectedEmpty = AnnotatedClass.class.getAnnotation(Empty.class);
    Empty actualEmpty = newEmpty();
    new EqualsTester().addEqualityGroup(expectedEmpty, actualEmpty).testEquals();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface Everything {
    byte aByte();

    short aShort();

    int anInt();

    long aLong();

    float aFloat();

    double aDouble();

    char aChar();

    boolean aBoolean();

    String aString();

    RetentionPolicy anEnum();

    StringValues anAnnotation();

    byte[] bytes();

    short[] shorts();

    int[] ints();

    long[] longs();

    float[] floats();

    double[] doubles();

    char[] chars();

    boolean[] booleans();

    String[] strings();

    RetentionPolicy[] enums();

    StringValues[] annotations();
  }

  @AutoAnnotation
  static Everything newEverything(
      byte aByte,
      short aShort,
      int anInt,
      long aLong,
      float aFloat,
      double aDouble,
      char aChar,
      boolean aBoolean,
      String aString,
      RetentionPolicy anEnum,
      StringValues anAnnotation,
      byte[] bytes,
      short[] shorts,
      int[] ints,
      long[] longs,
      float[] floats,
      double[] doubles,
      char[] chars,
      boolean[] booleans,
      String[] strings,
      RetentionPolicy[] enums,
      StringValues[] annotations) {
    return new AutoAnnotation_AutoAnnotationTest_newEverything(
        aByte,
        aShort,
        anInt,
        aLong,
        aFloat,
        aDouble,
        aChar,
        aBoolean,
        aString,
        anEnum,
        anAnnotation,
        bytes,
        shorts,
        ints,
        longs,
        floats,
        doubles,
        chars,
        booleans,
        strings,
        enums,
        annotations);
  }

  @AutoAnnotation
  static Everything newEverythingCollections(
      byte aByte,
      short aShort,
      int anInt,
      long aLong,
      float aFloat,
      double aDouble,
      char aChar,
      boolean aBoolean,
      String aString,
      RetentionPolicy anEnum,
      StringValues anAnnotation,
      Collection<Byte> bytes,
      List<Short> shorts,
      ArrayList<Integer> ints,
      Set<Long> longs,
      SortedSet<Float> floats,
      TreeSet<Double> doubles,
      LinkedHashSet<Character> chars,
      ImmutableCollection<Boolean> booleans,
      ImmutableList<String> strings,
      ImmutableSet<RetentionPolicy> enums,
      Set<StringValues> annotations) {
    return new AutoAnnotation_AutoAnnotationTest_newEverythingCollections(
        aByte,
        aShort,
        anInt,
        aLong,
        aFloat,
        aDouble,
        aChar,
        aBoolean,
        aString,
        anEnum,
        anAnnotation,
        bytes,
        shorts,
        ints,
        longs,
        floats,
        doubles,
        chars,
        booleans,
        strings,
        enums,
        annotations);
  }

  @Everything(
      aByte = 1,
      aShort = 2,
      anInt = 3,
      aLong = -4,
      aFloat = Float.NaN,
      aDouble = Double.NaN,
      aChar = '#',
      aBoolean = true,
      aString = "maybe\nmaybe not\n",
      anEnum = RetentionPolicy.RUNTIME,
      anAnnotation = @StringValues("whatever"),
      bytes = {5, 6},
      shorts = {},
      ints = {7},
      longs = {8, 9},
      floats = {10, 11},
      doubles = {Double.NEGATIVE_INFINITY, -12.0, Double.POSITIVE_INFINITY},
      chars = {'?', '!', '\n'},
      booleans = {false, true, false},
      strings = {"ver", "vers", "vert", "verre", "vair"},
      enums = {RetentionPolicy.CLASS, RetentionPolicy.RUNTIME},
      annotations = {@StringValues({}), @StringValues({"foo", "bar"})})
  private static class AnnotatedWithEverything {}

  // Get an instance of @Everything via reflection on the class AnnotatedWithEverything,
  // fabricate an instance using newEverything that is supposed to be equal to it, and
  // fabricate another instance using newEverything that is supposed to be different.
  private static final Everything EVERYTHING_FROM_REFLECTION =
      AnnotatedWithEverything.class.getAnnotation(Everything.class);
  private static final Everything EVERYTHING_FROM_AUTO =
      newEverything(
          (byte) 1,
          (short) 2,
          3,
          -4,
          Float.NaN,
          Double.NaN,
          '#',
          true,
          "maybe\nmaybe not\n",
          RetentionPolicy.RUNTIME,
          newStringValues(new String[] {"whatever"}),
          new byte[] {5, 6},
          new short[] {},
          new int[] {7},
          new long[] {8, 9},
          new float[] {10, 11},
          new double[] {Double.NEGATIVE_INFINITY, -12.0, Double.POSITIVE_INFINITY},
          new char[] {'?', '!', '\n'},
          new boolean[] {false, true, false},
          new String[] {"ver", "vers", "vert", "verre", "vair"},
          new RetentionPolicy[] {RetentionPolicy.CLASS, RetentionPolicy.RUNTIME},
          new StringValues[] {
            newStringValues(new String[] {}), newStringValues(new String[] {"foo", "bar"}),
          });
  private static final Everything EVERYTHING_FROM_AUTO_COLLECTIONS =
      newEverythingCollections(
          (byte) 1,
          (short) 2,
          3,
          -4,
          Float.NaN,
          Double.NaN,
          '#',
          true,
          "maybe\nmaybe not\n",
          RetentionPolicy.RUNTIME,
          newStringValues(new String[] {"whatever"}),
          Arrays.asList((byte) 5, (byte) 6),
          Collections.<Short>emptyList(),
          new ArrayList<Integer>(Collections.singleton(7)),
          ImmutableSet.of(8L, 9L),
          ImmutableSortedSet.of(10f, 11f),
          new TreeSet<Double>(
              ImmutableList.of(Double.NEGATIVE_INFINITY, -12.0, Double.POSITIVE_INFINITY)),
          new LinkedHashSet<Character>(ImmutableList.of('?', '!', '\n')),
          ImmutableList.of(false, true, false),
          ImmutableList.of("ver", "vers", "vert", "verre", "vair"),
          ImmutableSet.of(RetentionPolicy.CLASS, RetentionPolicy.RUNTIME),
          ImmutableSet.of(
              newStringValues(new String[] {}), newStringValues(new String[] {"foo", "bar"})));
  private static final Everything EVERYTHING_ELSE_FROM_AUTO =
      newEverything(
          (byte) 0,
          (short) 0,
          0,
          0,
          0,
          0,
          '0',
          false,
          "",
          RetentionPolicy.SOURCE,
          newStringValues(new String[] {""}),
          new byte[0],
          new short[0],
          new int[0],
          new long[0],
          new float[0],
          new double[0],
          new char[0],
          new boolean[0],
          new String[0],
          new RetentionPolicy[0],
          new StringValues[0]);
  private static final Everything EVERYTHING_ELSE_FROM_AUTO_COLLECTIONS =
      newEverythingCollections(
          (byte) 0,
          (short) 0,
          0,
          0,
          0,
          0,
          '0',
          false,
          "",
          RetentionPolicy.SOURCE,
          newStringValues(new String[] {""}),
          ImmutableList.<Byte>of(),
          Collections.<Short>emptyList(),
          new ArrayList<Integer>(),
          Collections.<Long>emptySet(),
          ImmutableSortedSet.<Float>of(),
          new TreeSet<Double>(),
          new LinkedHashSet<Character>(),
          ImmutableSet.<Boolean>of(),
          ImmutableList.<String>of(),
          ImmutableSet.<RetentionPolicy>of(),
          Collections.<StringValues>emptySet());

  @Test
  public void testEqualsAndHashCode() {
    new EqualsTester()
        .addEqualityGroup(
            EVERYTHING_FROM_REFLECTION, EVERYTHING_FROM_AUTO, EVERYTHING_FROM_AUTO_COLLECTIONS)
        .addEqualityGroup(EVERYTHING_ELSE_FROM_AUTO, EVERYTHING_ELSE_FROM_AUTO_COLLECTIONS)
        .testEquals();
  }

  @Test
  public void testSerialization() {
    Annotation[] instances = {EVERYTHING_FROM_AUTO, EVERYTHING_FROM_AUTO_COLLECTIONS};
    for (Annotation instance : instances) {
      SerializableTester.reserializeAndAssert(instance);
    }
  }

  @Test
  @SuppressWarnings("GetClassOnAnnotation") // yes, we really do want the implementation classes
  public void testSerialVersionUid() {
    Class<? extends Everything> everythingImpl = EVERYTHING_FROM_AUTO.getClass();
    Class<? extends Everything> everythingFromCollectionsImpl =
        EVERYTHING_FROM_AUTO_COLLECTIONS.getClass();
    assertThat(everythingImpl).isNotEqualTo(everythingFromCollectionsImpl);
    long everythingUid = ObjectStreamClass.lookup(everythingImpl).getSerialVersionUID();
    long everythingFromCollectionsUid =
        ObjectStreamClass.lookup(everythingFromCollectionsImpl).getSerialVersionUID();
    // Two different implementations of the same annotation with the same members being provided
    // (not defaulted) should have the same serialVersionUID. They won't be serial-compatible, of
    // course, because their classes are different. So we're really just checking that the
    // serialVersionUID depends only on the names and types of those members.
    assertThat(everythingFromCollectionsUid).isEqualTo(everythingUid);
    Class<? extends StringValues> stringValuesImpl = newStringValues(new String[0]).getClass();
    long stringValuesUid = ObjectStreamClass.lookup(stringValuesImpl).getSerialVersionUID();
    // The previous assertion would be vacuously true if every implementation had the same
    // serialVersionUID, so check that that's not true.
    assertThat(stringValuesUid).isNotEqualTo(everythingUid);
  }

  public static class IntList extends ArrayList<Integer> {
    private static final long serialVersionUID = 1L;

    IntList(Collection<Integer> c) {
      super(c);
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface IntArray {
    int[] ints();
  }

  @IntArray(ints = {1, 2, 3})
  private static class AnnotatedWithIntArray {}

  @AutoAnnotation
  static IntArray newIntArray(IntList ints) {
    return new AutoAnnotation_AutoAnnotationTest_newIntArray(ints);
  }

  /**
   * Test that we can represent a primitive array member with a parameter whose type is a collection
   * of the corresponding wrapper type, even if the wrapper type is not explicitly a type parameter.
   * Specifically, if the member is an {@code int[]} then obviously we can represent it as a {@code
   * List<Integer>}, but here we test that we can also represent it as an {@code IntList}, which is
   * only a {@code List<Integer>} by virtue of inheritance. This is a separate test rather than just
   * putting an {@code IntList} parameter into {@link #newEverythingCollections} because we want to
   * check that we are still able to detect the primitive wrapper type even though it's hidden in
   * this way. We need to generate a helper method for every primitive wrapper.
   */
  @Test
  public void testDerivedPrimitiveCollection() {
    IntList intList = new IntList(ImmutableList.of(1, 2, 3));
    IntArray actual = newIntArray(intList);
    IntArray expected = AnnotatedWithIntArray.class.getAnnotation(IntArray.class);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testToString() {
    String expected =
        "@com.google.auto.value.AutoAnnotationTest.Everything("
            + "aByte=1, aShort=2, anInt=3, aLong=-4, aFloat=NaN, aDouble=NaN, aChar='#', "
            + "aBoolean=true, aString=\"maybe\\nmaybe not\\n\", anEnum=RUNTIME, "
            + "anAnnotation=@com.google.auto.value.annotations.StringValues([\"whatever\"]), "
            + "bytes=[5, 6], shorts=[], ints=[7], longs=[8, 9], floats=[10.0, 11.0], "
            + "doubles=[-Infinity, -12.0, Infinity], "
            + "chars=['?', '!', '\\n'], "
            + "booleans=[false, true, false], "
            + "strings=[\"ver\", \"vers\", \"vert\", \"verre\", \"vair\"], "
            + "enums=[CLASS, RUNTIME], "
            + "annotations=["
            + "@com.google.auto.value.annotations.StringValues([]), "
            + "@com.google.auto.value.annotations.StringValues([\"foo\", \"bar\"])"
            + "]"
            + ")";
    assertThat(EVERYTHING_FROM_AUTO.toString()).isEqualTo(expected);
    assertThat(EVERYTHING_FROM_AUTO_COLLECTIONS.toString()).isEqualTo(expected);
  }

  @Test
  public void testStringQuoting() {
    StringValues instance =
        newStringValues(
            new String[] {
              "", "\r\n", "hello, world", "Éamonn", "\007\uffef",
            });
    String expected =
        "@com.google.auto.value.annotations.StringValues("
            + "[\"\", \"\\r\\n\", \"hello, world\", \"Éamonn\", \"\\007\\uffef\"])";
    assertThat(instance.toString()).isEqualTo(expected);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface AnnotationsAnnotation {
    Class<? extends Annotation>[] value();
  }

  @AnnotationsAnnotation(AnnotationsAnnotation.class)
  static class AnnotatedWithAnnotationsAnnotation {}

  @AutoAnnotation
  static AnnotationsAnnotation newAnnotationsAnnotation(List<Class<? extends Annotation>> value) {
    return new AutoAnnotation_AutoAnnotationTest_newAnnotationsAnnotation(value);
  }

  @Test
  public void testGenericArray() {
    AnnotationsAnnotation generated =
        newAnnotationsAnnotation(
            ImmutableList.<Class<? extends Annotation>>of(AnnotationsAnnotation.class));
    AnnotationsAnnotation fromReflect =
        AnnotatedWithAnnotationsAnnotation.class.getAnnotation(AnnotationsAnnotation.class);
    assertThat(generated).isEqualTo(fromReflect);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface ClassesAnnotation {
    Class<?>[] value();
  }

  @ClassesAnnotation(AnnotationsAnnotation.class)
  static class AnnotatedWithClassesAnnotation {}

  @AutoAnnotation
  static ClassesAnnotation newClassesAnnotation(List<Class<?>> value) {
    return new AutoAnnotation_AutoAnnotationTest_newClassesAnnotation(value);
  }

  @Test
  public void testWildcardArray() {
    ClassesAnnotation generated =
        newClassesAnnotation(Arrays.<Class<?>>asList(AnnotationsAnnotation.class));
    ClassesAnnotation fromReflect =
        AnnotatedWithClassesAnnotation.class.getAnnotation(ClassesAnnotation.class);
    assertThat(generated).isEqualTo(fromReflect);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface IntegersAnnotation {
    int one() default Integer.MAX_VALUE;

    int two() default Integer.MAX_VALUE;

    int three();
  }

  @IntegersAnnotation(three = 23)
  static class AnnotatedWithIntegersAnnotation {}

  @AutoAnnotation
  static IntegersAnnotation newIntegersAnnotation(int three) {
    return new AutoAnnotation_AutoAnnotationTest_newIntegersAnnotation(three);
  }

  @Test
  public void testConstantOverflowInHashCode() {
    IntegersAnnotation generated = newIntegersAnnotation(23);
    IntegersAnnotation fromReflect =
        AnnotatedWithIntegersAnnotation.class.getAnnotation(IntegersAnnotation.class);
    new EqualsTester().addEqualityGroup(generated, fromReflect).testEquals();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface EverythingWithDefaults {
    byte aByte() default 5;

    short aShort() default 17;

    int anInt() default 23;

    long aLong() default 1729;

    float aFloat() default 5;

    double aDouble() default 17;

    char aChar() default 'x';

    boolean aBoolean() default true;

    String aString() default "whatever";

    RetentionPolicy anEnum() default RetentionPolicy.CLASS;
    // We don't yet support defaulting annotation values.
    // StringValues anAnnotation() default @StringValues({"foo", "bar"});
    byte[] bytes() default {1, 2};

    short[] shorts() default {3, 4};

    int[] ints() default {5, 6};

    long[] longs() default {7, 8};

    float[] floats() default {9, 10};

    double[] doubles() default {11, 12};

    char[] chars() default {'D', 'E'};

    boolean[] booleans() default {true, false};

    String[] strings() default {"vrai", "faux"};

    RetentionPolicy[] enums() default {RetentionPolicy.SOURCE, RetentionPolicy.CLASS};
    // We don't yet support defaulting annotation values.
    // StringValues[] annotations() default {
    //   @StringValues({"foo", "bar"}), @StringValues({"baz", "buh"})
    // };
  }

  @EverythingWithDefaults
  static class AnnotatedWithEverythingWithDefaults {}

  @AutoAnnotation
  static EverythingWithDefaults newEverythingWithDefaults() {
    return new AutoAnnotation_AutoAnnotationTest_newEverythingWithDefaults();
  }

  @Test
  public void testDefaultedValues() {
    EverythingWithDefaults generated = newEverythingWithDefaults();
    EverythingWithDefaults fromReflect =
        AnnotatedWithEverythingWithDefaults.class.getAnnotation(EverythingWithDefaults.class);
    new EqualsTester().addEqualityGroup(generated, fromReflect).testEquals();
  }
}
