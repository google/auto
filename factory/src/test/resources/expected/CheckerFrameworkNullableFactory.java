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

import java.util.Map;
import javax.annotation.processing.Generated;
import javax.inject.Inject;
import javax.inject.Provider;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.checkerframework.checker.nullness.compatqual.NullableType;

@Generated(
    value = "com.google.auto.factory.processor.AutoFactoryProcessor",
    comments = "https://github.com/google/auto/tree/master/factory"
    )
final class CheckerFrameworkNullableFactory {

  private final Provider<String> java_lang_StringProvider;

  private final Provider<Map.@NullableType Entry<?, ?>> providedNestedNullableTypeProvider;

  @Inject
  CheckerFrameworkNullableFactory(
      Provider<String> java_lang_StringProvider,
      Provider<Map.@NullableType Entry<?, ?>> providedNestedNullableTypeProvider) {
    this.java_lang_StringProvider = checkNotNull(java_lang_StringProvider, 1);
    this.providedNestedNullableTypeProvider = checkNotNull(providedNestedNullableTypeProvider, 2);
  }

  CheckerFrameworkNullable create(
      @NullableDecl String nullableDecl,
      @NullableType String nullableType,
      Map.@NullableType Entry<?, ?> nestedNullableType) {
    return new CheckerFrameworkNullable(
        nullableDecl,
        java_lang_StringProvider.get(),
        nullableType,
        java_lang_StringProvider.get(),
        nestedNullableType,
        providedNestedNullableTypeProvider.get());
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
