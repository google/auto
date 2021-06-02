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

import javax.annotation.processing.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(
    value = "com.google.auto.factory.processor.AutoFactoryProcessor",
    comments = "https://github.com/google/auto/tree/master/factory"
    )
final class MultipleProvidedParamsSameKeyFactory {
  private final Provider<String> java_lang_StringProvider;

  @Inject
  MultipleProvidedParamsSameKeyFactory(Provider<String> java_lang_StringProvider) {
    this.java_lang_StringProvider = checkNotNull(java_lang_StringProvider, 1);
  }

  MultipleProvidedParamsSameKey create() {
    return new MultipleProvidedParamsSameKey(
        checkNotNull(java_lang_StringProvider.get(), 1),
        checkNotNull(java_lang_StringProvider.get(), 2),
        java_lang_StringProvider.get(),
        java_lang_StringProvider,
        java_lang_StringProvider);
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
