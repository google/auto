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
package tests;

import com.google.auto.factory.internal.Preconditions;
import javax.annotation.Generated;
import javax.annotation.Nullable;
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
    this.providedNullableProvider = Preconditions.checkNotNull(providedNullableProvider, 1);
    this.providedQualifiedNullableProvider =
        Preconditions.checkNotNull(providedQualifiedNullableProvider, 2);
  }

  SimpleClassNullableParameters create(
      @Nullable String nullable, @Nullable @AQualifier String qualifiedNullable) {
    return new SimpleClassNullableParameters(
        nullable,
        qualifiedNullable,
        providedNullableProvider.get(),
        providedQualifiedNullableProvider.get());
  }
}
