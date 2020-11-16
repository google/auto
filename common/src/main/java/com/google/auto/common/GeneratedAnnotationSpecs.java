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

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import java.util.Optional;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;

/** Utility methods for writing {@code @Generated} annotations using JavaPoet. */
public final class GeneratedAnnotationSpecs {

  private GeneratedAnnotationSpecs() {}

  /**
   * Returns {@code @Generated("processorClass")} if either {@code
   * javax.annotation.processing.Generated} or {@code javax.annotation.Generated} is {@linkplain
   * GeneratedAnnotations#generatedAnnotation(Elements) available at compile time}.
   *
   * @deprecated prefer {@link #generatedAnnotationSpec(Elements, SourceVersion, Class)}
   */
  @Deprecated
  public static Optional<AnnotationSpec> generatedAnnotationSpec(
      Elements elements, Class<?> processorClass) {
    return generatedAnnotationSpecBuilder(elements, processorClass)
        .map(AnnotationSpec.Builder::build);
  }

  /**
   * Returns {@code @Generated(value = "processorClass", comments = "comments")} if either {@code
   * javax.annotation.processing.Generated} or {@code javax.annotation.Generated} is {@linkplain
   * GeneratedAnnotations#generatedAnnotation(Elements) available at compile time}.
   *
   * @deprecated prefer {@link #generatedAnnotationSpec(Elements, SourceVersion, Class, String)}
   */
  @Deprecated
  public static Optional<AnnotationSpec> generatedAnnotationSpec(
      Elements elements, Class<?> processorClass, String comments) {
    return generatedAnnotationSpecBuilder(elements, processorClass)
        .map(annotation -> annotation.addMember("comments", "$S", comments).build());
  }

  /**
   * Returns {@code @Generated("processorClass")} for the target {@code SourceVersion}.
   *
   * <p>Returns {@code javax.annotation.processing.Generated} for JDK 9 and newer, {@code
   * javax.annotation.Generated} for earlier releases, and Optional#empty()} if the annotation is
   * not available.
   */
  public static Optional<AnnotationSpec> generatedAnnotationSpec(
      Elements elements, SourceVersion sourceVersion, Class<?> processorClass) {
    return generatedAnnotationSpecBuilder(elements, sourceVersion, processorClass)
        .map(AnnotationSpec.Builder::build);
  }

  /**
   * Returns {@code @Generated(value = "processorClass", comments = "comments")} for the target
   * {@code SourceVersion}.
   *
   * <p>Returns {@code javax.annotation.processing.Generated} for JDK 9 and newer, {@code
   * javax.annotation.Generated} for earlier releases, and Optional#empty()} if the annotation is
   * not available.
   */
  public static Optional<AnnotationSpec> generatedAnnotationSpec(
      Elements elements, SourceVersion sourceVersion, Class<?> processorClass, String comments) {
    return generatedAnnotationSpecBuilder(elements, sourceVersion, processorClass)
        .map(annotation -> annotation.addMember("comments", "$S", comments).build());
  }

  private static Optional<AnnotationSpec.Builder> generatedAnnotationSpecBuilder(
      Elements elements, Class<?> processorClass) {
    return GeneratedAnnotations.generatedAnnotation(elements)
        .map(
            generated ->
                AnnotationSpec.builder(ClassName.get(generated))
                    .addMember("value", "$S", processorClass.getCanonicalName()));
  }

  private static Optional<AnnotationSpec.Builder> generatedAnnotationSpecBuilder(
      Elements elements, SourceVersion sourceVersion, Class<?> processorClass) {
    return GeneratedAnnotations.generatedAnnotation(elements, sourceVersion)
        .map(
            generated ->
                AnnotationSpec.builder(ClassName.get(generated))
                    .addMember("value", "$S", processorClass.getCanonicalName()));
  }
}
