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

import com.google.common.collect.ImmutableList;

/**
 * The variables to substitute into the autovalue.vm, autooneof.vm, or builder.vm templates.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@SuppressWarnings("unused") // the fields in this class are only read via reflection
abstract class AutoValueishTemplateVars extends TemplateVars {
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

  /** The encoding of the {@code Generated} class. Empty if the class is not available. */
  String generated;

  /** The package of the class with the {@code @AutoValue} annotation and its generated subclass. */
  String pkg;

  /**
   * The name of the class with the {@code @AutoValue} annotation, including containing classes but
   * not including the package name.
   */
  String origClass;

  /** The simple name of the class with the {@code @AutoValue} annotation. */
  String simpleClassName;

  /**
   * The full spelling of any annotations to add to this class, or an empty list if there are none.
   * A non-empty value might look something like {@code
   * "@`com.google.common.annotations.GwtCompatible`(serializable = true)"}. The {@code ``} marks
   * are explained in {@link TypeEncoder}.
   */
  ImmutableList<String> annotations;

  /**
   * The formal generic signature of the class with the {@code @AutoValue} or {@code AutoOneOf}
   * annotation and any generated subclass. This is empty, or contains type variables with optional
   * bounds, for example {@code <K, V extends K>}.
   */
  String formalTypes;

  /**
   * The generic signature used by any generated subclass for its superclass reference. This is
   * empty, or contains only type variables with no bounds, for example {@code <K, V>}.
   */
  String actualTypes;

  /**
   * The generic signature in {@link #actualTypes} where every variable has been replaced by a
   * wildcard, for example {@code <?, ?>}.
   */
  String wildcardTypes;

  /**
   * The text of the complete serialVersionUID declaration, or empty if there is none. When
   * non-empty, it will be something like {@code private static final long serialVersionUID =
   * 123L;}.
   */
  String serialVersionUID;
}
