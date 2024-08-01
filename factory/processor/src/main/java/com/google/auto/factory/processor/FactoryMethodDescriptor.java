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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import javax.lang.model.type.TypeMirror;

/**
 * A value object representing a factory method to be generated.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class FactoryMethodDescriptor {
  abstract AutoFactoryDeclaration declaration();

  abstract String name();

  abstract TypeMirror returnType();

  abstract boolean publicMethod();

  abstract boolean overridingMethod();

  /** The parameters that are passed to the {@code create} method. */
  abstract ImmutableSet<Parameter> passedParameters();

  /**
   * The factory constructor parameters that this factory method requires. When there is more than
   * one AutoFactory constructor, each one can have its own {@code @Provided} parameters, or
   * constructors can have {@code @Provided} parameters in common. The generated factory has a
   * single constructor, which has one {@code @Injected} constructor parameter for each unique
   * {@code @Provided} parameter in any constructor.
   */
  abstract ImmutableSet<Parameter> providedParameters();

  /**
   * The parameters of the constructor that this {@code create} method calls. This is the union of
   * {@link #passedParameters()} and {@link #providedParameters()}.
   */
  abstract ImmutableSet<Parameter> creationParameters();

  abstract boolean isVarArgs();

  abstract ImmutableSet<TypeMirror> exceptions();

  abstract Builder toBuilder();

  final PackageAndClass factoryName() {
    return declaration().getFactoryName();
  }

  static Builder builder(AutoFactoryDeclaration declaration) {
    return new AutoValue_FactoryMethodDescriptor.Builder().declaration(checkNotNull(declaration));
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder declaration(AutoFactoryDeclaration declaration);

    abstract Builder name(String name);

    abstract Builder returnType(TypeMirror returnType);

    abstract Builder publicMethod(boolean publicMethod);

    abstract Builder overridingMethod(boolean overridingMethod);

    abstract Builder passedParameters(Iterable<Parameter> passedParameters);

    abstract Builder providedParameters(Iterable<Parameter> providedParameters);

    abstract Builder creationParameters(Iterable<Parameter> creationParameters);

    abstract Builder isVarArgs(boolean isVarargs);

    abstract Builder exceptions(Iterable<? extends TypeMirror> exceptions);

    abstract FactoryMethodDescriptor buildImpl();

    FactoryMethodDescriptor build() {
      FactoryMethodDescriptor descriptor = buildImpl();
      checkState(
          descriptor
              .creationParameters()
              .equals(Sets.union(descriptor.passedParameters(), descriptor.providedParameters())));
      return descriptor;
    }
  }
}
