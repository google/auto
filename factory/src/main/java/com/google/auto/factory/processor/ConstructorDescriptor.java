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
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import javax.lang.model.type.TypeMirror;

/**
 * A value object representing a constructor of the target class (either an explicit constructor or
 * the implicit default constructor) for which the generated factory will have a {@code create}
 * method ({@link FactoryMethodDescriptor}).
 */
@AutoValue
abstract class ConstructorDescriptor {
  abstract AutoFactoryDeclaration declaration();

  /**
   * The constructor parameters that are not annotated with {@code @Provided}, which become the
   * parameters of the generated {@code create} method.
   */
  abstract ImmutableSet<Parameter> passedParameters();

  /**
   * The constructor parameters that are annotated with {@code @Provided}. When there is more than
   * one AutoFactory constructor, each one can have its own {@code @Provided} parameters, or
   * constructors can have {@code @Provided} parameters in common. The generated factory has a
   * single constructor, which has one {@code @Inject} constructor parameter (and backing {@code
   * Provider} field) for each unique {@code @Provided} parameter in any constructor.
   */
  abstract ImmutableSet<Parameter> providedParameters();

  /**
   * All parameters of the constructor, in declaration order. This is the union of {@link
   * #passedParameters()} and {@link #providedParameters()}.
   */
  abstract ImmutableSet<Parameter> creationParameters();

  abstract boolean isVarArgs();

  abstract ImmutableSet<TypeMirror> exceptions();

  final PackageAndClass factoryName() {
    return declaration().getFactoryName();
  }

  final TypeMirror targetType() {
    return declaration().targetType().asType();
  }

  final boolean targetTypeIsPublic() {
    return declaration().targetType().getModifiers().contains(PUBLIC);
  }

  static Builder builder(AutoFactoryDeclaration declaration) {
    return new AutoValue_ConstructorDescriptor.Builder().declaration(checkNotNull(declaration));
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder declaration(AutoFactoryDeclaration declaration);

    abstract Builder passedParameters(Iterable<Parameter> passedParameters);

    abstract Builder providedParameters(Iterable<Parameter> providedParameters);

    abstract Builder creationParameters(Iterable<Parameter> creationParameters);

    abstract Builder isVarArgs(boolean isVarargs);

    abstract Builder exceptions(Iterable<? extends TypeMirror> exceptions);

    abstract ConstructorDescriptor buildImpl();

    ConstructorDescriptor build() {
      ConstructorDescriptor descriptor = buildImpl();
      checkState(
          descriptor
              .creationParameters()
              .equals(Sets.union(descriptor.passedParameters(), descriptor.providedParameters())));
      return descriptor;
    }
  }
}
