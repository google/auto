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

import com.google.auto.common.MoreTypes;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.auto.service.AutoService;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.googlejavaformat.java.filer.FormattingFiler;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

/**
 * The annotation processor that generates factories for {@link AutoFactory} annotations.
 *
 * @author Gregory Kick
 */
@AutoService(Processor.class)
public final class AutoFactoryProcessor extends AbstractProcessor {
  private FactoryDescriptorGenerator factoryDescriptorGenerator;
  private AutoFactoryDeclaration.Factory declarationFactory;
  private ProvidedChecker providedChecker;
  private Messager messager;
  private Elements elements;
  private Types types;
  private FactoryWriter factoryWriter;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    elements = processingEnv.getElementUtils();
    types = processingEnv.getTypeUtils();
    messager = processingEnv.getMessager();
    factoryWriter =
        new FactoryWriter(
            new FormattingFiler(processingEnv.getFiler()),
            elements,
            processingEnv.getSourceVersion());
    providedChecker = new ProvidedChecker(messager);
    declarationFactory = new AutoFactoryDeclaration.Factory(elements, messager);
    factoryDescriptorGenerator =
        new FactoryDescriptorGenerator(messager, types, declarationFactory);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      doProcess(roundEnv);
    } catch (Throwable e) {
      messager.printMessage(Kind.ERROR, "Failed to process @AutoFactory annotations:\n"
          + Throwables.getStackTraceAsString(e));
    }
    return false;
  }

  private void doProcess(RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Provided.class)) {
      providedChecker.checkProvidedParameter(element);
    }

    ImmutableListMultimap.Builder<String, FactoryMethodDescriptor> indexedMethods =
        ImmutableListMultimap.builder();
    ImmutableSetMultimap.Builder<String, ImplementationMethodDescriptor>
        implementationMethodDescriptorsBuilder = ImmutableSetMultimap.builder();
    for (Element element : roundEnv.getElementsAnnotatedWith(AutoFactory.class)) {
      Optional<AutoFactoryDeclaration> declaration = declarationFactory.createIfValid(element);
      if (declaration.isPresent()) {
        String factoryName = declaration.get().getFactoryName();
        TypeElement extendingType = declaration.get().extendingType();
        implementationMethodDescriptorsBuilder.putAll(
            factoryName, implementationMethods(extendingType, element));
        for (TypeElement implementingType : declaration.get().implementingTypes()) {
          implementationMethodDescriptorsBuilder.putAll(
              factoryName, implementationMethods(implementingType, element));
        }
      }

      ImmutableSet<FactoryMethodDescriptor> descriptors =
          factoryDescriptorGenerator.generateDescriptor(element);
      for (FactoryMethodDescriptor descriptor : descriptors) {
        indexedMethods.put(descriptor.factoryName(), descriptor);
      }
    }

    ImmutableSetMultimap<String, ImplementationMethodDescriptor>
        implementationMethodDescriptors = implementationMethodDescriptorsBuilder.build();

    ImmutableMap<String, Collection<FactoryMethodDescriptor>>
        implementationMethodDescriptorsMap = indexedMethods.build().asMap();
    ImmutableMap.Builder<CharSequence, TypeName> factoriesBuilder = ImmutableMap.builder();
    for (String name : implementationMethodDescriptorsMap.keySet()) {
        TypeName typeName = ClassName.bestGuess(name).withoutAnnotations();
        factoriesBuilder.put(Classes.getSimpleName(name), typeName);
    }
    ImmutableMap<CharSequence, TypeName> factories = factoriesBuilder.build();
    for (Entry<String, Collection<FactoryMethodDescriptor>> entry
        : implementationMethodDescriptorsMap.entrySet()) {
      ImmutableSet.Builder<TypeMirror> extending = ImmutableSet.builder();
      ImmutableSortedSet.Builder<TypeMirror> implementing =
          ImmutableSortedSet.orderedBy(
              new Comparator<TypeMirror>() {
                @Override
                public int compare(TypeMirror first, TypeMirror second) {
                  String firstName = MoreTypes.asTypeElement(first).getQualifiedName().toString();
                  String secondName = MoreTypes.asTypeElement(second).getQualifiedName().toString();
                  return firstName.compareTo(secondName);
                }
              });
      boolean publicType = false;
      Boolean allowSubclasses = null;
      boolean skipCreation = false;
      for (FactoryMethodDescriptor methodDescriptor : entry.getValue()) {
        extending.add(methodDescriptor.declaration().extendingType().asType());
        for (TypeElement implementingType : methodDescriptor.declaration().implementingTypes()) {
          implementing.add(implementingType.asType());
        }
        publicType |= methodDescriptor.publicMethod();
        if (allowSubclasses == null) {
          allowSubclasses = methodDescriptor.declaration().allowSubclasses();
        } else if (!allowSubclasses.equals(methodDescriptor.declaration().allowSubclasses())) {
          skipCreation = true;
          messager.printMessage(Kind.ERROR,
              "Cannot mix allowSubclasses=true and allowSubclasses=false in one factory.",
              methodDescriptor.declaration().target(),
              methodDescriptor.declaration().mirror(),
              methodDescriptor.declaration().valuesMap().get("allowSubclasses"));
        }
      }
      if (!skipCreation) {
        try {
          factoryWriter.writeFactory(
              FactoryDescriptor.create(
                  entry.getKey(),
                  Iterables.getOnlyElement(extending.build()),
                  implementing.build(),
                  publicType,
                  ImmutableSet.copyOf(entry.getValue()),
                  implementationMethodDescriptors.get(entry.getKey()),
                  allowSubclasses),
              factories);
        } catch (IOException e) {
          messager.printMessage(Kind.ERROR, "failed");
        }
      }
    }
  }

  private ImmutableSet<ImplementationMethodDescriptor> implementationMethods(
      TypeElement supertype, Element autoFactoryElement) {
    ImmutableSet.Builder<ImplementationMethodDescriptor> implementationMethodsBuilder =
        ImmutableSet.builder();
    for (ExecutableElement implementationMethod :
        ElementFilter.methodsIn(elements.getAllMembers(supertype))) {
      if (implementationMethod.getModifiers().contains(Modifier.ABSTRACT)) {
        ExecutableType methodType =
            Elements2.getExecutableElementAsMemberOf(
                types, implementationMethod, supertype);
        ImmutableSet<Parameter> passedParameters =
            Parameter.forParameterList(
                implementationMethod.getParameters(), methodType.getParameterTypes(), types);
        implementationMethodsBuilder.add(
            ImplementationMethodDescriptor.builder()
                .name(implementationMethod.getSimpleName().toString())
                .returnType(getAnnotatedType(autoFactoryElement))
                .publicMethod()
                .passedParameters(passedParameters)
                .isVarArgs(implementationMethod.isVarArgs())
                .build());
      }
    }
    return implementationMethodsBuilder.build();
  }

  private TypeMirror getAnnotatedType(Element element) {
    List<TypeElement> types = ImmutableList.of();
    while (types.isEmpty()) {
      types = ElementFilter.typesIn(Arrays.asList(element));
      element = element.getEnclosingElement();
    }
    return Iterables.getOnlyElement(types).asType();
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
