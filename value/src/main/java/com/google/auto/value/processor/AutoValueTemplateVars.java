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

import com.google.auto.value.processor.PropertyBuilderClassifier.PropertyBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.escapevelocity.Template;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Types;

/**
 * The variables to substitute into the autovalue.vm template.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@SuppressWarnings("unused") // the fields in this class are only read via reflection
class AutoValueTemplateVars extends AutoValueOrOneOfTemplateVars {
  /**
   * The properties defined by the parent class's abstract methods. The elements of this set are in
   * the same order as the original abstract method declarations in the AutoValue class.
   */
  ImmutableSet<AutoValueProcessor.Property> props;

  /**
   * Whether to include identifiers in strings in the generated code. If false, exception messages
   * will not mention properties by name, and {@code toString()} will include neither property names
   * nor the name of the {@code @AutoValue} class.
   */
  Boolean identifiers;

  /** The type utilities returned by {@link ProcessingEnvironment#getTypeUtils()}. */
  Types types;

  /**
   * The encoding of the {@code @GwtCompatible} annotation to add to this class, or an empty string
   * if there is none. A non-empty value will look something like {@code
   * "@`com.google.common.annotations.GwtCompatible`(serializable = true)"}, where the {@code ``}
   * represent the encoding used by {@link TypeEncoder}.
   */
  String gwtCompatibleAnnotation;

  /** The text of the serialVersionUID constant, or empty if there is none. */
  String serialVersionUID;

  /** The simple name of the generated subclass. */
  String subclass;
  /**
   * The simple name of the final generated subclass. For {@code @AutoValue public static class Foo
   * {}} this should always be "AutoValue_Foo".
   */
  String finalSubclass;

  /**
   * True if the generated class should be final (there are no extensions that will generate
   * subclasses)
   */
  Boolean isFinal = false;

  /**
   * The modifiers (for example {@code final} or {@code abstract}) for the generated subclass,
   * followed by a space if they are not empty.
   */
  String modifiers;

  /**
   * The name of the builder type as it should appear in source code, or empty if there is no
   * builder type. If class {@code Address} contains {@code @AutoValue.Builder} class Builder then
   * this will typically be {@code "Address.Builder"}.
   */
  String builderTypeName = "";

  /**
   * The formal generic signature of the {@code AutoValue.Builder} class. This is empty, or contains
   * type variables with optional bounds, for example {@code <K, V extends K>}.
   */
  String builderFormalTypes = "";
  /**
   * The generic signature used by the generated builder subclass for its superclass reference. This
   * is empty, or contains only type variables with no bounds, for example {@code <K, V>}.
   */
  String builderActualTypes = "";

  /** True if the builder being implemented is an interface, false if it is an abstract class. */
  Boolean builderIsInterface = false;

  /** The builder's build method, often {@code "build"}. */
  Optional<SimpleMethod> buildMethod = Optional.empty();

  /**
   * A multimap from property names (like foo) to the corresponding setters. The same property may
   * be set by more than one setter. For example, an ImmutableList might be set by {@code
   * setFoo(ImmutableList<String>)} and {@code setFoo(String[])}.
   */
  ImmutableMultimap<String, BuilderSpec.PropertySetter> builderSetters = ImmutableMultimap.of();

  /**
   * A map from property names to information about the associated property builder. A property
   * called foo (defined by a method foo() or getFoo()) can have a property builder called
   * fooBuilder(). The type of foo must be a type that has an associated builder following certain
   * conventions. Guava immutable types such as ImmutableList follow those conventions, as do many
   * {@code @AutoValue} types.
   */
  ImmutableMap<String, PropertyBuilder> builderPropertyBuilders = ImmutableMap.of();

  /**
   * Properties that are required to be set. A property must be set explicitly except in the
   * following cases:
   *
   * <ul>
   *   <li>it is {@code @Nullable} (in which case it defaults to null);
   *   <li>it is {@code Optional} (in which case it defaults to empty);
   *   <li>it has a property-builder method (in which case it defaults to empty).
   * </ul>
   */
  ImmutableSet<AutoValueProcessor.Property> builderRequiredProperties = ImmutableSet.of();

  /**
   * A map from property names to information about the associated property getter. A property
   * called foo (defined by a method foo() or getFoo()) can have a property getter method with the
   * same name (foo() or getFoo()) and either the same return type or an Optional (or OptionalInt,
   * etc) wrapping it.
   */
  ImmutableMap<String, BuilderSpec.PropertyGetter> builderGetters = ImmutableMap.of();

  /** Any {@code toBuilder()} methods, that is methods that return the builder type. */
  ImmutableList<SimpleMethod> toBuilderMethods;

  private static final Template TEMPLATE = parsedTemplateForResource("autovalue.vm");

  @Override
  Template parsedTemplate() {
    return TEMPLATE;
  }
}
