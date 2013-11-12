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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import junit.framework.TestCase;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.SerializableTester;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class AutoValueTest extends TestCase {

  // TODO(emcmanus): add tests for exotic locales

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

  @AutoValue
  abstract static class InheritedExplicitToString extends ExplicitToString {
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

  @AutoValue
  abstract static class SubAbstractToString extends AbstractToString {
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

  @AutoValue
  public static abstract class ComplexInheritance extends AbstractBase implements A, B {
    public static ComplexInheritance create(String name) {
      return new AutoValue_AutoValueTest_ComplexInheritance(name);
    }

    abstract String name();
  }

  static class AbstractBase implements Base {
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
}
