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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.auto.service.AutoService;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;

import dagger.ObjectGraph;

/**
 * The annotation processor that generates factories for {@link AutoFactory} annotations.
 *
 * @author Gregory Kick
 */
@AutoService(Processor.class)
public final class AutoFactoryProcessor extends AbstractProcessor {
  @Inject FactoryDescriptorGenerator factoryDescriptorGenerator;
  @Inject AutoFactoryDeclaration.Factory declarationFactory;
  @Inject ProvidedChecker providedChecker;
  @Inject Messager messager;
  @Inject Elements elements;
  @Inject Types types;
  @Inject FactoryWriter factoryWriter;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    ObjectGraph.create(new ProcessorModule(processingEnv), new AutoFactoryProcessorModule())
        .inject(this);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      doProcess(annotations, roundEnv);
    } catch (Throwable e) {
      messager.printMessage(Kind.ERROR, "Failed to process @AutoFactory annotations:\n" +
          Throwables.getStackTraceAsString(e));
    }
    return false;
  }

  private void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Provided.class)) {
      providedChecker.checkProvidedParameter(element);
    }

    ImmutableListMultimap.Builder<String, FactoryMethodDescriptor> indexedMethods =
        ImmutableListMultimap.builder();
    ImmutableSet.Builder<ImplemetationMethodDescriptor> implemetationMethodDescriptors =
        ImmutableSet.builder();
    for (Element element : roundEnv.getElementsAnnotatedWith(AutoFactory.class)) {
      Optional<AutoFactoryDeclaration> declaration = declarationFactory.createIfValid(element);
      if (declaration.isPresent()) {
        TypeElement extendingType = declaration.get().extendingType();
        List<ExecutableElement> supertypeMethods =
            ElementFilter.methodsIn(elements.getAllMembers(extendingType));
        for (ExecutableElement supertypeMethod : supertypeMethods) {
          if (supertypeMethod.getModifiers().contains(Modifier.ABSTRACT)) {
            ExecutableType methodType = Elements2.getExecutableElementAsMemberOf(
                types, supertypeMethod, extendingType);
            implemetationMethodDescriptors.add(new ImplemetationMethodDescriptor.Builder()
                .name(supertypeMethod.getSimpleName().toString())
                .returnType(getAnnotatedType(element).getQualifiedName().toString())
                .publicMethod()
                .passedParameters(Parameter.forParameterList(
                    supertypeMethod.getParameters(), methodType.getParameterTypes()))
                .build());
          }
        }
        for (TypeElement implementingType : declaration.get().implementingTypes()) {
          List<ExecutableElement> interfaceMethods =
              ElementFilter.methodsIn(elements.getAllMembers(implementingType));
          for (ExecutableElement interfaceMethod : interfaceMethods) {
            if (interfaceMethod.getModifiers().contains(Modifier.ABSTRACT)) {
              ExecutableType methodType = Elements2.getExecutableElementAsMemberOf(
                  types, interfaceMethod, implementingType);
              implemetationMethodDescriptors.add(new ImplemetationMethodDescriptor.Builder()
                  .name(interfaceMethod.getSimpleName().toString())
                  .returnType(getAnnotatedType(element).getQualifiedName().toString())
                  .publicMethod()
                  .passedParameters(Parameter.forParameterList(
                      interfaceMethod.getParameters(), methodType.getParameterTypes()))
                  .build());
            }
          }
        }
      }


      ImmutableSet<FactoryMethodDescriptor> descriptors =
          factoryDescriptorGenerator.generateDescriptor(element);
      indexedMethods.putAll(
          Multimaps.index(descriptors, new Function<FactoryMethodDescriptor, String>() {
            @Override public String apply(FactoryMethodDescriptor descriptor) {
              return descriptor.factoryName();
            }
          }));
    }

    for (Entry<String, Collection<FactoryMethodDescriptor>> entry
        : indexedMethods.build().asMap().entrySet()) {
      ImmutableSet.Builder<String> extending = ImmutableSet.builder();
      ImmutableSortedSet.Builder<String> implementing = ImmutableSortedSet.naturalOrder();
      boolean publicType = false;
      for (FactoryMethodDescriptor methodDescriptor : entry.getValue()) {
        extending.add(methodDescriptor.declaration().extendingType().getQualifiedName().toString());
        for (TypeElement implementingType : methodDescriptor.declaration().implementingTypes()) {
          implementing.add(implementingType.getQualifiedName().toString());
        }
        publicType |= methodDescriptor.publicMethod();
      }
      try {
        factoryWriter.writeFactory(
            new FactoryDescriptor(
                entry.getKey(),
                Iterables.getOnlyElement(extending.build()),
                implementing.build(),
                publicType,
                ImmutableSet.copyOf(entry.getValue()),
                // TODO(gak): this needs to be indexed too
                implemetationMethodDescriptors.build()));
      } catch (IOException e) {
        messager.printMessage(Kind.ERROR, "failed");
      }
    }
  }

  private TypeElement getAnnotatedType(Element element) {
    List<TypeElement> types = ImmutableList.of();
    while (types.isEmpty()) {
      types = ElementFilter.typesIn(Arrays.asList(element));
      element = element.getEnclosingElement();
    }
    return Iterables.getOnlyElement(types);
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(AutoFactory.class.getName(), Provided.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }
}
