/*
 * Copyright 2022 Google LLC
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
package tests;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@AutoFactory
final class ParameterAnnotations {
  @Retention(RUNTIME)
  @Target(PARAMETER)
  @interface NullableParameter {}

  // We have special treatment of @Nullable; make sure it doesn't get copied twice.
  @Retention(RUNTIME)
  @Target(PARAMETER)
  @interface Nullable {}

  @Retention(RUNTIME)
  @Target(TYPE_USE)
  @interface NullableType {}

  @Retention(RUNTIME)
  @Target({PARAMETER, TYPE_USE})
  @interface NullableParameterAndType {}

  ParameterAnnotations(
      @Provided @NullableParameter @NullableType String foo,
      @NullableParameter Integer bar,
      @Nullable Long baz,
      @NullableType Thread buh,
      @NullableParameterAndType String quux) {}
}
