/*
 * Copyright (C) 2018 Google, Inc.
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

import com.google.auto.value.processor.escapevelocity.Template;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Types;

/**
 * The variables to substitute into the autooneof.vm template.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@SuppressWarnings("unused")  // the fields in this class are only read via reflection
class AutoOneOfTemplateVars extends TemplateVars {
  /**
   * The properties defined by the parent class's abstract methods. The elements of this set are
   * in the same order as the original abstract method declarations in the AutoOneOf class.
   */
  ImmutableSet<AutoOneOfProcessor.Property> props;

  /** Whether to generate an equals(Object) method. */
  Boolean equals;
  /** Whether to generate a hashCode() method. */
  Boolean hashCode;
  /** Whether to generate a toString() method. */
  Boolean toString;

  /**
   * A string representing the parameter type declaration of the equals(Object) method, including
   * any annotations. If {@link #equals} is false, this field is ignored (but it must still be
   * non-null).
   */
  String equalsParameterType;

  /** The type utilities returned by {@link ProcessingEnvironment#getTypeUtils()}. */
  Types types;

  /**
   * The encoding of the {@code Generated} class. Empty if the class is not available.
   */
  String generated;

  /**
   * The package of the class with the {@code @AutoOneOf} annotation and its generated subclass.
   */
  String pkg;
  /**
   * The name of the class with the {@code @AutoOneOf} annotation, including containing
   * classes but not including the package name.
   */
  String origClass;
  /** The simple name of the class with the {@code @AutoOneOf} annotation. */
  String simpleClassName;
  /** The simple name of the generated class. */
  String generatedClass;

  /** The encoded name of the "kind" enum class. */
  String kindType;

  /** The name of the method that gets the kind of the current {@code @AutoOneOf} instance. */
  String kindGetter;

  /** Maps property names like {@code dog} to enum constants like {@code DOG}. */
  Map<String, String> propertyToKind;

  /**
   * The formal generic signature of the class with the {@code @AutoOneOf} annotation and its
   * generated subclasses. This is empty, or contains type variables with optional bounds,
   * for example {@code <K, V extends K>}.
   */
  String formalTypes;

  /**
   * The generic signature used by the generated subclass for its superclass reference.
   * This is empty, or contains only type variables with no bounds, for example
   * {@code <K, V>}.
   */
  String actualTypes;

  /**
   * The generic signature in {@link #actualTypes} where every variable has been replaced
   * by a wildcard, for example {@code <?, ?>}.
   */
  String wildcardTypes;

  private static final Template TEMPLATE = parsedTemplateForResource("autooneof.vm");

  @Override
  Template parsedTemplate() {
    return TEMPLATE;
  }
}
