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

import com.google.auto.common.MoreTypes;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.auto.service.AutoService;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType;

/**
 * The annotation processor that generates factories for {@link AutoFactory} annotations.
 *
 * @author Gregory Kick
 */
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.ISOLATING)
@AutoService(Processor.class)
public final class AutoFactoryProcessor extends AbstractProcessor {
  private FactoryDescriptorGenerator factoryDescriptorGenerator;
  private AutoFactoryDeclaration.Factory declarationFactory;
  private ProvidedChecker providedChecker;
  private Messager messager;
  private Elements elements;
  private Types types;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    elements = processingEnv.getElementUtils();
    types = processingEnv.getTypeUtils();
    messager = processingEnv.getMessager();
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
      messager.printMessage(
          Kind.ERROR,
          "Failed to process @AutoFactory annotations:\n" + Throwables.getStackTraceAsString(e));
    }
    return false;
  }

  private void doProcess(RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Provided.class)) {
      providedChecker.checkProvidedParameter(element);
    }

    ImmutableListMultimap.Builder<PackageAndClass, FactoryMethodDescriptor> indexedMethodsBuilder =
        ImmutableListMultimap.builder();
    ImmutableSetMultimap.Builder<PackageAndClass, ImplementationMethodDescriptor>
        implementationMethodDescriptorsBuilder = ImmutableSetMultimap.builder();
    // Iterate over the classes and methods that are annotated with @AutoFactory.
    for (Element element : roundEnv.getElementsAnnotatedWith(AutoFactory.class)) {
      Optional<AutoFactoryDeclaration> declaration = declarationFactory.createIfValid(element);
      if (declaration.isPresent()) {
        PackageAndClass factoryName = declaration.get().getFactoryName();
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
        indexedMethodsBuilder.put(descriptor.factoryName(), descriptor);
      }
    }

    ImmutableSetMultimap<PackageAndClass, ImplementationMethodDescriptor>
        implementationMethodDescriptors = implementationMethodDescriptorsBuilder.build();
    ImmutableListMultimap<PackageAndClass, FactoryMethodDescriptor> indexedMethods =
        indexedMethodsBuilder.build();
    ImmutableSetMultimap<String, PackageAndClass> factoriesBeingCreated =
        simpleNamesToNames(indexedMethods.keySet());
    FactoryWriter factoryWriter = new FactoryWriter(processingEnv, factoriesBeingCreated);

    indexedMethods
        .asMap()
        .forEach(
            (factoryName, methodDescriptors) -> {
              if (methodDescriptors.isEmpty()) {
                // This shouldn't happen, but check anyway to avoid an exception for
                // methodDescriptors.iterator().next() below.
                return;
              }
              // The sets of classes that are mentioned in the `extending` and `implementing`
              // elements, respectively, of the @AutoFactory annotations for this factory.
              ImmutableSet.Builder<TypeMirror> extending = newTypeSetBuilder();
              ImmutableSortedSet.Builder<TypeMirror> implementing = newTypeSetBuilder();
              boolean publicType = false;
              Set<Boolean> allowSubclassesSet = new HashSet<>();
              boolean skipCreation = false;
              for (FactoryMethodDescriptor methodDescriptor : methodDescriptors) {
                extending.add(methodDescriptor.declaration().extendingType().asType());
                for (TypeElement implementingType :
                    methodDescriptor.declaration().implementingTypes()) {
                  implementing.add(implementingType.asType());
                }
                publicType |= methodDescriptor.publicMethod();
                allowSubclassesSet.add(methodDescriptor.declaration().allowSubclasses());
                if (allowSubclassesSet.size() > 1) {
                  skipCreation = true;
                  messager.printMessage(
                      Kind.ERROR,
                      "Cannot mix allowSubclasses=true and allowSubclasses=false in one factory.",
                      methodDescriptor.declaration().target(),
                      methodDescriptor.declaration().mirror(),
                      methodDescriptor.declaration().valuesMap().get("allowSubclasses"));
                }
              }
              // The set can't be empty because we eliminated methodDescriptors.isEmpty() above.
              boolean allowSubclasses = allowSubclassesSet.iterator().next();
              if (!skipCreation) {
                try {
                  factoryWriter.writeFactory(
                      FactoryDescriptor.create(
                          factoryName,
                          Iterables.getOnlyElement(extending.build()),
                          implementing.build(),
                          publicType,
                          ImmutableSet.copyOf(methodDescriptors),
                          implementationMethodDescriptors.get(factoryName),
                          allowSubclasses));
                } catch (IOException e) {
                  messager.printMessage(Kind.ERROR, "failed: " + e);
                }
              }
            });
  }

  private ImmutableSet<ImplementationMethodDescriptor> implementationMethods(
      TypeElement supertype, Element autoFactoryElement) {
    ImmutableSet.Builder<ImplementationMethodDescriptor> implementationMethodsBuilder =
        ImmutableSet.builder();
    for (ExecutableElement implementationMethod :
        ElementFilter.methodsIn(elements.getAllMembers(supertype))) {
      if (implementationMethod.getModifiers().contains(Modifier.ABSTRACT)) {
        ExecutableType methodType =
            Elements2.getExecutableElementAsMemberOf(types, implementationMethod, supertype);
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
                .exceptions(implementationMethod.getThrownTypes())
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

  private static ImmutableSetMultimap<String, PackageAndClass> simpleNamesToNames(
      ImmutableSet<PackageAndClass> names) {
    // .collect(toImmutableSetMultimap(...)) would make this much simpler but ran into problems in
    // Google's internal build system because of multiple Guava versions.
    ImmutableSetMultimap.Builder<String, PackageAndClass> builder = ImmutableSetMultimap.builder();
    for (PackageAndClass name : names) {
      builder.put(name.className(), name);
    }
    return builder.build();
  }

  private static ImmutableSortedSet.Builder<TypeMirror> newTypeSetBuilder() {
    return ImmutableSortedSet.orderedBy(
        Comparator.comparing(t -> MoreTypes.asTypeElement(t).getQualifiedName().toString()));
  }

  @Override
  public ImmutableSet<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(AutoFactory.class.getName(), Provided.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }
}
