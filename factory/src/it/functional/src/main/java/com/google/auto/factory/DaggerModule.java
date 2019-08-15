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

import dagger.Module;
import dagger.Provides;

@Module
final class DaggerModule {
  @Provides Dependency provideDependency(DependencyImpl impl) {
    return impl;
  }

  @Provides
  @Qualifier
  Dependency provideQualifiedDependency(QualifiedDependencyImpl impl) {
    return impl;
  }

  @Provides
  int providePrimitive() {
    return 1;
  }

  @Provides
  @Qualifier
  int provideQualifiedPrimitive() {
    return 2;
  }

  @Provides
  Number provideNumber() {
    return 3;
  }
}
