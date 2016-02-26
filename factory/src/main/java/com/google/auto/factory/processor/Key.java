/*
 * Copyright (C) 2013 Google, Inc.
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

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

import static com.google.auto.factory.processor.Mirrors.unwrapOptionalEquivalence;
import static com.google.auto.factory.processor.Mirrors.wrapOptionalInEquivalence;

/**
 * A value object for types and qualifiers.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class Key {
  abstract TypeMirror type();
  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> qualifierWrapper();

  Optional<AnnotationMirror> qualifier() {
    return unwrapOptionalEquivalence(qualifierWrapper());
  }

  static Key create(Optional<AnnotationMirror> qualifier, TypeMirror type) {
    return new AutoValue_Key(
        type, wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), qualifier));
  }

  @Override
  public String toString() {
    String typeQualifiedName = MoreTypes.asTypeElement(type()).toString();
    return qualifier().isPresent()
        ? qualifier().get() + "/" + typeQualifiedName
        : typeQualifiedName;
  }
}
