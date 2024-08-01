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
package tests;

import javax.annotation.processing.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(
    value = "com.google.auto.factory.processor.AutoFactoryProcessor",
    comments = "https://github.com/google/auto/tree/main/factory"
    )
final class Generics_ExplicitFooImplFactory<M extends Generics.Bar>
    implements Generics.FooFactory<M> {
  private final Provider<M> unusedProvider;

  @Inject
  Generics_ExplicitFooImplFactory(Provider<M> unusedProvider) {
    this.unusedProvider = checkNotNull(unusedProvider, 1, 1);
  }

  @Override
  public Generics.ExplicitFooImpl<M> create() {
    return new Generics.ExplicitFooImpl<M>(checkNotNull(unusedProvider.get(), 1, 1));
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
