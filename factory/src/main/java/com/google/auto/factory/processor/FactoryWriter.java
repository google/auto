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

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.factory.internal.Preconditions;
import com.google.auto.common.MoreElements;
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
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor7;
import javax.tools.JavaFileObject;

final class FactoryWriter {
  private final Filer filer;

  FactoryWriter(Filer filer) {
    this.filer = filer;
  }

  private static final Joiner argumentJoiner = Joiner.on(", ");

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

    MethodSpec.Builder constructor = constructorBuilder().addAnnotation(Inject.class);
    if (descriptor.publicType()) {
      constructor.addModifiers(PUBLIC);
    }
    for (ProviderField provider : descriptor.providers().values()) {
      TypeName typeName = typeName(provider.key().type()).box();
      TypeName providerType = ParameterizedTypeName.get(ClassName.get(Provider.class), typeName);
      factory.addField(providerType, provider.name(), PRIVATE, FINAL);
      if (provider.key().qualifier().isPresent()) {
        // only qualify the constructor parameter
        providerType = providerType.annotated(AnnotationSpec.get(provider.key().qualifier().get()));
      }
      constructor.addParameter(providerType, provider.name());
      constructor.addStatement("this.$1L = $1L", provider.name());
    }

    factory.addMethod(constructor.build());

    for (final FactoryMethodDescriptor methodDescriptor : descriptor.methodDescriptors()) {
      MethodSpec.Builder method =
          MethodSpec.methodBuilder(methodDescriptor.name())
              .returns(TypeName.get(methodDescriptor.returnType()));
      if (methodDescriptor.publicMethod()) {
        method.addModifiers(PUBLIC);
      }
      CodeBlock.Builder args = CodeBlock.builder();
      method.addParameters(parameters(methodDescriptor.passedParameters()));
      Iterator<Parameter> parameters = methodDescriptor.creationParameters().iterator();
      while (parameters.hasNext()) {
        Parameter parameter = parameters.next();
        boolean nullableArgument;
        CodeBlock argument;
        if (methodDescriptor.passedParameters().contains(parameter)) {
          argument = codeBlock(parameter.name());
          nullableArgument = parameter.nullable().isPresent() || isPrimitive(parameter.type());
        } else {
          ProviderField provider = descriptor.providers().get(parameter.key());
          argument = codeBlock(provider.name());
          if (!parameter.providerOfType()) {
            argument = codeBlock("$L.get()", argument);
          }
          nullableArgument = parameter.nullable().isPresent();
        }
        if (!nullableArgument) {
          argument = codeBlock("$T.checkNotNull($L)", Preconditions.class, argument);
        }
        args.add(argument);
        if (parameters.hasNext()) {
          args.add(", ");
        }
      }
      method.addStatement("return new $T($L)", methodDescriptor.returnType(), args.build());
      factory.addMethod(method.build());
    }

    for (ImplementationMethodDescriptor methodDescriptor
        : descriptor.implementationMethodDescriptors()) {
      MethodSpec.Builder implementationMethod =
          methodBuilder(methodDescriptor.name())
              .addAnnotation(Override.class)
              .returns(TypeName.get(methodDescriptor.returnType()));
      if (methodDescriptor.publicMethod()) {
        implementationMethod.addModifiers(PUBLIC);
      }
      implementationMethod.addParameters(parameters(methodDescriptor.passedParameters()));
      FluentIterable<String> creationParameterNames =
          FluentIterable.from(methodDescriptor.passedParameters())
              .transform(new Function<Parameter, String>() {
                @Override public String apply(Parameter parameter) {
                  return parameter.name();
                }
              });
      implementationMethod.addStatement(
          "return create($L)", argumentJoiner.join(creationParameterNames));
      factory.addMethod(implementationMethod.build());
    }

    JavaFile javaFile =
        JavaFile.builder(getPackage(descriptor.name()), factory.build())
            .skipJavaLangImports(true)
            .build();

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

  private static Iterable<ParameterSpec> parameters(Iterable<Parameter> parameters) {
    ImmutableList.Builder<ParameterSpec> builder = ImmutableList.builder();
    for (Parameter parameter : parameters) {
      Iterable<AnnotationMirror> annotations =
          Iterables.concat(parameter.nullable().asSet(), parameter.key().qualifier().asSet());
      TypeName type = annotate(TypeName.get(parameter.type()), annotations);
      builder.add(ParameterSpec.builder(type, parameter.name()).build());
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

  private static TypeName annotate(TypeName type, Iterable<AnnotationMirror> annotations) {
    // TODO(ronshapiro): multiple calls to TypeName.annotated() will be fixed in JavaPoet 1.6
    ImmutableList.Builder<AnnotationSpec> specs = ImmutableList.builder();
    for (AnnotationMirror annotation : annotations) {
      specs.add(AnnotationSpec.get(annotation));
    }
    return type.annotated(specs.build());
  }

  private static CodeBlock codeBlock(String format, Object... args) {
    return CodeBlock.builder().add(format, args).build();
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

  /**
   * JavaPoet 1.5.1 does not handle {@link ErrorType} in {@link TypeName#get(TypeMirror)}. A fix is
   * proposed in https://github.com/square/javapoet/pull/430.
   */
  private static TypeName typeName(TypeMirror type) {
    return type.accept(new SimpleTypeVisitor7<TypeName, Void>(){
      @Override
      public TypeName visitError(ErrorType type, Void p) {
        return ClassName.get(MoreElements.asType(type.asElement()));
      }

      @Override
      protected TypeName defaultAction(TypeMirror type, Void p) {
        return TypeName.get(type);
      }
    }, null);
  }
}
