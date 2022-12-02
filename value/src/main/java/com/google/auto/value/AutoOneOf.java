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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies that the annotated class is a <em>one-of</em> class, also known as a <a
 * href="https://en.wikipedia.org/wiki/Tagged_union"><em>tagged union</em></a>. An
 * {@code @AutoOneOf} class is very similar to an {@link AutoValue @AutoValue} class, in that its
 * abstract methods define a set of properties. But unlike {@code @AutoValue}, only one of those
 * properties is defined in any given instance.
 *
 * <pre>{@code @AutoOneOf(StringOrInteger.Kind.class)
 * public abstract class StringOrInteger {
 *   public enum Kind {STRING, INTEGER}
 *
 *   public abstract Kind getKind();
 *
 *   public abstract String string();
 *   public abstract int integer();
 *
 *   public static StringOrInteger ofString(String s) {
 *     return AutoOneOf_StringOrInteger.string(s);
 *   }
 *
 *   public static StringOrInteger ofInteger(int i) {
 *     return AutoOneOf_StringOrInteger.integer(i);
 *   }
 * }
 *
 * String client(StringOrInteger stringOrInteger) {
 *   switch (stringOrInteger.getKind()) {
 *     case STRING:
 *       return "the string '" + stringOrInteger.string() + "'";
 *     case INTEGER:
 *       return "the integer " + stringOrInteger.integer();
 *   }
 *   throw new AssertionError();
 * }}</pre>
 *
 * <p>{@code @AutoOneOf} is explained in more detail in the <a
 * href="https://github.com/google/auto/blob/main/value/userguide/howto.md#oneof">user guide</a>.
 *
 * @author Chris Nokleberg
 * @author Éamonn McManus
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AutoOneOf {
  /** Specifies an enum that has one entry per variant in the one-of. */
  Class<? extends Enum<?>> value();
}
