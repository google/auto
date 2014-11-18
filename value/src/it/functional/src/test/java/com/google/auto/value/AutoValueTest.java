/*
 * Copyright (C) 2012 The Guava Authors
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

import com.google.auto.value.AutoValue;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.SerializableTester;

import junit.framework.TestCase;

import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class AutoValueTest extends TestCase {

  // TODO(user): add tests for exotic locales

  @AutoValue
  abstract static class Simple {
    public abstract String publicString();
    protected abstract int protectedInt();
    abstract Map<String, Long> packageMap();
    public static Simple create(String s, int i, Map<String, Long> m) {
      return new AutoValue_AutoValueTest_Simple(s, i, m);
    }
  }
  public void testSimple() throws Exception {
    Simple instance1a = Simple.create("example", 23, ImmutableMap.of("twenty-three", 23L));
    Simple instance1b = Simple.create("example", 23, ImmutableMap.of("twenty-three", 23L));
    Simple instance2 = Simple.create("", 0, ImmutableMap.<String, Long>of());
    assertEquals("example", instance1a.publicString());
    assertEquals(23, instance1a.protectedInt());
    assertEquals(ImmutableMap.of("twenty-three", 23L), instance1a.packageMap());
    Objects.ToStringHelper toStringHelper = Objects.toStringHelper(Simple.class);
    toStringHelper.add("publicString", "example");
    toStringHelper.add("protectedInt", 23);
    toStringHelper.add("packageMap", ImmutableMap.of("twenty-three", 23L));
    assertEquals(toStringHelper.toString(), instance1a.toString());
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

  public void testEmpty() throws Exception {
    Empty instance = Empty.create();
    assertEquals("Empty{}", instance.toString());
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

  public void testGetters() {
    SimpleWithGetters instance = SimpleWithGetters.create(23, true, false, "foo", "bar", "<html>");
    assertEquals(
        "SimpleWithGetters{"
            + "foo=23, bar=true, otherBar=false, package=foo, package0=bar, HTMLPage=<html>}",
        instance.toString());
  }

  @AutoValue
  abstract static class NotAllGetters {
    abstract int getFoo();
    abstract boolean bar();

    static NotAllGetters create(int foo, boolean bar) {
      return new AutoValue_AutoValueTest_NotAllGetters(foo, bar);
    }
  }

  public void testNotGetters() {
    NotAllGetters instance = NotAllGetters.create(23, true);
    assertEquals("NotAllGetters{getFoo=23, bar=true}", instance.toString());
  }

  @AutoValue
  public abstract static class Serialize implements Serializable {
    public abstract int integer();
    public abstract String string();
    public abstract BigInteger bigInteger();
    public static Serialize create(int integer, String string, BigInteger bigInteger) {
      return new AutoValue_AutoValueTest_Serialize(integer, string, bigInteger);
    }
  }

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

  public void testDoubleHashCode() {
    double doubleValue = 12345678901234567890d;
    DoubleProperty doubleProperty = DoubleProperty.create(doubleValue);
    assertEquals(singlePropertyHash(doubleValue), doubleProperty.hashCode());
  }

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
    @Nullable abstract String nullableString();
    abstract int randomInt();
    static NullableProperties create(@Nullable String nullableString, int randomInt) {
      return new AutoValue_AutoValueTest_NullableProperties(nullableString, randomInt);
    }
  }

  public void testNullablePropertiesCanBeNull() throws Exception {
    NullableProperties instance = NullableProperties.create(null, 23);
    assertNull(instance.nullableString());
    assertEquals(23, instance.randomInt());
    assertEquals("NullableProperties{nullableString=null, randomInt=23}", instance.toString());
  }

  @AutoValue
  abstract static class AlternativeNullableProperties {
    @interface Nullable {}
    @AlternativeNullableProperties.Nullable abstract String nullableString();
    abstract int randomInt();
    static AlternativeNullableProperties create(@Nullable String nullableString, int randomInt) {
      return new AutoValue_AutoValueTest_AlternativeNullableProperties(nullableString, randomInt);
    }
  }

  public void testNullableCanBeFromElsewhere() throws Exception {
    AlternativeNullableProperties instance = AlternativeNullableProperties.create(null, 23);
    assertNull(instance.nullableString());
    assertEquals(23, instance.randomInt());
    assertEquals(
        "AlternativeNullableProperties{nullableString=null, randomInt=23}", instance.toString());
  }

  @AutoValue
  abstract static class NonNullableProperties {
    abstract String nonNullableString();
    abstract int randomInt();
    static NonNullableProperties create(String nonNullableString, int randomInt) {
      return new AutoValue_AutoValueTest_NonNullableProperties(nonNullableString, randomInt);
    }
  }

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

  static class Nested {
    @AutoValue
    abstract static class Doubly {
      @Nullable abstract String nullableString();
      abstract int randomInt();
      static Doubly create(String nullableString, int randomInt) {
        return new AutoValue_AutoValueTest_Nested_Doubly(nullableString, randomInt);
      }
    }
  }

  public void testDoublyNestedClass() throws Exception {
    Nested.Doubly instance = Nested.Doubly.create(null, 23);
    assertNull(instance.nullableString());
    assertEquals(23, instance.randomInt());
    assertEquals("Doubly{nullableString=null, randomInt=23}", instance.toString());
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

  public void testClassNestedInInterface() throws Exception {
    Map<String, Integer> map = ImmutableMap.of("vingt-et-un", 21);
    NestedInInterface.Doubly instance = NestedInInterface.Doubly.create("foo", map);
    assertEquals("foo", instance.string());
    assertEquals(map, instance.map());
  }

  @AutoValue
  abstract static class NullableNonNullable {
    @Nullable abstract String nullableString();
    @Nullable abstract String otherNullableString();
    abstract String nonNullableString();
    static NullableNonNullable create(
        String nullableString, String otherNullableString, String nonNullableString) {
      return new AutoValue_AutoValueTest_NullableNonNullable(
          nullableString, otherNullableString, nonNullableString);
    }
  }

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

  public @interface Redacted {
  }

  @AutoValue
  abstract static class RedactedProperties {
    @Nullable @Redacted abstract String redactedString();
    @Nullable @Redacted abstract String otherRedactedString();
    static RedactedProperties create(String redactedString, String otherRedactedString) {
      return new AutoValue_AutoValueTest_RedactedProperties(redactedString, otherRedactedString);
    }
  }

  public void testRedactedPropertiesNonNull() {
    RedactedProperties instance =
        RedactedProperties.create("redactedString", "otherRedactedString");
    assertEquals(
        "RedactedProperties{redactedString=██, otherRedactedString=██}", instance.toString());
  }

  public void testRedactedPropertiesNull() {
    RedactedProperties instance = RedactedProperties.create(null, null);
    assertEquals(
        "RedactedProperties{redactedString=null, otherRedactedString=null}", instance.toString());
  }

  @AutoValue
  abstract static class RedactedNonRedacted {
    @Redacted abstract String redactedString();
    abstract String nonRedactedString();
    static RedactedNonRedacted create(String redactedString, String nonRedactedString) {
      return new AutoValue_AutoValueTest_RedactedNonRedacted(redactedString, nonRedactedString);
    }
  }
  public void testRedactedNonRedacted() {
    RedactedNonRedacted instance =
        RedactedNonRedacted.create("redactedString", "nonRedactedString");
    assertEquals("RedactedNonRedacted{redactedString=██, nonRedactedString=nonRedactedString}",
        instance.toString());
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

  public void testGenericProperties() throws Exception {
    GenericProperties instance1 = GenericProperties.create(
      ImmutableMap.of("twenty-three", 23),
      ImmutableMap.of("very", (Map<String, Integer>) ImmutableMap.of("hairy", 17)));
    GenericProperties instance2 = GenericProperties.create(
      ImmutableMap.of("seventeen", 17),
      ImmutableMap.of("very", (Map<String, Integer>) ImmutableMap.of("hairy", 23)));
    new EqualsTester()
        .addEqualityGroup(instance1)
        .addEqualityGroup(instance2)
        .testEquals();
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
  public void testGenericClassWithHairyBounds() throws Exception {
    class ComparableList<E> extends ArrayList<E> implements Comparable<ComparableList<E>> {
      @Override public int compareTo(ComparableList<E> list) {
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
  static abstract class Delta<M extends Mergeable<M>> {
    abstract M meta();

    static <M extends Mergeable<M>> Delta<M> create(M meta) {
      return new AutoValue_AutoValueTest_Delta<M>(meta);
    }
  }

  public void testRecursiveGeneric() {
    class MergeableImpl implements Mergeable<MergeableImpl> {
      @Override public MergeableImpl merge(MergeableImpl other) {
        return this;
      }
    }
    MergeableImpl mergeable = new MergeableImpl();
    Delta<MergeableImpl> instance = Delta.create(mergeable);
    assertSame(mergeable, instance.meta());
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
  public void testAbstractToString() throws Exception {
    AbstractToString instance = AbstractToString.create("foo");
    assertEquals("AbstractToString{string=foo}", instance.toString());
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
  public void testInheritedAbstractToString() throws Exception {
    SubAbstractToString instance = SubAbstractToString.create("foo");
    assertEquals("SubAbstractToString{string=foo}", instance.toString());
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

  public void testExplicitHashCode() throws Exception {
    ExplicitHashCode instance = ExplicitHashCode.create("foo");
    assertEquals(1234, instance.hashCode());
  }

  @AutoValue
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

  @AutoValue
  abstract static class PrimitiveArrays {
    abstract boolean[] booleans();
    @Nullable abstract int[] ints();

    static PrimitiveArrays create(boolean[] booleans, int[] ints) {
      // Real code would likely clone these parameters, but here we want to check that the
      // generated constructor rejects a null value for booleans.
      return new AutoValue_AutoValueTest_PrimitiveArrays(booleans, ints);
    }
  }

  public void testPrimitiveArrays() {
    PrimitiveArrays object0 = PrimitiveArrays.create(new boolean[0], new int[0]);
    boolean[] booleans = {false, true, true, false};
    int[] ints = {6, 28, 496, 8128, 33550336};
    PrimitiveArrays object1 = PrimitiveArrays.create(booleans.clone(), ints.clone());
    PrimitiveArrays object2 = PrimitiveArrays.create(booleans.clone(), ints.clone());
    new EqualsTester()
        .addEqualityGroup(object1, object2)
        .addEqualityGroup(object0)
        .testEquals();
    // EqualsTester also exercises hashCode(). We clone the arrays above to ensure that using the
    // default Object.hashCode() will fail.

    String expectedString = "PrimitiveArrays{booleans=" + Arrays.toString(booleans) + ", "
        + "ints=" + Arrays.toString(ints) + "}";
    assertEquals(expectedString, object1.toString());

    // Check that getters clone the arrays so callers can't change them.
    object1.ints()[0]++;
    assertTrue(Arrays.equals(ints, object1.ints()));
  }

  public void testNullablePrimitiveArrays() {
    PrimitiveArrays object0 = PrimitiveArrays.create(new boolean[0], null);
    boolean[] booleans = {false, true, true, false};
    PrimitiveArrays object1 = PrimitiveArrays.create(booleans.clone(), null);
    PrimitiveArrays object2 = PrimitiveArrays.create(booleans.clone(), null);
    new EqualsTester()
        .addEqualityGroup(object1, object2)
        .addEqualityGroup(object0)
        .testEquals();

    String expectedString = "PrimitiveArrays{booleans=" + Arrays.toString(booleans) + ", "
        + "ints=null}";
    assertEquals(expectedString, object1.toString());

    object1.booleans()[0] ^= true;
    assertTrue(Arrays.equals(booleans, object1.booleans()));
  }

  public void testNotNullablePrimitiveArrays() {
    try {
      PrimitiveArrays.create(null, new int[0]);
      fail("Construction with null value for non-@Nullable array should have failed");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("booleans"));
    }
  }

  // If users are mad enough to define their own Arrays class and have some properties of that
  // class and others of primitive array type, then we can't import java.util.Arrays.
  // This is unlikely.
  @AutoValue
  abstract static class AmbiguousArrays {
    static class Arrays {}

    abstract Arrays arrays();
    abstract int[] ints();

    static AmbiguousArrays create(Arrays arrays, int[] ints) {
      return new AutoValue_AutoValueTest_AmbiguousArrays(arrays, ints);
    }
  }

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

  public void testHashCodeNotCached() {
    HashCodeObserver observer = new HashCodeObserver();
    MaybeCachedHashCode maybeCached = MaybeCachedHashCode.create(observer, 17);
    int hash1 = maybeCached.hashCode();
    int hash2 = maybeCached.hashCode();
    assertEquals(hash1, hash2);
    assertEquals(2, observer.hashCodeCount);
  }

  @AutoValue
  static abstract class Version implements Comparable<Version> {
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

  public void testComparisonChain() {
    assertEquals(Version.create(1, 2), Version.create(1, 2));
    Version[] versions = {Version.create(1, 2), Version.create(1, 3), Version.create(2, 1)};
    for (int i = 0; i < versions.length; i++) {
      for (int j = 0; j < versions.length; j++) {
        int actual = Integer.signum(versions[i].compareTo(versions[j]));
        int expected = Integer.signum(i - j);
        assertEquals(actual, expected);
      }
    }
  }

  static abstract class LukesBase {
    interface LukesVisitor<T> {
      T visit(LukesSub s);
    }

    abstract <T> T accept(LukesVisitor<T> visitor);

    @AutoValue static abstract class LukesSub extends LukesBase {
      static LukesSub create() {
        return new AutoValue_AutoValueTest_LukesBase_LukesSub();
      }

      @Override <T> T accept(LukesVisitor<T> visitor) {
        return visitor.visit(this);
      }
    }
  }

  public void testVisitor() {
    LukesBase.LukesVisitor<String> visitor = new LukesBase.LukesVisitor<String>() {
      @Override public String visit(LukesBase.LukesSub s) {
        return s.toString();
      }
    };
    LukesBase.LukesSub sub = LukesBase.LukesSub.create();
    assertEquals(sub.toString(), sub.accept(visitor));
  }

  @AutoValue
  public static abstract class ComplexInheritance extends AbstractBase implements A, B {
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

  interface A extends Base {}
  interface B extends Base {}

  interface Base {
    int answer();
  }

  public void testComplexInheritance() {
    ComplexInheritance complex = ComplexInheritance.create("fred");
    assertEquals("fred", complex.name());
    assertEquals(42, complex.answer());
  }

  @AutoValue
  public static abstract class InheritTwice implements A, B {
    public static InheritTwice create(int answer) {
      return new AutoValue_AutoValueTest_InheritTwice(answer);
    }
  }

  public void testInheritTwice() {
    InheritTwice inheritTwice = InheritTwice.create(42);
    assertEquals(42, inheritTwice.answer());
  }

  @AutoValue
  public static abstract class Optional {
    public abstract com.google.common.base.Optional<Object> getOptional();

    public static Optional create(com.google.common.base.Optional<Object> opt) {
      return new AutoValue_AutoValueTest_Optional(opt);
    }
  }

  public void testAmbiguityFromAutoValueType() {
    Optional autoOptional = Optional.create(com.google.common.base.Optional.absent());
    assertEquals(com.google.common.base.Optional.absent(), autoOptional.getOptional());
  }

  static class BaseWithNestedType {
    static class Optional {}
  }

  @AutoValue
  public static abstract class InheritsNestedType extends BaseWithNestedType {
    public abstract com.google.common.base.Optional<Object> getOptional();

    public static InheritsNestedType create(com.google.common.base.Optional<Object> opt) {
      return new AutoValue_AutoValueTest_InheritsNestedType(opt);
    }
  }

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

  public void testOverrideNotDuplicated() {
    AbstractChild instance = AbstractChild.create(23);
    assertEquals(23, instance.foo());
  }
}
