/*
 * Copyright (C) 2015 Google Inc.
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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

class ExtensionContext implements AutoValueExtension.Context {

  private final ProcessingEnvironment processingEnvironment;
  private final TypeElement typeElement;
  private final ImmutableMap<String, ExecutableElement> properties;
  private final ImmutableSet<ExecutableElement> abstractMethods;

  ExtensionContext(
      ProcessingEnvironment processingEnvironment,
      TypeElement typeElement,
      ImmutableMap<String, ExecutableElement> properties,
      ImmutableSet<ExecutableElement> abstractMethods) {
    this.processingEnvironment = processingEnvironment;
    this.typeElement = typeElement;
    this.properties = properties;
    this.abstractMethods = abstractMethods;
  }

  @Override
  public ProcessingEnvironment processingEnvironment() {
    return processingEnvironment;
  }

  @Override
  public String packageName() {
    return TypeSimplifier.packageNameOf(typeElement);
  }

  @Override
  public TypeElement autoValueClass() {
    return typeElement;
  }

  @Override
  public Map<String, ExecutableElement> properties() {
    return properties;
  }

  @Override
  public Set<ExecutableElement> abstractMethods() {
    return abstractMethods;
  }
}
