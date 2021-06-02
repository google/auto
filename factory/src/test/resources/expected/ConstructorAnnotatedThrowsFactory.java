/*
 * Copyright 2020 Google LLC
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

import java.io.IOException;
import javax.annotation.processing.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(
    value = "com.google.auto.factory.processor.AutoFactoryProcessor",
    comments = "https://github.com/google/auto/tree/master/factory"
    )
final class ConstructorAnnotatedThrowsFactory {
  private final Provider<Object> objProvider;

  @Inject
  ConstructorAnnotatedThrowsFactory(Provider<Object> objProvider) {
    this.objProvider = checkNotNull(objProvider, 1);
  }

  ConstructorAnnotatedThrows create() throws IOException, InterruptedException {
    return new ConstructorAnnotatedThrows();
  }

  ConstructorAnnotatedThrows create(String s) {
    return new ConstructorAnnotatedThrows(checkNotNull(s, 1));
  }

  ConstructorAnnotatedThrows create(int i) throws IOException {
    return new ConstructorAnnotatedThrows(checkNotNull(objProvider.get(), 1), i);
  }

  ConstructorAnnotatedThrows create(char c) throws InterruptedException {
    return new ConstructorAnnotatedThrows(checkNotNull(objProvider.get(), 1), c);
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
