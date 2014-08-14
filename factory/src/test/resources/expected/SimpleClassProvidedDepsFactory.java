/*
 * Copyright (C) 2013 Google, Inc.
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

import javax.annotation.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated("com.google.auto.factory.processor.AutoFactoryProcessor")
class SimpleClassProvidedDepsFactory {
  private final Provider<String> providedDepAProvider;
  private final Provider<String> providedDepBProvider;
  
  @Inject SimpleClassProvidedDepsFactory(
      @AQualifier Provider<String> providedDepAProvider,
      @BQualifier Provider<String> providedDepBProvider) {
    this.providedDepAProvider = providedDepAProvider;
    this.providedDepBProvider = providedDepBProvider;
  }
  
  SimpleClassProvidedDeps create() {
    return new SimpleClassProvidedDeps(providedDepAProvider.get(), providedDepBProvider.get());
  }
}
