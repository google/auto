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

@Generated(value = "com.google.auto.factory.AutoFactoryProcessor")
final class ConstructorAnnotatedFactory {
  private final Provider<Object> objProvider;
  
  @Inject ConstructorAnnotatedFactory(Provider<Object> objProvider) {
    this.objProvider = objProvider;
  }
  
  ConstructorAnnotated create() {
    return new ConstructorAnnotated();
  }
  
  ConstructorAnnotated create(String s) {
    return new ConstructorAnnotated(s);
  }
  
  ConstructorAnnotated create(int i) {
    return new ConstructorAnnotated(objProvider.get(), i);
  }
  
  ConstructorAnnotated create(char c) {
    return new ConstructorAnnotated(objProvider.get(), c);
  }
}
