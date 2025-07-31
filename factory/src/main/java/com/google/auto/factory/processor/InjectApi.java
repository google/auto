/*
 * Copyright 2023 Google LLC
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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.stream.Collectors.joining;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.AbstractMap.SimpleEntry;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import org.jspecify.annotations.Nullable;

/** Encapsulates the choice of {@code jakarta.inject} or {@code javax.inject}. */
@AutoValue
abstract class InjectApi {
  abstract TypeElement inject();

  abstract TypeElement provider();

  abstract TypeElement qualifier();

  private static final ImmutableList<String> PREFIXES_IN_ORDER =
      ImmutableList.of("jakarta.inject.", "javax.inject.");

  static InjectApi from(Elements elementUtils, @Nullable String apiPrefix) {
    ImmutableList<String> apiPackages =
        (apiPrefix == null) ? PREFIXES_IN_ORDER : ImmutableList.of(apiPrefix + ".inject.");
    for (String apiPackage : apiPackages) {
      ImmutableMap<String, TypeElement> apiMap = apiMap(elementUtils, apiPackage);
      TypeElement inject = apiMap.get("Inject");
      TypeElement provider = apiMap.get("Provider");
      TypeElement qualifier = apiMap.get("Qualifier");
      if (inject != null && provider != null && qualifier != null) {
        return new AutoValue_InjectApi(inject, provider, qualifier);
      }
    }
    String classes = "{" + String.join(",", API_CLASSES) + "}";
    String missing = apiPackages.stream().sorted().map(s -> s + classes).collect(joining(" or "));
    throw new IllegalStateException("Class path for AutoFactory class must include " + missing);
  }

  /** True if {@code type} is a {@code Provider}. */
  boolean isProvider(TypeMirror type) {
    return type.getKind().equals(TypeKind.DECLARED)
        && MoreTypes.asTypeElement(type).equals(provider());
  }

  private static ImmutableMap<String, TypeElement> apiMap(
      Elements elementUtils, String apiPackage) {
    return API_CLASSES.stream()
        .map(name -> new SimpleEntry<>(name, elementUtils.getTypeElement(apiPackage + name)))
        .filter(entry -> entry.getValue() != null)
        .collect(toImmutableMap(SimpleEntry::getKey, SimpleEntry::getValue));
  }

  private static final ImmutableSet<String> API_CLASSES =
      ImmutableSet.of("Inject", "Provider", "Qualifier");
}
