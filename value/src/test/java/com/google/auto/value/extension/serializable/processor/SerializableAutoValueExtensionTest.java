/*
 * Copyright 2020 Google LLC
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
package com.google.auto.value.extension.serializable.processor;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.serializable.SerializableAutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.SerializableTester;
import java.io.ByteArrayOutputStream;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SerializableAutoValueExtensionTest {
  private static final String A = "a";
  private static final int B = 1;
  private static final String C = "c";
  private static final int D = 2;

  @SerializableAutoValue
  @AutoValue
  abstract static class DummySerializableAutoValue implements Serializable {
    // Primitive fields
    abstract String a();

    abstract int b();

    // Optional fields
    abstract Optional<String> optionalC();

    abstract Optional<Integer> optionalD();

    static DummySerializableAutoValue.Builder builder() {
      return new AutoValue_SerializableAutoValueExtensionTest_DummySerializableAutoValue.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract DummySerializableAutoValue.Builder setA(String value);

      abstract DummySerializableAutoValue.Builder setB(int value);

      abstract DummySerializableAutoValue.Builder setOptionalC(String value);

      abstract DummySerializableAutoValue.Builder setOptionalD(int value);

      abstract DummySerializableAutoValue build();
    }
  }

  @Test
  public void allFieldsAreSet_noEmpty() {
    DummySerializableAutoValue autoValue =
        DummySerializableAutoValue.builder()
            .setA(A)
            .setB(B)
            .setOptionalC(C)
            .setOptionalD(D)
            .build();

    assertThat(autoValue.a()).isEqualTo(A);
    assertThat(autoValue.b()).isEqualTo(B);
    assertThat(autoValue.optionalC()).hasValue(C);
    assertThat(autoValue.optionalD()).hasValue(D);
  }

  @Test
  public void allFieldsAreSet_withMixedEmpty() {
    DummySerializableAutoValue autoValue =
        DummySerializableAutoValue.builder().setA(A).setB(B).setOptionalC(C).build();

    assertThat(autoValue.a()).isEqualTo(A);
    assertThat(autoValue.b()).isEqualTo(B);
    assertThat(autoValue.optionalC()).hasValue(C);
    assertThat(autoValue.optionalD()).isEmpty();
  }

  @Test
  public void allFieldsAreSet_withEmpty() {
    DummySerializableAutoValue autoValue =
        DummySerializableAutoValue.builder().setA(A).setB(B).build();

    assertThat(autoValue.a()).isEqualTo(A);
    assertThat(autoValue.b()).isEqualTo(B);
    assertThat(autoValue.optionalC()).isEmpty();
    assertThat(autoValue.optionalD()).isEmpty();
  }

  @Test
  public void allFieldsAreSerialized_noEmpty() {
    DummySerializableAutoValue autoValue =
        DummySerializableAutoValue.builder()
            .setA(A)
            .setB(B)
            .setOptionalC(C)
            .setOptionalD(D)
            .build();

    DummySerializableAutoValue actualAutoValue = SerializableTester.reserialize(autoValue);

    assertThat(actualAutoValue).isEqualTo(autoValue);
  }

  @Test
  public void allFieldsAreSerialized_withEmpty() {
    DummySerializableAutoValue autoValue =
        DummySerializableAutoValue.builder().setA(A).setB(B).build();

    DummySerializableAutoValue actualAutoValue = SerializableTester.reserialize(autoValue);

    assertThat(actualAutoValue).isEqualTo(autoValue);
  }

  @Test
  public void allFieldsAreSerialized_withMixedEmpty() {
    DummySerializableAutoValue autoValue =
        DummySerializableAutoValue.builder().setA(A).setB(B).setOptionalC(C).build();

    DummySerializableAutoValue actualAutoValue = SerializableTester.reserialize(autoValue);

    assertThat(actualAutoValue).isEqualTo(autoValue);
  }

  @SerializableAutoValue
  @AutoValue
  abstract static class PrefixSerializableAutoValue implements Serializable {
    // Primitive fields
    abstract String getA();

    abstract boolean isB();

    // Optional fields
    abstract Optional<String> getC();

    abstract Optional<Boolean> getD();

    static PrefixSerializableAutoValue.Builder builder() {
      return new AutoValue_SerializableAutoValueExtensionTest_PrefixSerializableAutoValue.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract PrefixSerializableAutoValue.Builder a(String value);

      abstract PrefixSerializableAutoValue.Builder b(boolean value);

      abstract PrefixSerializableAutoValue.Builder c(String value);

      abstract PrefixSerializableAutoValue.Builder d(boolean value);

      abstract PrefixSerializableAutoValue build();
    }
  }

  @Test
  public void allPrefixFieldsAreSerialized_noEmpty() {
    PrefixSerializableAutoValue autoValue =
        PrefixSerializableAutoValue.builder().a("A").b(true).c("C").d(false).build();

    PrefixSerializableAutoValue actualAutoValue = SerializableTester.reserialize(autoValue);

    assertThat(actualAutoValue).isEqualTo(autoValue);
  }

  @Test
  public void allPrefixFieldsAreSerialized_WithEmpty() {
    PrefixSerializableAutoValue autoValue =
        PrefixSerializableAutoValue.builder().a("A").b(true).build();

    PrefixSerializableAutoValue actualAutoValue = SerializableTester.reserialize(autoValue);

    assertThat(actualAutoValue).isEqualTo(autoValue);
  }

  @SerializableAutoValue
  @AutoValue
  abstract static class NotSerializable {
    static NotSerializable create() {
      return new AutoValue_SerializableAutoValueExtensionTest_NotSerializable(Optional.of("A"));
    }

    abstract Optional<String> optionalA();
  }

  @Test
  public void missingImplementsSerializableThrowsException() throws Exception {
    NotSerializable autoValue = NotSerializable.create();
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    ObjectOutputStream so = new ObjectOutputStream(bo);

    assertThrows(NotSerializableException.class, () -> so.writeObject(autoValue));
  }

  @AutoValue
  abstract static class NotSerializableNoAnnotation implements Serializable {
    static NotSerializableNoAnnotation create() {
      return new AutoValue_SerializableAutoValueExtensionTest_NotSerializableNoAnnotation(
          Optional.of("A"));
    }

    abstract Optional<String> optionalA();
  }

  @Test
  public void missingSerializableAutoValueAnnotationThrowsException() throws Exception {
    NotSerializableNoAnnotation autoValue = NotSerializableNoAnnotation.create();
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    ObjectOutputStream so = new ObjectOutputStream(bo);

    assertThrows(NotSerializableException.class, () -> so.writeObject(autoValue));
  }

  @SerializableAutoValue
  @AutoValue
  // Technically all type parameters should extend serializable, but for the purposes of testing,
  // only one type parameter is bounded.
  abstract static class HasTypeParameters<T extends Serializable, S> implements Serializable {
    abstract T a();

    abstract Optional<S> optionalB();

    static <T extends Serializable, S> Builder<T, S> builder() {
      return new AutoValue_SerializableAutoValueExtensionTest_HasTypeParameters.Builder<>();
    }

    @AutoValue.Builder
    abstract static class Builder<T extends Serializable, S> {
      abstract Builder<T, S> setA(T value);

      abstract Builder<T, S> setOptionalB(S value);

      abstract HasTypeParameters<T, S> build();
    }
  }

  @Test
  public void typeParameterizedFieldsAreSet_noEmpty() {
    HasTypeParameters<String, Integer> autoValue =
        HasTypeParameters.<String, Integer>builder().setA(A).setOptionalB(B).build();

    assertThat(autoValue.a()).isEqualTo(A);
    assertThat(autoValue.optionalB()).hasValue(B);
  }

  @Test
  public void typeParameterizedFieldsAreSet_withEmpty() {
    HasTypeParameters<String, Integer> autoValue =
        HasTypeParameters.<String, Integer>builder().setA(A).build();

    assertThat(autoValue.a()).isEqualTo(A);
    assertThat(autoValue.optionalB()).isEmpty();
  }

  @Test
  public void typeParameterizedFieldsAreSerializable_noEmpty() {
    HasTypeParameters<String, Integer> autoValue =
        HasTypeParameters.<String, Integer>builder().setA(A).setOptionalB(B).build();

    HasTypeParameters<String, Integer> actualAutoValue = SerializableTester.reserialize(autoValue);

    assertThat(actualAutoValue).isEqualTo(autoValue);
  }

  @Test
  public void typeParameterizedFieldsAreSerializable_withEmpty() {
    HasTypeParameters<String, Integer> autoValue =
        HasTypeParameters.<String, Integer>builder().setA(A).build();

    HasTypeParameters<String, Integer> actualAutoValue = SerializableTester.reserialize(autoValue);

    assertThat(actualAutoValue).isEqualTo(autoValue);
  }

  @SerializableAutoValue
  @AutoValue
  abstract static class ImmutableListSerializableAutoValue implements Serializable {
    abstract ImmutableList<Optional<String>> payload();

    static ImmutableListSerializableAutoValue.Builder builder() {
      return new AutoValue_SerializableAutoValueExtensionTest_ImmutableListSerializableAutoValue
          .Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract ImmutableListSerializableAutoValue.Builder setPayload(
          ImmutableList<Optional<String>> payload);

      abstract ImmutableListSerializableAutoValue build();
    }
  }

  @Test
  public void immutableList_emptyListSerialized() {
    ImmutableListSerializableAutoValue autoValue =
        ImmutableListSerializableAutoValue.builder().setPayload(ImmutableList.of()).build();

    ImmutableListSerializableAutoValue actualAutoValue = SerializableTester.reserialize(autoValue);

    assertThat(actualAutoValue).isEqualTo(autoValue);
  }

  @Test
  public void immutableList_allFieldsSetAndSerialized() {
    ImmutableListSerializableAutoValue autoValue =
        ImmutableListSerializableAutoValue.builder()
            .setPayload(ImmutableList.of(Optional.of("a1"), Optional.of("a2")))
            .build();

    ImmutableListSerializableAutoValue actualAutoValue = SerializableTester.reserialize(autoValue);

    assertThat(actualAutoValue).isEqualTo(autoValue);
  }

  @SerializableAutoValue
  @AutoValue
  abstract static class ImmutableMapSerializableAutoValue implements Serializable {
    abstract ImmutableMap<Optional<String>, String> a();

    abstract ImmutableMap<String, Optional<String>> b();

    static ImmutableMapSerializableAutoValue.Builder builder() {
      return new AutoValue_SerializableAutoValueExtensionTest_ImmutableMapSerializableAutoValue
          .Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract ImmutableMapSerializableAutoValue.Builder setA(
          ImmutableMap<Optional<String>, String> a);

      abstract ImmutableMapSerializableAutoValue.Builder setB(
          ImmutableMap<String, Optional<String>> b);

      abstract ImmutableMapSerializableAutoValue build();
    }
  }

  @Test
  public void immutableMap_emptyMapSerialized() {
    ImmutableMapSerializableAutoValue autoValue =
        ImmutableMapSerializableAutoValue.builder()
            .setA(ImmutableMap.of())
            .setB(ImmutableMap.of())
            .build();

    ImmutableMapSerializableAutoValue actualAutoValue = SerializableTester.reserialize(autoValue);

    assertThat(actualAutoValue).isEqualTo(autoValue);
  }

  @Test
  public void immutableMap_allFieldsSetAndSerialized() {
    ImmutableMapSerializableAutoValue autoValue =
        ImmutableMapSerializableAutoValue.builder()
            .setA(ImmutableMap.of(Optional.of("key"), "value"))
            .setB(ImmutableMap.of("key", Optional.of("value")))
            .build();

    ImmutableMapSerializableAutoValue actualAutoValue = SerializableTester.reserialize(autoValue);

    assertThat(actualAutoValue).isEqualTo(autoValue);
  }

  @SerializableAutoValue
  @AutoValue
  abstract static class MultiplePropertiesSameType implements Serializable {
    abstract String a();

    abstract String b();

    static MultiplePropertiesSameType.Builder builder() {
      return new AutoValue_SerializableAutoValueExtensionTest_MultiplePropertiesSameType.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract MultiplePropertiesSameType.Builder setA(String value);

      abstract MultiplePropertiesSameType.Builder setB(String value);

      abstract MultiplePropertiesSameType build();
    }
  }

  @Test
  public void multiplePropertiesSameType_allFieldsSerialized() {
    MultiplePropertiesSameType autoValue =
        MultiplePropertiesSameType.builder().setA("A").setB("B").build();

    MultiplePropertiesSameType actualAutoValue = SerializableTester.reserialize(autoValue);

    assertThat(actualAutoValue).isEqualTo(autoValue);
  }

  /**
   * Type that may result in nested lambdas in the generated code. Including this allows us to
   * verify that we handle those correctly, in particular not reusing a lambda parameter name in
   * another lambda nested inside the first one.
   */
  @SerializableAutoValue
  @AutoValue
  abstract static class ComplexType implements Serializable {
    abstract ImmutableMap<String, ImmutableMap<String, Optional<String>>> a();

    static ComplexType.Builder builder() {
      return new AutoValue_SerializableAutoValueExtensionTest_ComplexType.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract ComplexType.Builder setA(
          ImmutableMap<String, ImmutableMap<String, Optional<String>>> a);

      abstract ComplexType build();
    }
  }

  @Test
  public void complexType() {
    ImmutableMap<String, ImmutableMap<String, Optional<String>>> map =
        ImmutableMap.of("foo", ImmutableMap.of("bar", Optional.of("baz")));
    ComplexType complexType = ComplexType.builder().setA(map).build();

    ComplexType reserialized = SerializableTester.reserialize(complexType);

    assertThat(reserialized).isEqualTo(complexType);
  }
}
