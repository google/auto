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

    abstract KotlinDataBuilder setInt(int x);

    abstract KotlinDataBuilder setString(String x);

    abstract KotlinData build();
  }

  @Test
  public void simpleKotlin() {
    KotlinData x = KotlinDataBuilder.builder().setInt(23).setString("skidoo").build();
    assertThat(x.getInt()).isEqualTo(23);
    assertThat(x.getString()).isEqualTo("skidoo");
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

    abstract KotlinDataWithDefaultsBuilder setAString(String x);

    abstract KotlinDataWithDefaults build();
  }

  @Test
  public void kotlinWithDefaults() {
    // AutoBuilder doesn't currently try to give the builder the same defaults as the Kotlin class,
    // but we do at least check that the presence of defaults doesn't throw AutoBuilder off.
    // When a constructor has default parameters, the Kotlin compiler generates an extra constructor
    // with two extra parameters: an int bitmask saying which parameters were defaulted, and a
    // DefaultConstructorMarker parameter to avoid clashing with another constructor that might have
    // an extra int parameter for some other reason. If AutoBuilder found this constructor it might
    // be confused, but fortunately the constructor is marked synthetic, and javax.lang.model
    // doesn't show synthetic elements.
    KotlinDataWithDefaults x =
        KotlinDataWithDefaultsBuilder.builder().setAString("answer").setAnInt(42).build();
    assertThat(x.getAString()).isEqualTo("answer");
    assertThat(x.getAnInt()).isEqualTo(42);
  }
}
