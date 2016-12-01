/*
 * Copyright (C) 2016 Google, Inc.
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
package com.google.auto.value.extension.memoized;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MemoizedTest {

  private Value value;
  private ListValue<Integer, String> listValue;

  @AutoValue
  abstract static class Value {
    private int primitiveCount;
    private int notNullableCount;
    private int nullableCount;
    private int returnsNullCount;
    private int notNullableButReturnsNullCount;

    abstract String string();

    abstract HashCodeAndToStringCounter counter();

    @Memoized
    int primitive() {
      return ++primitiveCount;
    }

    @Memoized
    String notNullable() {
      notNullableCount++;
      return "derived " + string() + " " + notNullableCount;
    }

    @Memoized
    @Nullable
    String nullable() {
      nullableCount++;
      return "nullable derived " + string() + " " + nullableCount;
    }

    @Memoized
    @Nullable
    String returnsNull() {
      returnsNullCount++;
      return null;
    }

    @Memoized
    String notNullableButReturnsNull() {
      notNullableButReturnsNullCount++;
      return null;
    }

    @Override
    @Memoized
    public abstract int hashCode();

    @Override
    @Memoized
    public abstract String toString();
  }

  @AutoValue
  abstract static class ListValue<T extends Number, K> {

    abstract T value();

    abstract K otherValue();

    @Memoized
    ImmutableList<T> myTypedList() {
      return ImmutableList.of(value());
    }
  }

  static class HashCodeAndToStringCounter {
    int hashCodeCount;
    int toStringCount;

    @Override
    public int hashCode() {
      return ++hashCodeCount;
    }

    @Override
    public String toString() {
      return "a string" + ++toStringCount;
    }
  }

  @Before
  public void setUp() {
    value = new AutoValue_MemoizedTest_Value("string", new HashCodeAndToStringCounter());
    listValue = new AutoValue_MemoizedTest_ListValue<Integer, String>(0, "hello");
  }

  @Test
  public void listValueList() {
    assertThat(listValue.myTypedList()).containsExactly(listValue.value());
  }

  @Test
  public void listValueString() {
    assertThat(listValue.otherValue()).isEqualTo("hello");
  }

  @Test
  public void primitive() {
    assertThat(value.primitive()).isEqualTo(1);
    assertThat(value.primitive()).isEqualTo(1);
    assertThat(value.primitiveCount).isEqualTo(1);
  }

  @Test
  public void notNullable() {
    assertThat(value.notNullable()).isEqualTo("derived string 1");
    assertThat(value.notNullable()).isSameAs(value.notNullable());
    assertThat(value.notNullableCount).isEqualTo(1);
  }

  @Test
  public void nullable() {
    assertThat(value.nullable()).isEqualTo("nullable derived string 1");
    assertThat(value.nullable()).isSameAs(value.nullable());
    assertThat(value.nullableCount).isEqualTo(1);
  }

  @Test
  public void returnsNull() {
    assertThat(value.returnsNull()).isNull();
    assertThat(value.returnsNull()).isNull();
    assertThat(value.returnsNullCount).isEqualTo(1);
  }

  @Test
  public void notNullableButReturnsNull() {
    try {
      value.notNullableButReturnsNull();
      fail();
    } catch (NullPointerException expected) {
      assertThat(expected).hasMessage("notNullableButReturnsNull() cannot return null");
    }
    assertThat(value.notNullableButReturnsNullCount).isEqualTo(1);
  }

  @Test
  public void testHashCode() {
    assertThat(value.hashCode()).isEqualTo(value.hashCode());
    assertThat(value.counter().hashCodeCount).isEqualTo(1);
  }

  @Test
  public void testToString() {
    assertThat(value.toString()).isEqualTo(value.toString());
    assertThat(value.counter().toStringCount).isEqualTo(1);
  }
}
