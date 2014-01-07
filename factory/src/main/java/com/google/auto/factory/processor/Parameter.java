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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;

import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

final class Parameter {
  private final Optional<String> qualifier;
  private final String type;
  private final String name;


  private Parameter(Optional<String> qualifier, String type, String name) {
    this.qualifier = checkNotNull(qualifier);
    this.type = checkNotNull(type);
    this.name = checkNotNull(name);
  }

  Optional<String> qualifier() {
    return qualifier;
  }

  String type() {
    return type;
  }

  Key asKey() {
    return new Key(qualifier, type);
  }

  String name() {
    return name;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof  Parameter) {
      Parameter that = (Parameter) obj;
      return this.type.equals(that.type)
          && this.name.equals(that.name)
          && this.qualifier.equals(that.qualifier);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type, name, qualifier);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder().append('\'');
    if (qualifier.isPresent()) {
      builder.append(qualifier.get()).append(' ');
    }
    builder.append(type).append(' ').append(name).append('\'');
    return builder.toString();
  }

  static Parameter forVariableElement(VariableElement variable, TypeMirror type) {
    ImmutableSet.Builder<String> qualifiers = ImmutableSet.builder();
    for (AnnotationMirror annotationMirror : variable.getAnnotationMirrors()) {
      DeclaredType annotationType = annotationMirror.getAnnotationType();
      if (annotationType.asElement().getAnnotation(Qualifier.class) != null) {
        qualifiers.add(Mirrors.getQualifiedName(annotationType).toString());
      }
    }
    // TODO(gak): check for only one qualifier rather than using the first
    return new Parameter(FluentIterable.from(qualifiers.build()).first(),
        type.toString(),
        variable.getSimpleName().toString());
  }

  static ImmutableSet<Parameter> forParameterList(
      List<? extends VariableElement> variables, List<? extends TypeMirror> variableTypes) {
    checkArgument(variables.size() == variableTypes.size());
    ImmutableSet.Builder<Parameter> builder = ImmutableSet.builder();
    Set<String> names = Sets.newHashSetWithExpectedSize(variables.size());
    for (int i = 0; i < variables.size(); i++) {
      Parameter parameter = forVariableElement(variables.get(i), variableTypes.get(i));
      checkArgument(names.add(parameter.name));
      builder.add(parameter);
    }
    ImmutableSet<Parameter> parameters = builder.build();
    checkArgument(variables.size() == parameters.size());
    return parameters;
  }

  static ImmutableSet<Parameter> forParameterList(List<? extends VariableElement> variables) {
    List<TypeMirror> variableTypes = Lists.newArrayListWithExpectedSize(variables.size());
    for (VariableElement var : variables) {
      variableTypes.add(var.asType());
    }
    return forParameterList(variables, variableTypes);
  }
}
