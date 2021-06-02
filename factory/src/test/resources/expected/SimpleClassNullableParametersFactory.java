/*
 * Copyright 2016 Google LLC
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

import javax.annotation.Nullable;
import javax.annotation.processing.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(
    value = "com.google.auto.factory.processor.AutoFactoryProcessor",
    comments = "https://github.com/google/auto/tree/master/factory"
    )
final class SimpleClassNullableParametersFactory {
  private final Provider<String> providedNullableProvider;

  private final Provider<String> providedQualifiedNullableProvider;

  @Inject
  SimpleClassNullableParametersFactory(
      Provider<String> providedNullableProvider,
      @BQualifier Provider<String> providedQualifiedNullableProvider) {
    this.providedNullableProvider = checkNotNull(providedNullableProvider, 1);
    this.providedQualifiedNullableProvider = checkNotNull(providedQualifiedNullableProvider, 2);
  }

  SimpleClassNullableParameters create(
      @Nullable String nullable, @Nullable @AQualifier String qualifiedNullable) {
    return new SimpleClassNullableParameters(
        nullable,
        qualifiedNullable,
        providedNullableProvider.get(),
        providedQualifiedNullableProvider.get());
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
