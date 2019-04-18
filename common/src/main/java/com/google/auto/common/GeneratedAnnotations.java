/*
 * Copyright 2017 Google LLC
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
package com.google.auto.common;

import java.util.Optional;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** Utility methods for writing {@code @Generated} annotations. */
public final class GeneratedAnnotations {
  private GeneratedAnnotations() {}

  /**
   * Returns the element corresponding to the version of the {@code @Generated} annotation present
   * in the compile-time class- or module-path.
   *
   * <p>First looks for {@code javax.annotation.processing.Generated}, and then {@code
   * javax.annotation.Generated}. Returns whichever is in the classpath (or modulepath), or {@link
   * Optional#empty()} if neither is.
   *
   * @deprecated prefer {@link #generatedAnnotation(Elements, SourceVersion)}
   */
  @Deprecated
  public static Optional<TypeElement> generatedAnnotation(Elements elements) {
    TypeElement jdk9Generated = elements.getTypeElement("javax.annotation.processing.Generated");
    if (jdk9Generated != null) {
      return Optional.of(jdk9Generated);
    }
    return Optional.ofNullable(elements.getTypeElement("javax.annotation.Generated"));
  }

  /**
   * Returns the element corresponding to the {@code @Generated} annotation present at the target
   * {@code SourceVersion}.
   *
   * <p>Returns {@code javax.annotation.processing.Generated} for JDK 9 and newer, {@code
   * javax.annotation.Generated} for earlier releases, and Optional#empty()} if the annotation is
   * not available.
   */
  public static Optional<TypeElement> generatedAnnotation(
      Elements elements, SourceVersion sourceVersion) {
    return Optional.ofNullable(
        elements.getTypeElement(
            sourceVersion.compareTo(SourceVersion.RELEASE_8) > 0
                ? "javax.annotation.processing.Generated"
                : "javax.annotation.Generated"));
  }
}
