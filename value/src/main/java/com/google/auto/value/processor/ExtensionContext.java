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

import com.google.auto.value.extension.AutoValueExtension;
import com.google.auto.value.extension.AutoValueExtension.BuilderContext;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
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
  public Optional<BuilderContext> builder() {
    return builderContext;
  }
}
