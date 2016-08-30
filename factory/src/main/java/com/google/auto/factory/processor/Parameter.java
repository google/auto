/*
 * Copyright (C) 2013 Google, Inc.
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
package com.google.auto.factory.processor;

import static com.google.auto.factory.processor.Mirrors.unwrapOptionalEquivalence;
import static com.google.auto.factory.processor.Mirrors.wrapOptionalInEquivalence;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * Model for a parameter from an {@link com.google.auto.factory.AutoFactory} constructor or
 * implementation method.
 */
@AutoValue
abstract class Parameter {

  static final Function<Parameter, TypeMirror> TYPE = new Function<Parameter, TypeMirror>() {
      @Override
      public TypeMirror apply(Parameter parameter) {
        return parameter.type();
      }
    };

  /**
   * The original type of the parameter, while {@code key().type()} erases the wrapped {@link
   * Provider}, if any.
   */
  abstract TypeMirror type();

  /** The name of the parameter. */
  abstract String name();

  abstract Key key();
  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> nullableWrapper();

  Optional<AnnotationMirror> nullable() {
    return unwrapOptionalEquivalence(nullableWrapper());
  }

  static Parameter forVariableElement(VariableElement variable, TypeMirror type, Types types) {
    Optional<AnnotationMirror> nullable = Optional.absent();
    Iterable<? extends AnnotationMirror> annotations = variable.getAnnotationMirrors();
    for (AnnotationMirror annotation : annotations) {
      if (annotation.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable")) {
        nullable = Optional.of(annotation);
        break;
      }
    }

    Key key = Key.create(type, annotations, types);
    return new AutoValue_Parameter(
        type,
        variable.getSimpleName().toString(),
        key,
        wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), nullable));
  }

  static ImmutableSet<Parameter> forParameterList(
      List<? extends VariableElement> variables,
      List<? extends TypeMirror> variableTypes,
      Types types) {
    checkArgument(variables.size() == variableTypes.size());
    ImmutableSet.Builder<Parameter> builder = ImmutableSet.builder();
    Set<String> names = Sets.newHashSetWithExpectedSize(variables.size());
    for (int i = 0; i < variables.size(); i++) {
      Parameter parameter = forVariableElement(variables.get(i), variableTypes.get(i), types);
      checkArgument(names.add(parameter.name()));
      builder.add(parameter);
    }
    ImmutableSet<Parameter> parameters = builder.build();
    checkArgument(variables.size() == parameters.size());
    return parameters;
  }

  static ImmutableSet<Parameter> forParameterList(
      List<? extends VariableElement> variables, Types types) {
    List<TypeMirror> variableTypes = Lists.newArrayListWithExpectedSize(variables.size());
    for (VariableElement var : variables) {
      variableTypes.add(var.asType());
    }
    return forParameterList(variables, variableTypes, types);
  }
}
