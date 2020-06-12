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
package com.google.auto.factory.processor;

import com.google.auto.value.AutoValue;

/** A Java class name, separated into its package part and its class part. */
@AutoValue
abstract class PackageAndClass {
  /**
   * The package part of this class name. For {@code java.util.Map.Entry}, it would be {@code
   * java.util}.
   */
  abstract String packageName();

  /**
   * The class part of this class name. For {@code java.util.Map.Entry}, it would be {@code
   * Map.Entry}.
   */
  abstract String className();

  static PackageAndClass of(String packageName, String className) {
    return new AutoValue_PackageAndClass(packageName, className);
  }
}
