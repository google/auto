/*
 * Copyright 2021 Google LLC
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalTime;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AutoBuilderTest {
  static class Simple {
    private final int anInt;
    private final String aString;

    Simple(int anInt, String aString) {
      this.anInt = anInt;
      this.aString = aString;
    }

    static Simple of(int anInt, String aString) {
      return new Simple(anInt, aString);
    }

    @Override
    public boolean equals(Object x) {
      if (x instanceof Simple) {
        Simple that = (Simple) x;
        return this.anInt == that.anInt && Objects.equals(this.aString, that.aString);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(anInt, aString);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("anInt", anInt)
          .add("aString", aString)
          .toString();
    }

    static Builder builder() {
      return new AutoBuilder_AutoBuilderTest_Simple_Builder();
    }

    @AutoBuilder
    abstract static class Builder {
      abstract Builder setAnInt(int x);

      abstract Builder setAString(String x);

      abstract Simple build();
    }
  }

  @Test
  public void simple() {
    Simple x = Simple.builder().setAnInt(23).setAString("skidoo").build();
    assertThat(x).isEqualTo(new Simple(23, "skidoo"));
  }

  @AutoValue
  abstract static class SimpleAuto {
    abstract int getFoo();

    abstract String getBar();

    static Builder builder() {
      return new AutoBuilder_AutoBuilderTest_SimpleAuto_Builder();
    }

    // There's no particular reason to do this since @AutoValue.Builder works just as well, but
    // let's check anyway.
    @AutoBuilder(ofClass = AutoValue_AutoBuilderTest_SimpleAuto.class)
    abstract static class Builder {
      abstract Builder setFoo(int x);

      abstract Builder setBar(String x);

      abstract AutoValue_AutoBuilderTest_SimpleAuto build();
    }
  }

  @Test
  public void simpleAuto() {
    SimpleAuto x = SimpleAuto.builder().setFoo(23).setBar("skidoo").build();
    assertThat(x.getFoo()).isEqualTo(23);
    assertThat(x.getBar()).isEqualTo("skidoo");
  }

  enum Truthiness {
    FALSY,
    TRUTHY
  }

  @interface MyAnnotation {
    String value();

    int DEFAULT_ID = -1;

    int id() default DEFAULT_ID;

    Truthiness DEFAULT_TRUTHINESS = Truthiness.FALSY;

    Truthiness truthiness() default Truthiness.FALSY;
  }

  // This method has a parameter for `truthiness`, even though that has a default, but it has no
  // parameter for `id`, which also has a default.
  @AutoAnnotation
  static MyAnnotation myAnnotation(String value, Truthiness truthiness) {
    return new AutoAnnotation_AutoBuilderTest_myAnnotation(value, truthiness);
  }

  @AutoBuilder(callMethod = "myAnnotation")
  interface MyAnnotationBuilder {
    MyAnnotationBuilder value(String x);

    MyAnnotationBuilder truthiness(Truthiness x);

    MyAnnotation build();
  }

  static MyAnnotationBuilder myAnnotationBuilder() {
    return new AutoBuilder_AutoBuilderTest_MyAnnotationBuilder()
        .truthiness(MyAnnotation.DEFAULT_TRUTHINESS);
  }

  @Test
  public void simpleAutoAnnotation() {
    MyAnnotation annotation1 = myAnnotationBuilder().value("foo").build();
    assertThat(annotation1.value()).isEqualTo("foo");
    assertThat(annotation1.id()).isEqualTo(MyAnnotation.DEFAULT_ID);
    assertThat(annotation1.truthiness()).isEqualTo(MyAnnotation.DEFAULT_TRUTHINESS);
    MyAnnotation annotation2 =
        myAnnotationBuilder().value("bar").truthiness(Truthiness.TRUTHY).build();
    assertThat(annotation2.value()).isEqualTo("bar");
    assertThat(annotation2.id()).isEqualTo(MyAnnotation.DEFAULT_ID);
    assertThat(annotation2.truthiness()).isEqualTo(Truthiness.TRUTHY);
  }

  static class Overload {
    final int anInt;
    final String aString;
    final BigInteger aBigInteger;

    Overload(int anInt, String aString) {
      this(anInt, aString, BigInteger.ZERO);
    }

    Overload(int anInt, String aString, BigInteger aBigInteger) {
      this.anInt = anInt;
      this.aString = aString;
      this.aBigInteger = aBigInteger;
    }

    @Override
    public boolean equals(Object x) {
      if (x instanceof Overload) {
        Overload that = (Overload) x;
        return this.anInt == that.anInt
            && Objects.equals(this.aString, that.aString)
            && Objects.equals(this.aBigInteger, that.aBigInteger);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(anInt, aString, aBigInteger);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("anInt", anInt)
          .add("aString", aString)
          .add("aBigInteger", aBigInteger)
          .toString();
    }

    static Builder1 builder1() {
      return new AutoBuilder_AutoBuilderTest_Overload_Builder1();
    }

    static Builder2 builder2() {
      return new AutoBuilder_AutoBuilderTest_Overload_Builder2();
    }

    @AutoBuilder
    interface Builder1 {
      Builder1 setAnInt(int x);

      Builder1 setAString(String x);

      Overload build();
    }

    @AutoBuilder
    interface Builder2 {
      Builder2 setAnInt(int x);

      Builder2 setAString(String x);

      Builder2 setABigInteger(BigInteger x);

      Overload build();
    }
  }

  @Test
  public void overloadedConstructor() {
    Overload actual1 = Overload.builder1().setAnInt(23).setAString("skidoo").build();
    Overload expected1 = new Overload(23, "skidoo");
    assertThat(actual1).isEqualTo(expected1);

    BigInteger big17 = BigInteger.valueOf(17);
    Overload actual2 =
        Overload.builder2().setAnInt(17).setAString("17").setABigInteger(big17).build();
    Overload expected2 = new Overload(17, "17", big17);
    assertThat(actual2).isEqualTo(expected2);
  }

  @AutoBuilder(callMethod = "of", ofClass = Simple.class)
  interface SimpleStaticBuilder {
    SimpleStaticBuilder anInt(int x);

    SimpleStaticBuilder aString(String x);

    Simple build();
  }

  static SimpleStaticBuilder simpleStaticBuilder() {
    return new AutoBuilder_AutoBuilderTest_SimpleStaticBuilder();
  }

  @Test
  public void staticMethod() {
    Simple actual = simpleStaticBuilder().anInt(17).aString("17").build();
    Simple expected = new Simple(17, "17");
    assertThat(actual).isEqualTo(expected);
  }

  // We can't be sure that the java.time package has parameter names, so we use this intermediary.
  // Otherwise we could just write @AutoBuilder(callMethod = "of", ofClass = LocalTime.class).
  // It's still interesting to test this as a realistic example.
  static LocalTime localTimeOf(int hour, int minute, int second, int nanoOfSecond) {
    return LocalTime.of(hour, minute, second, nanoOfSecond);
  }

  static LocalTimeBuilder localTimeBuilder() {
    return new AutoBuilder_AutoBuilderTest_LocalTimeBuilder().nanoOfSecond(0);
  }

  @AutoBuilder(callMethod = "localTimeOf")
  interface LocalTimeBuilder {
    LocalTimeBuilder hour(int hour);

    LocalTimeBuilder minute(int minute);

    LocalTimeBuilder second(int second);

    LocalTimeBuilder nanoOfSecond(int nanoOfSecond);

    LocalTime build();
  }

  @Test
  public void staticMethodOfContainingClass() {
    LocalTime actual = localTimeBuilder().hour(12).minute(34).second(56).build();
    LocalTime expected = LocalTime.of(12, 34, 56);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void missingRequiredProperty() {
    // This test is compiled at source level 7 by CompileWithEclipseTest, so we can't use
    // assertThrows with a lambda.
    try {
      localTimeBuilder().hour(12).minute(34).build();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().isEqualTo("Missing required properties: second");
    }
  }

  static void throwException() throws IOException {
    throw new IOException("oops");
  }

  static ThrowExceptionBuilder throwExceptionBuilder() {
    return new AutoBuilder_AutoBuilderTest_ThrowExceptionBuilder();
  }

  @AutoBuilder(callMethod = "throwException")
  interface ThrowExceptionBuilder {
    void build() throws IOException;
  }

  @Test
  public void emptyBuilderThrowsException() {
    try {
      throwExceptionBuilder().build();
      fail();
    } catch (IOException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("oops");
    }
  }

  static class ListContainer {
    private final ImmutableList<String> list;

    ListContainer(ImmutableList<String> list) {
      this.list = checkNotNull(list);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof ListContainer && list.equals(((ListContainer) o).list);
    }

    @Override
    public int hashCode() {
      return list.hashCode();
    }

    @Override
    public String toString() {
      return list.toString();
    }

    static Builder builder() {
      return new AutoBuilder_AutoBuilderTest_ListContainer_Builder();
    }

    @AutoBuilder
    interface Builder {
      Builder setList(Iterable<String> list);

      ImmutableList.Builder<String> listBuilder();

      ListContainer build();
    }
  }

  @Test
  public void propertyBuilder() {
    ListContainer expected = new ListContainer(ImmutableList.of("one", "two", "three"));
    ListContainer actual1 =
        ListContainer.builder().setList(ImmutableList.of("one", "two", "three")).build();
    assertThat(actual1).isEqualTo(expected);

    ListContainer.Builder builder2 = ListContainer.builder();
    builder2.listBuilder().add("one", "two", "three");
    assertThat(builder2.build()).isEqualTo(expected);

    ListContainer.Builder builder3 = ListContainer.builder().setList(ImmutableList.of("one"));
    builder3.listBuilder().add("two", "three");
    assertThat(builder3.build()).isEqualTo(expected);

    ListContainer.Builder builder4 = ListContainer.builder();
    builder4.listBuilder();
    try {
      builder4.setList(ImmutableList.of("one", "two", "three"));
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().isEqualTo("Cannot set list after calling listBuilder()");
    }
  }

  static <T> String concatList(ImmutableList<T> list) {
    // We're avoiding streams for now so we compile this in Java 7 mode in CompileWithEclipseTest.
    StringBuilder sb = new StringBuilder();
    for (T element : list) {
      sb.append(element);
    }
    return sb.toString();
  }

  @AutoBuilder(callMethod = "concatList")
  interface ConcatListCaller<T> {
    ImmutableList.Builder<T> listBuilder();

    String call();
  }

  @Test
  public void propertyBuilderWithoutSetter() {
    ConcatListCaller<Integer> caller = new AutoBuilder_AutoBuilderTest_ConcatListCaller<>();
    caller.listBuilder().add(1, 1, 2, 3, 5, 8);
    String s = caller.call();
    assertThat(s).isEqualTo("112358");
  }

  static <K, V extends Number> Map<K, V> singletonMap(K key, V value) {
    return Collections.singletonMap(key, value);
  }

  static <K, V extends Number> SingletonMapBuilder<K, V> singletonMapBuilder() {
    return new AutoBuilder_AutoBuilderTest_SingletonMapBuilder<>();
  }

  @AutoBuilder(callMethod = "singletonMap")
  interface SingletonMapBuilder<K, V extends Number> {
    SingletonMapBuilder<K, V> key(K key);

    SingletonMapBuilder<K, V> value(V value);

    Map<K, V> build();
  }

  @Test
  public void genericStaticMethod() {
    ImmutableMap<String, Integer> expected = ImmutableMap.of("17", 17);
    SingletonMapBuilder<String, Integer> builder = singletonMapBuilder();
    Map<String, Integer> actual = builder.key("17").value(17).build();
    assertThat(actual).isEqualTo(expected);
  }

  static class SingletonSet<E> extends AbstractSet<E> {
    private final E element;

    SingletonSet(E element) {
      this.element = element;
    }

    @Override
    public int size() {
      return 1;
    }

    @Override
    public Iterator<E> iterator() {
      return new Iterator<E>() {
        private boolean first = true;

        @Override
        public boolean hasNext() {
          return first;
        }

        @Override
        public E next() {
          if (!first) {
            throw new NoSuchElementException();
          }
          first = false;
          return element;
        }
      };
    }
  }

  @AutoBuilder(ofClass = SingletonSet.class)
  interface SingletonSetBuilder<E> {
    SingletonSetBuilder<E> setElement(E element);

    SingletonSet<E> build();
  }

  static <E> SingletonSetBuilder<E> singletonSetBuilder() {
    return new AutoBuilder_AutoBuilderTest_SingletonSetBuilder<>();
  }

  @Test
  public void genericClass() {
    ImmutableSet<String> expected = ImmutableSet.of("foo");
    SingletonSetBuilder<String> builder = singletonSetBuilder();
    Set<String> actual = builder.setElement("foo").build();
    assertThat(actual).isEqualTo(expected);
  }

  static class TypedSingletonSet<E> extends SingletonSet<E> {
    private final Class<?> type;

    <T extends E> TypedSingletonSet(T element, Class<T> type) {
      super(element);
      this.type = type;
    }

    @Override
    public String toString() {
      return type.getName() + super.toString();
    }
  }

  @AutoBuilder(ofClass = TypedSingletonSet.class)
  interface TypedSingletonSetBuilder<E, T extends E> {
    TypedSingletonSetBuilder<E, T> setElement(T element);

    TypedSingletonSetBuilder<E, T> setType(Class<T> type);

    TypedSingletonSet<E> build();
  }

  static <E, T extends E> TypedSingletonSetBuilder<E, T> typedSingletonSetBuilder() {
    return new AutoBuilder_AutoBuilderTest_TypedSingletonSetBuilder<>();
  }

  @Test
  public void genericClassWithGenericConstructor() {
    TypedSingletonSetBuilder<CharSequence, String> builder = typedSingletonSetBuilder();
    TypedSingletonSet<CharSequence> set = builder.setElement("foo").setType(String.class).build();
    assertThat(set.toString()).isEqualTo("java.lang.String[foo]");
  }

  static <T> ImmutableList<T> pair(T first, T second) {
    return ImmutableList.of(first, second);
  }

  @AutoBuilder(callMethod = "pair")
  interface PairBuilder<T> {
    PairBuilder<T> setFirst(T x);

    T getFirst();

    PairBuilder<T> setSecond(T x);

    Optional<T> getSecond();

    ImmutableList<T> build();
  }

  static <T> PairBuilder<T> pairBuilder() {
    return new AutoBuilder_AutoBuilderTest_PairBuilder<>();
  }

  @Test
  public void genericGetters() {
    PairBuilder<Number> builder = pairBuilder();
    assertThat(builder.getSecond()).isEmpty();
    builder.setSecond(2);
    assertThat(builder.getSecond()).hasValue(2);
    try {
      builder.getFirst();
      fail();
    } catch (IllegalStateException expected) {
    }
    builder.setFirst(1.0);
    assertThat(builder.getFirst()).isEqualTo(1.0);
    assertThat(builder.build()).containsExactly(1.0, 2).inOrder();
  }
}
