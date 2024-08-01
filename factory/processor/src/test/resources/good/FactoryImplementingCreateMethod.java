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
import java.util.List;

final class FactoryImplementingCreateMethod {

  interface Interface {}

  interface FactoryInterfaceWithCreateMethod {
    Interface create();

    Interface create(int a);

    Interface create(List<Integer> generic);
  }

  @AutoFactory(implementing = FactoryInterfaceWithCreateMethod.class)
  static class ConcreteClass implements Interface {
    // Will generate a method with a signature that matches one from the interface.
    ConcreteClass() {}

    // Will generate a method with a signature that matches one from the interface.
    ConcreteClass(int aDifferentArgumentName) {}

    // Will generate a method with a signature that matches one from the interface.
    ConcreteClass(List<Integer> genericWithDifferentArgumentName) {}

    ConcreteClass(int a, boolean b) {}
  }
}
