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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigInteger;
import java.time.LocalTime;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import javax.lang.model.SourceVersion;
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

  @Retention(RetentionPolicy.RUNTIME)
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

  // This method has parameters for all the annotation elements.
  @AutoAnnotation
  static MyAnnotation myAnnotationAll(String value, int id, Truthiness truthiness) {
    return new AutoAnnotation_AutoBuilderTest_myAnnotationAll(value, id, truthiness);
  }

  @AutoBuilder(callMethod = "myAnnotation")
  interface MyAnnotationBuilder {
    MyAnnotationBuilder value(String x);

    MyAnnotationBuilder truthiness(Truthiness x);

    MyAnnotation build();
  }

  static MyAnnotationBuilder myAnnotationBuilder() {
    return new AutoBuilder_AutoBuilderTest_MyAnnotationBuilder();
  }

  @AutoBuilder(callMethod = "myAnnotationAll")
  interface MyAnnotationAllBuilder {
    MyAnnotationAllBuilder value(String x);

    MyAnnotationAllBuilder id(int x);

    MyAnnotationAllBuilder truthiness(Truthiness x);

    MyAnnotation build();
  }

  static MyAnnotationAllBuilder myAnnotationAllBuilder() {
    return new AutoBuilder_AutoBuilderTest_MyAnnotationAllBuilder();
  }

  @Test
  public void simpleAutoAnnotation() {
    // We haven't supplied a value for `truthiness`, so AutoBuilder should use the default one in
    // the annotation.
    MyAnnotation annotation1 = myAnnotationBuilder().value("foo").build();
    assertThat(annotation1.value()).isEqualTo("foo");
    assertThat(annotation1.id()).isEqualTo(MyAnnotation.DEFAULT_ID);
    assertThat(annotation1.truthiness()).isEqualTo(MyAnnotation.DEFAULT_TRUTHINESS);
    MyAnnotation annotation2 =
        myAnnotationBuilder().value("bar").truthiness(Truthiness.TRUTHY).build();
    assertThat(annotation2.value()).isEqualTo("bar");
    assertThat(annotation2.id()).isEqualTo(MyAnnotation.DEFAULT_ID);
    assertThat(annotation2.truthiness()).isEqualTo(Truthiness.TRUTHY);

    MyAnnotation annotation3 = myAnnotationAllBuilder().value("foo").build();
    MyAnnotation annotation4 =
        myAnnotationAllBuilder()
            .value("foo")
            .id(MyAnnotation.DEFAULT_ID)
            .truthiness(MyAnnotation.DEFAULT_TRUTHINESS)
            .build();
    assertThat(annotation3).isEqualTo(annotation4);
  }

  @AutoBuilder(ofClass = MyAnnotation.class)
  public interface MyAnnotationSimpleBuilder {
    MyAnnotationSimpleBuilder value(String x);

    MyAnnotationSimpleBuilder id(int x);

    MyAnnotationSimpleBuilder truthiness(Truthiness x);

    MyAnnotation build();
  }

  public static MyAnnotationSimpleBuilder myAnnotationSimpleBuilder() {
    return new AutoBuilder_AutoBuilderTest_MyAnnotationSimpleBuilder();
  }

  @Test
  public void buildWithoutAutoAnnotation() {
    // We don't set a value for `id` or `truthiness`, so AutoBuilder should use the default ones in
    // the annotation.
    MyAnnotation annotation1 = myAnnotationSimpleBuilder().value("foo").build();
    assertThat(annotation1.value()).isEqualTo("foo");
    assertThat(annotation1.id()).isEqualTo(MyAnnotation.DEFAULT_ID);
    assertThat(annotation1.truthiness()).isEqualTo(MyAnnotation.DEFAULT_TRUTHINESS);

    // Now we set `truthiness` but still not `id`.
    MyAnnotation annotation2 =
        myAnnotationSimpleBuilder().value("bar").truthiness(Truthiness.TRUTHY).build();
    assertThat(annotation2.value()).isEqualTo("bar");
    assertThat(annotation2.id()).isEqualTo(MyAnnotation.DEFAULT_ID);
    assertThat(annotation2.truthiness()).isEqualTo(Truthiness.TRUTHY);

    // All three elements set explicitly.
    MyAnnotation annotation3 =
        myAnnotationSimpleBuilder().value("foo").id(23).truthiness(Truthiness.TRUTHY).build();
    assertThat(annotation3.value()).isEqualTo("foo");
    assertThat(annotation3.id()).isEqualTo(23);
    assertThat(annotation3.truthiness()).isEqualTo(Truthiness.TRUTHY);
  }

  // This builder doesn't have a setter for the `truthiness` element, so the annotations it builds
  // should always get the default value.
  @AutoBuilder(ofClass = MyAnnotation.class)
  public interface MyAnnotationSimplerBuilder {
    MyAnnotationSimplerBuilder value(String x);

    MyAnnotationSimplerBuilder id(int x);

    MyAnnotation build();
  }

  public static MyAnnotationSimplerBuilder myAnnotationSimplerBuilder() {
    return new AutoBuilder_AutoBuilderTest_MyAnnotationSimplerBuilder();
  }

  @Test
  public void buildWithoutAutoAnnotation_noSetterForElement() {
    MyAnnotation annotation = myAnnotationSimplerBuilder().value("foo").id(23).build();
    assertThat(annotation.value()).isEqualTo("foo");
    assertThat(annotation.id()).isEqualTo(23);
    assertThat(annotation.truthiness()).isEqualTo(MyAnnotation.DEFAULT_TRUTHINESS);
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
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class, () -> localTimeBuilder().hour(12).minute(34).build());
    assertThat(e).hasMessageThat().isEqualTo("Missing required properties: second");
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
    ImmutableList.Builder<String> unused = builder4.listBuilder();
    try {
      builder4.setList(ImmutableList.of("one", "two", "three"));
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().isEqualTo("Cannot set list after calling listBuilder()");
    }
  }

  static <T> String concatList(ImmutableList<T> list) {
    return list.stream().map(String::valueOf).collect(joining());
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
    Truth.assertThat(builder.getSecond()).isEmpty();
    builder.setSecond(2);
    Truth.assertThat(builder.getSecond()).hasValue(2);
    try {
      builder.getFirst();
      fail();
    } catch (IllegalStateException expected) {
    }
    builder.setFirst(1.0);
    assertThat(builder.getFirst()).isEqualTo(1.0);
    assertThat(builder.build()).containsExactly(1.0, 2).inOrder();
  }

  static class NumberHolder<T extends Number> {
    private final T number;

    NumberHolder(T number) {
      this.number = number;
    }

    T getNumber() {
      return number;
    }
  }

  static <T extends Number> NumberHolder<T> buildNumberHolder(T number) {
    return new NumberHolder<>(number);
  }

  @AutoBuilder(callMethod = "buildNumberHolder")
  interface NumberHolderBuilder<T extends Number> {
    NumberHolderBuilder<T> setNumber(T number);

    NumberHolder<T> build();
  }

  static <T extends Number> NumberHolderBuilder<T> numberHolderBuilder() {
    return new AutoBuilder_AutoBuilderTest_NumberHolderBuilder<>();
  }

  static <T extends Number> NumberHolderBuilder<T> numberHolderBuilder(
      NumberHolder<T> numberHolder) {
    return new AutoBuilder_AutoBuilderTest_NumberHolderBuilder<>(numberHolder);
  }

  @Test
  public void builderFromInstance() {
    NumberHolder<Integer> instance1 =
        AutoBuilderTest.<Integer>numberHolderBuilder().setNumber(23).build();
    assertThat(instance1.getNumber()).isEqualTo(23);
    NumberHolder<Integer> instance2 = numberHolderBuilder(instance1).build();
    assertThat(instance2.getNumber()).isEqualTo(23);
    NumberHolder<Integer> instance3 = numberHolderBuilder(instance2).setNumber(17).build();
    assertThat(instance3.getNumber()).isEqualTo(17);
  }

  @AutoBuilder(callMethod = "of", ofClass = Simple.class)
  @MyAnnotation("thing")
  interface AnnotatedSimpleStaticBuilder1 {
    AnnotatedSimpleStaticBuilder1 anInt(int x);

    AnnotatedSimpleStaticBuilder1 aString(String x);

    Simple build();
  }

  @Test
  public void builderAnnotationsNotCopiedByDefault() {
    assertThat(AutoBuilder_AutoBuilderTest_AnnotatedSimpleStaticBuilder1.class.getAnnotations())
        .asList()
        .isEmpty();
  }

  @AutoBuilder(callMethod = "of", ofClass = Simple.class)
  @AutoValue.CopyAnnotations
  @MyAnnotation("thing")
  interface AnnotatedSimpleStaticBuilder2 {
    AnnotatedSimpleStaticBuilder2 anInt(int x);

    AnnotatedSimpleStaticBuilder2 aString(String x);

    Simple build();
  }

  @Test
  public void builderAnnotationsCopiedIfRequested() {
    assertThat(AutoBuilder_AutoBuilderTest_AnnotatedSimpleStaticBuilder2.class.getAnnotations())
        .asList()
        .contains(myAnnotationBuilder().value("thing").build());
  }

  @Target(TYPE_USE)
  public @interface Nullable {}

  public static <T extends @Nullable Object, U> T frob(T arg, U notNull) {
    return arg;
  }

  @AutoBuilder(callMethod = "frob")
  interface FrobCaller<T extends @Nullable Object, U> {
    FrobCaller<T, U> arg(T arg);

    FrobCaller<T, U> notNull(U notNull);

    T call();

    static <T extends @Nullable Object, U> FrobCaller<T, U> caller() {
      return new AutoBuilder_AutoBuilderTest_FrobCaller<>();
    }
  }

  @Test
  public void builderTypeVariableWithNullableBound() {
    // The Annotation Processing API doesn't see the @Nullable Object bound on Java 8.
    assumeTrue(SourceVersion.latest().ordinal() > SourceVersion.RELEASE_8.ordinal());
    assertThat(FrobCaller.<@Nullable String, String>caller().arg(null).notNull("foo").call())
        .isNull();
    assertThrows(
        NullPointerException.class,
        () -> FrobCaller.<@Nullable String, String>caller().arg(null).notNull(null).call());
  }

  static class NullablesExposedAsOptionals {
    private final @Nullable String aString1;
    private final @Nullable String aString2;
    private final @Nullable Double aDouble;
    private final @Nullable Integer anInt;
    private final @Nullable Long aLong;

    NullablesExposedAsOptionals(
        @Nullable String aString1,
        @Nullable String aString2,
        @Nullable Double aDouble,
        @Nullable Integer anInt,
        @Nullable Long aLong) {
      this.aString1 = aString1;
      this.aString2 = aString2;
      this.aDouble = aDouble;
      this.anInt = anInt;
      this.aLong = aLong;
    }

    Optional<String> aString1() {
      return Optional.ofNullable(aString1);
    }

    com.google.common.base.Optional<String> aString2() {
      return com.google.common.base.Optional.fromNullable(aString2);
    }

    OptionalDouble aDouble() {
      return aDouble == null ? OptionalDouble.empty() : OptionalDouble.of(aDouble);
    }

    OptionalInt anInt() {
      return anInt == null ? OptionalInt.empty() : OptionalInt.of(anInt);
    }

    OptionalLong aLong() {
      return aLong == null ? OptionalLong.empty() : OptionalLong.of(aLong);
    }

    Builder toBuilder() {
      return new AutoBuilder_AutoBuilderTest_NullablesExposedAsOptionals_Builder(this);
    }

    static Builder builder() {
      return new AutoBuilder_AutoBuilderTest_NullablesExposedAsOptionals_Builder();
    }

    @AutoBuilder
    abstract static class Builder {
      abstract Builder setAString1(Optional<String> aString1);

      abstract Builder setAString2(com.google.common.base.Optional<String> aString2);

      abstract Builder setADouble(OptionalDouble aDouble);

      abstract Builder setAnInt(OptionalInt anInt);

      abstract Builder setALong(OptionalLong aLong);

      abstract NullablesExposedAsOptionals build();
    }
  }

  @Test
  public void builderExposingNullableAsOptionalString() {
    NullablesExposedAsOptionals built =
        NullablesExposedAsOptionals.builder().setAString1(Optional.of("foo")).build();
    assertThat(built.aString1()).hasValue("foo");
    assertThat(built.toBuilder().build().aString1()).hasValue("foo");

    NullablesExposedAsOptionals builtEmpty =
        built.toBuilder().setAString1(Optional.empty()).build();
    assertThat(builtEmpty.aString1()).isEmpty();
    assertThat(builtEmpty.toBuilder().build().aString1()).isEmpty();
  }

  @Test
  public void builderExposingNullableAsBaseOptionalString() {
    NullablesExposedAsOptionals built =
        NullablesExposedAsOptionals.builder()
            .setAString2(com.google.common.base.Optional.of("foo"))
            .build();
    assertThat(built.aString2()).hasValue("foo");
    assertThat(built.toBuilder().build().aString2()).hasValue("foo");

    NullablesExposedAsOptionals builtAbsent =
        built.toBuilder().setAString2(com.google.common.base.Optional.absent()).build();
    assertThat(builtAbsent.aString2()).isAbsent();
    assertThat(builtAbsent.toBuilder().build().aString2()).isAbsent();
  }

  @Test
  public void builderExposingNullableAsOptionalDouble() {
    NullablesExposedAsOptionals built =
        NullablesExposedAsOptionals.builder().setADouble(OptionalDouble.of(3.14)).build();
    assertThat(built.aDouble()).hasValue(3.14);
    assertThat(built.toBuilder().build().aDouble()).hasValue(3.14);

    NullablesExposedAsOptionals builtEmpty =
        built.toBuilder().setADouble(OptionalDouble.empty()).build();
    assertThat(builtEmpty.aDouble()).isEmpty();
    assertThat(builtEmpty.toBuilder().build().aDouble()).isEmpty();
  }

  @Test
  public void builderExposingNullableAsOptionalInt() {
    NullablesExposedAsOptionals built =
        NullablesExposedAsOptionals.builder().setAnInt(OptionalInt.of(3)).build();
    assertThat(built.anInt()).hasValue(3);
    assertThat(built.toBuilder().build().anInt()).hasValue(3);

    NullablesExposedAsOptionals builtEmpty =
        built.toBuilder().setAnInt(OptionalInt.empty()).build();
    assertThat(builtEmpty.anInt()).isEmpty();
    assertThat(builtEmpty.toBuilder().build().anInt()).isEmpty();
  }

  @Test
  public void builderExposingNullableAsOptionalLong() {
    NullablesExposedAsOptionals built =
        NullablesExposedAsOptionals.builder().setALong(OptionalLong.of(3)).build();
    assertThat(built.aLong()).hasValue(3);
    assertThat(built.toBuilder().build().aLong()).hasValue(3);

    NullablesExposedAsOptionals builtEmpty =
        built.toBuilder().setALong(OptionalLong.empty()).build();
    assertThat(builtEmpty.aLong()).isEmpty();
    assertThat(builtEmpty.toBuilder().build().aLong()).isEmpty();
  }

  /**
   * Demo for how to require that null or optional properties have to be set explicitly.
   *
   * <p>Normally properties of type {@code @Nullable Foo} or {@code Optional<Foo>} are optional in
   * the builder, meaning that you can call {@code build()} without setting them. If you want to
   * require that they be set explicitly, you need a subterfuge like the one used here. AutoBuilder
   * allows the {@code foo} property to be set either via {@code setFoo(Foo)} or {@code foo(Foo)}.
   * In the demo, we expose {@code foo(@Nullable Foo)} in the public API, but the abstract method
   * that we get AutoBuilder to implement is the package-private {@code setFoo(@Nullable Foo)}. The
   * public {@code foo} calls this package-private {@code setFoo} but also records whether it was
   * called. Then we do a similar trick with {@code build()} in the public API calling the
   * package-private abstract {@code autoBuild()} that AutoBuilder generates, but first checking
   * that all required properties have been set.
   *
   * <p>Unfortunately it's harder to do the same thing with {@code AutoValue.Builder} because it has
   * a rule whereby all the abstract setter methods must follow the same naming convention, either
   * all {@code setFoo} or all {@code foo}. So if you want to use the trick here you may end up
   * having to define pairs of {@code foo} and {@code setFoo} methods for every property {@code
   * foo}, even ones where you don't need to track whether they were set because the generated
   * builder code will do it for you.
   */
  public static class NoDefaultDemo {
    final @Nullable String nullableString;
    final Optional<String> optionalString;

    NoDefaultDemo(@Nullable String nullableString, Optional<String> optionalString) {
      this.nullableString = nullableString;
      this.optionalString = optionalString;
    }

    public static Builder builder() {
      return new AutoBuilder_AutoBuilderTest_NoDefaultDemo_Builder();
    }

    @AutoBuilder
    public abstract static class Builder {
      private boolean nullableStringSet = false;
      private boolean optionalStringSet = false;

      abstract Builder setNullableString(@Nullable String nullableString);

      abstract Builder setOptionalString(Optional<String> optionalString);

      public Builder nullableString(@Nullable String nullableString) {
        nullableStringSet = true;
        return setNullableString(nullableString);
      }

      public Builder optionalString(Optional<String> optionalString) {
        optionalStringSet = true;
        return setOptionalString(optionalString);
      }

      abstract NoDefaultDemo autoBuild();

      public NoDefaultDemo build() {
        checkState(nullableStringSet, "nullableString is required");
        checkState(optionalStringSet, "optionalString is required");
        return autoBuild();
      }
    }
  }

  @Test
  public void noDefaultDemo() {
    NoDefaultDemo builtWithRealValues =
        NoDefaultDemo.builder().nullableString("foo").optionalString(Optional.of("bar")).build();
    assertThat(builtWithRealValues.nullableString).isEqualTo("foo");
    assertThat(builtWithRealValues.optionalString).hasValue("bar");

    NoDefaultDemo builtWithDefaultValues =
        NoDefaultDemo.builder().nullableString(null).optionalString(Optional.empty()).build();
    assertThat(builtWithDefaultValues.nullableString).isNull();
    assertThat(builtWithDefaultValues.optionalString).isEmpty();

    NoDefaultDemo.Builder builder1 = NoDefaultDemo.builder().nullableString("foo");
    IllegalStateException e1 = assertThrows(IllegalStateException.class, () -> builder1.build());
    assertThat(e1).hasMessageThat().isEqualTo("optionalString is required");
    NoDefaultDemo.Builder builder2 = NoDefaultDemo.builder().optionalString(Optional.of("bar"));
    IllegalStateException e2 = assertThrows(IllegalStateException.class, () -> builder2.build());
    assertThat(e2).hasMessageThat().isEqualTo("nullableString is required");
  }
}
