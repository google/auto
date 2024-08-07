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

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import javax.inject.Provider;

class MultipleFactoriesConflictingParameterNames {

  @AutoFactory
  MultipleFactoriesConflictingParameterNames(
      @Provided String string,
      @Provided Object duplicatedKey_nameDoesntMatter,
      @Provided Provider<Object> duplicatedKeyProvider_nameDoesntMatter,
      // used to disambiguate with the second constructor since qualifiers aren't part of the type
      // system
      Object unused) {}

  @AutoFactory
  MultipleFactoriesConflictingParameterNames(
      @Provided @AQualifier String string,
      @Provided @AQualifier Object qualifiedDuplicatedKey_nameDoesntMatter,
      @Provided @AQualifier Provider<Object> qualifiedDuplicatedKeyProvider_nameDoesntMatter) {}
}
