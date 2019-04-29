/*
 * Copyright 2018 Google LLC
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for Java8-specific {@code @AutoOneOf} behaviour.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class AutoOneOfJava8Test {
  @AutoOneOf(EqualsNullable.Kind.class)
  public abstract static class EqualsNullable {

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Nullable {}

    public enum Kind {
      THING
    }

    public abstract Kind kind();

    public abstract String thing();

    public static EqualsNullable ofThing(String thing) {
      return AutoOneOf_AutoOneOfJava8Test_EqualsNullable.thing(thing);
    }

    @Override
    public abstract boolean equals(@Nullable Object x);

    @Override
    public abstract int hashCode();
  }

  /**
   * Tests that a type annotation on the parameter of {@code equals(Object)} is copied into the
   * implementation class.
   */
  @Test
  public void equalsNullable() throws ReflectiveOperationException {
    EqualsNullable x = EqualsNullable.ofThing("foo");
    Class<? extends EqualsNullable> c = x.getClass();
    Method equals = c.getMethod("equals", Object.class);
    assertThat(equals.getDeclaringClass()).isNotSameInstanceAs(EqualsNullable.class);
    AnnotatedType parameterType = equals.getAnnotatedParameterTypes()[0];
    assertThat(parameterType.isAnnotationPresent(EqualsNullable.Nullable.class)).isTrue();
  }
}
