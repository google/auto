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

import com.google.auto.value.processor.escapevelocity.Template;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Types;

/**
 * The variables to substitute into the autovalue.vm template.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@SuppressWarnings("unused")  // the fields in this class are only read via reflection
class AutoValueTemplateVars extends TemplateVars {
  /**
   * The properties defined by the parent class's abstract methods. The elements of this set are
   * in the same order as the original abstract method declarations in the AutoValue class.
   */
  ImmutableSet<AutoValueProcessor.Property> props;

  /** Whether to generate an equals(Object) method. */
  Boolean equals;
  /** Whether to generate a hashCode() method. */
  Boolean hashCode;
  /** Whether to generate a toString() method. */
  Boolean toString;

  /** The type utilities returned by {@link ProcessingEnvironment#getTypeUtils()}. */
  Types types;

  /** The fully-qualified names of the classes to be imported in the generated class. */
  ImmutableSortedSet<String> imports;

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
   * The simple name of the final generated subclass.
   * For {@code @AutoValue public static class Foo {}} this should always be "AutoValue_Foo".
   */
  String finalSubclass;

  /**
   * True if the generated class should be final (there are no extensions that
   * will generate subclasses)
   */
  Boolean isFinal = false;

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
   * A multimap from property names (like foo) to the corresponding setters. The same property may
   * be set by more than one setter. For example, an ImmutableList might be set by
   * {@code setFoo(ImmutableList<String>)} and {@code setFoo(String[])}.
   */
  ImmutableMultimap<String, BuilderSpec.PropertySetter> builderSetters = ImmutableMultimap.of();

  /**
   * A map from property names to information about the associated property builder. A property
   * called foo (defined by a method foo() or getFoo()) can have a property builder called
   * fooBuilder(). The type of foo must be an immutable Guava type, like ImmutableSet, and
   * fooBuilder() must return the corresponding builder, like ImmutableSet.Builder.
   */
  ImmutableMap<String, BuilderSpec.PropertyBuilder> builderPropertyBuilders =
      ImmutableMap.of();

  /**
   * Properties that are required to be set. A property must be set explicitly unless it is either
   * {@code @Nullable} (in which case it defaults to null), or has a property-builder method
   * (in which case it defaults to empty).
   */
  ImmutableSet<AutoValueProcessor.Property> builderRequiredProperties = ImmutableSet.of();

  /**
   * Properties that have getters in the builder.
   */
  ImmutableSet<String> propertiesWithBuilderGetters = ImmutableSet.of();

  /**
   * The names of any {@code toBuilder()} methods, that is methods that return the builder type.
   */
  ImmutableList<String> toBuilderMethods;

  private static final Template TEMPLATE = parsedTemplateForResource("autovalue.vm");

  @Override
  Template parsedTemplate() {
    return TEMPLATE;
  }
}
