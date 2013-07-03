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
package com.google.autofactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;

/**
 * The annotation processor that generates factories for {@link AutoFactory} annotations.
 *
 * @author Gregory Kick
 */
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
    for (Element element : roundEnv.getElementsAnnotatedWith(AutoFactory.class)) {
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
      ImmutableSet.Builder<String> implementing = ImmutableSet.builder();
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
                ImmutableSet.copyOf(entry.getValue())));
      } catch (IOException e) {
        messager.printMessage(Kind.ERROR, "failed");
      }
    }

    return false;
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(AutoFactory.class.getName(), Provided.class.getName());
  }
}
