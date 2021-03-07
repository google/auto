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

package com.google.auto.value.extension.toprettystring;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;
import java.util.Collection;

/**
 * Annotates instance methods that return an easy-to-read {@link String} representing the instance.
 * When the method is {@code abstract} and enclosed in an {@link com.google.auto.value.AutoValue}
 * class, an implementation of the method will be automatically generated.
 *
 * <p>When generating an implementation of an {@code @ToPrettyString} method, each property of the
 * {@code @AutoValue} type is individually printed in an easy-to-read format. If the type of the
 * property itself has a {@code @ToPrettyString} method, that method will be called in assistance of
 * computing the pretty string. Non-{@code @AutoValue} classes can contribute a pretty string
 * representation by annotating a method with {@code @ToPrettyString}.
 *
 * <p>{@link Collection} and {@link Collection}-like types have special representations in generated
 * pretty strings.
 *
 * <p>If no {@code @ToPrettyString} method is found on a type and the type is not one with a built
 * in rendering, the {@link Object#toString()} value will be used instead.
 *
 * <p>{@code @ToPrettyString} is valid on overridden {@code toString()} and other methods alike.
 *
 * <h3>Example</h3>
 *
 * <pre>
 *   {@code @AutoValue}
 *   abstract class Pretty {
 *     abstract {@code List<String>} property();
 *
 *     {@code @ToPrettyString}
 *     abstract String toPrettyString();
 *   }
 *
 *   System.out.println(new AutoValue_Pretty(List.of("abc", "def", "has\nnewline)).toPrettyString())
 *   // Pretty{
 *   //   property = [
 *   //     abc,
 *   //     def,
 *   //     has
 *   //     newline,
 *   //   ]
 *   // }
 *   }</pre>
 */
@Documented
@Target(METHOD)
public @interface ToPrettyString {}
