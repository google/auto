/*
 * Copyright 2022 Google LLC
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

import com.google.auto.value.processor.AutoValueishProcessor.Property;
import com.google.common.collect.ImmutableSet;
import com.google.escapevelocity.Template;

/** The variables to substitute into the autobuilderannotation.vm template. */
class AutoBuilderAnnotationTemplateVars extends TemplateVars {
  private static final Template TEMPLATE = parsedTemplateForResource("autobuilderannotation.vm");

  /** Package of generated class. */
  String pkg;

  /** The encoding of the {@code Generated} class. Empty if the class is not available. */
  String generated;

  /** The name of the class to generate. */
  String className;

  /**
   * The {@linkplain TypeEncoder#encode encoded} name of the annotation type that the generated code
   * will build.
   */
  String annotationType;

  /**
   * The {@linkplain TypeEncoder#encode encoded} name of the {@code @AutoBuilder} type that users
   * will call to build this annotation.
   */
  String autoBuilderType;

  /**
   * The "properties" that the builder will build. These are really just names and types, being the
   * names and types of the annotation elements.
   */
  ImmutableSet<Property> props;

  @Override
  Template parsedTemplate() {
    return TEMPLATE;
  }
}
