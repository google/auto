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
import tests.SimpleClassImplementingSimpleInterface.SimpleInterface;

@Generated("com.google.auto.factory.processor.AutoFactoryProcessor")
final class SimpleClassImplementingSimpleInterfaceFactory implements SimpleInterface {
  @Inject SimpleClassImplementingSimpleInterfaceFactory() {}
  
  SimpleClassImplementingSimpleInterface create() {
    return new SimpleClassImplementingSimpleInterface();
  }

  @Override public SimpleClassImplementingSimpleInterface newInstance() {
    return create();
  }
}
