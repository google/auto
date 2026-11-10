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

import com.google.auto.factory.AutoFactory;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import javax.lang.model.type.TypeMirror;

/**
 * A value object representing an {@code abstract} method from an {@link AutoFactory#extending}
 * superclass or {@link AutoFactory#implementing} interface that the generated factory must
 * implement.
 *
 * <p>If the method is named {@code create}, it is implemented directly by the {@link
 * FactoryMethodDescriptor} with matching parameter types (which constructs the target type via
 * {@code new}). Otherwise, the generated factory includes a delegating implementation of this
 * method that calls the matching {@code create} method.
 */
@AutoValue
abstract class AbstractMethodDescriptor {
  abstract String name();

  abstract TypeMirror returnType();

  abstract boolean publicMethod();

  abstract ImmutableSet<Parameter> passedParameters();

  abstract boolean isVarArgs();

  abstract ImmutableSet<TypeMirror> exceptions();

  static Builder builder() {
    return new AutoValue_AbstractMethodDescriptor.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder name(String name);

    abstract Builder returnType(TypeMirror returnTypeElement);

    abstract Builder publicMethod(boolean publicMethod);

    final Builder publicMethod() {
      return publicMethod(true);
    }

    abstract Builder passedParameters(Iterable<Parameter> passedParameters);

    abstract Builder isVarArgs(boolean isVarargs);

    abstract Builder exceptions(Iterable<? extends TypeMirror> exceptions);

    abstract AbstractMethodDescriptor build();
  }
}
