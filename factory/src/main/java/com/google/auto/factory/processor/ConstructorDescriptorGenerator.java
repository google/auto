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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.partitioningBy;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor8;
import javax.lang.model.util.Types;

/**
 * A service that traverses an {@code @AutoFactory}-annotated element and returns the {@link
 * ConstructorDescriptor}s for the target constructors defined therein.
 *
 * @author Gregory Kick
 */
final class ConstructorDescriptorGenerator {
  private final Messager messager;
  private final Types types;
  private final AutoFactoryDeclaration.Factory declarationFactory;
  private final InjectApi injectApi;

  ConstructorDescriptorGenerator(
      Messager messager,
      Types types,
      AutoFactoryDeclaration.Factory declarationFactory,
      InjectApi injectApi) {
    this.messager = messager;
    this.types = types;
    this.declarationFactory = declarationFactory;
    this.injectApi = injectApi;
  }

  ImmutableSet<ConstructorDescriptor> generateDescriptors(Element element) {
    final AnnotationMirror mirror = Mirrors.getAnnotationMirror(element, AutoFactory.class).get();
    final Optional<AutoFactoryDeclaration> declaration = declarationFactory.createIfValid(element);
    if (!declaration.isPresent()) {
      return ImmutableSet.of();
    }
    return element.accept(
        new ElementKindVisitor8<ImmutableSet<ConstructorDescriptor>, Void>() {
          @Override
          protected ImmutableSet<ConstructorDescriptor> defaultAction(Element e, Void p) {
            throw new AssertionError("@AutoFactory applied to an impossible element");
          }

          @Override
          public ImmutableSet<ConstructorDescriptor> visitTypeAsClass(TypeElement type, Void p) {
            if (type.getModifiers().contains(ABSTRACT)) {
              // applied to an abstract factory
              messager.printMessage(
                  ERROR,
                  "Auto-factory doesn't support being applied to abstract classes.",
                  type,
                  mirror);
              return ImmutableSet.of();
            } else {
              // applied to the type to be created
              ImmutableSet<ExecutableElement> constructors = Elements2.getConstructors(type);
              if (constructors.isEmpty()) {
                return generateDescriptorForDefaultConstructor(declaration.get());
              } else {
                return FluentIterable.from(constructors)
                    .transform(
                        constructor ->
                            generateDescriptorForConstructor(declaration.get(), constructor))
                    .toSet();
              }
            }
          }

          @Override
          public ImmutableSet<ConstructorDescriptor> visitTypeAsInterface(
              TypeElement type, Void p) {
            // applied to the factory interface
            messager.printMessage(
                ERROR, "Auto-factory doesn't support being applied to interfaces.", type, mirror);
            return ImmutableSet.of();
          }

          @Override
          public ImmutableSet<ConstructorDescriptor> visitExecutableAsConstructor(
              ExecutableElement e, Void p) {
            // applied to a constructor of a type to be created
            return ImmutableSet.of(generateDescriptorForConstructor(declaration.get(), e));
          }
        },
        null);
  }

  ConstructorDescriptor generateDescriptorForConstructor(
      final AutoFactoryDeclaration declaration, ExecutableElement constructor) {
    checkNotNull(constructor);
    checkArgument(constructor.getKind() == ElementKind.CONSTRUCTOR);
    Map<Boolean, List<VariableElement>> parameterMap =
        constructor.getParameters().stream()
            .collect(partitioningBy(parameter -> isAnnotationPresent(parameter, Provided.class)));
    // The map returned by partitioningBy always has entries for both key values but our
    // null-checker isn't yet smart enough to know that.
    ImmutableSet<Parameter> providedParameters =
        Parameter.forParameterList(requireNonNull(parameterMap.get(true)), types, injectApi);
    ImmutableSet<Parameter> passedParameters =
        Parameter.forParameterList(requireNonNull(parameterMap.get(false)), types, injectApi);
    return ConstructorDescriptor.builder(declaration)
        .providedParameters(providedParameters)
        .passedParameters(passedParameters)
        .creationParameters(
            Parameter.forParameterList(constructor.getParameters(), types, injectApi))
        .isVarArgs(constructor.isVarArgs())
        .exceptions(constructor.getThrownTypes())
        .build();
  }

  private ImmutableSet<ConstructorDescriptor> generateDescriptorForDefaultConstructor(
      AutoFactoryDeclaration declaration) {
    return ImmutableSet.of(
        ConstructorDescriptor.builder(declaration)
            .providedParameters(ImmutableSet.of())
            .passedParameters(ImmutableSet.of())
            .creationParameters(ImmutableSet.of())
            .isVarArgs(false)
            .exceptions(ImmutableSet.of())
            .build());
  }
}
