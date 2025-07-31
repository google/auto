/*
 * Copyright 2013 Google LLC
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
final class SimpleClassProvidedDepsFactory {
  private final Provider<Integer> providedPrimitiveAProvider;
  private final Provider<Integer> providedPrimitiveBProvider;
  private final Provider<String> providedDepAProvider;
  private final Provider<String> providedDepBProvider;

  @Inject
  SimpleClassProvidedDepsFactory(
      @AQualifier Provider<Integer> providedPrimitiveAProvider,
      @BQualifier Provider<Integer> providedPrimitiveBProvider,
      @AQualifier Provider<String> providedDepAProvider,
      @BQualifier Provider<String> providedDepBProvider) {
    this.providedPrimitiveAProvider = checkNotNull(providedPrimitiveAProvider, 1, 4);
    this.providedPrimitiveBProvider = checkNotNull(providedPrimitiveBProvider, 2, 4);
    this.providedDepAProvider = checkNotNull(providedDepAProvider, 3, 4);
    this.providedDepBProvider = checkNotNull(providedDepBProvider, 4, 4);
  }

  SimpleClassProvidedDeps create() {
    return new SimpleClassProvidedDeps(
        checkNotNull(providedPrimitiveAProvider.get(), 1, 4),
        checkNotNull(providedPrimitiveBProvider.get(), 2, 4),
        checkNotNull(providedDepAProvider.get(), 3, 4),
        checkNotNull(providedDepBProvider.get(), 4, 4));
  }

  private static <T> T checkNotNull(T reference, int argumentNumber, int argumentCount) {
    if (reference == null) {
      throw new NullPointerException(
          "@AutoFactory method argument is null but is not marked @Nullable. Argument "
              + argumentNumber
              + " of "
              + argumentCount);
    }
    return reference;
  }
}
