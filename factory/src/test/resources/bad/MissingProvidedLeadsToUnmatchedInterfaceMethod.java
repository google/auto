/*
 * Copyright 2026 Google LLC
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

/**
 * Regression for https://github.com/google/auto/issues/697: forgetting {@code @Provided} on a
 * constructor parameter makes that parameter a factory argument, so the {@code implementing}
 * interface method no longer matches. The processor must fail at compile time instead of
 * generating a recursive {@code create} stub that StackOverflowErrors at runtime.
 */
@AutoFactory(implementing = MissingProvidedLeadsToUnmatchedInterfaceMethod.Factory.class)
class MissingProvidedLeadsToUnmatchedInterfaceMethod {

  interface Factory {
    MissingProvidedLeadsToUnmatchedInterfaceMethod create(String runtimeDep);
  }

  MissingProvidedLeadsToUnmatchedInterfaceMethod(
      @Provided String dep1,
      String dep2, // missing @Provided — becomes an extra factory parameter
      String runtimeDep) {}
}
