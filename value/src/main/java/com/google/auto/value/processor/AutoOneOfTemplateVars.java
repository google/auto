/*
 * Copyright 2018 Google LLC
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

import com.google.common.collect.ImmutableSet;
import com.google.escapevelocity.Template;
import java.util.Map;

/**
 * The variables to substitute into the autooneof.vm template.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@SuppressWarnings("unused") // the fields in this class are only read via reflection
class AutoOneOfTemplateVars extends AutoValueishTemplateVars {
  /**
   * The properties defined by the parent class's abstract methods. The elements of this set are in
   * the same order as the original abstract method declarations in the AutoOneOf class.
   */
  ImmutableSet<AutoOneOfProcessor.Property> props;

  /** The simple name of the generated class. */
  String generatedClass;

  /** The encoded name of the "kind" enum class. */
  String kindType;

  /** The name of the method that gets the kind of the current {@code @AutoOneOf} instance. */
  String kindGetter;

  /** Maps property names like {@code dog} to enum constants like {@code DOG}. */
  Map<String, String> propertyToKind;

  /** True if this {@code @AutoOneOf} class is Serializable. */
  Boolean serializable;

  private static final Template TEMPLATE = parsedTemplateForResource("autooneof.vm");

  @Override
  Template parsedTemplate() {
    return TEMPLATE;
  }
}
