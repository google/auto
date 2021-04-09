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
package com.google.auto.value.processor;

import static com.google.common.collect.ImmutableBiMap.toImmutableBiMap;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

class BuilderMethodClassifierForAutoBuilder extends BuilderMethodClassifier<VariableElement> {
  private final ExecutableElement executable;
  private final ImmutableBiMap<VariableElement, String> paramToPropertyName;

  private BuilderMethodClassifierForAutoBuilder(
      ErrorReporter errorReporter,
      ProcessingEnvironment processingEnv,
      ExecutableElement executable,
      TypeMirror builtType,
      TypeElement builderType,
      ImmutableBiMap<VariableElement, String> paramToPropertyName,
      ImmutableMap<String, TypeMirror> propertyTypes) {
    super(
        errorReporter,
        processingEnv,
        builtType,
        builderType,
        propertyTypes);
    this.executable = executable;
    this.paramToPropertyName = paramToPropertyName;
  }

  /**
   * Classifies the given methods from a builder type and its ancestors.
   *
   * @param methods the abstract methods in {@code builderType} and its ancestors.
   * @param errorReporter where to report errors.
   * @param processingEnv the ProcessingEnvironment for annotation processing.
   * @param executable the constructor or static method that AutoBuilder will call.
   * @param builtType the type to be built.
   * @param builderType the builder class or interface within {@code ofClass}.
   * @return an {@code Optional} that contains the results of the classification if it was
   *     successful or nothing if it was not.
   */
  static Optional<BuilderMethodClassifier<VariableElement>> classify(
      Iterable<ExecutableElement> methods,
      ErrorReporter errorReporter,
      ProcessingEnvironment processingEnv,
      ExecutableElement executable,
      TypeMirror builtType,
      TypeElement builderType) {
    ImmutableBiMap<VariableElement, String> paramToPropertyName =
        executable.getParameters().stream()
            .collect(toImmutableBiMap(v -> v, v -> v.getSimpleName().toString()));
    ImmutableMap<String, TypeMirror> propertyTypes =
        executable.getParameters().stream()
            .collect(toImmutableMap(v -> v.getSimpleName().toString(), Element::asType));
    BuilderMethodClassifier<VariableElement> classifier =
        new BuilderMethodClassifierForAutoBuilder(
            errorReporter,
            processingEnv,
            executable,
            builtType,
            builderType,
            paramToPropertyName,
            propertyTypes);
    if (classifier.classifyMethods(methods, false)) {
      return Optional.of(classifier);
    } else {
      return Optional.empty();
    }
  }

  @Override
  Optional<String> propertyForBuilderGetter(String methodName) {
    // TODO: handle getFoo -> foo
    return paramToPropertyName.containsValue(methodName)
        ? Optional.of(methodName)
        : Optional.empty();
  }

  @Override
  void checkForFailedJavaBean(ExecutableElement rejectedSetter) {}

  @Override
  ImmutableBiMap<String, VariableElement> propertyElements() {
    return paramToPropertyName.inverse();
  }

  @Override
  TypeMirror originalPropertyType(VariableElement propertyElement) {
    return propertyElement.asType();
  }

  @Override
  String autoWhat() {
    return "AutoBuilder";
  }

  @Override
  String getterMustMatch() {
    return "a parameter of " + AutoBuilderProcessor.executableString(executable);
  }

  @Override
  String fooBuilderMustMatch() {
    return "foo";
  }
}
