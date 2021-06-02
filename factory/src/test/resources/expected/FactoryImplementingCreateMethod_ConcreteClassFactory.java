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

import java.util.List;
import javax.annotation.processing.Generated;
import javax.inject.Inject;

@Generated(
    value = "com.google.auto.factory.processor.AutoFactoryProcessor",
    comments = "https://github.com/google/auto/tree/master/factory"
    )
final class FactoryImplementingCreateMethod_ConcreteClassFactory
    implements FactoryImplementingCreateMethod.FactoryInterfaceWithCreateMethod {

  @Inject
  FactoryImplementingCreateMethod_ConcreteClassFactory() {}

  @Override
  public FactoryImplementingCreateMethod.ConcreteClass create() {
    return new FactoryImplementingCreateMethod.ConcreteClass();
  }

  @Override
  public FactoryImplementingCreateMethod.ConcreteClass create(int aDifferentArgumentName) {
    return new FactoryImplementingCreateMethod.ConcreteClass(aDifferentArgumentName);
  }

  @Override
  public FactoryImplementingCreateMethod.ConcreteClass create(
      List<Integer> genericWithDifferentArgumentName) {
    return new FactoryImplementingCreateMethod.ConcreteClass(
        checkNotNull(genericWithDifferentArgumentName, 1));
  }

  FactoryImplementingCreateMethod.ConcreteClass create(int a, boolean b) {
    return new FactoryImplementingCreateMethod.ConcreteClass(a, b);
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
