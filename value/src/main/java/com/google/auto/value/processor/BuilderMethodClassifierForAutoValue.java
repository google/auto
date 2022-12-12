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

import static com.google.common.collect.Sets.difference;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

class BuilderMethodClassifierForAutoValue extends BuilderMethodClassifier<ExecutableElement> {
  private final ErrorReporter errorReporter;
  private final ImmutableBiMap<ExecutableElement, String> getterToPropertyName;
  private final ImmutableMap<String, ExecutableElement> getterNameToGetter;
  private final TypeMirror builtType;

  private BuilderMethodClassifierForAutoValue(
      ErrorReporter errorReporter,
      ProcessingEnvironment processingEnv,
      TypeMirror builtType,
      TypeElement builderType,
      ImmutableBiMap<ExecutableElement, String> getterToPropertyName,
      ImmutableMap<String, TypeMirror> rewrittenPropertyTypes,
      Nullables nullables) {
    super(
        errorReporter,
        processingEnv,
        builtType,
        builderType,
        rewrittenPropertyTypes,
        ImmutableSet.of(),
        nullables);
    this.errorReporter = errorReporter;
    this.getterToPropertyName = getterToPropertyName;
    this.getterNameToGetter =
        Maps.uniqueIndex(getterToPropertyName.keySet(), m -> m.getSimpleName().toString());
    this.builtType = builtType;
  }

  /**
   * Classifies the given methods from a builder type and its ancestors.
   *
   * @param methods the abstract methods in {@code builderType} and its ancestors.
   * @param errorReporter where to report errors.
   * @param processingEnv the {@link ProcessingEnvironment} for annotation processing.
   * @param autoValueClass the {@code AutoValue} class containing the builder.
   * @param builderType the builder class or interface within {@code autoValueClass}.
   * @param getterToPropertyName a map from getter methods to the properties they get.
   * @param rewrittenPropertyTypes a map from property names to types. The types here use type
   *     parameters from the builder class (if any) rather than from the {@code AutoValue} class,
   *     even though the getter methods are in the latter.
   * @param autoValueHasToBuilder true if the containing {@code @AutoValue} class has a {@code
   *     toBuilder()} method.
   * @return an {@code Optional} that contains the results of the classification if it was
   *     successful or nothing if it was not.
   */
  static Optional<BuilderMethodClassifier<ExecutableElement>> classify(
      Iterable<ExecutableElement> methods,
      ErrorReporter errorReporter,
      ProcessingEnvironment processingEnv,
      TypeElement autoValueClass,
      TypeElement builderType,
      ImmutableBiMap<ExecutableElement, String> getterToPropertyName,
      ImmutableMap<String, TypeMirror> rewrittenPropertyTypes,
      Nullables nullables,
      boolean autoValueHasToBuilder) {
    BuilderMethodClassifier<ExecutableElement> classifier =
        new BuilderMethodClassifierForAutoValue(
            errorReporter,
            processingEnv,
            autoValueClass.asType(),
            builderType,
            getterToPropertyName,
            rewrittenPropertyTypes,
            nullables);
    if (classifier.classifyMethods(methods, autoValueHasToBuilder)) {
      return Optional.of(classifier);
    } else {
      return Optional.empty();
    }
  }

  @Override
  TypeMirror originalPropertyType(ExecutableElement propertyElement) {
    return propertyElement.getReturnType();
  }

  @Override
  String propertyString(ExecutableElement propertyElement) {
    TypeElement type = MoreElements.asType(propertyElement.getEnclosingElement());
    return "property method "
        + type.getQualifiedName()
        + "."
        + propertyElement.getSimpleName()
        + "()";
  }

  @Override
  ImmutableBiMap<String, ExecutableElement> propertyElements() {
    return getterToPropertyName.inverse();
  }

  @Override
  Optional<String> propertyForBuilderGetter(ExecutableElement method) {
    String methodName = method.getSimpleName().toString();
    return Optional.ofNullable(getterNameToGetter.get(methodName)).map(getterToPropertyName::get);
  }

  @Override
  void checkForFailedJavaBean(ExecutableElement rejectedSetter) {
    ImmutableSet<ExecutableElement> allGetters = getterToPropertyName.keySet();
    ImmutableSet<ExecutableElement> prefixedGetters =
        AutoValueProcessor.prefixedGettersIn(allGetters);
    if (prefixedGetters.size() < allGetters.size()
        && prefixedGetters.size() >= allGetters.size() / 2) {
      errorReporter.reportNote(
          rejectedSetter,
          "This might be because you are using the getFoo() convention"
              + " for some but not all methods. These methods don't follow the convention: %s",
          difference(allGetters, prefixedGetters));
    }
  }

  @Override
  String autoWhat() {
    return "AutoValue";
  }

  @Override
  String getterMustMatch() {
    return "a property method of " + builtType;
  }

  @Override
  String fooBuilderMustMatch() {
    return "foo() or getFoo()";
  }
}
