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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  /** The spelling of the java.util.BitSet class: BitSet or java.util.BitSet. */
  String bitSet;

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

  /**
   * The name of the builder type as it should appear in source code, or empty if there is no
   * builder type. If class {@code Address} contains {@code @AutoValue.Builder} class Builder
   * then this will typically be {@code "Address.Builder"}.
   */
  String builderTypeName = "";

  /**
   * The formal generic signature of the {@code AutoValue.Builder} class. This is empty, or contains
   * type variables with optional bounds, for example {@code <K, V extends K>}.
   */
  String builderFormalTypes = "";
  /**
   * The generic signature used by the generated builder subclass for its superclass reference.
   * This is empty, or contains only type variables with no bounds, for example
   * {@code <K, V>}.
   */
  String builderActualTypes = "";

  /**
   * True if the builder being implemented is an interface, false if it is an abstract class.
   */
  Boolean builderIsInterface = false;

  /**
   * The simple name of the builder's build method, often {@code "build"}.
   */
  String buildMethodName = "";

  /**
   * A map from property names (like foo) to the corresponding setter method names (foo or setFoo).
   */
  Map<String, String> builderSetterNames = Collections.emptyMap();

  /**
   * The names of any {@code toBuilder()} methods, that is methods that return the builder type.
   */
  List<String> toBuilderMethods;

  /**
   * The simple names of validation methods (marked {@code @AutoValue.Validate}) in the AutoValue
   * class. (Currently, this set is either empty or a singleton.)
   */
  Set<String> validators = Collections.emptySet();

  private static final SimpleNode TEMPLATE = parsedTemplateForResource("autovalue.vm");

  @Override
  SimpleNode parsedTemplate() {
    return TEMPLATE;
  }
}
