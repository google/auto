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
package com.google.auto.factory.processor;

import static com.google.auto.factory.processor.Mirrors.unwrapOptionalEquivalence;
import static com.google.auto.factory.processor.Mirrors.wrapOptionalInEquivalence;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;

@AutoValue
abstract class ProviderField {
  abstract String name();

  abstract Key key();

  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> nullableWrapper();

  Optional<AnnotationMirror> nullable() {
    return unwrapOptionalEquivalence(nullableWrapper());
  }

  static ProviderField create(String name, Key key, Optional<AnnotationMirror> nullable) {
    return new AutoValue_ProviderField(
        name, key, wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), nullable));
  }
}
