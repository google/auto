/*
 * Copyright 2013 Google LLC
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
import static java.util.stream.Collectors.toList;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * Model for a parameter from an {@link com.google.auto.factory.AutoFactory} constructor or
 * implementation method.
 */
@AutoValue
abstract class Parameter {

  /**
   * The original type of the parameter, while {@code key().type()} erases the wrapped {@link
   * Provider}, if any.
   */
  abstract Equivalence.Wrapper<TypeMirror> type();

  boolean isProvider() {
    return Mirrors.isProvider(type().get());
  }

  boolean isPrimitive() {
    return type().get().getKind().isPrimitive();
  }

  /** The name of the parameter. */
  abstract String name();

  abstract Key key();

  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> nullableWrapper();

  Optional<AnnotationMirror> nullable() {
    return unwrapOptionalEquivalence(nullableWrapper());
  }

  private static Parameter forVariableElement(
      VariableElement variable, TypeMirror type, Types types) {
    List<AnnotationMirror> annotations =
        Stream.of(variable.getAnnotationMirrors(), type.getAnnotationMirrors())
            .flatMap(List::stream)
            .collect(toList());
    Optional<AnnotationMirror> nullable =
        annotations.stream().filter(Parameter::isNullable).findFirst();

    Key key = Key.create(type, annotations, types);
    return new AutoValue_Parameter(
        MoreTypes.equivalence().wrap(type),
        variable.getSimpleName().toString(),
        key,
        wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), nullable));
  }

  private static boolean isNullable(AnnotationMirror annotation) {
    TypeElement annotationType = MoreElements.asType(annotation.getAnnotationType().asElement());
    return annotationType.getSimpleName().contentEquals("Nullable")
        || annotationType
            .getQualifiedName()
            .toString()
            // For NullableDecl and NullableType compatibility annotations
            .startsWith("org.checkerframework.checker.nullness.compatqual.Nullable");
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
      checkArgument(names.add(parameter.name()), "Duplicate parameter name: %s", parameter.name());
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
