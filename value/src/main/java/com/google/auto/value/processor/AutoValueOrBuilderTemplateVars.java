/*
 * Copyright 2012 Google LLC
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
import com.google.auto.value.processor.PropertyBuilderClassifier.PropertyBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Types;

/**
 * Variables to substitute into the autovalue.vm or builder.vm template.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@SuppressWarnings("unused") // the fields in this class are only read via reflection
abstract class AutoValueOrBuilderTemplateVars extends AutoValueishTemplateVars {
  /**
   * The properties defined by the parent class's abstract methods. The elements of this set are in
   * the same order as the original abstract method declarations in the AutoValue class.
   */
  ImmutableSet<Property> props;

  /**
   * The simple name of the generated builder, or empty if there is no builder. This is just
   * {@code Builder} for AutoValue, since it is nested inside the {@code AutoValue_Foo} class. But
   * it is {@code AutoBuilder_Foo} for AutoBuilder.
   */
  String builderName = "";

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

  /**
   * The full spelling of any annotations to add to the generated builder subclass, or an empty list
   * if there are none. A non-empty value might look something like {@code
   * @`java.lang.SuppressWarnings`("Immutable")}. The {@code ``} marks are explained in
   * {@link TypeEncoder}.
   */
  ImmutableList<String> builderAnnotations = ImmutableList.of();

  /** The builder's build method, often {@code "build"}. */
  Optional<SimpleMethod> buildMethod = Optional.empty();

  /** The type that will be built by the {@code build()} method of a builder. */
  String builtType;

  /**
   * The constructor or method invocation that the {@code build()} method of a builder should use,
   * without any parameters. This might be {@code "new Foo"} or {@code "Foo.someMethod"}.
   */
  String build;

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
  ImmutableSet<Property> builderRequiredProperties = ImmutableSet.of();

  /**
   * A map from property names to information about the associated property getter. A property
   * called foo (defined by a method foo() or getFoo()) can have a property getter method with the
   * same name (foo() or getFoo()) and either the same return type or an Optional (or OptionalInt,
   * etc) wrapping it.
   */
  ImmutableMap<String, BuilderSpec.PropertyGetter> builderGetters = ImmutableMap.of();

  /**
   * True if the generated builder should have a second constructor with a parameter of the built
   * class. The constructor produces a new builder that starts off with the values from the
   * parameter.
   */
  Boolean toBuilderConstructor;

  /**
   * Any {@code toBuilder()} methods, that is methods that return the builder type. AutoBuilder does
   * not currently support this, but it's included in these shared variables to simplify the
   * template.
   */
  ImmutableList<SimpleMethod> toBuilderMethods;

  /**
   * Whether to include identifiers in strings in the generated code. If false, exception messages
   * will not mention properties by name, and {@code toString()} will include neither property names
   * nor the name of the {@code @AutoValue} class.
   */
  Boolean identifiers;

  /**
   * True if the generated class should be final (there are no extensions that will generate
   * subclasses)
   */
  Boolean isFinal = false;

  /** The type utilities returned by {@link ProcessingEnvironment#getTypeUtils()}. */
  Types types;
}
