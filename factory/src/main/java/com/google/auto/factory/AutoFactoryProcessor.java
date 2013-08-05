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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;

import com.google.auto.service.AutoService;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;

/**
 * The annotation processor that generates factories for {@link AutoFactory} annotations.
 *
 * @author Gregory Kick
 */
@AutoService(Processor.class)
public final class AutoFactoryProcessor extends AbstractProcessor {
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Messager messager = processingEnv.getMessager();
    Elements elements = processingEnv.getElementUtils();
    ProvidedChecker providedChecker = new ProvidedChecker(messager);
    for (Element element : roundEnv.getElementsAnnotatedWith(Provided.class)) {
      providedChecker.checkProvidedParameter(element);
    }

    FactoryDescriptorGenerator factoryDescriptorGenerator =
        new FactoryDescriptorGenerator(messager, elements);
    FactoryWriter factoryWriter = new FactoryWriter(processingEnv.getFiler());
    ImmutableListMultimap.Builder<String, FactoryMethodDescriptor> indexedMethods =
        ImmutableListMultimap.builder();
    ImmutableSet.Builder<ImplemetationMethodDescriptor> implemetationMethodDescriptors =
        ImmutableSet.builder();
    for (Element element : roundEnv.getElementsAnnotatedWith(AutoFactory.class)) {
      AutoFactoryDeclaration declaration = AutoFactoryDeclaration.fromAnnotationMirror(
          elements, Mirrors.getAnnotationMirror(element, AutoFactory.class).get());
      for (String implementing : declaration.implementingQualifiedNames()) {
        TypeElement interfaceType = elements.getTypeElement(implementing);
        List<ExecutableElement> interfaceMethods =
            ElementFilter.methodsIn(interfaceType.getEnclosedElements());
        for (ExecutableElement interfaceMethod : interfaceMethods) {
          implemetationMethodDescriptors.add(new ImplemetationMethodDescriptor.Builder()
              .factoryName(interfaceType.getQualifiedName().toString())
              .name(interfaceMethod.getSimpleName().toString())
              .returnType(getAnnotatedType(element).getQualifiedName().toString())
              .publicMethod()
              .passedParameters(Parameter.forParameterList(interfaceMethod.getParameters()))
              .build());
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
        extending.add(methodDescriptor.declaration().extendingQualifiedName());
        implementing.addAll(methodDescriptor.declaration().implementingQualifiedNames());
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

    return false;
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
