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

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import com.squareup.javawriter.JavaWriter;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

final class FactoryWriter {
  private final Filer filer;

  FactoryWriter(Filer filer) {
    this.filer = filer;
  }

  private static final Joiner argumentJoiner = Joiner.on(", ");

  void writeFactory(final FactoryDescriptor descriptor)
      throws IOException {
    JavaFileObject sourceFile = filer.createSourceFile(descriptor.name());
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());
    String packageName = getPackage(descriptor.name()).toString();
    writer.emitPackage(packageName)
        .emitImports("javax.annotation.Generated");

    writer.emitImports("javax.inject.Inject");
    if (!descriptor.providerNames().isEmpty()) {
      writer.emitImports("javax.inject.Provider");
    }

    for (String implementingType : descriptor.implementingTypes()) {
      String implementingPackageName = getPackage(implementingType).toString();
      if (!"java.lang".equals(implementingPackageName)
          && !packageName.equals(implementingPackageName)) {
        writer.emitImports(implementingType);
      }
    }

    String[] implementedClasses = FluentIterable.from(descriptor.implementingTypes())
        .transform(new Function<String, String>() {
          @Override public String apply(String implemetingClass) {
            return getSimpleName(implemetingClass).toString();
          }
        })
        .toSortedSet(Ordering.natural())
        .toArray(new String[0]);

    String factoryName = getSimpleName(descriptor.name()).toString();
    writer.emitAnnotation(Generated.class,
        ImmutableMap.of("value", "\"" + AutoFactoryProcessor.class.getName() + "\""));
    EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
    if (!descriptor.allowSubclasses()) {
      modifiers.add(FINAL);
    }
    if (descriptor.publicType()) {
      modifiers.add(PUBLIC);
    }
    writer.beginType(factoryName, "class", modifiers,
        Object.class.getName().equals(descriptor.extendingType())
            ? null : descriptor.extendingType(),
        implementedClasses);

    ImmutableList.Builder<String> constructorTokens = ImmutableList.builder();
    for (Entry<Key, String> entry : descriptor.providerNames().entrySet()) {
      Key key = entry.getKey();
      String providerName = entry.getValue();
      writer.emitField("Provider<" + key.getType() + ">", providerName, EnumSet.of(PRIVATE, FINAL));
      Optional<AnnotationMirror> qualifier = key.getQualifier();
      String qualifierPrefix = qualifier.isPresent() ? qualifier.get() + " " : "";
      constructorTokens.add(qualifierPrefix + "Provider<" + key.getType() + ">").add(providerName);
    }

    writer.emitAnnotation("Inject");
    writer.beginMethod(null, factoryName,
        descriptor.publicType() ? EnumSet.of(PUBLIC) : EnumSet.noneOf(Modifier.class),
        constructorTokens.build().toArray(new String[0]));

    for (String providerName : descriptor.providerNames().values()) {
      writer.emitStatement("this.%1$s = %1$s", providerName);
    }

    writer.endMethod();

    for (final FactoryMethodDescriptor methodDescriptor : descriptor.methodDescriptors()) {
      writer.beginMethod(methodDescriptor.returnType(), methodDescriptor.name(),
          methodDescriptor.publicMethod() ? EnumSet.of(PUBLIC) : EnumSet.noneOf(Modifier.class),
          parameterTokens(methodDescriptor.passedParameters()));
      FluentIterable<String> creationParameterNames =
          FluentIterable.from(methodDescriptor.creationParameters())
              .transform(new Function<Parameter, String>() {
                @Override public String apply(Parameter parameter) {
                  return methodDescriptor.passedParameters().contains(parameter)
                      ? parameter.name()
                      : descriptor.providerNames().get(parameter.asKey()) + ".get()";
                }
              });
      writer.emitStatement("return new %s(%s)", writer.compressType(methodDescriptor.returnType()),
          argumentJoiner.join(creationParameterNames));
      writer.endMethod();
    }

    for (ImplementationMethodDescriptor methodDescriptor
        : descriptor.implementationMethodDescriptors()) {
      writer.emitAnnotation(Override.class);
      writer.beginMethod(methodDescriptor.returnType(), methodDescriptor.name(),
          methodDescriptor.publicMethod() ? EnumSet.of(PUBLIC) : EnumSet.noneOf(Modifier.class),
          parameterTokens(methodDescriptor.passedParameters()));
      FluentIterable<String> creationParameterNames =
          FluentIterable.from(methodDescriptor.passedParameters())
              .transform(new Function<Parameter, String>() {
                @Override public String apply(Parameter parameter) {
                  return parameter.name();
                }
              });
      writer.emitStatement("return create(%s)", argumentJoiner.join(creationParameterNames));
      writer.endMethod();
    }

    writer.endType();
    writer.close();
  }

  private static String[] parameterTokens(Collection<Parameter> parameters) {
    List<String> parameterTokens =
        Lists.newArrayListWithCapacity(parameters.size());
    for (Parameter parameter : parameters) {
      parameterTokens.add(parameter.type());
      parameterTokens.add(parameter.name());
    }
    return parameterTokens.toArray(new String[0]);
  }

  private static CharSequence getSimpleName(CharSequence fullyQualifiedName) {
    int lastDot = lastIndexOf(fullyQualifiedName, '.');
    return fullyQualifiedName.subSequence(lastDot + 1, fullyQualifiedName.length());
  }

  private static CharSequence getPackage(CharSequence fullyQualifiedName) {
    int lastDot = lastIndexOf(fullyQualifiedName, '.');
    return fullyQualifiedName.subSequence(0, lastDot);
  }

  private static int lastIndexOf(CharSequence charSequence, char c) {
    for (int i = charSequence.length() - 1; i >= 0; i--) {
      if (charSequence.charAt(i) == c) {
        return i;
      }
    }
    return -1;
  }
}
