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

import com.google.common.base.MoreObjects;
import java.math.BigInteger;
import java.util.Objects;
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
}
