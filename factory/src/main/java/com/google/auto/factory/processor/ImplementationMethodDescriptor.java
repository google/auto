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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import javax.lang.model.type.TypeMirror;

@AutoValue
abstract class ImplementationMethodDescriptor {
  abstract String name();

  abstract TypeMirror returnType();

  abstract boolean publicMethod();

  abstract ImmutableSet<Parameter> passedParameters();

  abstract boolean isVarArgs();

  abstract ImmutableSet<TypeMirror> exceptions();

  static Builder builder() {
    return new AutoValue_ImplementationMethodDescriptor.Builder();
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

    abstract ImplementationMethodDescriptor build();
  }
}
