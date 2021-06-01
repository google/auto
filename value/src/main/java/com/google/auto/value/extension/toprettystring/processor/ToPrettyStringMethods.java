/*
 * Copyright 2021 Google LLC
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

package com.google.auto.value.extension.toprettystring.processor;

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreStreams.toImmutableList;
import static com.google.auto.common.MoreStreams.toImmutableSet;
import static com.google.auto.value.extension.toprettystring.processor.Annotations.toPrettyStringAnnotation;
import static com.google.common.collect.MoreCollectors.toOptional;

import com.google.auto.value.extension.AutoValueExtension.Context;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

final class ToPrettyStringMethods {
  /**
   * Returns the {@link com.google.auto.value.extension.toprettystring.ToPrettyString} annotated
   * methods for an {@code @AutoValue} type.
   */
  static ImmutableSet<ExecutableElement> toPrettyStringMethods(Context context) {
    return context.abstractMethods().stream()
        .filter(method -> toPrettyStringAnnotation(method).isPresent())
        .collect(toImmutableSet());
  }

  /**
   * Returns the {@link com.google.auto.value.extension.toprettystring.ToPrettyString} annotated
   * method for a type.
   */
  static ImmutableList<ExecutableElement> toPrettyStringMethods(
      TypeElement element, Types types, Elements elements) {
    return getLocalAndInheritedMethods(element, types, elements).stream()
        .filter(method -> toPrettyStringAnnotation(method).isPresent())
        .collect(toImmutableList());
  }

  /**
   * Returns the {@link com.google.auto.value.extension.toprettystring.ToPrettyString} annotated
   * method for a type.
   */
  static Optional<ExecutableElement> toPrettyStringMethod(
      TypeElement element, Types types, Elements elements) {
    return toPrettyStringMethods(element, types, elements).stream().collect(toOptional());
  }

  private ToPrettyStringMethods() {}
}
