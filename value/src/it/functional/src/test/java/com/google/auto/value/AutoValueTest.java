/*
 * Copyright 2012 Google LLC
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
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Ordering;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.SerializableTester;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author emcmanus@google.com (Éamonn McManus) */
@RunWith(JUnit4.class)
@SuppressWarnings({"AutoValueImmutableFields", "AutoValueFinalMethods", "TypeNameShadowing"})
public class AutoValueTest {
  private static boolean omitIdentifiers;

  @BeforeClass
  public static void initOmitIdentifiers() {
    omitIdentifiers = System.getProperty("OmitIdentifiers") != null;
  }

  @AutoValue
  abstract static class Simple {
    public abstract String publicString();

    protected abstract int protectedInt();

    abstract Map<String, Long> packageMap();

    public static Simple create(String s, int i, Map<String, Long> m) {
      return new AutoValue_AutoValueTest_Simple(s, i, m);
    }
  }

  @Test
  public void testSimple() throws Exception {
    Simple instance1a = Simple.create("example", 23, ImmutableMap.of("twenty-three", 23L));
    Simple instance1b = Simple.create("example", 23, ImmutableMap.of("twenty-three", 23L));
    Simple instance2 = Simple.create("", 0, ImmutableMap.<String, Long>of());
    assertEquals("example", instance1a.publicString());
    assertEquals(23, instance1a.protectedInt());
    assertEquals(ImmutableMap.of("twenty-three", 23L), instance1a.packageMap());
    MoreObjects.ToStringHelper toStringHelper = MoreObjects.toStringHelper(Simple.class);
    toStringHelper.add("publicString", "example");
    toStringHelper.add("protectedInt", 23);
    toStringHelper.add("packageMap", ImmutableMap.of("twenty-three", 23L));
    String expectedString =
        omitIdentifiers ? "{example, 23, {twenty-three=23}}" : toStringHelper.toString();
    assertThat(instance1a.toString()).isEqualTo(expectedString);
    new EqualsTester()
        .addEqualityGroup(instance1a, instance1b)
        .addEqualityGroup(instance2)
        .testEquals();
  }

  @AutoValue
  abstract static class Empty {
    public static Empty create() {
      return new AutoValue_AutoValueTest_Empty();
    }
  }

  @Test
  public void testEmpty() throws Exception {
    Empty instance = Empty.create();
    String expectedString = omitIdentifiers ? "{}" : "Empty{}";
    assertThat(instance.toString()).isEqualTo(expectedString);
    assertEquals(instance, instance);
    assertEquals(instance, Empty.create());
  }

  @AutoValue
  abstract static class SimpleWithGetters {
    abstract int getFoo();

    abstract boolean isBar();

    abstract boolean getOtherBar();

    abstract String getPackage(); // package is a reserved word

    abstract String getPackage0();

    abstract String getHTMLPage();

    static SimpleWithGetters create(
        int foo, boolean bar, boolean otherBar, String pkg, String pkg0, String htmlPage) {
      return new AutoValue_AutoValueTest_SimpleWithGetters(foo, bar, otherBar, pkg, pkg0, htmlPage);
    }
  }

  @Test
  public void testGetters() {
    SimpleWithGetters instance = SimpleWithGetters.create(23, true, false, "foo", "bar", "<html>");
    String expectedString =
        omitIdentifiers
            ? "{23, true, false, foo, bar, <html>}"
            : "SimpleWithGetters{"
                + "foo=23, bar=true, otherBar=false, package=foo, package0=bar, HTMLPage=<html>}";
    assertThat(instance.toString()).isEqualTo(expectedString);
  }

  @AutoValue
  abstract static class NotAllGetters {
    abstract int getFoo();

    abstract boolean bar();

    static NotAllGetters create(int foo, boolean bar) {
      return new AutoValue_AutoValueTest_NotAllGetters(foo, bar);
    }
  }

  @Test
  public void testNotGetters() {
    NotAllGetters instance = NotAllGetters.create(23, true);
    String expectedString = omitIdentifiers ? "{23, true}" : "NotAllGetters{getFoo=23, bar=true}";
    assertThat(instance.toString()).isEqualTo(expectedString);
  }

  @AutoValue
  abstract static class StrangeGetters {
    abstract int get1st();

    abstract int get_1st(); // by default we'll use _1st where identifiers are needed, so foil that.

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder set1st(int x);

      abstract Builder set_1st(int x);

      abstract StrangeGetters build();
    }

    static Builder builder() {
      return new AutoValue_AutoValueTest_StrangeGetters.Builder();
    }
  }

  @Test
  public void testStrangeGetters() {
    StrangeGetters instance = StrangeGetters.builder().set1st(17).set_1st(23).build();
    String expectedString = omitIdentifiers ? "{17, 23}" : "StrangeGetters{1st=17, _1st=23}";
    assertThat(instance.toString()).isEqualTo(expectedString);
  }

  @AutoValue
  abstract static class GettersAndConcreteNonGetters {
    abstract int getFoo();

    @SuppressWarnings("mutable")
    abstract byte[] getBytes();

    boolean hasNoBytes() {
      return getBytes().length == 0;
    }

    static GettersAndConcreteNonGetters create(int foo, byte[] bytes) {
      return new AutoValue_AutoValueTest_GettersAndConcreteNonGetters(foo, bytes);
    }
  }

  @Test
  public void testGettersAndConcreteNonGetters() {
    GettersAndConcreteNonGetters instance = GettersAndConcreteNonGetters.create(23, new byte[] {1});
    assertFalse(instance.hasNoBytes());
    String expectedString =
        omitIdentifiers ? "{23, [1]}" : "GettersAndConcreteNonGetters{foo=23, bytes=[1]}";
    assertThat(instance.toString()).isEqualTo(expectedString);
  }

  @AutoValue
  abstract static class ClassProperty {
    abstract Class<?> theClass();

    static ClassProperty create(Class<?> theClass) {
      return new AutoValue_AutoValueTest_ClassProperty(theClass);
    }
  }

  @Test
  public void testClassProperty() {
    ClassProperty instance = ClassProperty.create(Thread.class);
    assertThat(instance.theClass()).isEqualTo(Thread.class);

    try {
      ClassProperty.create(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @AutoValue
  abstract static class ClassPropertyWithBuilder {
    abstract Class<? extends Number> numberClass();

    static Builder builder() {
      return new AutoValue_AutoValueTest_ClassPropertyWithBuilder.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setNumberClass(Class<? extends Number> x);

      abstract ClassPropertyWithBuilder build();
    }
  }

  @Test
  public void testClassPropertyWithBuilder() {
    ClassPropertyWithBuilder instance =
        ClassPropertyWithBuilder.builder().setNumberClass(Integer.class).build();
    assertThat(instance.numberClass()).isEqualTo(Integer.class);

    try {
      ClassPropertyWithBuilder.builder().build();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      ClassPropertyWithBuilder.builder().setNumberClass(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @AutoValue
  public abstract static class Serialize implements Serializable {
    private static final long serialVersionUID = 1L;

    public abstract int integer();

    public abstract String string();

    public abstract BigInteger bigInteger();

    public static Serialize create(int integer, String string, BigInteger bigInteger) {
      return new AutoValue_AutoValueTest_Serialize(integer, string, bigInteger);
    }
  }

  @Test
  public void testSerialize() throws Exception {
    Serialize instance = Serialize.create(23, "23", BigInteger.valueOf(23));
    assertEquals(instance, SerializableTester.reserialize(instance));
  }

  @AutoValue
  public abstract static class SerializeWithVersionUID implements Serializable {
    private static final long serialVersionUID = 4294967297L;

    public abstract int integer();

    public abstract String string();

    public static SerializeWithVersionUID create(int integer, String string) {
      return new AutoValue_AutoValueTest_SerializeWithVersionUID(integer, string);
    }
  }

  @Test
  public void testSerializeWithVersionUID() throws Exception {
    SerializeWithVersionUID instance = SerializeWithVersionUID.create(23, "23");
    assertEquals(instance, SerializableTester.reserialize(instance));

    long serialVersionUID =
        ObjectStreamClass.lookup(AutoValue_AutoValueTest_SerializeWithVersionUID.class)
            .getSerialVersionUID();
    assertEquals(4294967297L, serialVersionUID);
  }

  @AutoValue
  abstract static class LongProperty {
    public abstract long longProperty();

    public static LongProperty create(long longProperty) {
      return new AutoValue_AutoValueTest_LongProperty(longProperty);
    }
  }

  @Test
  public void testLongHashCode() {
    long longValue = 0x1234567887654321L;
    LongProperty longProperty = LongProperty.create(longValue);
    assertEquals(singlePropertyHash(longValue), longProperty.hashCode());
  }

  @AutoValue
  abstract static class IntProperty {
    public abstract int intProperty();

    public static IntProperty create(int intProperty) {
      return new AutoValue_AutoValueTest_IntProperty(intProperty);
    }
  }

  @Test
  public void testIntHashCode() {
    int intValue = 0x12345678;
    IntProperty intProperty = IntProperty.create(intValue);
    assertEquals(singlePropertyHash(intValue), intProperty.hashCode());
  }

  @AutoValue
  abstract static class ShortProperty {
    public abstract short shortProperty();

    public static ShortProperty create(short shortProperty) {
      return new AutoValue_AutoValueTest_ShortProperty(shortProperty);
    }
  }

  @Test
  public void testShortHashCode() {
    short shortValue = 0x1234;
    ShortProperty shortProperty = ShortProperty.create(shortValue);
    assertEquals(singlePropertyHash(shortValue), shortProperty.hashCode());
  }

  @AutoValue
  abstract static class ByteProperty {
    public abstract byte byteProperty();

    public static ByteProperty create(byte byteProperty) {
      return new AutoValue_AutoValueTest_ByteProperty(byteProperty);
    }
  }

  @Test
  public void testByteHashCode() {
    byte byteValue = 123;
    ByteProperty byteProperty = ByteProperty.create(byteValue);
    assertEquals(singlePropertyHash(byteValue), byteProperty.hashCode());
  }

  @AutoValue
  abstract static class CharProperty {
    public abstract char charProperty();

    public static CharProperty create(char charProperty) {
      return new AutoValue_AutoValueTest_CharProperty(charProperty);
    }
  }

  @Test
  public void testCharHashCode() {
    char charValue = 123;
    CharProperty charProperty = CharProperty.create(charValue);
    assertEquals(singlePropertyHash(charValue), charProperty.hashCode());
  }

  @AutoValue
  abstract static class BooleanProperty {
    public abstract boolean booleanProperty();

    public static BooleanProperty create(boolean booleanProperty) {
      return new AutoValue_AutoValueTest_BooleanProperty(booleanProperty);
    }
  }

  @Test
  public void testBooleanHashCode() {
    for (boolean booleanValue : new boolean[] {false, true}) {
      BooleanProperty booleanProperty = BooleanProperty.create(booleanValue);
      assertEquals(singlePropertyHash(booleanValue), booleanProperty.hashCode());
    }
  }

  @AutoValue
  abstract static class FloatProperty {
    public abstract float floatProperty();

    public static FloatProperty create(float floatProperty) {
      return new AutoValue_AutoValueTest_FloatProperty(floatProperty);
    }
  }

  @Test
  public void testFloatHashCode() {
    float floatValue = 123456f;
    FloatProperty floatProperty = FloatProperty.create(floatValue);
    assertEquals(singlePropertyHash(floatValue), floatProperty.hashCode());
  }

  @AutoValue
  abstract static class DoubleProperty {
    public abstract double doubleProperty();

    public static DoubleProperty create(double doubleProperty) {
      return new AutoValue_AutoValueTest_DoubleProperty(doubleProperty);
    }
  }

  @Test
  public void testDoubleHashCode() {
    double doubleValue = 1234567890123456d;
    DoubleProperty doubleProperty = DoubleProperty.create(doubleValue);
    assertEquals(singlePropertyHash(doubleValue), doubleProperty.hashCode());
  }

  @Test
  public void testFloatingEquality() {
    FloatProperty floatZero = FloatProperty.create(0.0f);
    FloatProperty floatMinusZero = FloatProperty.create(-0.0f);
    FloatProperty floatNaN = FloatProperty.create(Float.NaN);
    DoubleProperty doubleZero = DoubleProperty.create(0.0);
    DoubleProperty doubleMinusZero = DoubleProperty.create(-0.0);
    DoubleProperty doubleNaN = DoubleProperty.create(Double.NaN);
    new EqualsTester()
        .addEqualityGroup(floatZero)
        .addEqualityGroup(floatMinusZero)
        .addEqualityGroup(floatNaN)
        .addEqualityGroup(doubleZero)
        .addEqualityGroup(doubleMinusZero)
        .addEqualityGroup(doubleNaN)
        .testEquals();
  }

  private static int singlePropertyHash(Object property) {
    return 1000003 ^ property.hashCode();
  }

  abstract static class Super {
    public abstract Object superObject();

    public abstract boolean superBoolean();
    // The above two are out of alphabetical order to test EclipseHack.
  }

  @AutoValue
  public abstract static class Sub extends Super {
    public abstract int subInt();

    public static Sub create(Object superObject, boolean superBoolean, int subInt) {
      return new AutoValue_AutoValueTest_Sub(superObject, superBoolean, subInt);
    }
  }

  // The @AutoValue class can inherit abstract methods from its superclass.
  @Test
  public void testSuperclass() throws Exception {
    Sub instance = Sub.create("blim", true, 1729);
    assertEquals("blim", instance.superObject());
    assertTrue(instance.superBoolean());
    assertEquals(1729, instance.subInt());
    assertEquals(instance, instance);
    assertEqualsNullIsFalse(instance);
  }

  abstract static class NonPublicSuper {
    abstract Object superObject();
  }

  // The properties in this subclass are not in alphabetical order, which enables us to test that
  // everything works correctly when Eclipse sorts them into the order
  // [superObject, subInt, subString], since it sorts per class.
  @AutoValue
  abstract static class NonPublicSub extends NonPublicSuper {
    abstract String subString();

    abstract int subInt();

    static NonPublicSub create(Object superObject, String subString, int subInt) {
      return new AutoValue_AutoValueTest_NonPublicSub(superObject, subString, subInt);
    }
  }

  @Test
  public void testNonPublicInheritedGetters() throws Exception {
    NonPublicSub instance = NonPublicSub.create("blim", "blam", 1729);
    assertEquals("blim", instance.superObject());
    assertEquals("blam", instance.subString());
    assertEquals(1729, instance.subInt());
    assertEquals(instance, instance);
    assertEqualsNullIsFalse(instance);
  }

  @SuppressWarnings("ObjectEqualsNull")
  private void assertEqualsNullIsFalse(Object instance) {
    assertFalse(instance.equals(null));
  }

  @AutoValue
  abstract static class NullableProperties {
    @Nullable
    abstract String nullableString();

    abstract int randomInt();

    static NullableProperties create(@Nullable String nullableString, int randomInt) {
      return new AutoValue_AutoValueTest_NullableProperties(nullableString, randomInt);
    }
  }

  @Test
  public void testNullablePropertiesCanBeNull() {
    NullableProperties instance = NullableProperties.create(null, 23);
    assertNull(instance.nullableString());
    assertThat(instance.randomInt()).isEqualTo(23);
    String expectedString =
        omitIdentifiers ? "{null, 23}" : "NullableProperties{nullableString=null, randomInt=23}";
    assertThat(instance.toString()).isEqualTo(expectedString);
  }

  @AutoAnnotation
  static Nullable nullable() {
    return new AutoAnnotation_AutoValueTest_nullable();
  }

  @Test
  public void testNullablePropertyConstructorParameterIsNullable() throws NoSuchMethodException {
    Constructor<?> constructor =
        AutoValue_AutoValueTest_NullableProperties.class.getDeclaredConstructor(
            String.class, int.class);
    assertThat(constructor.getParameterAnnotations()[0]).asList().contains(nullable());
  }

  @AutoValue
  abstract static class AlternativeNullableProperties {
    @interface Nullable {}

    @AlternativeNullableProperties.Nullable
    abstract String nullableString();

    abstract int randomInt();

    static AlternativeNullableProperties create(@Nullable String nullableString, int randomInt) {
      return new AutoValue_AutoValueTest_AlternativeNullableProperties(nullableString, randomInt);
    }
  }

  @Test
  public void testNullableCanBeFromElsewhere() throws Exception {
    AlternativeNullableProperties instance = AlternativeNullableProperties.create(null, 23);
    assertNull(instance.nullableString());
    assertThat(instance.randomInt()).isEqualTo(23);
    String expectedString =
        omitIdentifiers
            ? "{null, 23}"
            : "AlternativeNullableProperties{nullableString=null, randomInt=23}";
    assertThat(instance.toString()).isEqualTo(expectedString);
  }

  @AutoValue
  abstract static class NonNullableProperties {
    abstract String nonNullableString();

    abstract int randomInt();

    static NonNullableProperties create(String nonNullableString, int randomInt) {
      return new AutoValue_AutoValueTest_NonNullableProperties(nonNullableString, randomInt);
    }
  }

  @Test
  public void testNonNullablePropertiesCannotBeNull() throws Exception {
    try {
      NonNullableProperties.create(null, 23);
      fail("Object creation succeeded but should not have");
    } catch (NullPointerException expected) {
    }
    NonNullableProperties instance = NonNullableProperties.create("nonnull", 23);
    assertEquals("nonnull", instance.nonNullableString());
    assertEquals(23, instance.randomInt());
  }

  @AutoValue
  abstract static class NullableListProperties {
    @Nullable
    abstract ImmutableList<String> nullableStringList();

    static NullableListProperties create(@Nullable ImmutableList<String> nullableStringList) {
      return new AutoValue_AutoValueTest_NullableListProperties(nullableStringList);
    }
  }

  @Test
  public void testNullableListPropertiesCanBeNonNull() {
    NullableListProperties instance = NullableListProperties.create(ImmutableList.of("foo", "bar"));
    assertEquals(ImmutableList.of("foo", "bar"), instance.nullableStringList());
  }

  @Test
  public void testNullableListPropertiesCanBeNull() {
    NullableListProperties instance = NullableListProperties.create(null);
    assertNull(instance.nullableStringList());
  }

  @AutoValue
  abstract static class NullableListPropertiesWithBuilder {
    @Nullable
    abstract ImmutableList<String> nullableStringList();

    static Builder builder() {
      return new AutoValue_AutoValueTest_NullableListPropertiesWithBuilder.Builder();
    }

    @AutoValue.Builder
    interface Builder {
      Builder nullableStringList(List<String> nullableStringList);

      NullableListPropertiesWithBuilder build();
    }
  }

  @Test
  public void testNullableListPropertiesWithBuilderCanBeNonNull() {
    NullableListPropertiesWithBuilder instance =
        NullableListPropertiesWithBuilder.builder()
            .nullableStringList(ImmutableList.of("foo", "bar"))
            .build();
    assertEquals(ImmutableList.of("foo", "bar"), instance.nullableStringList());
  }

  @Test
  public void testNullableListPropertiesWithBuilderCanBeUnset() {
    NullableListPropertiesWithBuilder instance =
        NullableListPropertiesWithBuilder.builder().build();
    assertNull(instance.nullableStringList());
  }

  @Test
  public void testNullableListPropertiesWithBuilderCanBeNull() {
    NullableListPropertiesWithBuilder instance =
        NullableListPropertiesWithBuilder.builder().nullableStringList(null).build();
    assertNull(instance.nullableStringList());
  }

  static class Nested {
    @AutoValue
    abstract static class Doubly {
      @Nullable
      abstract String nullableString();

      abstract int randomInt();

      static Doubly create(String nullableString, int randomInt) {
        return new AutoValue_AutoValueTest_Nested_Doubly(nullableString, randomInt);
      }
    }
  }

  @Test
  public void testDoublyNestedClass() throws Exception {
    Nested.Doubly instance = Nested.Doubly.create(null, 23);
    assertNull(instance.nullableString());
    assertThat(instance.randomInt()).isEqualTo(23);
    String expectedString =
        omitIdentifiers ? "{null, 23}" : "Doubly{nullableString=null, randomInt=23}";
    assertThat(instance.toString()).isEqualTo(expectedString);
  }

  static interface NestedInInterface {
    @AutoValue
    abstract class Doubly {
      abstract String string();

      abstract Map<String, Integer> map();

      static Doubly create(String string, Map<String, Integer> map) {
        return new AutoValue_AutoValueTest_NestedInInterface_Doubly(string, map);
      }
    }
  }

  @Test
  public void testClassNestedInInterface() throws Exception {
    Map<String, Integer> map = ImmutableMap.of("vingt-et-un", 21);
    NestedInInterface.Doubly instance = NestedInInterface.Doubly.create("foo", map);
    assertEquals("foo", instance.string());
    assertEquals(map, instance.map());
  }

  @AutoValue
  abstract static class NullableNonNullable {
    @Nullable
    abstract String nullableString();

    @Nullable
    abstract String otherNullableString();

    abstract String nonNullableString();

    static NullableNonNullable create(
        String nullableString, String otherNullableString, String nonNullableString) {
      return new AutoValue_AutoValueTest_NullableNonNullable(
          nullableString, otherNullableString, nonNullableString);
    }
  }

  @Test
  public void testEqualsWithNullable() throws Exception {
    NullableNonNullable everythingNull =
        NullableNonNullable.create(null, null, "nonNullableString");
    NullableNonNullable somethingNull =
        NullableNonNullable.create(null, "otherNullableString", "nonNullableString");
    NullableNonNullable nothingNull =
        NullableNonNullable.create("nullableString", "otherNullableString", "nonNullableString");
    NullableNonNullable nothingNullAgain =
        NullableNonNullable.create("nullableString", "otherNullableString", "nonNullableString");
    new EqualsTester()
        .addEqualityGroup(everythingNull)
        .addEqualityGroup(somethingNull)
        .addEqualityGroup(nothingNull, nothingNullAgain)
        .testEquals();
  }

  @AutoValue
  abstract static class GenericProperties {
    abstract Map<String, Integer> simpleMap();

    abstract Map<String, Map<String, Integer>> hairyMap();

    static GenericProperties create(
        Map<String, Integer> simpleMap, Map<String, Map<String, Integer>> hairyMap) {
      return new AutoValue_AutoValueTest_GenericProperties(simpleMap, hairyMap);
    }
  }

  @Test
  public void testGenericProperties() throws Exception {
    GenericProperties instance1 =
        GenericProperties.create(
            ImmutableMap.of("twenty-three", 23),
            ImmutableMap.of("very", (Map<String, Integer>) ImmutableMap.of("hairy", 17)));
    GenericProperties instance2 =
        GenericProperties.create(
            ImmutableMap.of("seventeen", 17),
            ImmutableMap.of("very", (Map<String, Integer>) ImmutableMap.of("hairy", 23)));
    new EqualsTester().addEqualityGroup(instance1).addEqualityGroup(instance2).testEquals();
    assertEquals(
        ImmutableMap.of("very", (Map<String, Integer>) ImmutableMap.of("hairy", 23)),
        instance2.hairyMap());
  }

  @AutoValue
  abstract static class GenericClass<K, V> {
    abstract K key();

    abstract Map<K, V> map();

    static <K, V> GenericClass<K, V> create(K key, Map<K, V> map) {
      return new AutoValue_AutoValueTest_GenericClass<K, V>(key, map);
    }
  }

  @Test
  public void testGenericClass() throws Exception {
    GenericClass<String, Boolean> instance =
        GenericClass.create("whatever", ImmutableMap.of("no", false));
    assertEquals(instance, instance);
    assertEquals("whatever", instance.key());
    assertEquals(ImmutableMap.of("no", false), instance.map());
  }

  @AutoValue
  abstract static class GenericClassSimpleBounds<K extends Number, V extends K> {
    abstract K key();

    abstract Map<K, V> map();

    static <K extends Number, V extends K> GenericClassSimpleBounds<K, V> create(
        K key, Map<K, V> map) {
      return new AutoValue_AutoValueTest_GenericClassSimpleBounds<K, V>(key, map);
    }
  }

  @Test
  public void testGenericClassWithSimpleBounds() throws Exception {
    GenericClassSimpleBounds<Integer, Integer> instance =
        GenericClassSimpleBounds.create(23, ImmutableMap.of(17, 23));
    assertEquals(instance, instance);
    assertEquals(23, (int) instance.key());
    assertEquals(ImmutableMap.of(17, 23), instance.map());
  }

  @AutoValue
  abstract static class GenericClassHairyBounds<K extends List<V> & Comparable<K>, V> {
    abstract K key();

    abstract Map<K, V> map();

    static <K extends List<V> & Comparable<K>, V> GenericClassHairyBounds<K, V> create(
        K key, Map<K, V> map) {
      return new AutoValue_AutoValueTest_GenericClassHairyBounds<K, V>(key, map);
    }
  }

  @Test
  public void testGenericClassWithHairyBounds() throws Exception {
    class ComparableList<E> extends ArrayList<E> implements Comparable<ComparableList<E>> {
      private static final long serialVersionUID = 1L;

      @Override
      public int compareTo(ComparableList<E> list) {
        throw new UnsupportedOperationException();
      }
    }
    ComparableList<String> emptyList = new ComparableList<String>();
    GenericClassHairyBounds<ComparableList<String>, String> instance =
        GenericClassHairyBounds.create(emptyList, ImmutableMap.of(emptyList, "23"));
    assertEquals(instance, instance);
    assertEquals(emptyList, instance.key());
    assertEquals(ImmutableMap.of(emptyList, "23"), instance.map());
  }

  interface Mergeable<M extends Mergeable<M>> {
    M merge(M other);
  }

  @AutoValue
  abstract static class Delta<M extends Mergeable<M>> {
    abstract M meta();

    static <M extends Mergeable<M>> Delta<M> create(M meta) {
      return new AutoValue_AutoValueTest_Delta<M>(meta);
    }
  }

  @Test
  public void testRecursiveGeneric() {
    class MergeableImpl implements Mergeable<MergeableImpl> {
      @Override
      public MergeableImpl merge(MergeableImpl other) {
        return this;
      }
    }
    MergeableImpl mergeable = new MergeableImpl();
    Delta<MergeableImpl> instance = Delta.create(mergeable);
    assertSame(mergeable, instance.meta());
  }

  static class NodeType<O> {}

  abstract static class NodeExpressionClass<O> {
    abstract NodeType<O> getType();
  }

  @AutoValue
  abstract static class NotNodeExpression extends NodeExpressionClass<Boolean> {
    static NotNodeExpression create() {
      return new AutoValue_AutoValueTest_NotNodeExpression(new NodeType<Boolean>());
    }
  }

  interface NodeExpressionInterface<O> {
    NodeType<O> getType();
  }

  @AutoValue
  abstract static class NotNodeExpression2 implements NodeExpressionInterface<Boolean> {
    static NotNodeExpression2 create() {
      return new AutoValue_AutoValueTest_NotNodeExpression2(new NodeType<Boolean>());
    }
  }

  @Test
  public void testConcreteWithGenericParent() {
    NotNodeExpression instance = NotNodeExpression.create();
    assertThat(instance.getType()).isInstanceOf(NodeType.class);
    NotNodeExpression2 instance2 = NotNodeExpression2.create();
    assertThat(instance2.getType()).isInstanceOf(NodeType.class);
  }

  @AutoValue
  abstract static class ExplicitToString {
    abstract String string();

    static ExplicitToString create(String string) {
      return new AutoValue_AutoValueTest_ExplicitToString(string);
    }

    @Override
    public String toString() {
      return "Bazinga{" + string() + "}";
    }
  }

  // We should not generate a toString() method if there already is a non-default one.
  @Test
  public void testExplicitToString() throws Exception {
    ExplicitToString instance = ExplicitToString.create("foo");
    assertEquals("Bazinga{foo}", instance.toString());
  }

  abstract static class NonAutoExplicitToString {
    abstract String string();

    @Override
    public String toString() {
      return "Bazinga{" + string() + "}";
    }
  }

  @AutoValue
  abstract static class InheritedExplicitToString extends NonAutoExplicitToString {
    static InheritedExplicitToString create(String string) {
      return new AutoValue_AutoValueTest_InheritedExplicitToString(string);
    }
  }

  // We should not generate a toString() method if we already inherit a non-default one.
  @Test
  public void testInheritedExplicitToString() throws Exception {
    InheritedExplicitToString instance = InheritedExplicitToString.create("foo");
    assertEquals("Bazinga{foo}", instance.toString());
  }

  @AutoValue
  abstract static class AbstractToString {
    abstract String string();

    static AbstractToString create(String string) {
      return new AutoValue_AutoValueTest_AbstractToString(string);
    }

    @Override
    public abstract String toString();
  }

  // We should generate a toString() method if the parent class has an abstract one.
  // That allows users to cancel a toString() from a parent class if they want.
  @Test
  public void testAbstractToString() throws Exception {
    AbstractToString instance = AbstractToString.create("foo");
    String expectedString = omitIdentifiers ? "{foo}" : "AbstractToString{string=foo}";
    assertThat(instance.toString()).isEqualTo(expectedString);
  }

  abstract static class NonAutoAbstractToString {
    abstract String string();

    @Override
    public abstract String toString();
  }

  @AutoValue
  abstract static class SubAbstractToString extends NonAutoAbstractToString {
    static SubAbstractToString create(String string) {
      return new AutoValue_AutoValueTest_SubAbstractToString(string);
    }
  }

  // We should generate a toString() method if the parent class inherits an abstract one.
  @Test
  public void testInheritedAbstractToString() throws Exception {
    SubAbstractToString instance = SubAbstractToString.create("foo");
    String expectedString = omitIdentifiers ? "{foo}" : "SubAbstractToString{string=foo}";
    assertThat(instance.toString()).isEqualTo(expectedString);
  }

  @AutoValue
  abstract static class ExplicitHashCode {
    abstract String string();

    static ExplicitHashCode create(String string) {
      return new AutoValue_AutoValueTest_ExplicitHashCode(string);
    }

    @Override
    public int hashCode() {
      return 1234;
    }
  }

  @Test
  public void testExplicitHashCode() throws Exception {
    ExplicitHashCode instance = ExplicitHashCode.create("foo");
    assertEquals(1234, instance.hashCode());
  }

  @AutoValue
  @SuppressWarnings("EqualsHashCode")
  abstract static class ExplicitEquals {
    int equalsCount;

    static ExplicitEquals create() {
      return new AutoValue_AutoValueTest_ExplicitEquals();
    }

    @Override
    public boolean equals(Object o) {
      equalsCount++;
      return super.equals(o);
    }
  }

  @SuppressWarnings("SelfEquals")
  @Test
  public void testExplicitEquals() throws Exception {
    ExplicitEquals instance = ExplicitEquals.create();
    assertEquals(0, instance.equalsCount);
    assertTrue(instance.equals(instance));
    assertEquals(1, instance.equalsCount);
    Method equals = instance.getClass().getMethod("equals", Object.class);
    assertNotSame(ExplicitEquals.class, instance.getClass());
    assertSame(ExplicitEquals.class, equals.getDeclaringClass());
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface MyAnnotation {
    String value();
  }

  @AutoAnnotation
  private static MyAnnotation myAnnotation(String value) {
    return new AutoAnnotation_AutoValueTest_myAnnotation(value);
  }

  @AutoValue
  abstract static class PrimitiveArrays {
    @SuppressWarnings("mutable")
    abstract boolean[] booleans();

    @SuppressWarnings("mutable")
    @Nullable
    abstract int[] ints();

    static PrimitiveArrays create(boolean[] booleans, int[] ints) {
      // Real code would likely clone these parameters, but here we want to check that the
      // generated constructor rejects a null value for booleans.
      return new AutoValue_AutoValueTest_PrimitiveArrays(booleans, ints);
    }
  }

  @Test
  public void testPrimitiveArrays() {
    PrimitiveArrays object0 = PrimitiveArrays.create(new boolean[0], new int[0]);
    boolean[] booleans = {false, true, true, false};
    int[] ints = {6, 28, 496, 8128, 33550336};
    PrimitiveArrays object1 = PrimitiveArrays.create(booleans.clone(), ints.clone());
    PrimitiveArrays object2 = PrimitiveArrays.create(booleans.clone(), ints.clone());
    new EqualsTester().addEqualityGroup(object1, object2).addEqualityGroup(object0).testEquals();
    // EqualsTester also exercises hashCode(). We clone the arrays above to ensure that using the
    // default Object.hashCode() will fail.

    String expectedString =
        omitIdentifiers
            ? ("{" + Arrays.toString(booleans) + ", " + Arrays.toString(ints) + "}")
            : ("PrimitiveArrays{booleans="
                + Arrays.toString(booleans)
                + ", "
                + "ints="
                + Arrays.toString(ints)
                + "}");
    assertThat(object1.toString()).isEqualTo(expectedString);
    assertThat(object1.ints()).isSameInstanceAs(object1.ints());
  }

  @Test
  public void testNullablePrimitiveArrays() {
    PrimitiveArrays object0 = PrimitiveArrays.create(new boolean[0], null);
    boolean[] booleans = {false, true, true, false};
    PrimitiveArrays object1 = PrimitiveArrays.create(booleans.clone(), null);
    PrimitiveArrays object2 = PrimitiveArrays.create(booleans.clone(), null);
    new EqualsTester().addEqualityGroup(object1, object2).addEqualityGroup(object0).testEquals();

    String expectedString =
        omitIdentifiers
            ? ("{" + Arrays.toString(booleans) + ", null}")
            : ("PrimitiveArrays{booleans=" + Arrays.toString(booleans) + ", " + "ints=null}");
    assertThat(object1.toString()).isEqualTo(expectedString);

    assertThat(object1.booleans()).isSameInstanceAs(object1.booleans());
    assertThat(object1.booleans()).isEqualTo(booleans);
    object1.booleans()[0] ^= true;
    assertThat(object1.booleans()).isNotEqualTo(booleans);
  }

  @Test
  public void testNotNullablePrimitiveArrays() {
    try {
      PrimitiveArrays.create(null, new int[0]);
      fail("Construction with null value for non-@Nullable array should have failed");
    } catch (NullPointerException e) {
      if (omitIdentifiers) {
        assertThat(e).hasMessageThat().isNull();
      } else {
        assertThat(e).hasMessageThat().contains("booleans");
      }
    }
  }

  // If users are mad enough to define their own Arrays class and have some properties of that
  // class and others of primitive array type, then we can't import java.util.Arrays.
  // This is unlikely.
  @AutoValue
  abstract static class AmbiguousArrays {
    static class Arrays {}

    abstract Arrays arrays();

    @SuppressWarnings("mutable")
    abstract int[] ints();

    static AmbiguousArrays create(Arrays arrays, int[] ints) {
      return new AutoValue_AutoValueTest_AmbiguousArrays(arrays, ints);
    }
  }

  @Test
  public void testAmbiguousArrays() {
    // If this test compiles at all then we presumably don't have the import problem above.
    AmbiguousArrays object1 = AmbiguousArrays.create(new AmbiguousArrays.Arrays(), new int[0]);
    assertNotNull(object1.arrays());
    assertEquals(0, object1.ints().length);
  }

  static final class HashCodeObserver {
    int hashCodeCount;

    @Override
    public boolean equals(Object obj) {
      return obj instanceof HashCodeObserver;
    }

    @Override
    public int hashCode() {
      hashCodeCount++;
      return 23;
    }
  }

  @AutoValue
  abstract static class MaybeCachedHashCode {
    abstract HashCodeObserver hashCodeObserver();

    abstract int randomInt();

    static MaybeCachedHashCode create(HashCodeObserver hashCodeObserver, int randomInt) {
      return new AutoValue_AutoValueTest_MaybeCachedHashCode(hashCodeObserver, randomInt);
    }
  }

  @Test
  public void testHashCodeNotCached() {
    HashCodeObserver observer = new HashCodeObserver();
    MaybeCachedHashCode maybeCached = MaybeCachedHashCode.create(observer, 17);
    int hash1 = maybeCached.hashCode();
    int hash2 = maybeCached.hashCode();
    assertEquals(hash1, hash2);
    assertEquals(2, observer.hashCodeCount);
  }

  @AutoValue
  abstract static class Version implements Comparable<Version> {
    abstract int major();

    abstract int minor();

    static Version create(int major, int minor) {
      return new AutoValue_AutoValueTest_Version(major, minor);
    }

    @Override
    public int compareTo(Version that) {
      return ComparisonChain.start()
          .compare(this.major(), that.major())
          .compare(this.minor(), that.minor())
          .result();
    }
  }

  @Test
  public void testComparisonChain() {
    assertEquals(Version.create(1, 2), Version.create(1, 2));
    Version[] versions = {Version.create(1, 2), Version.create(1, 3), Version.create(2, 1)};
    for (int i = 0; i < versions.length; i++) {
      for (int j = 0; j < versions.length; j++) {
        int actual = Integer.signum(versions[i].compareTo(versions[j]));
        int expected = Integer.signum(i - j);
        assertEquals(expected, actual);
      }
    }
  }

  abstract static class LukesBase {
    interface LukesVisitor<T> {
      T visit(LukesSub s);
    }

    abstract <T> T accept(LukesVisitor<T> visitor);

    @AutoValue
    abstract static class LukesSub extends LukesBase {
      static LukesSub create() {
        return new AutoValue_AutoValueTest_LukesBase_LukesSub();
      }

      @Override
      <T> T accept(LukesVisitor<T> visitor) {
        return visitor.visit(this);
      }
    }
  }

  @Test
  public void testVisitor() {
    LukesBase.LukesVisitor<String> visitor =
        new LukesBase.LukesVisitor<String>() {
          @Override
          public String visit(LukesBase.LukesSub s) {
            return s.toString();
          }
        };
    LukesBase.LukesSub sub = LukesBase.LukesSub.create();
    assertEquals(sub.toString(), sub.accept(visitor));
  }

  @AutoValue
  public abstract static class ComplexInheritance extends AbstractBase implements IntfA, IntfB {
    public static ComplexInheritance create(String name) {
      return new AutoValue_AutoValueTest_ComplexInheritance(name);
    }

    abstract String name();
  }

  static class AbstractBase implements Base {
    @Override
    public int answer() {
      return 42;
    }
  }

  interface IntfA extends Base {}

  interface IntfB extends Base {}

  interface Base {
    int answer();
  }

  @Test
  public void testComplexInheritance() {
    ComplexInheritance complex = ComplexInheritance.create("fred");
    assertEquals("fred", complex.name());
    assertEquals(42, complex.answer());
  }

  // This tests the case where we inherit abstract methods on more than one path. AbstractList
  // extends AbstractCollection, which implements Collection; and AbstractList also implements List,
  // which extends Collection. So the class here inherits the methods of Collection on more than
  // one path. In an earlier version of the logic for handling inheritance, this confused us into
  // thinking that the methods from Collection were still abstract and therefore candidates for
  // implementation, even though we inherit concrete implementations of them from AbstractList.
  @AutoValue
  public static class MoreComplexInheritance extends AbstractList<String> {
    @Override
    public String get(int index) {
      throw new NoSuchElementException(String.valueOf(index));
    }

    @Override
    public int size() {
      return 0;
    }

    public static MoreComplexInheritance create() {
      return new AutoValue_AutoValueTest_MoreComplexInheritance();
    }
  }

  @Test
  public void testMoreComplexInheritance() {
    MoreComplexInheritance instance1 = MoreComplexInheritance.create();
    MoreComplexInheritance instance2 = MoreComplexInheritance.create();
    assertThat(instance1).isEqualTo(instance2);
    assertThat(instance1).isNotSameInstanceAs(instance2);
  }

  // Test that we are not misled by the privateness of an ancestor into thinking that its methods
  // are invisible to descendants.
  public abstract static class PublicGrandparent {
    public abstract String foo();
  }

  private static class PrivateParent extends PublicGrandparent {
    @Override
    public String foo() {
      return "foo";
    }
  }

  @AutoValue
  static class EffectiveVisibility extends PrivateParent {
    static EffectiveVisibility create() {
      return new AutoValue_AutoValueTest_EffectiveVisibility();
    }
  }

  @Test
  public void testEffectiveVisibility() {
    EffectiveVisibility instance1 = EffectiveVisibility.create();
    EffectiveVisibility instance2 = EffectiveVisibility.create();
    assertThat(instance1).isEqualTo(instance2);
    assertThat(instance1).isNotSameInstanceAs(instance2);
  }

  @AutoValue
  public abstract static class InheritTwice implements IntfA, IntfB {
    public static InheritTwice create(int answer) {
      return new AutoValue_AutoValueTest_InheritTwice(answer);
    }
  }

  @Test
  public void testInheritTwice() {
    InheritTwice inheritTwice = InheritTwice.create(42);
    assertEquals(42, inheritTwice.answer());
  }

  @AutoValue
  public abstract static class Optional {
    public abstract com.google.common.base.Optional<Object> getOptional();

    public static Optional create(com.google.common.base.Optional<Object> opt) {
      return new AutoValue_AutoValueTest_Optional(opt);
    }
  }

  @Test
  public void testAmbiguityFromAutoValueType() {
    Optional autoOptional = Optional.create(com.google.common.base.Optional.absent());
    assertEquals(com.google.common.base.Optional.absent(), autoOptional.getOptional());
  }

  static class BaseWithNestedType {
    static class Optional {}
  }

  @AutoValue
  public abstract static class InheritsNestedType extends BaseWithNestedType {
    public abstract com.google.common.base.Optional<Object> getOptional();

    public static InheritsNestedType create(com.google.common.base.Optional<Object> opt) {
      return new AutoValue_AutoValueTest_InheritsNestedType(opt);
    }
  }

  @Test
  public void testAmbiguityFromInheritedType() {
    InheritsNestedType inheritsNestedType =
        InheritsNestedType.create(com.google.common.base.Optional.absent());
    assertEquals(com.google.common.base.Optional.absent(), inheritsNestedType.getOptional());
  }

  abstract static class AbstractParent {
    abstract int foo();
  }

  @AutoValue
  abstract static class AbstractChild extends AbstractParent {
    // The main point of this test is to ensure that we don't try to copy this @Override into the
    // generated implementation alongside the @Override that we put on all implementation methods.
    @Override
    abstract int foo();

    static AbstractChild create(int foo) {
      return new AutoValue_AutoValueTest_AbstractChild(foo);
    }
  }

  @Test
  public void testOverrideNotDuplicated() {
    AbstractChild instance = AbstractChild.create(23);
    assertEquals(23, instance.foo());
  }

  @AutoValue
  public abstract static class BasicWithBuilder {
    public abstract int foo();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_BasicWithBuilder.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder foo(int foo);

      BasicWithBuilder build();
    }
  }

  @Test
  public void testBasicWithBuilder() {
    BasicWithBuilder x = BasicWithBuilder.builder().foo(23).build();
    assertEquals(23, x.foo());
    try {
      BasicWithBuilder.builder().build();
      fail("Expected exception for missing property");
    } catch (IllegalStateException e) {
      if (omitIdentifiers) {
        assertThat(e).hasMessageThat().isNull();
      } else {
        assertThat(e).hasMessageThat().contains("foo");
      }
    }
  }

  @Test
  public void testBasicWithBuilderHasOnlyOneConstructor() throws Exception {
    Class<?> builderClass = AutoValue_AutoValueTest_BasicWithBuilder.Builder.class;
    Constructor<?>[] constructors = builderClass.getDeclaredConstructors();
    assertThat(constructors).hasLength(1);
    Constructor<?> constructor = constructors[0];
    assertThat(constructor.getParameterTypes()).isEmpty();
  }

  @AutoValue
  public abstract static class EmptyWithBuilder {
    public static Builder builder() {
      return new AutoValue_AutoValueTest_EmptyWithBuilder.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      EmptyWithBuilder build();
    }
  }

  @Test
  public void testEmptyWithBuilder() {
    EmptyWithBuilder x = EmptyWithBuilder.builder().build();
    EmptyWithBuilder y = EmptyWithBuilder.builder().build();
    assertEquals(x, y);
  }

  @AutoValue
  public abstract static class TwoPropertiesWithBuilderClass {
    public abstract String string();

    public abstract int integer();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_TwoPropertiesWithBuilderClass.Builder();
    }

    public static Builder builder(String string) {
      return new AutoValue_AutoValueTest_TwoPropertiesWithBuilderClass.Builder().string(string);
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder string(String x);

      public abstract Builder integer(int x);

      public abstract TwoPropertiesWithBuilderClass build();
    }
  }

  @Test
  public void testTwoPropertiesWithBuilderClass() {
    TwoPropertiesWithBuilderClass a1 =
        TwoPropertiesWithBuilderClass.builder().string("23").integer(17).build();
    TwoPropertiesWithBuilderClass a2 =
        TwoPropertiesWithBuilderClass.builder("23").integer(17).build();
    TwoPropertiesWithBuilderClass a3 =
        TwoPropertiesWithBuilderClass.builder().integer(17).string("23").build();
    TwoPropertiesWithBuilderClass b =
        TwoPropertiesWithBuilderClass.builder().string("17").integer(17).build();
    new EqualsTester().addEqualityGroup(a1, a2, a3).addEqualityGroup(b).testEquals();

    try {
      TwoPropertiesWithBuilderClass.builder().string(null);
      fail("Did not get expected exception");
    } catch (NullPointerException expected) {
    }
  }

  @AutoValue
  public abstract static class NullablePropertyWithBuilder {
    public abstract String notNullable();

    @Nullable
    public abstract String nullable();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_NullablePropertyWithBuilder.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder notNullable(String s);

      Builder nullable(@Nullable String s);

      NullablePropertyWithBuilder build();
    }
  }

  @Test
  public void testOmitNullableWithBuilder() {
    NullablePropertyWithBuilder instance1 =
        NullablePropertyWithBuilder.builder().notNullable("hello").build();
    assertThat(instance1.notNullable()).isEqualTo("hello");
    assertThat(instance1.nullable()).isNull();

    NullablePropertyWithBuilder instance2 =
        NullablePropertyWithBuilder.builder().notNullable("hello").nullable(null).build();
    assertThat(instance2.notNullable()).isEqualTo("hello");
    assertThat(instance2.nullable()).isNull();
    assertThat(instance1).isEqualTo(instance2);

    NullablePropertyWithBuilder instance3 =
        NullablePropertyWithBuilder.builder().notNullable("hello").nullable("world").build();
    assertThat(instance3.notNullable()).isEqualTo("hello");
    assertThat(instance3.nullable()).isEqualTo("world");

    try {
      NullablePropertyWithBuilder.builder().build();
      fail("Expected IllegalStateException for unset non-@Nullable property");
    } catch (IllegalStateException e) {
      if (omitIdentifiers) {
        assertThat(e).hasMessageThat().isNull();
      } else {
        assertThat(e).hasMessageThat().contains("notNullable");
      }
    }
  }

  @AutoValue
  public abstract static class PrimitiveAndBoxed {
    public abstract int anInt();

    @Nullable
    public abstract Integer aNullableInteger();

    public abstract Integer aNonNullableInteger();

    public abstract Builder toBuilder();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_PrimitiveAndBoxed.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder setAnInt(Integer x);

      Builder setANullableInteger(int x);

      Builder setANonNullableInteger(int x);

      PrimitiveAndBoxed build();
    }
  }

  @Test
  public void testPrimitiveAndBoxed() {
    PrimitiveAndBoxed instance1 =
        PrimitiveAndBoxed.builder().setAnInt(17).setANonNullableInteger(23).build();
    assertThat(instance1.anInt()).isEqualTo(17);
    assertThat(instance1.aNullableInteger()).isNull();
    assertThat(instance1.aNonNullableInteger()).isEqualTo(23);

    PrimitiveAndBoxed instance2 = instance1.toBuilder().setANullableInteger(5).build();
    assertThat(instance2.aNullableInteger()).isEqualTo(5);

    try {
      instance1.toBuilder().setAnInt(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @AutoValue
  public abstract static class OptionalPropertiesWithBuilder {
    public abstract com.google.common.base.Optional<String> optionalString();

    public abstract com.google.common.base.Optional<Integer> optionalInteger();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_OptionalPropertiesWithBuilder.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder setOptionalString(com.google.common.base.Optional<String> s);

      Builder setOptionalString(String s);

      Builder setOptionalInteger(com.google.common.base.Optional<Integer> i);

      Builder setOptionalInteger(int i);

      OptionalPropertiesWithBuilder build();
    }
  }

  @Test
  public void testOmitOptionalWithBuilder() {
    OptionalPropertiesWithBuilder omitted = OptionalPropertiesWithBuilder.builder().build();
    assertThat(omitted.optionalString()).isAbsent();
    assertThat(omitted.optionalInteger()).isAbsent();

    OptionalPropertiesWithBuilder supplied =
        OptionalPropertiesWithBuilder.builder()
            .setOptionalString(com.google.common.base.Optional.of("foo"))
            .build();
    assertThat(supplied.optionalString()).hasValue("foo");
    assertThat(omitted.optionalInteger()).isAbsent();

    OptionalPropertiesWithBuilder suppliedDirectly =
        OptionalPropertiesWithBuilder.builder()
            .setOptionalString("foo")
            .setOptionalInteger(23)
            .build();
    assertThat(suppliedDirectly.optionalString()).hasValue("foo");
    assertThat(suppliedDirectly.optionalInteger()).hasValue(23);

    try {
      // The parameter is not marked @Nullable so this should fail.
      OptionalPropertiesWithBuilder.builder().setOptionalString((String) null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @AutoValue
  public abstract static class OptionalPropertyWithNullableBuilder {
    public abstract String notOptional();

    public abstract com.google.common.base.Optional<String> optional();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_OptionalPropertyWithNullableBuilder.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder notOptional(String s);

      Builder optional(@Nullable String s);

      OptionalPropertyWithNullableBuilder build();
    }
  }

  @Test
  public void testOmitOptionalWithNullableBuilder() {
    OptionalPropertyWithNullableBuilder instance1 =
        OptionalPropertyWithNullableBuilder.builder().notOptional("hello").build();
    assertThat(instance1.notOptional()).isEqualTo("hello");
    assertThat(instance1.optional()).isAbsent();

    OptionalPropertyWithNullableBuilder instance2 =
        OptionalPropertyWithNullableBuilder.builder().notOptional("hello").optional(null).build();
    assertThat(instance2.notOptional()).isEqualTo("hello");
    assertThat(instance2.optional()).isAbsent();
    assertThat(instance1).isEqualTo(instance2);

    OptionalPropertyWithNullableBuilder instance3 =
        OptionalPropertyWithNullableBuilder.builder()
            .notOptional("hello")
            .optional("world")
            .build();
    assertThat(instance3.notOptional()).isEqualTo("hello");
    assertThat(instance3.optional()).hasValue("world");

    try {
      OptionalPropertyWithNullableBuilder.builder().build();
      fail("Expected IllegalStateException for unset non-Optional property");
    } catch (IllegalStateException expected) {
    }
  }

  @AutoValue
  public abstract static class NullableOptionalPropertiesWithBuilder {
    @Nullable
    public abstract com.google.common.base.Optional<String> optionalString();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_NullableOptionalPropertiesWithBuilder.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder setOptionalString(com.google.common.base.Optional<String> s);

      NullableOptionalPropertiesWithBuilder build();
    }
  }

  @Test
  public void testOmitNullableOptionalWithBuilder() {
    NullableOptionalPropertiesWithBuilder omitted =
        NullableOptionalPropertiesWithBuilder.builder().build();
    assertThat(omitted.optionalString()).isNull();

    NullableOptionalPropertiesWithBuilder supplied =
        NullableOptionalPropertiesWithBuilder.builder()
            .setOptionalString(com.google.common.base.Optional.of("foo"))
            .build();
    assertThat(supplied.optionalString()).hasValue("foo");
  }

  @AutoValue
  public abstract static class OptionalPropertiesWithBuilderSimpleSetter {
    public abstract com.google.common.base.Optional<String> optionalString();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_OptionalPropertiesWithBuilderSimpleSetter.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder setOptionalString(String s);

      OptionalPropertiesWithBuilderSimpleSetter build();
    }
  }

  @Test
  public void testOptionalPropertySimpleSetter() {
    OptionalPropertiesWithBuilderSimpleSetter omitted =
        OptionalPropertiesWithBuilderSimpleSetter.builder().build();
    assertThat(omitted.optionalString()).isAbsent();

    OptionalPropertiesWithBuilderSimpleSetter supplied =
        OptionalPropertiesWithBuilderSimpleSetter.builder().setOptionalString("foo").build();
    assertThat(supplied.optionalString()).hasValue("foo");
  }

  @AutoValue
  public abstract static class PropertyWithOptionalGetter {
    public abstract String getString();

    public abstract int getInt();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_PropertyWithOptionalGetter.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder setString(String s);

      com.google.common.base.Optional<String> getString();

      Builder setInt(int x);

      com.google.common.base.Optional<Integer> getInt();

      PropertyWithOptionalGetter build();
    }
  }

  @Test
  public void testOptionalGetter() {
    PropertyWithOptionalGetter.Builder omitted = PropertyWithOptionalGetter.builder();
    assertThat(omitted.getString()).isAbsent();
    assertThat(omitted.getInt()).isAbsent();

    PropertyWithOptionalGetter.Builder supplied =
        PropertyWithOptionalGetter.builder().setString("foo").setInt(23);
    assertThat(supplied.getString()).hasValue("foo");
    assertThat(supplied.getInt()).hasValue(23);
  }

  @AutoValue
  public abstract static class PropertyNamedMissing {
    public abstract String missing();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_PropertyNamedMissing.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setMissing(String x);

      public abstract PropertyNamedMissing build();
    }
  }

  // https://github.com/google/auto/issues/412
  @Test
  public void testPropertyNamedMissing() {
    try {
      PropertyNamedMissing.builder().build();
      fail();
    } catch (IllegalStateException expected) {
    }
    PropertyNamedMissing x = PropertyNamedMissing.builder().setMissing("foo").build();
    assertThat(x.missing()).isEqualTo("foo");
  }

  @AutoValue
  public abstract static class GenericsWithBuilder<T extends Number & Comparable<T>, U extends T> {
    public abstract List<T> list();

    public abstract U u();

    public static <T extends Number & Comparable<T>, U extends T> Builder<T, U> builder() {
      return new AutoValue_AutoValueTest_GenericsWithBuilder.Builder<T, U>();
    }

    public abstract Builder<T, U> toBuilderGenerated();

    @AutoValue.Builder
    public interface Builder<T extends Number & Comparable<T>, U extends T> {
      Builder<T, U> list(List<T> list);

      Builder<T, U> u(U u);

      GenericsWithBuilder<T, U> build();
    }
  }

  @Test
  public void testBuilderGenerics() {
    List<Integer> integers = ImmutableList.of(1, 2, 3);
    GenericsWithBuilder<Integer, Integer> instance =
        GenericsWithBuilder.<Integer, Integer>builder().list(integers).u(23).build();
    assertEquals(integers, instance.list());
    assertEquals((Integer) 23, instance.u());

    GenericsWithBuilder<Integer, Integer> instance2 = instance.toBuilderGenerated().build();
    assertEquals(instance, instance2);
    assertNotSame(instance, instance2);

    GenericsWithBuilder<Integer, Integer> instance3 = instance.toBuilderGenerated().u(17).build();
    assertEquals(integers, instance3.list());
    assertEquals((Integer) 17, instance3.u());
  }

  public interface ToBuilder<BuilderT> {
    BuilderT toBuilder();
  }

  @AutoValue
  public abstract static class InheritedToBuilder<T, U>
      implements ToBuilder<InheritedToBuilder.Builder<T, U>> {

    public abstract T t();

    public abstract U u();

    public static <T, U> Builder<T, U> builder() {
      return new AutoValue_AutoValueTest_InheritedToBuilder.Builder<T, U>();
    }

    @AutoValue.Builder
    public abstract static class Builder<T, U> {
      public abstract Builder<T, U> setT(T t);

      public abstract Builder<T, U> setU(U u);

      public abstract InheritedToBuilder<T, U> build();
    }
  }

  @Test
  public void testInheritedToBuilder() {
    InheritedToBuilder<Integer, String> x =
        InheritedToBuilder.<Integer, String>builder().setT(17).setU("wibble").build();
    InheritedToBuilder<Integer, String> y = x.toBuilder().setT(23).build();
    assertThat(y.u()).isEqualTo("wibble");
  }

  @AutoValue
  public abstract static class BuilderWithSet<T extends Comparable<T>> {
    public abstract List<T> list();

    public abstract T t();

    public static <T extends Comparable<T>> Builder<T> builder() {
      return new AutoValue_AutoValueTest_BuilderWithSet.Builder<T>();
    }

    @AutoValue.Builder
    public interface Builder<T extends Comparable<T>> {
      Builder<T> setList(List<T> list);

      Builder<T> setT(T t);

      BuilderWithSet<T> build();
    }
  }

  @Test
  public void testBuilderWithSet() {
    List<Integer> integers = ImmutableList.of(1, 2, 3);
    BuilderWithSet<Integer> instance =
        BuilderWithSet.<Integer>builder().setList(integers).setT(23).build();
    assertEquals(integers, instance.list());
    assertEquals((Integer) 23, instance.t());
  }

  @AutoValue
  public abstract static class BuilderWithSetAndGet {
    public abstract List<Integer> getAList();

    public abstract int getAnInt();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_BuilderWithSetAndGet.Builder();
    }

    public abstract Builder toBuilder();

    @AutoValue.Builder
    public interface Builder {
      Builder setAList(List<Integer> list);

      Builder setAnInt(int i);

      BuilderWithSetAndGet build();
    }
  }

  @Test
  public void testBuilderWithSetAndGet() {
    List<Integer> integers = ImmutableList.of(1, 2, 3);
    BuilderWithSetAndGet instance =
        BuilderWithSetAndGet.builder().setAList(integers).setAnInt(23).build();
    assertEquals(integers, instance.getAList());
    assertEquals(23, instance.getAnInt());

    BuilderWithSetAndGet instance2 = instance.toBuilder().build();
    assertEquals(instance, instance2);
    assertNotSame(instance, instance2);

    BuilderWithSetAndGet instance3 = instance.toBuilder().setAnInt(17).build();
    assertEquals(integers, instance3.getAList());
    assertEquals(17, instance3.getAnInt());
  }

  @AutoValue
  public abstract static class BuilderWithUnprefixedGetters<T extends Comparable<T>> {
    public abstract ImmutableList<T> list();

    @Nullable
    public abstract T t();

    @SuppressWarnings("mutable")
    public abstract int[] ints();

    public abstract int noGetter();

    public abstract String oAuth();

    public abstract String oBrien();

    public static <T extends Comparable<T>> Builder<T> builder() {
      return new AutoValue_AutoValueTest_BuilderWithUnprefixedGetters.Builder<T>();
    }

    @AutoValue.Builder
    public interface Builder<T extends Comparable<T>> {
      Builder<T> setList(ImmutableList<T> list);

      Builder<T> setT(T t);

      Builder<T> setInts(int[] ints);

      Builder<T> setNoGetter(int x);

      Builder<T> setoAuth(String x); // this ugly spelling is for compatibility

      Builder<T> setOBrien(String x);

      ImmutableList<T> list();

      T t();

      int[] ints();

      String oAuth();

      String oBrien();

      BuilderWithUnprefixedGetters<T> build();
    }
  }

  @Test
  public void testBuilderWithUnprefixedGetter() {
    ImmutableList<String> names = ImmutableList.of("fred", "jim");
    int[] ints = {6, 28, 496, 8128, 33550336};
    int noGetter = -1;

    BuilderWithUnprefixedGetters.Builder<String> builder = BuilderWithUnprefixedGetters.builder();
    assertNull(builder.t());
    try {
      builder.list();
      fail("Attempt to retrieve unset list property should have failed");
    } catch (IllegalStateException e) {
      if (omitIdentifiers) {
        assertThat(e).hasMessageThat().isNull();
      } else {
        assertThat(e).hasMessageThat().isEqualTo("Property \"list\" has not been set");
      }
    }
    try {
      builder.ints();
      fail("Attempt to retrieve unset ints property should have failed");
    } catch (IllegalStateException e) {
      if (omitIdentifiers) {
        assertThat(e).hasMessageThat().isNull();
      } else {
        assertThat(e).hasMessageThat().isEqualTo("Property \"ints\" has not been set");
      }
    }

    builder.setList(names);
    assertThat(builder.list()).isSameInstanceAs(names);
    builder.setInts(ints);
    assertThat(builder.ints()).isEqualTo(ints);
    builder.setoAuth("OAuth");
    assertThat(builder.oAuth()).isEqualTo("OAuth");
    builder.setOBrien("Flann");
    assertThat(builder.oBrien()).isEqualTo("Flann");
    // The array is not cloned by the getter, so the client can modify it (but shouldn't).
    ints[0] = 0;
    assertThat(builder.ints()[0]).isEqualTo(0);
    ints[0] = 6;

    BuilderWithUnprefixedGetters<String> instance = builder.setNoGetter(noGetter).build();
    assertThat(instance.list()).isSameInstanceAs(names);
    assertThat(instance.t()).isNull();
    assertThat(instance.ints()).isEqualTo(ints);
    assertThat(instance.noGetter()).isEqualTo(noGetter);
    assertThat(instance.oAuth()).isEqualTo("OAuth");
    assertThat(instance.oBrien()).isEqualTo("Flann");
  }

  @AutoValue
  public abstract static class BuilderWithPrefixedGetters<T extends Comparable<T>> {
    public abstract ImmutableList<T> getList();

    public abstract T getT();

    @SuppressWarnings("mutable")
    @Nullable
    public abstract int[] getInts();

    public abstract String getOAuth();

    public abstract int getNoGetter();

    public static <T extends Comparable<T>> Builder<T> builder() {
      return new AutoValue_AutoValueTest_BuilderWithPrefixedGetters.Builder<T>();
    }

    @AutoValue.Builder
    public abstract static class Builder<T extends Comparable<T>> {
      public abstract Builder<T> setList(ImmutableList<T> list);

      public abstract Builder<T> setT(T t);

      public abstract Builder<T> setInts(int[] ints);

      public abstract Builder<T> setNoGetter(int x);

      public abstract Builder<T> setOAuth(String x);

      abstract ImmutableList<T> getList();

      abstract T getT();

      abstract int[] getInts();

      public abstract BuilderWithPrefixedGetters<T> build();
    }
  }

  @Test
  public void testBuilderWithPrefixedGetter() {
    ImmutableList<String> names = ImmutableList.of("fred", "jim");
    String name = "sheila";
    int noGetter = -1;

    BuilderWithPrefixedGetters.Builder<String> builder = BuilderWithPrefixedGetters.builder();
    assertThat(builder.getInts()).isNull();
    try {
      builder.getList();
      fail("Attempt to retrieve unset list property should have failed");
    } catch (IllegalStateException e) {
      if (omitIdentifiers) {
        assertThat(e).hasMessageThat().isNull();
      } else {
        assertThat(e).hasMessageThat().isEqualTo("Property \"list\" has not been set");
      }
    }

    builder.setList(names);
    assertThat(builder.getList()).isSameInstanceAs(names);
    builder.setT(name);
    assertThat(builder.getInts()).isNull();
    builder.setOAuth("OAuth");

    BuilderWithPrefixedGetters<String> instance = builder.setNoGetter(noGetter).build();
    assertThat(instance.getList()).isSameInstanceAs(names);
    assertThat(instance.getT()).isEqualTo(name);
    assertThat(instance.getInts()).isNull();
    assertThat(instance.getNoGetter()).isEqualTo(noGetter);
    assertThat(instance.getOAuth()).isEqualTo("OAuth");
  }

  @AutoValue
  public abstract static class BuilderWithPrefixedGettersAndUnprefixedSetters {
    public abstract String getOAuth();

    public abstract String getOBrien();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_BuilderWithPrefixedGettersAndUnprefixedSetters.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder oAuth(String x);

      public abstract Builder OBrien(String x);

      public abstract BuilderWithPrefixedGettersAndUnprefixedSetters build();
    }
  }

  @Test
  public void testBuilderWithPrefixedGetterAndUnprefixedSetter() {
    BuilderWithPrefixedGettersAndUnprefixedSetters x =
        BuilderWithPrefixedGettersAndUnprefixedSetters.builder()
            .oAuth("OAuth")
            .OBrien("Flann")
            .build();
    assertThat(x.getOAuth()).isEqualTo("OAuth");
    assertThat(x.getOBrien()).isEqualTo("Flann");
  }

  @AutoValue
  public abstract static class BuilderWithPropertyBuilders<FooT extends Comparable<FooT>> {
    public abstract ImmutableList<FooT> getFoos();

    public abstract ImmutableSet<String> getStrings();

    public abstract BuilderWithPropertyBuilders.Builder<FooT> toBuilder();

    public static <FooT extends Comparable<FooT>> Builder<FooT> builder() {
      return new AutoValue_AutoValueTest_BuilderWithPropertyBuilders.Builder<FooT>();
    }

    @AutoValue.Builder
    public abstract static class Builder<FooT extends Comparable<FooT>> {
      public abstract ImmutableList<FooT> getFoos();

      public Builder<FooT> addFoos(Iterable<FooT> foos) {
        foosBuilder().addAll(foos);
        return this;
      }

      abstract ImmutableList.Builder<FooT> foosBuilder();

      public Builder<FooT> addToTs(FooT element) {
        foosBuilder().add(element);
        return this;
      }

      abstract Builder<FooT> setStrings(ImmutableList<String> strings);

      abstract ImmutableSet.Builder<String> stringsBuilder();

      public Builder<FooT> addToStrings(String element) {
        stringsBuilder().add(element);
        return this;
      }

      public abstract BuilderWithPropertyBuilders<FooT> build();
    }
  }

  @Test
  public void testBuilderWithPropertyBuilders() {
    ImmutableList<Integer> numbers = ImmutableList.of(1, 1, 2, 6, 24);
    ImmutableSet<String> names = ImmutableSet.of("one", "two", "six", "twenty-four");

    BuilderWithPropertyBuilders<Integer> a =
        BuilderWithPropertyBuilders.<Integer>builder()
            .addFoos(numbers)
            .addToStrings("one")
            .addToStrings("two")
            .addToStrings("six")
            .addToStrings("twenty-four")
            .build();

    assertEquals(numbers, a.getFoos());
    assertEquals(names, a.getStrings());

    BuilderWithPropertyBuilders.Builder<Integer> bBuilder = BuilderWithPropertyBuilders.builder();
    bBuilder.stringsBuilder().addAll(names);
    bBuilder.foosBuilder().addAll(numbers);

    assertEquals(numbers, bBuilder.getFoos());

    BuilderWithPropertyBuilders<Integer> b = bBuilder.build();
    assertEquals(a, b);

    BuilderWithPropertyBuilders.Builder<Integer> cBuilder = a.toBuilder();
    cBuilder.addToStrings("one hundred and twenty");
    cBuilder.addToTs(120);
    BuilderWithPropertyBuilders<Integer> c = cBuilder.build();
    assertEquals(
        ImmutableSet.of("one", "two", "six", "twenty-four", "one hundred and twenty"),
        c.getStrings());
    assertEquals(ImmutableList.of(1, 1, 2, 6, 24, 120), c.getFoos());

    BuilderWithPropertyBuilders.Builder<Integer> dBuilder = a.toBuilder();
    dBuilder.addFoos(ImmutableList.of(120, 720));
    BuilderWithPropertyBuilders<Integer> d = dBuilder.build();
    assertEquals(ImmutableList.of(1, 1, 2, 6, 24, 120, 720), d.getFoos());
    assertEquals(names, d.getStrings());

    BuilderWithPropertyBuilders<Integer> empty =
        BuilderWithPropertyBuilders.<Integer>builder().build();
    assertEquals(ImmutableList.of(), empty.getFoos());
    assertEquals(ImmutableSet.of(), empty.getStrings());

    try {
      BuilderWithPropertyBuilders.<Integer>builder().setStrings(null).build();
      fail("Did not get expected exception");
    } catch (RuntimeException expected) {
      // We don't specify whether you get the exception on setStrings(null) or on build(), nor
      // which exception it is exactly.
    }
  }

  interface ImmutableListOf<T> {
    ImmutableList<T> list();
  }

  @AutoValue
  abstract static class PropertyBuilderInheritsType implements ImmutableListOf<String> {
    static Builder builder() {
      return new AutoValue_AutoValueTest_PropertyBuilderInheritsType.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract ImmutableList.Builder<String> listBuilder();

      abstract PropertyBuilderInheritsType build();
    }
  }

  @Test
  public void propertyBuilderInheritsType() {
    PropertyBuilderInheritsType.Builder builder = PropertyBuilderInheritsType.builder();
    builder.listBuilder().add("foo", "bar");
    PropertyBuilderInheritsType x = builder.build();
    assertThat(x.list()).containsExactly("foo", "bar").inOrder();
  }

  @AutoValue
  public abstract static class BuilderWithExoticPropertyBuilders<
      K extends Number, V extends Comparable<K>> {
    public abstract ImmutableMap<String, V> map();

    public abstract ImmutableTable<String, K, V> table();

    public static <K extends Number, V extends Comparable<K>> Builder<K, V> builder() {
      return new AutoValue_AutoValueTest_BuilderWithExoticPropertyBuilders.Builder<K, V>();
    }

    @AutoValue.Builder
    public abstract static class Builder<K extends Number, V extends Comparable<K>> {
      public Builder<K, V> putAll(Map<String, V> map) {
        mapBuilder().putAll(map);
        return this;
      }

      public abstract ImmutableMap.Builder<String, V> mapBuilder();

      public Builder<K, V> putAll(ImmutableTable<String, K, V> table) {
        tableBuilder().putAll(table);
        return this;
      }

      public abstract ImmutableTable.Builder<String, K, V> tableBuilder();

      public abstract BuilderWithExoticPropertyBuilders<K, V> build();
    }
  }

  @Test
  public void testBuilderWithExoticPropertyBuilders() {
    ImmutableMap<String, Integer> map = ImmutableMap.of("one", 1);
    ImmutableTable<String, Integer, Integer> table = ImmutableTable.of("one", 1, -1);

    BuilderWithExoticPropertyBuilders<Integer, Integer> a =
        BuilderWithExoticPropertyBuilders.<Integer, Integer>builder()
            .putAll(map)
            .putAll(table)
            .build();
    assertEquals(map, a.map());
    assertEquals(table, a.table());

    BuilderWithExoticPropertyBuilders.Builder<Integer, Integer> bBuilder =
        BuilderWithExoticPropertyBuilders.builder();
    bBuilder.mapBuilder().putAll(map);
    bBuilder.tableBuilder().putAll(table);
    BuilderWithExoticPropertyBuilders<Integer, Integer> b = bBuilder.build();
    assertEquals(a, b);

    BuilderWithExoticPropertyBuilders<Integer, Integer> empty =
        BuilderWithExoticPropertyBuilders.<Integer, Integer>builder().build();
    assertEquals(ImmutableMap.of(), empty.map());
    assertEquals(ImmutableTable.of(), empty.table());
  }

  @AutoValue
  public abstract static class BuilderWithCopyingSetters<T extends Number> {
    public abstract ImmutableSet<? extends T> things();

    public abstract ImmutableList<Number> numbers();

    public abstract ImmutableMap<String, T> map();

    public static <T extends Number> Builder<T> builder(T value) {
      return new AutoValue_AutoValueTest_BuilderWithCopyingSetters.Builder<T>()
          .setNumbers(ImmutableSet.of(17, 23.0))
          .setMap(Collections.singletonMap("foo", value));
    }

    @AutoValue.Builder
    public interface Builder<T extends Number> {
      Builder<T> setThings(ImmutableSet<T> things);

      Builder<T> setThings(Iterable<? extends T> things);

      Builder<T> setThings(T... things);

      Builder<T> setNumbers(Collection<? extends Number> strings);

      Builder<T> setMap(Map<String, T> map);

      BuilderWithCopyingSetters<T> build();
    }
  }

  @Test
  public void testBuilderWithCopyingSetters() {
    BuilderWithCopyingSetters.Builder<Integer> builder = BuilderWithCopyingSetters.builder(23);

    BuilderWithCopyingSetters<Integer> a = builder.setThings(ImmutableSet.of(1, 2)).build();
    assertThat(a.things()).containsExactly(1, 2);
    assertThat(a.numbers()).containsExactly(17, 23.0).inOrder();
    assertThat(a.map()).containsExactly("foo", 23);

    BuilderWithCopyingSetters<Integer> b = builder.setThings(Arrays.asList(1, 2)).build();
    assertThat(b).isEqualTo(a);

    BuilderWithCopyingSetters<Integer> c = builder.setThings(1, 2).build();
    assertThat(c).isEqualTo(a);
  }

  @AutoValue
  public abstract static class BuilderWithImmutableSorted<T extends Comparable<T>> {
    public abstract ImmutableSortedSet<T> sortedSet();

    public abstract ImmutableSortedMap<T, Integer> sortedMap();

    public static <T extends Comparable<T>> Builder<T> builder() {
      return new AutoValue_AutoValueTest_BuilderWithImmutableSorted.Builder<T>()
          .setSortedSet(new TreeSet<T>())
          .setSortedMap(new TreeMap<T, Integer>());
    }

    @AutoValue.Builder
    public interface Builder<T extends Comparable<T>> {
      @SuppressWarnings("unchecked")
      Builder<T> setSortedSet(T... x);

      Builder<T> setSortedSet(NavigableSet<T> x);

      ImmutableSortedSet.Builder<T> sortedSetBuilder();

      Builder<T> setSortedMap(SortedMap<T, Integer> x);

      Builder<T> setSortedMap(NavigableMap<T, Integer> x);

      ImmutableSortedMap.Builder<T, Integer> sortedMapBuilder();

      BuilderWithImmutableSorted<T> build();
    }
  }

  @Test
  public void testBuilderWithImmutableSorted_Varargs() {
    BuilderWithImmutableSorted<String> x =
        BuilderWithImmutableSorted.<String>builder().setSortedSet("foo", "bar", "baz").build();
    assertThat(x.sortedSet()).containsExactly("bar", "baz", "foo").inOrder();
  }

  @Test
  public void testBuilderWithImmutableSorted_SetSet() {
    BuilderWithImmutableSorted<String> x =
        BuilderWithImmutableSorted.<String>builder()
            .setSortedSet(new TreeSet<String>(String.CASE_INSENSITIVE_ORDER))
            .build();
    assertThat(x.sortedSet().comparator()).isEqualTo(String.CASE_INSENSITIVE_ORDER);
  }

  @Test
  public void testBuilderWithImmutableSorted_SetMap() {
    BuilderWithImmutableSorted<String> x =
        BuilderWithImmutableSorted.<String>builder()
            .setSortedMap(new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER))
            .build();
    assertThat(x.sortedMap().comparator()).isEqualTo(String.CASE_INSENSITIVE_ORDER);
  }

  @Test
  public void testBuilderWithImmutableSorted_SetCollectionBuilder() {
    BuilderWithImmutableSorted.Builder<String> builder =
        BuilderWithImmutableSorted.<String>builder();
    builder.sortedSetBuilder().add("is", "ea", "id");
    BuilderWithImmutableSorted<String> x = builder.build();
    assertThat(x.sortedSet()).containsExactly("ea", "id", "is").inOrder();
  }

  @Test
  public void testBuilderWithImmutableSorted_MapCollectionBuilder() {
    BuilderWithImmutableSorted.Builder<String> builder =
        BuilderWithImmutableSorted.<String>builder();
    builder.sortedMapBuilder().put("two", 2).put("one", 1);
    BuilderWithImmutableSorted<String> x = builder.build();
    assertThat(x.sortedMap()).containsExactly("one", 1, "two", 2).inOrder();
  }

  @AutoValue
  public abstract static class BuilderWithCollectionBuilderAndSetter<T extends Number> {
    public abstract ImmutableList<T> things();

    public static <T extends Number> Builder<T> builder() {
      return new AutoValue_AutoValueTest_BuilderWithCollectionBuilderAndSetter.Builder<T>();
    }

    @AutoValue.Builder
    public interface Builder<T extends Number> {
      Builder<T> setThings(List<T> things);

      ImmutableList<T> things();

      ImmutableList.Builder<T> thingsBuilder();

      BuilderWithCollectionBuilderAndSetter<T> build();
    }
  }

  @Test
  public void testBuilderAndSetterDefaultsEmpty() {
    BuilderWithCollectionBuilderAndSetter.Builder<Integer> builder =
        BuilderWithCollectionBuilderAndSetter.<Integer>builder();
    assertThat(builder.things()).isEmpty();
    assertThat(builder.build().things()).isEmpty();
  }

  @Test
  public void testBuilderAndSetterUsingBuilder() {
    BuilderWithCollectionBuilderAndSetter.Builder<Integer> builder =
        BuilderWithCollectionBuilderAndSetter.builder();
    builder.thingsBuilder().add(17, 23);
    BuilderWithCollectionBuilderAndSetter<Integer> x = builder.build();
    assertThat(x.things()).isEqualTo(ImmutableList.of(17, 23));
  }

  @Test
  public void testBuilderAndSetterUsingSetter() {
    ImmutableList<Integer> things = ImmutableList.of(17, 23);
    BuilderWithCollectionBuilderAndSetter.Builder<Integer> builder =
        BuilderWithCollectionBuilderAndSetter.<Integer>builder().setThings(things);
    assertThat(builder.things()).isSameInstanceAs(things);
    assertThat(builder.build().things()).isSameInstanceAs(things);

    List<Integer> moreThings = Arrays.asList(5, 17, 23);
    BuilderWithCollectionBuilderAndSetter.Builder<Integer> builder2 =
        BuilderWithCollectionBuilderAndSetter.<Integer>builder().setThings(moreThings);
    assertThat(builder2.things()).isEqualTo(moreThings);
    assertThat(builder2.build().things()).isEqualTo(moreThings);
  }

  @Test
  public void testBuilderAndSetterUsingSetterThenBuilder() {
    BuilderWithCollectionBuilderAndSetter.Builder<Integer> builder =
        BuilderWithCollectionBuilderAndSetter.builder();
    builder.setThings(ImmutableList.of(5));
    builder.thingsBuilder().add(17, 23);
    List<Integer> expectedThings = ImmutableList.of(5, 17, 23);
    assertThat(builder.things()).isEqualTo(expectedThings);
    assertThat(builder.build().things()).isEqualTo(expectedThings);
  }

  @Test
  public void testBuilderAndSetterCannotSetAfterBuilder() {
    BuilderWithCollectionBuilderAndSetter.Builder<Integer> builder =
        BuilderWithCollectionBuilderAndSetter.builder();
    builder.setThings(ImmutableList.of(5));
    builder.thingsBuilder().add(17, 23);
    try {
      builder.setThings(ImmutableList.of(1729));
      fail("Setting list after retrieving builder should provoke an exception");
    } catch (IllegalStateException e) {
      if (omitIdentifiers) {
        assertThat(e).hasMessageThat().isNull();
      } else {
        assertThat(e).hasMessageThat().isEqualTo("Cannot set things after calling thingsBuilder()");
      }
    }
  }

  abstract static class AbstractParentWithBuilder {
    abstract String foo();

    abstract static class Builder<B extends Builder<B>> {
      abstract B foo(String s);
    }
  }

  @AutoValue
  abstract static class ChildWithBuilder extends AbstractParentWithBuilder {
    abstract String bar();

    static Builder builder() {
      return new AutoValue_AutoValueTest_ChildWithBuilder.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder extends AbstractParentWithBuilder.Builder<Builder> {
      abstract Builder bar(String s);

      abstract ChildWithBuilder build();
    }
  }

  @Test
  public void testInheritedBuilder() {
    ChildWithBuilder x = ChildWithBuilder.builder().foo("foo").bar("bar").build();
    assertThat(x.foo()).isEqualTo("foo");
    assertThat(x.bar()).isEqualTo("bar");
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface GwtCompatible {
    boolean funky() default false;
  }

  @AutoValue
  @GwtCompatible(funky = true)
  abstract static class GwtCompatibleTest {
    abstract int foo();

    static GwtCompatibleTest create(int foo) {
      return new AutoValue_AutoValueTest_GwtCompatibleTest(foo);
    }
  }

  @AutoValue
  @GwtCompatible
  abstract static class GwtCompatibleTestNoArgs {
    abstract String bar();

    static GwtCompatibleTestNoArgs create(String bar) {
      return new AutoValue_AutoValueTest_GwtCompatibleTestNoArgs(bar);
    }
  }

  @Test
  public void testGwtCompatibleInherited() {
    GwtCompatibleTest test = GwtCompatibleTest.create(23);
    GwtCompatible gwtCompatible = test.getClass().getAnnotation(GwtCompatible.class);
    assertNotNull(gwtCompatible);
    assertTrue(gwtCompatible.funky());

    GwtCompatibleTestNoArgs testNoArgs = GwtCompatibleTestNoArgs.create("23");
    GwtCompatible gwtCompatibleNoArgs = testNoArgs.getClass().getAnnotation(GwtCompatible.class);
    assertNotNull(gwtCompatibleNoArgs);
    assertFalse(gwtCompatibleNoArgs.funky());
  }

  @interface NestedAnnotation {
    int anInt();

    Class<?>[] aClassArray();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface HairyAnnotation {
    String aString();

    Class<? extends Number> aClass();

    RetentionPolicy anEnum();

    NestedAnnotation anAnnotation();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface CopiedAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @interface ExcludedAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @interface InheritedAnnotation {}

  @CopiedAnnotation
  @ExcludedAnnotation
  @InheritedAnnotation
  @AutoValue
  @AutoValue.CopyAnnotations(exclude = {ExcludedAnnotation.class})
  abstract static class CopyAnnotation {
    @HairyAnnotation(
        aString = "hello",
        aClass = Integer.class,
        anEnum = RetentionPolicy.RUNTIME,
        anAnnotation =
            @NestedAnnotation(
                anInt = 73,
                aClassArray = {String.class, Object.class}))
    abstract String field1();

    @CopiedAnnotation
    @ExcludedAnnotation
    @InheritedAnnotation
    @AutoValue.CopyAnnotations(exclude = {ExcludedAnnotation.class})
    abstract String field2();

    static CopyAnnotation create() {
      return new AutoValue_AutoValueTest_CopyAnnotation("field1", "field2");
    }
  }

  @Test
  public void testCopyClassAnnotations() throws Exception {
    CopyAnnotation x = CopyAnnotation.create();
    Class<?> c = x.getClass();
    assertNotSame(CopyAnnotation.class, c);

    // Sanity check: if these don't appear on CopyAnnotation, it makes no sense to assert that they
    // don't appear on the AutoValue_ subclass.
    {
      List<Class<? extends Annotation>> annotationsOnSuperclass =
          new ArrayList<Class<? extends Annotation>>();
      for (Annotation annotation : CopyAnnotation.class.getDeclaredAnnotations()) {
        annotationsOnSuperclass.add(annotation.annotationType());
      }
      assertThat(annotationsOnSuperclass)
          .containsAtLeast(
              CopiedAnnotation.class, ExcludedAnnotation.class, InheritedAnnotation.class);
    }

    {
      List<Class<? extends Annotation>> annotationsOnSubclass =
          new ArrayList<Class<? extends Annotation>>();
      for (Annotation annotation : c.getDeclaredAnnotations()) {
        annotationsOnSubclass.add(annotation.annotationType());
      }
      assertThat(annotationsOnSubclass).containsExactly(CopiedAnnotation.class);
    }
  }

  @Test
  public void testCopyMethodAnnotations() throws Exception {
    CopyAnnotation x = CopyAnnotation.create();
    Class<?> c = x.getClass();
    assertNotSame(CopyAnnotation.class, c);

    Method methodInSubclass = c.getDeclaredMethod("field2");
    Method methodInSuperclass = CopyAnnotation.class.getDeclaredMethod("field2");

    // Sanity check: if these don't appear on CopyAnnotation, it makes no sense to assert that they
    // don't appear on the AutoValue_ subclass.
    assertThat(methodInSuperclass.isAnnotationPresent(CopiedAnnotation.class)).isTrue();
    assertThat(methodInSuperclass.isAnnotationPresent(ExcludedAnnotation.class)).isTrue();
    assertThat(methodInSuperclass.isAnnotationPresent(InheritedAnnotation.class)).isTrue();

    assertThat(methodInSubclass.isAnnotationPresent(CopiedAnnotation.class)).isTrue();
    assertThat(methodInSubclass.isAnnotationPresent(ExcludedAnnotation.class)).isFalse();
    assertThat(methodInSubclass.isAnnotationPresent(InheritedAnnotation.class)).isTrue();
  }

  @Test
  public void testCopyMethodAnnotationsByDefault() throws Exception {
    CopyAnnotation x = CopyAnnotation.create();
    Class<?> c = x.getClass();
    assertNotSame(CopyAnnotation.class, c);
    Method methodInSubclass = c.getDeclaredMethod("field1");
    Method methodInSuperclass = CopyAnnotation.class.getDeclaredMethod("field1");
    assertNotSame(methodInSuperclass, methodInSubclass);
    HairyAnnotation annotationInSubclass = methodInSubclass.getAnnotation(HairyAnnotation.class);
    HairyAnnotation annotationInSuperclass =
        methodInSuperclass.getAnnotation(HairyAnnotation.class);
    assertEquals(annotationInSuperclass, annotationInSubclass);
  }

  @AutoValue
  abstract static class HProperty {
    public abstract Object h();

    public static HProperty create(Object h) {
      return new AutoValue_AutoValueTest_HProperty(h);
    }
  }

  @Test
  public void testHProperty() throws Exception {
    // Checks that we can have a property called `h`. The generated hashCode() method has
    // a local variable of that name and can cause the error `int cannot be dereferenced`
    HProperty.create(new Object());
  }

  interface Parent1 {
    int something();
  }

  interface Parent2 {
    int something();
  }

  @AutoValue
  abstract static class InheritSameMethodTwice implements Parent1, Parent2 {
    static InheritSameMethodTwice create(int something) {
      return new AutoValue_AutoValueTest_InheritSameMethodTwice(something);
    }
  }

  @Test
  public void testInheritSameMethodTwice() {
    InheritSameMethodTwice x = InheritSameMethodTwice.create(23);
    assertThat(x.something()).isEqualTo(23);
  }

  // Make sure we behave correctly when we inherit the same method definition from more than
  // one parent interface. We expect methods to appear in the order they are seen, with parents
  // preceding children, the superclass of a class preceding interfaces that class implements,
  // and an interface mentioned earlier in the "implements" clause preceding one mentioned later.
  // https://github.com/google/auto/issues/372
  interface OneTwoThreeFour {
    String one();

    String two();

    boolean three();

    long four();
  }

  interface TwoFour {
    String two();

    long four();
  }

  @AutoValue
  abstract static class OneTwoThreeFourImpl implements OneTwoThreeFour, TwoFour {
    static OneTwoThreeFourImpl create(String one, String two, boolean three, long four) {
      return new AutoValue_AutoValueTest_OneTwoThreeFourImpl(one, two, three, four);
    }
  }

  @Test
  public void testOneTwoThreeFour() {
    OneTwoThreeFour x = OneTwoThreeFourImpl.create("one", "two", false, 4);
    String expectedString =
        omitIdentifiers
            ? "{one, two, false, 4}"
            : "OneTwoThreeFourImpl{one=one, two=two, three=false, four=4}";
    assertThat(x.toString()).isEqualTo(expectedString);
  }

  @AutoValue
  abstract static class OuterWithBuilder {
    abstract String foo();

    abstract InnerWithBuilder inner();

    abstract Builder toBuilder();

    static Builder builder() {
      return new AutoValue_AutoValueTest_OuterWithBuilder.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder foo(String x);

      abstract Builder inner(InnerWithBuilder x);

      abstract InnerWithBuilder.Builder innerBuilder();

      abstract OuterWithBuilder build();
    }
  }

  @AutoValue
  abstract static class InnerWithBuilder {
    abstract int bar();

    abstract Builder toBuilder();

    static Builder builder() {
      return new AutoValue_AutoValueTest_InnerWithBuilder.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setBar(int x);

      abstract InnerWithBuilder build();
    }
  }

  @Test
  public void testBuilderWithinBuilder() {
    OuterWithBuilder x =
        OuterWithBuilder.builder()
            .inner(InnerWithBuilder.builder().setBar(23).build())
            .foo("yes")
            .build();
    String expectedStringX =
        omitIdentifiers
            ? "{yes, {23}}"
            : "OuterWithBuilder{foo=yes, inner=InnerWithBuilder{bar=23}}";
    assertThat(x.toString()).isEqualTo(expectedStringX);

    OuterWithBuilder.Builder xBuilder = x.toBuilder();
    xBuilder.innerBuilder().setBar(17);
    OuterWithBuilder y = xBuilder.build();
    String expectedStringY =
        omitIdentifiers
            ? "{yes, {17}}"
            : "OuterWithBuilder{foo=yes, inner=InnerWithBuilder{bar=17}}";
    assertThat(y.toString()).isEqualTo(expectedStringY);
  }

  public static class MyMap<K, V> extends HashMap<K, V> {
    private static final long serialVersionUID = 1L;

    public MyMap() {}

    public MyMap(Map<K, V> map) {
      super(map);
    }
  }

  public static class MyMapBuilder<K, V> extends LinkedHashMap<K, V> {
    private static final long serialVersionUID = 1L;

    public MyMapBuilder() {}

    public MyMapBuilder(Map<K, V> map) {
      super(map);
    }

    public MyMap<K, V> build() {
      return new MyMap<K, V>(this);
    }
  }

  @AutoValue
  abstract static class BuildMyMap<K, V> {
    abstract MyMap<K, V> map();

    abstract Builder<K, V> toBuilder();

    static <K, V> Builder<K, V> builder() {
      return new AutoValue_AutoValueTest_BuildMyMap.Builder<K, V>();
    }

    @AutoValue.Builder
    abstract static class Builder<K, V> {
      abstract MyMapBuilder<K, V> mapBuilder();

      abstract BuildMyMap<K, V> build();
    }
  }

  @Test
  public void testMyMapBuilder() {
    BuildMyMap.Builder<String, Integer> builder = BuildMyMap.builder();
    MyMapBuilder<String, Integer> mapBuilder = builder.mapBuilder();
    mapBuilder.put("23", 23);
    BuildMyMap<String, Integer> built = builder.build();
    assertThat(built.map()).containsExactly("23", 23);

    BuildMyMap.Builder<String, Integer> builder2 = built.toBuilder();
    MyMapBuilder<String, Integer> mapBuilder2 = builder2.mapBuilder();
    mapBuilder2.put("17", 17);
    BuildMyMap<String, Integer> built2 = builder2.build();
    assertThat(built2.map()).containsExactly("23", 23, "17", 17);
  }

  public static class MyStringMap<V> extends MyMap<String, V> {
    private static final long serialVersionUID = 1L;

    public MyStringMap() {}

    public MyStringMap(Map<String, V> map) {
      super(map);
    }

    public MyStringMapBuilder<V> toBuilder() {
      return new MyStringMapBuilder<V>(this);
    }
  }

  public static class MyStringMapBuilder<V> extends MyMapBuilder<String, V> {
    private static final long serialVersionUID = 1L;

    public MyStringMapBuilder() {}

    public MyStringMapBuilder(Map<String, V> map) {
      super(map);
    }

    @Override
    public MyStringMap<V> build() {
      return new MyStringMap<V>(this);
    }
  }

  @AutoValue
  abstract static class BuildMyStringMap<V> {
    abstract MyStringMap<V> map();

    abstract Builder<V> toBuilder();

    static <V> Builder<V> builder() {
      return new AutoValue_AutoValueTest_BuildMyStringMap.Builder<V>();
    }

    @AutoValue.Builder
    abstract static class Builder<V> {
      abstract MyStringMapBuilder<V> mapBuilder();

      abstract BuildMyStringMap<V> build();
    }
  }

  @Test
  public void testMyStringMapBuilder() {
    BuildMyStringMap.Builder<Integer> builder = BuildMyStringMap.builder();
    MyStringMapBuilder<Integer> mapBuilder = builder.mapBuilder();
    mapBuilder.put("23", 23);
    BuildMyStringMap<Integer> built = builder.build();
    assertThat(built.map()).containsExactly("23", 23);

    BuildMyStringMap.Builder<Integer> builder2 = built.toBuilder();
    MyStringMapBuilder<Integer> mapBuilder2 = builder2.mapBuilder();
    mapBuilder2.put("17", 17);
    BuildMyStringMap<Integer> built2 = builder2.build();
    assertThat(built2.map()).containsExactly("17", 17, "23", 23);
  }

  @AutoValue
  abstract static class BuilderOfManyAccessLevels {
    public abstract int publicGetterProtectedBuilderGetterPackageProtectedSetterInt();

    protected abstract int protectedGetterPackageProtectedBuilderGetterPublicSetterInt();

    abstract int packageProtectedGetterPublicBuilderGetterProtectedSetterInt();

    @AutoValue.Builder
    public abstract static class Builder {
      protected abstract int publicGetterProtectedBuilderGetterPackageProtectedSetterInt();

      abstract int protectedGetterPackageProtectedBuilderGetterPublicSetterInt();

      public abstract int packageProtectedGetterPublicBuilderGetterProtectedSetterInt();

      abstract Builder setPublicGetterProtectedBuilderGetterPackageProtectedSetterInt(int x);

      public abstract Builder setProtectedGetterPackageProtectedBuilderGetterPublicSetterInt(int x);

      protected abstract Builder setPackageProtectedGetterPublicBuilderGetterProtectedSetterInt(
          int x);

      public abstract BuilderOfManyAccessLevels build();
    }
  }

  @Test
  public void testBuilderOfManyAccessLevels_accessLevels() throws NoSuchMethodException {
    Class<?> builderClass = AutoValue_AutoValueTest_BuilderOfManyAccessLevels.Builder.class;

    testMethodAccess(
        Access.PROTECTED,
        builderClass,
        "publicGetterProtectedBuilderGetterPackageProtectedSetterInt");
    testMethodAccess(
        Access.PACKAGE,
        builderClass,
        "protectedGetterPackageProtectedBuilderGetterPublicSetterInt");
    testMethodAccess(
        Access.PUBLIC, builderClass, "packageProtectedGetterPublicBuilderGetterProtectedSetterInt");

    testMethodAccess(
        Access.PACKAGE,
        builderClass,
        "setPublicGetterProtectedBuilderGetterPackageProtectedSetterInt",
        int.class);
    testMethodAccess(
        Access.PUBLIC,
        builderClass,
        "setProtectedGetterPackageProtectedBuilderGetterPublicSetterInt",
        int.class);
    testMethodAccess(
        Access.PROTECTED,
        builderClass,
        "setPackageProtectedGetterPublicBuilderGetterProtectedSetterInt",
        int.class);
  }

  private enum Access {
    PRIVATE,
    PACKAGE,
    PROTECTED,
    PUBLIC
  }

  private static final ImmutableMap<Integer, Access> MODIFIER_BITS_TO_ACCESS =
      ImmutableMap.of(
          Modifier.PUBLIC,
          Access.PUBLIC,
          Modifier.PROTECTED,
          Access.PROTECTED,
          Modifier.PRIVATE,
          Access.PRIVATE,
          0,
          Access.PACKAGE);

  private static void testMethodAccess(
      Access expectedAccess, Class<?> clazz, String methodName, Class<?>... parameterTypes)
      throws NoSuchMethodException {
    Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
    int modBits = method.getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE);
    Access actualAccess = MODIFIER_BITS_TO_ACCESS.get(modBits);
    assertWithMessage("Wrong access for %s", methodName)
        .that(actualAccess)
        .isEqualTo(expectedAccess);
  }

  static class VersionId {}

  static class ItemVersionId extends VersionId {}

  interface VersionedPersistent {
    VersionId getVersionId();
  }

  interface Item extends VersionedPersistent {
    @Override
    ItemVersionId getVersionId();
  }

  @AutoValue
  abstract static class FakeItem implements Item {
    static Builder builder() {
      return new AutoValue_AutoValueTest_FakeItem.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setVersionId(ItemVersionId x);

      abstract FakeItem build();
    }
  }

  @Test
  public void testParentInterfaceOverridesGrandparent() {
    ItemVersionId version = new ItemVersionId();
    FakeItem fakeItem = FakeItem.builder().setVersionId(version).build();
    assertThat(fakeItem.getVersionId()).isSameInstanceAs(version);
  }

  /** Fake ApkVersionCode class. */
  public static class ApkVersionCode {}

  /**
   * Illustrates a potential problem that showed up while generalizing builders. If our imports are
   * not accurate we may end up importing ImmutableList.Builder, which won't work because the
   * generated Builder subclass of ReleaseInfoBuilder will supersede it. Normally we wouldn't import
   * ImmutableList.Builder because the nested Builder class in the {@code @AutoValue} class would
   * prevent us trying. But in this case the nested class is called ReleaseInfoBuilder so we might
   * import anyway if we're not careful. This is one reason why we moved away from importing nested
   * classes to only importing top-level classes.
   */
  @AutoValue
  public abstract static class ReleaseInfo {
    public static ReleaseInfoBuilder newBuilder() {
      return new AutoValue_AutoValueTest_ReleaseInfo.Builder();
    }

    public abstract ImmutableList<ApkVersionCode> apkVersionCodes();

    ReleaseInfo() {}

    /** Notice that this is called ReleaseInfoBuilder and not Builder. */
    @AutoValue.Builder
    public abstract static class ReleaseInfoBuilder {
      public ReleaseInfoBuilder addApkVersionCode(ApkVersionCode code) {
        apkVersionCodesBuilder().add(code);
        return this;
      }

      abstract ImmutableList.Builder<ApkVersionCode> apkVersionCodesBuilder();

      public abstract ReleaseInfo build();
    }
  }

  @Test
  public void testUnusualBuilderName() {
    ApkVersionCode apkVersionCode = new ApkVersionCode();
    ReleaseInfo x = ReleaseInfo.newBuilder().addApkVersionCode(apkVersionCode).build();
    assertThat(x.apkVersionCodes()).containsExactly(apkVersionCode);
  }

  @AutoValue
  public abstract static class OuterWithDefaultableInner {
    public abstract ImmutableList<String> names();

    public abstract DefaultableInner inner();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_OuterWithDefaultableInner.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract ImmutableList<String> names();

      public abstract ImmutableList.Builder<String> namesBuilder();

      public abstract DefaultableInner inner();

      public abstract DefaultableInner.Builder innerBuilder();

      public abstract OuterWithDefaultableInner build();
    }
  }

  @AutoValue
  public abstract static class DefaultableInner {
    public abstract int bar();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_DefaultableInner.Builder().setBar(23);
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setBar(int x);

      public abstract DefaultableInner build();
    }
  }

  @Test
  public void testOuterWithDefaultableInner_Defaults() {
    DefaultableInner defaultInner = DefaultableInner.builder().build();
    OuterWithDefaultableInner x = OuterWithDefaultableInner.builder().build();
    assertThat(x.names()).isEmpty();
    assertThat(x.inner()).isEqualTo(defaultInner);
  }

  @Test
  public void testOuterWithDefaultableInner_Getters() {
    DefaultableInner defaultInner = DefaultableInner.builder().build();

    OuterWithDefaultableInner.Builder builder = OuterWithDefaultableInner.builder();
    assertThat(builder.names()).isEmpty();
    assertThat(builder.inner()).isEqualTo(defaultInner);

    OuterWithDefaultableInner x1 = builder.build();
    assertThat(x1.names()).isEmpty();
    assertThat(x1.inner()).isEqualTo(defaultInner);

    builder.namesBuilder().add("Fred");
    builder.innerBuilder().setBar(17);
    OuterWithDefaultableInner x2 = builder.build();
    assertThat(x2.names()).containsExactly("Fred");
    assertThat(x2.inner().bar()).isEqualTo(17);
  }

  @AutoValue
  public abstract static class OuterWithNonDefaultableInner<T> {
    public abstract int foo();

    public abstract NonDefaultableInner<T> inner();

    public static <T> Builder<T> builder() {
      return new AutoValue_AutoValueTest_OuterWithNonDefaultableInner.Builder<T>();
    }

    @AutoValue.Builder
    public abstract static class Builder<T> {
      public abstract Builder<T> setFoo(int x);

      public abstract NonDefaultableInner.Builder<T> innerBuilder();

      public abstract OuterWithNonDefaultableInner<T> build();
    }
  }

  @AutoValue
  public abstract static class NonDefaultableInner<E> {
    public abstract E bar();

    public static <E> Builder<E> builder() {
      return new AutoValue_AutoValueTest_NonDefaultableInner.Builder<E>();
    }

    @AutoValue.Builder
    public abstract static class Builder<E> {
      public abstract Builder<E> setBar(E x);

      public abstract NonDefaultableInner<E> build();
    }
  }

  @Test
  public void testOuterWithNonDefaultableInner() {
    OuterWithNonDefaultableInner.Builder<String> builder = OuterWithNonDefaultableInner.builder();
    builder.setFoo(23);
    try {
      builder.build();
      fail("Did not get expected exception for unbuilt inner instance");
    } catch (IllegalStateException expected) {
    }
  }

  @SuppressWarnings("JavaLangClash")
  @AutoValue
  public abstract static class RedeclareJavaLangClasses {
    // If you really really want to do this, we have you covered.

    public static class Object {}

    public static class String {}

    public abstract Object alienObject();

    public abstract String alienString();

    public static Builder builder() {
      return new AutoValue_AutoValueTest_RedeclareJavaLangClasses.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setAlienObject(Object x);

      public abstract Builder setAlienString(String x);

      public abstract RedeclareJavaLangClasses build();
    }
  }

  @Test
  public void testRedeclareJavaLangClasses() {
    RedeclareJavaLangClasses x =
        RedeclareJavaLangClasses.builder()
            .setAlienObject(new RedeclareJavaLangClasses.Object())
            .setAlienString(new RedeclareJavaLangClasses.String())
            .build();
    assertThat(x).isNotNull();
  }

  // b/28382293
  @AutoValue
  abstract static class GenericExtends {
    abstract ImmutableSet<Number> metrics();

    static Builder builder() {
      return new AutoValue_AutoValueTest_GenericExtends.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setMetrics(ImmutableSet<? extends Number> metrics);

      abstract GenericExtends build();
    }
  }

  @Test
  public void testGenericExtends() {
    ImmutableSet<Integer> ints = ImmutableSet.of(1, 2, 3);
    GenericExtends g = GenericExtends.builder().setMetrics(ints).build();
    assertThat(g.metrics()).isEqualTo(ints);
  }

  abstract static class Parent<T> {
    abstract List<T> getList();
  }

  @AutoValue
  abstract static class Child extends Parent<String> {
    static Builder builder() {
      return new AutoValue_AutoValueTest_Child.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setList(List<String> list);

      abstract Child build();
    }
  }

  @Test
  public void nonGenericExtendsGeneric() {
    List<String> list = ImmutableList.of("foo", "bar", "baz");
    Child child = Child.builder().setList(list).build();
    assertThat(child.getList()).containsExactlyElementsIn(list).inOrder();
  }

  abstract static class AbstractGenericParentWithBuilder<T> {
    abstract T foo();

    abstract static class Builder<T, B extends Builder<T, B>> {
      abstract B foo(T s);
    }
  }

  @AutoValue
  abstract static class ChildOfAbstractGenericParentWithBuilder<T>
      extends AbstractGenericParentWithBuilder<T> {
    static <T> Builder<T> builder() {
      return new AutoValue_AutoValueTest_ChildOfAbstractGenericParentWithBuilder.Builder<T>();
    }

    @AutoValue.Builder
    abstract static class Builder<T>
        extends AbstractGenericParentWithBuilder.Builder<T, Builder<T>> {
      abstract ChildOfAbstractGenericParentWithBuilder<T> build();
    }
  }

  @Test
  public void genericExtendsGeneric() {
    ChildOfAbstractGenericParentWithBuilder<String> child =
        ChildOfAbstractGenericParentWithBuilder.<String>builder().foo("foo").build();
    assertThat(child.foo()).isEqualTo("foo");
  }

  @SuppressWarnings("ClassCanBeStatic")
  static class OuterWithTypeParam<T extends Number> {
    class InnerWithTypeParam<U> {}

    class InnerWithoutTypeParam {}

    static class Nested {}
  }

  @AutoValue
  abstract static class Nesty {
    abstract OuterWithTypeParam<Double>.InnerWithTypeParam<String> innerWithTypeParam();

    abstract OuterWithTypeParam<Double>.InnerWithoutTypeParam innerWithoutTypeParam();

    abstract OuterWithTypeParam.Nested nested();

    static Builder builder() {
      return new AutoValue_AutoValueTest_Nesty.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setInnerWithTypeParam(
          OuterWithTypeParam<Double>.InnerWithTypeParam<String> x);

      abstract Builder setInnerWithoutTypeParam(OuterWithTypeParam<Double>.InnerWithoutTypeParam x);

      abstract Builder setNested(OuterWithTypeParam.Nested x);

      abstract Nesty build();
    }
  }

  @Test
  public void outerWithTypeParam() throws ReflectiveOperationException {
    @SuppressWarnings("UseDiamond") // Currently we compile this with -source 6 in the Eclipse test.
    OuterWithTypeParam<Double> outer = new OuterWithTypeParam<Double>();
    Nesty nesty =
        Nesty.builder()
            .setInnerWithTypeParam(outer.new InnerWithTypeParam<String>())
            .setInnerWithoutTypeParam(outer.new InnerWithoutTypeParam())
            .setNested(new OuterWithTypeParam.Nested())
            .build();
    Type originalReturnType =
        Nesty.class.getDeclaredMethod("innerWithTypeParam").getGenericReturnType();
    Type generatedReturnType =
        nesty.getClass().getDeclaredMethod("innerWithTypeParam").getGenericReturnType();
    assertThat(generatedReturnType).isEqualTo(originalReturnType);
    Type generatedBuilderParamType =
        Nesty.builder()
            .getClass()
            .getDeclaredMethod("setInnerWithTypeParam", OuterWithTypeParam.InnerWithTypeParam.class)
            .getGenericParameterTypes()[0];
    assertThat(generatedBuilderParamType).isEqualTo(originalReturnType);
  }

  @AutoValue
  abstract static class BuilderAnnotationsNotCopied {
    abstract String foo();

    static Builder builder() {
      return new AutoValue_AutoValueTest_BuilderAnnotationsNotCopied.Builder();
    }

    @AutoValue.Builder
    @MyAnnotation("thing")
    abstract static class Builder {
      abstract Builder setFoo(String x);

      abstract BuilderAnnotationsNotCopied build();
    }
  }

  @Test
  public void builderAnnotationsNotCopiedByDefault() {
    BuilderAnnotationsNotCopied.Builder builder = BuilderAnnotationsNotCopied.builder();
    assertThat(builder.getClass().getAnnotations()).isEmpty();
    assertThat(builder.setFoo("foo").build().foo()).isEqualTo("foo");
  }

  @AutoValue
  abstract static class BuilderAnnotationsCopied {
    abstract String foo();

    static Builder builder() {
      return new AutoValue_AutoValueTest_BuilderAnnotationsCopied.Builder();
    }

    @AutoValue.Builder
    @AutoValue.CopyAnnotations
    @MyAnnotation("thing")
    abstract static class Builder {
      abstract Builder setFoo(String x);

      abstract BuilderAnnotationsCopied build();
    }
  }

  @Test
  public void builderAnnotationsCopiedIfRequested() {
    BuilderAnnotationsCopied.Builder builder = BuilderAnnotationsCopied.builder();
    assertThat(builder.getClass().getAnnotations()).asList().containsExactly(myAnnotation("thing"));
    assertThat(builder.setFoo("foo").build().foo()).isEqualTo("foo");
  }

  @AutoValue
  @AutoValue.CopyAnnotations
  @SuppressWarnings({"rawtypes", "unchecked"}) // deliberately checking handling of raw types
  abstract static class DataWithSortedCollectionBuilders<K, V> {
    abstract ImmutableSortedMap<K, V> anImmutableSortedMap();

    abstract ImmutableSortedSet<V> anImmutableSortedSet();

    abstract ImmutableSortedMap<Integer, V> nonGenericImmutableSortedMap();

    abstract ImmutableSortedSet rawImmutableSortedSet();

    abstract DataWithSortedCollectionBuilders.Builder<K, V> toBuilder();

    static <K, V> DataWithSortedCollectionBuilders.Builder<K, V> builder() {
      return new AutoValue_AutoValueTest_DataWithSortedCollectionBuilders.Builder<K, V>();
    }

    @AutoValue.Builder
    abstract static class Builder<K, V> {
      abstract DataWithSortedCollectionBuilders.Builder<K, V> anImmutableSortedMap(
          SortedMap<K, V> anImmutableSortedMap);

      abstract ImmutableSortedMap.Builder<K, V> anImmutableSortedMapBuilder(
          Comparator<K> keyComparator);

      abstract DataWithSortedCollectionBuilders.Builder<K, V> anImmutableSortedSet(
          SortedSet<V> anImmutableSortedSet);

      abstract ImmutableSortedSet.Builder<V> anImmutableSortedSetBuilder(Comparator<V> comparator);

      abstract ImmutableSortedMap.Builder<Integer, V> nonGenericImmutableSortedMapBuilder(
          Comparator<Integer> keyComparator);

      abstract ImmutableSortedSet.Builder rawImmutableSortedSetBuilder(Comparator comparator);

      abstract DataWithSortedCollectionBuilders<K, V> build();
    }
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"}) // deliberately checking handling of raw types
  public void shouldGenerateBuildersWithComparators() {
    Comparator<String> stringComparator =
        new Comparator<String>() {
          @Override
          public int compare(String left, String right) {
            return left.compareTo(right);
          }
        };

    Comparator<Integer> intComparator =
        new Comparator<Integer>() {
          @Override
          public int compare(Integer o1, Integer o2) {
            return o1 - o2;
          }
        };

    Comparator comparator =
        new Comparator() {
          @Override
          public int compare(Object left, Object right) {
            return String.valueOf(left).compareTo(String.valueOf(right));
          }
        };

    AutoValueTest.DataWithSortedCollectionBuilders.Builder<String, Integer> builder =
        AutoValueTest.DataWithSortedCollectionBuilders.builder();

    builder
        .anImmutableSortedMapBuilder(stringComparator)
        .put("Charlie", 1)
        .put("Alfa", 2)
        .put("Bravo", 3);
    builder.anImmutableSortedSetBuilder(intComparator).add(1, 5, 9, 3);
    builder.nonGenericImmutableSortedMapBuilder(intComparator).put(9, 99).put(1, 11).put(3, 33);
    builder.rawImmutableSortedSetBuilder(comparator).add("Bravo", "Charlie", "Alfa");

    AutoValueTest.DataWithSortedCollectionBuilders<String, Integer> data = builder.build();

    AutoValueTest.DataWithSortedCollectionBuilders.Builder<String, Integer> copiedBuilder =
        data.toBuilder();
    AutoValueTest.DataWithSortedCollectionBuilders<String, Integer> copiedData =
        copiedBuilder.build();

    assertThat(data.anImmutableSortedMap().keySet())
        .containsExactly("Alfa", "Bravo", "Charlie")
        .inOrder();
    assertThat(data.anImmutableSortedSet()).containsExactly(1, 3, 5, 9).inOrder();
    assertThat(data.nonGenericImmutableSortedMap().keySet()).containsExactly(1, 3, 9).inOrder();
    assertThat(data.rawImmutableSortedSet()).containsExactly("Alfa", "Bravo", "Charlie").inOrder();

    assertThat(copiedData).isEqualTo(data);

    try {
      builder.anImmutableSortedMapBuilder(Ordering.from(stringComparator).reverse());
      fail("Calling property builder method a second time should have failed");
    } catch (IllegalStateException expected) {
    }
  }
}
