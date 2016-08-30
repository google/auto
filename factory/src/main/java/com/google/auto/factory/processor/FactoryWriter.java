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

import static com.google.auto.factory.processor.Mirrors.isProvider;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.factory.internal.Preconditions;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.CharSink;
import com.google.common.io.CharSource;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor7;
import javax.tools.JavaFileObject;

final class FactoryWriter {

  private final Filer filer;

  FactoryWriter(Filer filer) {
    this.filer = filer;
  }

  private static final Joiner ARGUMENT_JOINER = Joiner.on(", ");

  void writeFactory(final FactoryDescriptor descriptor)
      throws IOException {
    String factoryName = getSimpleName(descriptor.name()).toString();
    TypeSpec.Builder factory = classBuilder(factoryName);
    factory.addAnnotation(
        AnnotationSpec.builder(Generated.class)
            .addMember("value", "$S", AutoFactoryProcessor.class.getName())
            .addMember(
                "comments", "$S", "https://github.com/google/auto/tree/master/factory")
            .build());
    if (!descriptor.allowSubclasses()) {
      factory.addModifiers(FINAL);
    }
    if (descriptor.publicType()) {
      factory.addModifiers(PUBLIC);
    }

    factory.superclass(TypeName.get(descriptor.extendingType()));
    for (TypeMirror implementingType : descriptor.implementingTypes()) {
      factory.addSuperinterface(TypeName.get(implementingType));
    }

    addConstructorAndProviderFields(factory, descriptor);
    addFactoryMethods(factory, descriptor);
    addImplementationMethods(factory, descriptor);

    writeFile(factory.build(), descriptor);
  }

  private void addConstructorAndProviderFields(
      TypeSpec.Builder factory, FactoryDescriptor descriptor) {
    MethodSpec.Builder constructor = constructorBuilder().addAnnotation(Inject.class);
    if (descriptor.publicType()) {
      constructor.addModifiers(PUBLIC);
    }
    Iterator<ProviderField> providerFields = descriptor.providers().values().iterator();
    for (int argumentIndex = 1; providerFields.hasNext(); argumentIndex++) {
      ProviderField provider = providerFields.next();
      TypeName typeName = TypeName.get(provider.key().type()).box();
      TypeName providerType = ParameterizedTypeName.get(ClassName.get(Provider.class), typeName);
      factory.addField(providerType, provider.name(), PRIVATE, FINAL);
      if (provider.key().qualifier().isPresent()) {
        // only qualify the constructor parameter
        providerType = providerType.annotated(AnnotationSpec.get(provider.key().qualifier().get()));
      }
      constructor.addParameter(providerType, provider.name());
      constructor.addStatement(
          "this.$1L = $2T.checkNotNull($1L, $3L)",
          provider.name(),
          Preconditions.class,
          argumentIndex);
    }

    factory.addMethod(constructor.build());
  }

  private void addFactoryMethods(TypeSpec.Builder factory, FactoryDescriptor descriptor) {
    for (FactoryMethodDescriptor methodDescriptor : descriptor.methodDescriptors()) {
      MethodSpec.Builder method =
          MethodSpec.methodBuilder(methodDescriptor.name())
              .returns(TypeName.get(methodDescriptor.returnType()))
              .varargs(methodDescriptor.isVarArgs());
      if (methodDescriptor.overridingMethod()) {
        method.addAnnotation(Override.class);
      }
      if (methodDescriptor.publicMethod()) {
        method.addModifiers(PUBLIC);
      }
      CodeBlock.Builder args = CodeBlock.builder();
      method.addParameters(parameters(methodDescriptor.passedParameters()));
      Iterator<Parameter> parameters = methodDescriptor.creationParameters().iterator();
      for (int argumentIndex = 1; parameters.hasNext(); argumentIndex++) {
        Parameter parameter = parameters.next();
        boolean checkNotNull = !parameter.nullable().isPresent();
        CodeBlock argument;
        if (methodDescriptor.passedParameters().contains(parameter)) {
          argument = CodeBlock.of(parameter.name());
          if (isPrimitive(parameter.type())) {
            checkNotNull = false;
          }
        } else {
          ProviderField provider = descriptor.providers().get(parameter.key());
          argument = CodeBlock.of(provider.name());
          if (isProvider(parameter.type())) {
            // Providers are checked for nullness in the Factory's constructor.
            checkNotNull = false;
          } else {
            argument = CodeBlock.of("$L.get()", argument);
          }
        }
        if (checkNotNull) {
          argument =
              CodeBlock.of("$T.checkNotNull($L, $L)", Preconditions.class, argument, argumentIndex);
        }
        args.add(argument);
        if (parameters.hasNext()) {
          args.add(", ");
        }
      }
      method.addStatement("return new $T($L)", methodDescriptor.returnType(), args.build());
      factory.addMethod(method.build());
    }
  }

  private void addImplementationMethods(TypeSpec.Builder factory, FactoryDescriptor descriptor) {
    for (ImplementationMethodDescriptor methodDescriptor :
        descriptor.implementationMethodDescriptors()) {
      MethodSpec.Builder implementationMethod =
          methodBuilder(methodDescriptor.name())
              .addAnnotation(Override.class)
              .returns(TypeName.get(methodDescriptor.returnType()))
              .varargs(methodDescriptor.isVarArgs());
      if (methodDescriptor.publicMethod()) {
        implementationMethod.addModifiers(PUBLIC);
      }
      implementationMethod.addParameters(parameters(methodDescriptor.passedParameters()));
      implementationMethod.addStatement(
          "return create($L)",
          FluentIterable.from(methodDescriptor.passedParameters())
              .transform(
                  new Function<Parameter, String>() {
                    @Override
                    public String apply(Parameter parameter) {
                      return parameter.name();
                    }
                  })
              .join(ARGUMENT_JOINER));
      factory.addMethod(implementationMethod.build());
    }
  }

  private void writeFile(TypeSpec factory, FactoryDescriptor descriptor)
      throws IOException {
    JavaFile javaFile =
        JavaFile.builder(getPackage(descriptor.name()), factory).skipJavaLangImports(true).build();

    final JavaFileObject sourceFile = filer.createSourceFile(
        ClassName.get(javaFile.packageName, javaFile.typeSpec.name).toString(),
        Iterables.toArray(javaFile.typeSpec.originatingElements, Element.class));
    try {
      new Formatter().formatSource(
          CharSource.wrap(javaFile.toString()),
          new CharSink() {
            @Override public Writer openStream() throws IOException {
              return sourceFile.openWriter();
            }
          });
    } catch (FormatterException e) {
      Throwables.propagate(e);
    }
  }

  /**
   * {@link ParameterSpec}s to match {@code parameters}. Note that the type of the {@link
   * ParameterSpec}s match {@link Parameter#type()} and not {@link Key#type()}.
   */
  private static Iterable<ParameterSpec> parameters(Iterable<Parameter> parameters) {
    ImmutableList.Builder<ParameterSpec> builder = ImmutableList.builder();
    for (Parameter parameter : parameters) {
      ParameterSpec.Builder parameterBuilder =
          ParameterSpec.builder(TypeName.get(parameter.type()), parameter.name());
      for (AnnotationMirror annotation :
          Iterables.concat(parameter.nullable().asSet(), parameter.key().qualifier().asSet())) {
        parameterBuilder.addAnnotation(AnnotationSpec.get(annotation));
      }
      builder.add(parameterBuilder.build());
    }
    return builder.build();
  }

  private static CharSequence getSimpleName(CharSequence fullyQualifiedName) {
    int lastDot = lastIndexOf(fullyQualifiedName, '.');
    return fullyQualifiedName.subSequence(lastDot + 1, fullyQualifiedName.length());
  }

  private static String getPackage(CharSequence fullyQualifiedName) {
    int lastDot = lastIndexOf(fullyQualifiedName, '.');
    return fullyQualifiedName.subSequence(0, lastDot).toString();
  }

  private static int lastIndexOf(CharSequence charSequence, char c) {
    for (int i = charSequence.length() - 1; i >= 0; i--) {
      if (charSequence.charAt(i) == c) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isPrimitive(TypeMirror type) {
    return type.accept(new SimpleTypeVisitor7<Boolean, Void>(){
      @Override
      public Boolean visitPrimitive(PrimitiveType t, Void aVoid) {
        return true;
      }

      @Override
      protected Boolean defaultAction(TypeMirror e, Void aVoid) {
        return false;
      }
    }, null);
  }
}
