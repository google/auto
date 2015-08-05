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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

// TODO(cgruber): AutoValue
final class Parameter {
  private final String type;
  private final String name;
  private final boolean providerOfType;
  private final Key key;

  private Parameter(String type, Key key, String name, boolean providerOfType) {
    this.type = checkNotNull(type);
    this.key = checkNotNull(key);
    this.name = checkNotNull(name);
    this.providerOfType = providerOfType;
  }

  String type() {
    return type;
  }

  Key key() {
    return key;
  }

  String name() {
    return name;
  }

  boolean providerOfType() {
    return providerOfType;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof Parameter) {
      Parameter that = (Parameter) obj;
      return this.type.equals(that.type)
          && this.key.equals(that.key)
          && this.name.equals(that.name)
          && this.providerOfType == that.providerOfType;
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type, key, name, providerOfType);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder().append('\'');
    if (key.getQualifier().isPresent()) {
      builder.append(key.getQualifier().get()).append(' ');
    }
    if (providerOfType) {
      builder.append("Provider<");
    }
    builder.append(type);
    if (providerOfType) {
      builder.append('>');
    }
    builder.append(' ').append(name).append('\'');
    return builder.toString();
  }

  static Parameter forVariableElement(VariableElement variable, TypeMirror type, Types types) {
    ImmutableSet.Builder<AnnotationMirror> qualifiers = ImmutableSet.builder();
    for (AnnotationMirror annotationMirror : variable.getAnnotationMirrors()) {
      DeclaredType annotationType = annotationMirror.getAnnotationType();
      if (isAnnotationPresent(annotationType.asElement(), Qualifier.class)) {
        qualifiers.add(annotationMirror);
      }
    }

    boolean provider = MoreTypes.isType(type) && MoreTypes.isTypeOf(Provider.class, type);
    TypeMirror providedType =
        provider ? MoreTypes.asDeclared(type).getTypeArguments().get(0) : type;

    // TODO(gak): check for only one qualifier rather than using the first
    Optional<AnnotationMirror> qualifier = FluentIterable.from(qualifiers.build()).first();
    Key key = new Key(qualifier, boxedType(providedType, types).toString());

    return new Parameter(
        providedType.toString(), key, variable.getSimpleName().toString(), provider);
  }

  /**
   * If {@code type} is a primitive type, returns the boxed equivalent; otherwise returns
   * {@code type}.
   */
  private static TypeMirror boxedType(TypeMirror type, Types types) {
    return type.getKind().isPrimitive()
        ? types.boxedClass(MoreTypes.asPrimitiveType(type)).asType()
        : type;
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
      checkArgument(names.add(parameter.name));
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
