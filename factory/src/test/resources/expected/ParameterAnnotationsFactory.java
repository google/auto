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

import javax.annotation.processing.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(
    value = "com.google.auto.factory.processor.AutoFactoryProcessor",
    comments = "https://github.com/google/auto/tree/main/factory"
    )
final class ParameterAnnotationsFactory {
  private final Provider<@ParameterAnnotations.NullableType String> fooProvider;

  @Inject
  ParameterAnnotationsFactory(Provider<@ParameterAnnotations.NullableType String> fooProvider) {
    this.fooProvider = checkNotNull(fooProvider, 1);
  }

  ParameterAnnotations create(
      @ParameterAnnotations.NullableParameter Integer bar,
      @ParameterAnnotations.Nullable Long baz,
      @ParameterAnnotations.NullableType Thread buh,
      @ParameterAnnotations.NullableParameterAndType String quux) {
    return new ParameterAnnotations(
        checkNotNull(fooProvider.get(), 1),
        checkNotNull(bar, 2),
        baz,
        checkNotNull(buh, 4),
        checkNotNull(quux, 5));
  }

  private static <T> T checkNotNull(T reference, int argumentIndex) {
    if (reference == null) {
      throw new NullPointerException(
          "@AutoFactory method argument is null but is not marked @Nullable. Argument index: "
              + argumentIndex);
    }
    return reference;
  }
}
