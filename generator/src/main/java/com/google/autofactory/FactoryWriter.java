package com.google.autofactory;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.squareup.java.JavaWriter;

final class FactoryWriter {
  private final Filer filer;

  @Inject FactoryWriter(Filer filer) {
    this.filer = filer;
  }

  private static final Joiner argumentJoiner = Joiner.on(", ");

  void writeFactory(final FactoryDescriptor descriptor, Element originatingElement)
      throws IOException {
    JavaFileObject sourceFile = filer.createSourceFile(descriptor.name(), originatingElement);
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());
    String packageName = Names.getPackage(descriptor.name()).toString();
    writer.emitPackage(packageName)
        .emitImports("javax.annotation.Generated");

    writer.emitImports("javax.inject.Inject");
    if (!descriptor.providerNames().isEmpty()) {
      writer.emitImports("javax.inject.Provider");
    }

    for (String implementingType : descriptor.implementingTypes()) {
      String implementingPackageName = Names.getPackage(implementingType).toString();
      if (!"java.lang".equals(implementingPackageName)
          && !packageName.equals(implementingPackageName)) {
        writer.emitImports(implementingType);
      }
    }

    String[] implementedClasses = FluentIterable.from(descriptor.implementingTypes())
        .transform(new Function<String, String>() {
          @Override public String apply(String implemetingClass) {
            return Names.getSimpleName(implemetingClass).toString();
          }
        })
        .toSortedSet(Ordering.natural())
        .toArray(new String[0]);

    String factoryName = Names.getSimpleName(descriptor.name()).toString();
    writer.emitAnnotation(Generated.class, ImmutableMap.of("value", "\"auto-factory\""))
        .beginType(factoryName, "class", Modifier.FINAL, null, implementedClasses);

    ImmutableList.Builder<String> constructorTokens = ImmutableList.builder();
    for (Entry<Key, String> entry : descriptor.providerNames().entrySet()) {
      Key key = entry.getKey();
      String providerName = entry.getValue();
      writer.emitField("Provider<" + key.getType() + ">", providerName,
          Modifier.PRIVATE | Modifier.FINAL);
      Optional<String> qualifier = key.getQualifier();
      String qualifierPrefix = qualifier.isPresent() ? "@" + qualifier.get() + " " : "";
      constructorTokens.add(qualifierPrefix + "Provider<" + key.getType() + ">").add(providerName);
    }


    writer.emitAnnotation("Inject");
    writer.beginMethod(null, factoryName, 0, constructorTokens.build().toArray(new String[0]));

    for (String providerName : descriptor.providerNames().values()) {
      writer.emitStatement("this.%1$s = %1$s", providerName);
    }

    writer.endMethod();

    for (final FactoryMethodDescriptor methodDescriptor : descriptor.methodDescriptors()) {
      writer.beginMethod(methodDescriptor.returnType(), methodDescriptor.name(), 0,
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
}
