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
    for (Element element : roundEnv.getElementsAnnotatedWith(AutoFactory.class)) {
      ImmutableSet<FactoryMethodDescriptor> descriptors =
          factoryDescriptorGenerator.generateDescriptor(element);
      ImmutableListMultimap<String, FactoryMethodDescriptor> indexedMethods =
          Multimaps.index(descriptors, new Function<FactoryMethodDescriptor, String>() {
            @Override public String apply(FactoryMethodDescriptor descriptor) {
              return descriptor.factoryName();
            }
          });
      for (Entry<String, Collection<FactoryMethodDescriptor>> entry
          : indexedMethods.asMap().entrySet()) {
        ImmutableSet.Builder<String> extending = ImmutableSet.builder();
        ImmutableSet.Builder<String> implementing = ImmutableSet.builder();
        for (FactoryMethodDescriptor methodDescriptor : entry.getValue()) {
          extending.add(methodDescriptor.declaration().extendingQualifiedName());
          implementing.addAll(methodDescriptor.declaration().implementingQualifiedNames());
        }
        try {

          factoryWriter.writeFactory(
              new FactoryDescriptor(
                  entry.getKey(),
                  Iterables.getOnlyElement(extending.build()),
                  implementing.build(),
                  ImmutableSet.copyOf(entry.getValue())),
              element);
        } catch (IOException e) {
          messager.printMessage(Kind.ERROR, "failed", element);
        }
      }
    }

    return false;
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(AutoFactory.class.getName(), Provided.class.getName());
  }
}
