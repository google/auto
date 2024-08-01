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
import com.google.auto.factory.Provided;
import javax.annotation.Nullable;

@AutoFactory
@SuppressWarnings("unused")
final class SimpleClassNullableParameters {
  @Nullable private final String nullable;
  @Nullable private final String qualifiedNullable;
  @Nullable private final String providedNullable;
  @Nullable private final String providedQualifiedNullable;

  // TODO(ronshapiro): with Java 8, test Provider<@Nullable String> parameters and provider fields
  SimpleClassNullableParameters(
      @Nullable String nullable,
      @Nullable @AQualifier String qualifiedNullable,
      @Nullable @Provided String providedNullable,
      @Nullable @Provided @BQualifier String providedQualifiedNullable) {
    this.nullable = nullable;
    this.qualifiedNullable = qualifiedNullable;
    this.providedNullable = providedNullable;
    this.providedQualifiedNullable = providedQualifiedNullable;
  }
}
