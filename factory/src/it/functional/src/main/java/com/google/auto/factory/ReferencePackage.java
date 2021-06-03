/*
 * Copyright 2020 Google LLC
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
import com.google.auto.factory.otherpackage.OtherPackageFactory;
import javax.inject.Inject;

@AutoFactory
public class ReferencePackage {
  private final OtherPackageFactory otherPackageFactory;
  private final int random;

  @Inject
  ReferencePackage(@Provided OtherPackageFactory otherPackageFactory, int random) {
    this.otherPackageFactory = otherPackageFactory;
    this.random = random;
  }

  public OtherPackage otherPackage() {
    return otherPackageFactory.create(random);
  }
}
