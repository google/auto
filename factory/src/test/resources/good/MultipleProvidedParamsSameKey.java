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
import javax.annotation.Nullable;
import javax.inject.Provider;

@AutoFactory
final class MultipleProvidedParamsSameKey {
  private final String one;
  private final String two;
  private final String three;
  private final Provider<String> providerOne;
  private final Provider<String> providerTwo;

  public MultipleProvidedParamsSameKey(
      @Provided String one,
      @Provided String two,
      @Nullable @Provided String three,
      @Provided Provider<String> providerOne,
      @Provided Provider<String> providerTwo) {
    this.one = one;
    this.two = two;
    this.three = three;
    this.providerOne = providerOne;
    this.providerTwo = providerTwo;
  }
}
