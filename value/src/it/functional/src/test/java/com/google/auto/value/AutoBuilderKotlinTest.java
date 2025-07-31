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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AutoBuilderKotlinTest {
  @AutoBuilder(ofClass = KotlinData.class)
  abstract static class KotlinDataBuilder {
    static KotlinDataBuilder builder() {
      return new AutoBuilder_AutoBuilderKotlinTest_KotlinDataBuilder();
    }

    static KotlinDataBuilder builder(KotlinData kotlinData) {
      return new AutoBuilder_AutoBuilderKotlinTest_KotlinDataBuilder(kotlinData);
    }

    abstract KotlinDataBuilder setInt(int x);

    abstract KotlinDataBuilder setString(String x);

    abstract KotlinData build();
  }

  @Test
  public void simpleKotlin() {
    KotlinData x = KotlinDataBuilder.builder().setInt(23).setString("skidoo").build();
    assertThat(x.getInt()).isEqualTo(23);
    assertThat(x.getString()).isEqualTo("skidoo");

    KotlinData y = KotlinDataBuilder.builder(x).setString("chromosomes").build();
    assertThat(y.getInt()).isEqualTo(23);
    assertThat(y.getString()).isEqualTo("chromosomes");

    assertThrows(IllegalStateException.class, () -> KotlinDataBuilder.builder().build());
  }

  @AutoBuilder(ofClass = KotlinDataWithNullable.class)
  abstract static class KotlinDataWithNullableBuilder {
    static KotlinDataWithNullableBuilder builder() {
      return new AutoBuilder_AutoBuilderKotlinTest_KotlinDataWithNullableBuilder();
    }

    abstract KotlinDataWithNullableBuilder setAnInt(int x);

    abstract KotlinDataWithNullableBuilder setAString(String x);

    abstract KotlinDataWithNullable build();
  }

  @Test
  public void kotlinWithNullable() {
    KotlinDataWithNullable empty = KotlinDataWithNullableBuilder.builder().build();
    assertThat(empty.getAnInt()).isNull();
    assertThat(empty.getAString()).isNull();

    KotlinDataWithNullable notEmpty =
        KotlinDataWithNullableBuilder.builder().setAString("answer").setAnInt(42).build();
    assertThat(notEmpty.getAString()).isEqualTo("answer");
    assertThat(notEmpty.getAnInt()).isEqualTo(42);
  }

  @AutoBuilder(ofClass = KotlinDataWithDefaults.class)
  abstract static class KotlinDataWithDefaultsBuilder {
    static KotlinDataWithDefaultsBuilder builder() {
      return new AutoBuilder_AutoBuilderKotlinTest_KotlinDataWithDefaultsBuilder();
    }

    abstract KotlinDataWithDefaultsBuilder setAnInt(int x);

    abstract int getAnInt();

    abstract ImmutableList.Builder<String> anImmutableListBuilder();

    abstract KotlinDataWithDefaultsBuilder setNotDefaulted(long x);

    abstract long getNotDefaulted();

    abstract KotlinDataWithDefaultsBuilder setAString(String x);

    abstract String getAString();

    abstract KotlinDataWithDefaults build();
  }

  @Test
  public void kotlinWithDefaults_explicit() {
    KotlinDataWithDefaultsBuilder builder =
        KotlinDataWithDefaultsBuilder.builder()
            .setAString("answer")
            .setNotDefaulted(100L)
            .setAnInt(42);
    builder.anImmutableListBuilder().add("bar");
    KotlinDataWithDefaults x = builder.build();
    assertThat(x.getAString()).isEqualTo("answer");
    assertThat(x.getAnImmutableList()).containsExactly("bar");
    assertThat(x.getNotDefaulted()).isEqualTo(100L);
    assertThat(x.getAnInt()).isEqualTo(42);
  }

  @Test
  public void kotlinWithDefaults_defaulted() {
    KotlinDataWithDefaults x =
        KotlinDataWithDefaultsBuilder.builder().setNotDefaulted(100L).build();
    assertThat(x.getAnInt()).isEqualTo(23);
    assertThat(x.getAnImmutableList()).containsExactly("foo");
    assertThat(x.getAString()).isEqualTo("skidoo");
    assertThat(x.getNotDefaulted()).isEqualTo(100L);
    KotlinDataWithDefaults copy =
        new AutoBuilder_AutoBuilderKotlinTest_KotlinDataWithDefaultsBuilder(x).build();
    assertThat(copy).isEqualTo(x);
    assertThat(copy).isNotSameInstanceAs(x);
    KotlinDataWithDefaults modified =
        new AutoBuilder_AutoBuilderKotlinTest_KotlinDataWithDefaultsBuilder(x).setAnInt(17).build();
    assertThat(modified.getAnInt()).isEqualTo(17);
  }

  @Test
  public void kotlinWithDefaults_getter() {
    KotlinDataWithDefaultsBuilder builder = KotlinDataWithDefaultsBuilder.builder();
    assertThrows(IllegalStateException.class, builder::getAnInt);
    builder.setAnInt(42);
    assertThat(builder.getAnInt()).isEqualTo(42);
    assertThrows(IllegalStateException.class, builder::getNotDefaulted);
    builder.setNotDefaulted(100L);
    assertThat(builder.getNotDefaulted()).isEqualTo(100L);
    assertThrows(IllegalStateException.class, builder::getAString);
    builder.setAString("answer");
    assertThat(builder.getAString()).isEqualTo("answer");
  }

  @AutoBuilder(ofClass = KotlinDataEightDefaults.class)
  interface KotlinDataEightDefaultsBuilder {
    static KotlinDataEightDefaultsBuilder builder() {
      return new AutoBuilder_AutoBuilderKotlinTest_KotlinDataEightDefaultsBuilder();
    }

    KotlinDataEightDefaultsBuilder a1(int x);

    KotlinDataEightDefaultsBuilder a2(int x);

    KotlinDataEightDefaultsBuilder a3(int x);

    KotlinDataEightDefaultsBuilder a4(int x);

    KotlinDataEightDefaultsBuilder a5(int x);

    KotlinDataEightDefaultsBuilder a6(int x);

    KotlinDataEightDefaultsBuilder a7(int x);

    KotlinDataEightDefaultsBuilder a8(int x);

    KotlinDataEightDefaults build();
  }

  // We test a class that has exactly 8 default parameters because we will use a byte for the
  // bitmask in that case and it is possible that we might have an issue with sign extension when
  // bit 7 of that bitmask is set.
  @Test
  public void kotlinEightDefaults() {
    KotlinDataEightDefaults allDefaulted = KotlinDataEightDefaultsBuilder.builder().build();
    assertThat(allDefaulted.getA1()).isEqualTo(1);
    assertThat(allDefaulted.getA8()).isEqualTo(8);
    KotlinDataEightDefaults noneDefaulted =
        KotlinDataEightDefaultsBuilder.builder()
            .a1(-1)
            .a2(-2)
            .a3(-3)
            .a4(-4)
            .a5(-5)
            .a6(-6)
            .a7(-7)
            .a8(-8)
            .build();
    assertThat(noneDefaulted.getA1()).isEqualTo(-1);
    assertThat(noneDefaulted.getA8()).isEqualTo(-8);
  }

  @AutoBuilder(ofClass = KotlinDataSomeDefaults.class)
  interface KotlinDataSomeDefaultsBuilder {
    static KotlinDataSomeDefaultsBuilder builder() {
      return new AutoBuilder_AutoBuilderKotlinTest_KotlinDataSomeDefaultsBuilder();
    }

    static KotlinDataSomeDefaultsBuilder fromInstance(KotlinDataSomeDefaults instance) {
      return new AutoBuilder_AutoBuilderKotlinTest_KotlinDataSomeDefaultsBuilder(instance);
    }

    KotlinDataSomeDefaultsBuilder requiredInt(int x);

    KotlinDataSomeDefaultsBuilder requiredString(String x);

    KotlinDataSomeDefaultsBuilder optionalInt(int x);

    KotlinDataSomeDefaultsBuilder optionalString(String x);

    KotlinDataSomeDefaults build();
  }

  @Test
  public void kotlinSomeDefaults_someDefaulted() {
    KotlinDataSomeDefaults someDefaulted =
        KotlinDataSomeDefaultsBuilder.builder().requiredInt(12).requiredString("Monkeys").build();
    assertThat(someDefaulted.getOptionalInt()).isEqualTo(23);
    assertThat(someDefaulted.getOptionalString()).isEqualTo("Skidoo");
    assertThat(KotlinDataSomeDefaultsBuilder.fromInstance(someDefaulted).build())
        .isEqualTo(someDefaulted);
  }

  @Test
  public void kotlinSomeDefaults_noneDefaulted() {
    KotlinDataSomeDefaults noneDefaulted =
        KotlinDataSomeDefaultsBuilder.builder()
            .requiredInt(12)
            .requiredString("Monkeys")
            .optionalInt(3)
            .optionalString("Oranges")
            .build();
    KotlinDataSomeDefaults copy = KotlinDataSomeDefaultsBuilder.fromInstance(noneDefaulted).build();
    assertThat(copy).isEqualTo(noneDefaulted);
  }

  @Test
  public void kotlinSomeDefaults_missingRequired() {
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class, () -> KotlinDataSomeDefaultsBuilder.builder().build());
    assertThat(e).hasMessageThat().contains("requiredInt");
    assertThat(e).hasMessageThat().contains("requiredString");
  }

  @AutoBuilder(ofClass = KotlinDataSomeDefaultsBig.class)
  interface KotlinDataSomeDefaultsBigBuilder {
    static KotlinDataSomeDefaultsBigBuilder builder() {
      return new AutoBuilder_AutoBuilderKotlinTest_KotlinDataSomeDefaultsBigBuilder();
    }

    KotlinDataSomeDefaultsBigBuilder requiredInt(int x);

    KotlinDataSomeDefaultsBigBuilder requiredString(String x);

    KotlinDataSomeDefaultsBigBuilder a1(int x);

    KotlinDataSomeDefaultsBigBuilder a2(int x);

    KotlinDataSomeDefaultsBigBuilder a3(int x);

    KotlinDataSomeDefaultsBigBuilder a4(int x);

    KotlinDataSomeDefaultsBigBuilder a5(int x);

    KotlinDataSomeDefaultsBigBuilder a6(int x);

    KotlinDataSomeDefaultsBigBuilder a7(int x);

    KotlinDataSomeDefaultsBigBuilder a8(int x);

    KotlinDataSomeDefaultsBigBuilder a9(int x);

    KotlinDataSomeDefaultsBigBuilder a10(int x);

    KotlinDataSomeDefaultsBigBuilder a11(int x);

    KotlinDataSomeDefaultsBigBuilder a12(int x);

    KotlinDataSomeDefaultsBigBuilder a13(int x);

    KotlinDataSomeDefaultsBigBuilder a14(int x);

    KotlinDataSomeDefaultsBigBuilder a15(int x);

    KotlinDataSomeDefaultsBigBuilder a16(int x);

    KotlinDataSomeDefaultsBigBuilder a17(int x);

    KotlinDataSomeDefaultsBigBuilder a18(int x);

    KotlinDataSomeDefaultsBigBuilder a19(int x);

    KotlinDataSomeDefaultsBigBuilder a20(int x);

    KotlinDataSomeDefaultsBigBuilder a21(int x);

    KotlinDataSomeDefaultsBigBuilder a22(int x);

    KotlinDataSomeDefaultsBigBuilder a23(int x);

    KotlinDataSomeDefaultsBigBuilder a24(int x);

    KotlinDataSomeDefaultsBigBuilder a25(int x);

    KotlinDataSomeDefaultsBigBuilder a26(int x);

    KotlinDataSomeDefaultsBigBuilder a27(int x);

    KotlinDataSomeDefaultsBigBuilder a28(int x);

    KotlinDataSomeDefaultsBigBuilder a29(int x);

    KotlinDataSomeDefaultsBigBuilder a30(int x);

    KotlinDataSomeDefaultsBigBuilder a31(int x);

    KotlinDataSomeDefaultsBig build();
  }

  @Test
  public void kotlinSomeDefaultsBig() {
    KotlinDataSomeDefaultsBig allDefaulted =
        KotlinDataSomeDefaultsBigBuilder.builder().requiredInt(23).requiredString("skidoo").build();
    assertThat(allDefaulted.getRequiredInt()).isEqualTo(23);
    assertThat(allDefaulted.getRequiredString()).isEqualTo("skidoo");
    assertThat(allDefaulted.getA1()).isEqualTo(1);
    assertThat(allDefaulted.getA31()).isEqualTo(31);
  }

  @AutoBuilder(ofClass = KotlinDataWithList.class)
  interface KotlinDataWithListBuilder {
    static KotlinDataWithListBuilder builder() {
      return new AutoBuilder_AutoBuilderKotlinTest_KotlinDataWithListBuilder();
    }

    static KotlinDataWithListBuilder builder(KotlinDataWithList kotlinData) {
      return new AutoBuilder_AutoBuilderKotlinTest_KotlinDataWithListBuilder(kotlinData);
    }

    KotlinDataWithListBuilder list(List<? extends CharSequence> list);

    KotlinDataWithListBuilder number(int number);

    KotlinDataWithList build();
  }

  // The `getList()` method returns `List<CharSequence>` as seen from Java, but the `list` parameter
  // to the constructor has type `List<? extends CharSequence>`.
  @Test
  public void kotlinWildcards() {
    List<String> strings = ImmutableList.of("foo");
    KotlinDataWithList x = KotlinDataWithListBuilder.builder().list(strings).number(17).build();
    assertThat(x.getList()).isEqualTo(strings);
    assertThat(x.getNumber()).isEqualTo(17);
    KotlinDataWithList y = KotlinDataWithListBuilder.builder(x).number(23).build();
    assertThat(y.getList()).isEqualTo(strings);
    assertThat(y.getNumber()).isEqualTo(23);
  }
}
