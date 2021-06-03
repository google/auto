/*
 * Copyright 2013 Google LLC
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
package com.google.auto.factory;

import com.google.auto.factory.otherpackage.OtherPackage;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

@Module
abstract class DaggerModule {
  private DaggerModule() {} // no instances

  @Binds
  abstract Dependency provideDependency(DependencyImpl impl);

  @Binds
  @Qualifier
  abstract Dependency provideQualifiedDependency(QualifiedDependencyImpl impl);

  @Provides
  static int providePrimitive() {
    return 1;
  }

  @Provides
  @Qualifier
  static int provideQualifiedPrimitive() {
    return 2;
  }

  @Provides
  static Number provideNumber() {
    return 3;
  }

  @Provides
  static ReferencePackage provideReferencePackage(ReferencePackageFactory factory) {
    return factory.create(17);
  }

  @Provides
  static OtherPackage provideOtherPackage() {
    return new OtherPackage(null, 23);
  }
}
