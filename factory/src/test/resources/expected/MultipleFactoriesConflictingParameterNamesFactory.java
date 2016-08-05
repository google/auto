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
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(
    value = "com.google.auto.factory.processor.AutoFactoryProcessor",
    comments = "https://github.com/google/auto/tree/master/factory"
)
final class MultipleFactoriesConflictingParameterNamesFactory {

  private final Provider<String> stringProvider;
  private final Provider<Object> java_lang_ObjectProvider;
  private final Provider<String> stringProvider2;
  private final Provider<Object> _tests_AQualifier_java_lang_ObjectProvider;

  @Inject
  MultipleFactoriesConflictingParameterNamesFactory(
      Provider<String> stringProvider,
      Provider<Object> java_lang_ObjectProvider,
      @AQualifier Provider<String> stringProvider2,
      @AQualifier Provider<Object> _tests_AQualifier_java_lang_ObjectProvider) {
    this.stringProvider = Preconditions.checkNotNull(stringProvider, 1);
    this.java_lang_ObjectProvider = Preconditions.checkNotNull(java_lang_ObjectProvider, 2);
    this.stringProvider2 = Preconditions.checkNotNull(stringProvider2, 3);
    this._tests_AQualifier_java_lang_ObjectProvider =
        Preconditions.checkNotNull(_tests_AQualifier_java_lang_ObjectProvider, 4);
  }

  MultipleFactoriesConflictingParameterNames create(Object unused) {
    return new MultipleFactoriesConflictingParameterNames(
        Preconditions.checkNotNull(stringProvider.get(), 1),
        Preconditions.checkNotNull(java_lang_ObjectProvider.get(), 2),
        java_lang_ObjectProvider,
        Preconditions.checkNotNull(unused, 4));
  }

  MultipleFactoriesConflictingParameterNames create() {
    return new MultipleFactoriesConflictingParameterNames(
        Preconditions.checkNotNull(stringProvider2.get(), 1),
        Preconditions.checkNotNull(_tests_AQualifier_java_lang_ObjectProvider.get(), 2),
        _tests_AQualifier_java_lang_ObjectProvider);
  }
}
