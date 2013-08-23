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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.tools.Diagnostic.Kind.ERROR;

import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor6;
import javax.lang.model.util.Elements;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

/**
 * A service that traverses an element and returns the set of factory methods defined therein.
 *
 * @author Gregory Kick
 */
final class FactoryDescriptorGenerator {
  private final Messager messager;
  private final Elements elements;

  @Inject FactoryDescriptorGenerator(Messager messager, Elements elements) {
    this.messager = messager;
    this.elements = elements;
  }

  ImmutableSet<FactoryMethodDescriptor> generateDescriptor(Element element) {
    final AnnotationMirror mirror = Mirrors.getAnnotationMirror(element, AutoFactory.class).get();
    final AutoFactoryDeclaration declaration = AutoFactoryDeclaration.fromAnnotationMirror(
        elements, mirror);
    return element.accept(new ElementKindVisitor6<ImmutableSet<FactoryMethodDescriptor>, Void>() {
      @Override
      protected ImmutableSet<FactoryMethodDescriptor> defaultAction(Element e, Void p) {
        return ImmutableSet.of();
      }

      @Override
      public ImmutableSet<FactoryMethodDescriptor> visitTypeAsClass(TypeElement type, Void p) {
        if (type.getModifiers().contains(ABSTRACT)) {
          // applied to an abstract factory
          messager.printMessage(ERROR,
              "Auto-factory doesn't support being applied to abstract classes.", type, mirror);
          return ImmutableSet.of();
        } else {
          // applied to the type to be created
          ImmutableSet<ExecutableElement> constructors = Elements2.getConstructors(type);
          if (constructors.isEmpty()) {
            return generateDescriptorForDefaultConstructor(declaration, type);
          } else {
            return FluentIterable.from(constructors)
                .transform(new Function<ExecutableElement, FactoryMethodDescriptor>() {
                  @Override public FactoryMethodDescriptor apply(ExecutableElement constructor) {
                    return generateDescriptorForConstructor(declaration, constructor);
                  }
                })
                .toSet();
          }
        }
      }

      @Override
      public ImmutableSet<FactoryMethodDescriptor> visitTypeAsInterface(TypeElement type, Void p) {
        // applied to the factory interface
        messager.printMessage(ERROR,
            "Auto-factory doesn't support being applied to interfaces.", type, mirror);
        return ImmutableSet.of();
      }

      @Override
      public ImmutableSet<FactoryMethodDescriptor> visitExecutableAsConstructor(ExecutableElement e,
          Void p) {
        // applied to a constructor of a type to be created
        return ImmutableSet.of(generateDescriptorForConstructor(declaration, e));
      }
    }, null);
  }

  FactoryMethodDescriptor generateDescriptorForConstructor(final AutoFactoryDeclaration declaration,
      ExecutableElement constructor) {
    checkNotNull(constructor);
    checkArgument(constructor.getKind() == ElementKind.CONSTRUCTOR);
    Element classElement = constructor.getEnclosingElement();
    Name returnType = classElement.accept(
        new ElementKindVisitor6<Name, Void>() {
          @Override
          protected Name defaultAction(Element e, Void p) {
            throw new AssertionError();
          }

          @Override
          public Name visitTypeAsClass(TypeElement e, Void p) {
            if (!e.getTypeParameters().isEmpty()) {
              messager.printMessage(ERROR, "AutoFactory does not support generic types", e/*,
                  declaration.mirror()*/);
            }
            return e.getQualifiedName();
          }
        }, null);
    ImmutableListMultimap<Boolean, ? extends VariableElement> parameterMap =
        Multimaps.index(constructor.getParameters(), Functions.forPredicate(
            new Predicate<VariableElement>() {
              @Override
              public boolean apply(VariableElement parameter) {
                return parameter.getAnnotation(Provided.class) != null;
              }
            }));
    ImmutableSet<Parameter> providedParameters = Parameter.forParameterList(parameterMap.get(true));
    ImmutableSet<Parameter> passedParameters = Parameter.forParameterList(parameterMap.get(false));
    return new FactoryMethodDescriptor.Builder(declaration)
        .factoryName(declaration.getFactoryName(
            elements.getPackageOf(constructor).getQualifiedName(), classElement.getSimpleName()))
        .name("create")
        .returnType(returnType.toString())
        .publicMethod(constructor.getEnclosingElement().getModifiers().contains(PUBLIC))
        .providedParameters(providedParameters)
        .passedParameters(passedParameters)
        .creationParameters(Parameter.forParameterList(constructor.getParameters()))
        .build();
  }

  @SuppressWarnings("unused") // not used yet
  private void generateDescriptorForFactoryMethodDeclaration(final ExecutableElement e,
      Optional<TypeElement> referenceType) {
    String returnType = e.getReturnType().toString();
    ImmutableSet<ExecutableElement> constructors =
        Elements2.getConstructors(elements.getTypeElement(returnType));
    ImmutableList<String> passedParameters = FluentIterable.from(e.getParameters())
        .transform(new Function<VariableElement, String>() {
          @Override
          public String apply(VariableElement e) {
            // TODO(gak): get qualifiers
            return e.asType().toString();
          }
        }).toList();
    final ImmutableSet<VariableElement> providedParameters;
    switch (constructors.size()) {
      case 0:
        // default constructor
        if (!passedParameters.isEmpty()) {
          messager.printMessage(ERROR, "passing parameters, but no params for constructor");
        }
        providedParameters = ImmutableSet.of();
        break;
      case 1:
        // the normal case
        ExecutableElement constructor = Iterables.getOnlyElement(constructors);
        providedParameters = Sets.difference(
            ImmutableSet.copyOf(constructor.getParameters()),
            ImmutableSet.copyOf(passedParameters))
                .immutableCopy();
        break;
      default:
        messager.printMessage(ERROR, "ambiguous!");
        break;
    }
  }

  private ImmutableSet<FactoryMethodDescriptor> generateDescriptorForDefaultConstructor(
      AutoFactoryDeclaration declaration, TypeElement type) {
    return ImmutableSet.of(new FactoryMethodDescriptor.Builder(declaration)
        .factoryName(declaration.getFactoryName(
            elements.getPackageOf(type).getQualifiedName(), type.getSimpleName()))
        .name("create")
        .returnType(type.getQualifiedName().toString())
        .publicMethod(type.getModifiers().contains(PUBLIC))
        .passedParameters(ImmutableSet.<Parameter>of())
        .creationParameters(ImmutableSet.<Parameter>of())
        .providedParameters(ImmutableSet.<Parameter>of())
        .build());
  }
}
