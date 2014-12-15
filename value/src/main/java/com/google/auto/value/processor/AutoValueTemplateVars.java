/*
 * Copyright (C) 2012 Google, Inc.
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

import org.apache.velocity.runtime.parser.node.SimpleNode;

import java.util.List;
import java.util.SortedSet;

/**
 * The variables to substitute into the autovalue.vm template.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@SuppressWarnings("unused")  // the fields in this class are only read via reflection
class AutoValueTemplateVars extends TemplateVars {
  /** The properties defined by the parent class's abstract methods. */
  List<AutoValueProcessor.Property> props;

  /** Whether to generate an equals(Object) method. */
  Boolean equals;
  /** Whether to generate a hashCode() method. */
  Boolean hashCode;
  /** Whether to generate a toString() method. */
  Boolean toString;

  /** The fully-qualified names of the classes to be imported in the generated class. */
  SortedSet<String> imports;

  /**
   * The spelling of the javax.annotation.Generated class: Generated or javax.annotation.Generated.
   */
  String generated;

  /** The spelling of the java.util.Arrays class: Arrays or java.util.Arrays. */
  String arrays;

  /**
   * The full spelling of the {@code @GwtCompatible} annotation to add to this class, or an empty
   * string if there is none. A non-empty value might look something like
   * {@code "@com.google.common.annotations.GwtCompatible(serializable = true)"}.
   */
  String gwtCompatibleAnnotation;

  /** The text of the serialVersionUID constant, or empty if there is none. */
  String serialVersionUID;

  /**
   * The package of the class with the {@code @AutoValue} annotation and its generated subclass.
   */
  String pkg;
  /**
   * The name of the class with the {@code @AutoValue} annotation, including containing
   * classes but not including the package name.
   */
  String origClass;
  /** The simple name of the class with the {@code @AutoValue} annotation. */
  String simpleClassName;
  /** The simple name of the generated subclass. */
  String subclass;

  /**
   * The formal generic signature of the class with the {@code @AutoValue} annotation and its
   * generated subclass. This is empty, or contains type variables with optional bounds,
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

  private static final SimpleNode TEMPLATE = parsedTemplateForResource("autovalue.vm");

  @Override
  SimpleNode parsedTemplate() {
    return TEMPLATE;
  }
}
