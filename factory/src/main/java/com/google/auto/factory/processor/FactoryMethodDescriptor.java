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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/**
 * A value object representing a {@code create} factory method to be generated.
 *
 * <p>Each {@code create} method instantiates the target class by calling a constructor described by
 * {@link #constructor()}. If the factory's {@code extending} or {@code implementing} supertypes
 * declare an abstract {@code create} method with matching parameter types, this method directly
 * overrides that {@link #overriddenMethod()} (adopting {@code @Override}, its visibility, and its
 * {@code throws} clause).
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class FactoryMethodDescriptor {
  abstract ConstructorDescriptor constructor();

  /** The supertype {@code create} method that this method overrides, if any. */
  abstract Optional<AbstractMethodDescriptor> overriddenMethod();

  static FactoryMethodDescriptor create(
      ConstructorDescriptor constructor, Optional<AbstractMethodDescriptor> overriddenMethod) {
    overriddenMethod.ifPresent(method -> checkArgument(method.name().equals("create")));
    return new AutoValue_FactoryMethodDescriptor(constructor, overriddenMethod);
  }

  final AutoFactoryDeclaration declaration() {
    return constructor().declaration();
  }

  final TypeMirror returnType() {
    return overriddenMethod().isPresent()
        ? overriddenMethod().get().returnType()
        : constructor().targetType();
  }

  final boolean isPublic() {
    return overriddenMethod().isPresent()
        ? overriddenMethod().get().publicMethod()
        : constructor().targetTypeIsPublic();
  }

  final boolean overridesMethod() {
    return overriddenMethod().isPresent();
  }

  /** The parameters that are passed to the {@code create} method. */
  final ImmutableSet<Parameter> passedParameters() {
    return constructor().passedParameters();
  }

  /**
   * The parameters of the constructor that this {@code create} method calls. This is the union of
   * {@link #passedParameters()} and {@link ConstructorDescriptor#providedParameters()}.
   */
  final ImmutableSet<Parameter> creationParameters() {
    return constructor().creationParameters();
  }

  final boolean isVarArgs() {
    return constructor().isVarArgs();
  }

  final ImmutableSet<TypeMirror> exceptions() {
    return overriddenMethod().isPresent()
        ? overriddenMethod().get().exceptions()
        : constructor().exceptions();
  }
}
