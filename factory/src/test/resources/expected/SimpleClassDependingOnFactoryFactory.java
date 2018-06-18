/*
 * Copyright (C) 2018 Google, Inc.
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
package tests.sample;

import javax.annotation.processing.Generated;
import javax.inject.Inject;
import javax.inject.Provider;
import tests.PublicClassFactory;

@Generated(
  value = "com.google.auto.factory.processor.AutoFactoryProcessor",
  comments = "https://github.com/google/auto/tree/master/factory"
)
final class SimpleClassDependingOnFactoryFactory {
  private final Provider<PublicClassFactory> depAProvider;

  @Inject
  SimpleClassDependingOnFactoryFactory(Provider<PublicClassFactory> depAProvider) {
    this.depAProvider = checkNotNull(depAProvider, 1);
  }

  SimpleClassDependingOnFactory create() {
    return new SimpleClassDependingOnFactory(checkNotNull(depAProvider.get(), 1));
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
