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

@Generated(
    value = "com.google.auto.factory.processor.AutoFactoryProcessor",
    comments = "https://github.com/google/auto/tree/main/factory"
    )
final class SimpleClassPassedDepsFactory {
  @Inject
  SimpleClassPassedDepsFactory() {}

  SimpleClassPassedDeps create(String depA, String depB) {
    return new SimpleClassPassedDeps(checkNotNull(depA, 1, 2), checkNotNull(depB, 2, 2));
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
