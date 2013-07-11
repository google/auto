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
package com.google.auto.factory;

import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

final class ImplemetationMethodDescriptor {
  private final String factoryName;
  private final String name;
  private final String returnType;
  private final boolean publicMethod;
  private final ImmutableSet<Parameter> passedParameters;

  private ImplemetationMethodDescriptor(Builder builder) {
    this.factoryName = builder.factoryName.get();
    this.name = builder.name.get();
    this.returnType = builder.returnType.get();
    this.publicMethod = builder.publicMethod;
    this.passedParameters = ImmutableSet.copyOf(builder.passedParameters);
  }

  String factoryName() {
    return factoryName;
  }

  String name() {
    return name;
  }

  String returnType() {
    return returnType;
  }

  boolean publicMethod() {
    return publicMethod;
  }

  ImmutableSet<Parameter> passedParameters() {
    return passedParameters;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("factoryName", factoryName)
        .add("name", name)
        .add("returnType", returnType)
        .add("publicMethod", publicMethod)
        .add("passedParameters", passedParameters)
        .toString();
  }

  static final class Builder {
    private Optional<String> factoryName = Optional.absent();
    private Optional<String> name = Optional.absent();
    private Optional<String> returnType = Optional.absent();
    private boolean publicMethod = false;
    private final Set<Parameter> passedParameters = Sets.newLinkedHashSet();

    Builder factoryName(String factoryName) {
      this.factoryName = Optional.of(factoryName);
      return this;
    }

    Builder name(String name) {
      this.name = Optional.of(name);
      return this;
    }

    Builder returnType(String returnType) {
      this.returnType = Optional.of(returnType);
      return this;
    }

    Builder publicMethod() {
      return publicMethod(true);
    }

    Builder publicMethod(boolean publicMethod) {
      this.publicMethod = publicMethod;
      return this;
    }

    Builder passedParameters(Iterable<Parameter> passedParameters) {
      Iterables.addAll(this.passedParameters, passedParameters);
      return this;
    }

    ImplemetationMethodDescriptor build() {
      return new ImplemetationMethodDescriptor(this);
    }
  }
}
