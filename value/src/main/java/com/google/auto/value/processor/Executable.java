/*
 * Copyright 2022 Google LLC
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

import static com.google.auto.common.MoreStreams.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.auto.common.MoreElements;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * A wrapper for an {@link ExecutableElement}, representing a static method or a constructor. This
 * wrapper then allows us to attach additional information, such as which parameters have Kotlin
 * defaults.
 */
class Executable {
  private final ExecutableElement executableElement;

  private final ImmutableList<VariableElement> parameters;
  private final ImmutableSet<String> optionalParameters;
  private final ImmutableList<TypeParameterElement> typeParameters;

  private Executable(ExecutableElement executableElement, ImmutableSet<String> optionalParameters) {
    this.executableElement = executableElement;
    this.parameters = ImmutableList.copyOf(executableElement.getParameters());
    this.optionalParameters = optionalParameters;

    switch (executableElement.getKind()) {
      case CONSTRUCTOR:
        // A constructor can have its own type parameters, in addition to any that its containing
        // class has. That's pretty unusual, but we allow it, requiring the builder to have type
        // parameters that are the concatenation of the class's and the constructor's.
        TypeElement container = MoreElements.asType(executableElement.getEnclosingElement());
        this.typeParameters = ImmutableList.<TypeParameterElement>builder()
            .addAll(container.getTypeParameters())
            .addAll(executableElement.getTypeParameters())
            .build();
        break;
      case METHOD:
        this.typeParameters = ImmutableList.copyOf(executableElement.getTypeParameters());
        break;
      default:
        throw new VerifyException("Unexpected executable kind " + executableElement.getKind());
    }
  }

  static Executable of(ExecutableElement executableElement) {
    return of(executableElement, ImmutableSet.of());
  }

  static Executable of(
      ExecutableElement executableElement, ImmutableSet<String> optionalParameters) {
    return new Executable(executableElement, optionalParameters);
  }

  ExecutableElement executableElement() {
    return executableElement;
  }

  ImmutableList<VariableElement> parameters() {
    return parameters;
  }

  ImmutableList<String> parameterNames() {
    return parameters.stream().map(v -> v.getSimpleName().toString()).collect(toImmutableList());
  }

  boolean isOptional(String parameterName) {
    return optionalParameters.contains(parameterName);
  }

  int optionalParameterCount() {
    return optionalParameters.size();
  }

  ImmutableList<TypeParameterElement> typeParameters() {
    return typeParameters;
  }

  TypeMirror builtType() {
    switch (executableElement.getKind()) {
      case CONSTRUCTOR:
        return executableElement.getEnclosingElement().asType();
      case METHOD:
        return executableElement.getReturnType();
      default:
        throw new VerifyException("Unexpected executable kind " + executableElement.getKind());
    }
  }

  /**
   * The Java code to invoke this constructor or method, up to just before the opening {@code (}.
   */
  String invoke() {
    TypeElement enclosing = MoreElements.asType(executableElement.getEnclosingElement());
    String type = TypeEncoder.encodeRaw(enclosing.asType());
    switch (executableElement.getKind()) {
      case CONSTRUCTOR:
        boolean generic = !enclosing.getTypeParameters().isEmpty();
        String typeParams = generic ? "<>" : "";
        return "new " + type + typeParams;
      case METHOD:
        return type + "." + executableElement.getSimpleName();
      default:
        throw new VerifyException("Unexpected executable kind " + executableElement.getKind());
    }
  }

  // Used in error messages, for example if more than one constructor matches your setters.
  @Override
  public String toString() {
    ExecutableElement executable = executableElement;
    Element nameSource =
        executable.getKind() == ElementKind.CONSTRUCTOR
            ? executable.getEnclosingElement()
            : executable;
    return nameSource.getSimpleName()
        + executable.getParameters().stream()
            .map(v -> v.asType() + " " + v.getSimpleName())
            .collect(joining(", ", "(", ")"));
  }
}
