/*
 * Copyright 2015 Google LLC
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
package com.google.auto.value.processor;

import static com.google.auto.value.processor.ClassNames.COPY_ANNOTATIONS_NAME;

import com.google.auto.value.extension.AutoValueExtension;
import com.google.auto.value.extension.AutoValueExtension.BuilderContext;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

class ExtensionContext implements AutoValueExtension.Context {

  private final ProcessingEnvironment processingEnvironment;
  private final TypeElement autoValueClass;
  private final ImmutableMap<String, ExecutableElement> properties;
  private final ImmutableMap<String, TypeMirror> propertyTypes;
  private final ImmutableSet<ExecutableElement> abstractMethods;
  private Optional<BuilderContext> builderContext = Optional.empty();

  ExtensionContext(
      ProcessingEnvironment processingEnvironment,
      TypeElement autoValueClass,
      ImmutableMap<String, ExecutableElement> properties,
      ImmutableMap<ExecutableElement, TypeMirror> propertyMethodsAndTypes,
      ImmutableSet<ExecutableElement> abstractMethods) {
    this.processingEnvironment = processingEnvironment;
    this.autoValueClass = autoValueClass;
    this.properties = properties;
    this.propertyTypes =
        ImmutableMap.copyOf(Maps.transformValues(properties, propertyMethodsAndTypes::get));
    this.abstractMethods = abstractMethods;
  }

  void setBuilderContext(BuilderContext builderContext) {
    this.builderContext = Optional.of(builderContext);
  }

  @Override
  public ProcessingEnvironment processingEnvironment() {
    return processingEnvironment;
  }

  @Override
  public String packageName() {
    return TypeSimplifier.packageNameOf(autoValueClass);
  }

  @Override
  public TypeElement autoValueClass() {
    return autoValueClass;
  }

  @Override
  public String finalAutoValueClassName() {
    return AutoValueProcessor.generatedSubclassName(autoValueClass, 0);
  }

  @Override
  public Map<String, ExecutableElement> properties() {
    return properties;
  }

  @Override
  public Map<String, TypeMirror> propertyTypes() {
    return propertyTypes;
  }

  @Override
  public Set<ExecutableElement> abstractMethods() {
    return abstractMethods;
  }

  @Override
  public List<AnnotationMirror> classAnnotationsToCopy(TypeElement classToCopyFrom) {
    // Only copy annotations from a class if it has @AutoValue.CopyAnnotations.
    if (!AutoValueishProcessor.hasAnnotationMirror(classToCopyFrom, COPY_ANNOTATIONS_NAME)) {
      return ImmutableList.of();
    }

    ImmutableSet<String> excludedAnnotations =
        ImmutableSet.<String>builder()
            .addAll(AutoValueishProcessor.getExcludedAnnotationClassNames(classToCopyFrom))
            .addAll(AutoValueishProcessor.getAnnotationsMarkedWithInherited(classToCopyFrom))
            //
            // Kotlin classes have an intrinsic @Metadata annotation generated
            // onto them by kotlinc. This annotation is specific to the annotated
            // class and should not be implicitly copied. Doing so can mislead
            // static analysis or metaprogramming tooling that reads the data
            // contained in these annotations.
            //
            // It may be surprising to see AutoValue classes written in Kotlin
            // when they could be written as Kotlin data classes, but this can
            // come up in cases where consumers rely on AutoValue features or
            // extensions that are not available in data classes.
            //
            // See: https://github.com/google/auto/issues/1087
            //
            .add(ClassNames.KOTLIN_METADATA_NAME)
            .build();

    return AutoValueishProcessor.annotationsToCopy(
        autoValueClass, classToCopyFrom, excludedAnnotations, processingEnvironment.getTypeUtils());
  }

  @Override
  public List<AnnotationMirror> methodAnnotationsToCopy(ExecutableElement method) {
    return AutoValueishProcessor.propertyMethodAnnotations(
        autoValueClass, method, processingEnvironment.getTypeUtils());
  }

  @Override
  public Optional<BuilderContext> builder() {
    return builderContext;
  }
}
