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
package tests;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

final class ConstructorAnnotatedNonFinal {
  @AutoFactory(allowSubclasses = true)
  ConstructorAnnotatedNonFinal() {}

  ConstructorAnnotatedNonFinal(Object obj) {}

  @AutoFactory(allowSubclasses = true)
  ConstructorAnnotatedNonFinal(String s) {}

  @AutoFactory(allowSubclasses = true)
  ConstructorAnnotatedNonFinal(@Provided Object obj, int i) {}

  @AutoFactory(allowSubclasses = true)
  ConstructorAnnotatedNonFinal(@Provided Object obj, char c) {}
}
