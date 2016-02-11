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

import javax.annotation.Generated;
import javax.inject.Inject;

@Generated(
    value = "com.google.auto.factory.processor.AutoFactoryProcessor",
    comments = "https://github.com/google/auto/tree/master/factory"
)
public final class FactoryImplementingCreateMethod_ConcreteClassFactory
    implements FactoryImplementingCreateMethod.FactoryInterfaceWithCreateMethod {

  @Inject
  public FactoryImplementingCreateMethod_ConcreteClassFactory() {}

  public FactoryImplementingCreateMethod.ConcreteClass create() {
    return new FactoryImplementingCreateMethod.ConcreteClass();
  }

  public FactoryImplementingCreateMethod.ConcreteClass create(int a) {
    return new FactoryImplementingCreateMethod.ConcreteClass(a);
  }

  public FactoryImplementingCreateMethod.ConcreteClass create(int a, boolean b) {
    return new FactoryImplementingCreateMethod.ConcreteClass(a, b);
  }
}
