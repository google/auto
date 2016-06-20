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

import com.google.auto.common.MoreElements;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.Map.Entry;

import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor7;

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
    for (Entry<Key, String> entry : descriptor.providerNames().entrySet()) {
      Key key = entry.getKey();
      String providerName = entry.getValue();
      Optional<AnnotationMirror> qualifier = key.getQualifier();

      TypeName providerType =
          ParameterizedTypeName.get(ClassName.get(Provider.class), typeName(key.type()));
      factory.addField(providerType, providerName, PRIVATE, FINAL);
      constructor.addParameter(annotateIfPresent(providerType, qualifier), providerName);
    }

    for (String providerName : descriptor.providerNames().values()) {
      constructor.addStatement("this.$1L = $1L", providerName);
    }

    factory.addMethod(constructor.build());

    for (final FactoryMethodDescriptor methodDescriptor : descriptor.methodDescriptors()) {
      MethodSpec.Builder method =
          MethodSpec.methodBuilder(methodDescriptor.name())
              .returns(TypeName.get(methodDescriptor.returnType()));
      if (methodDescriptor.overridingMethod()) {
        method.addAnnotation(Override.class);
      }
      if (methodDescriptor.publicMethod()) {
        method.addModifiers(PUBLIC);
      }
      method.addParameters(parameters(methodDescriptor.passedParameters()));
      FluentIterable<String> creationParameterNames =
          FluentIterable.from(methodDescriptor.creationParameters())
              .transform(
                  new Function<Parameter, String>() {
                    @Override
                    public String apply(Parameter parameter) {
                      if (methodDescriptor.passedParameters().contains(parameter)) {
                        return parameter.name();
                      } else if (parameter.providerOfType()) {
                        return descriptor.providerNames().get(parameter.key());
                      } else {
                        return descriptor.providerNames().get(parameter.key()) + ".get()";
                      }
                    }
                  });
      method.addStatement(
          "return new $T($L)",
          methodDescriptor.returnType(),
          argumentJoiner.join(creationParameterNames));
      method.varargs(methodDescriptor.isVarargs());
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

    JavaFile.builder(getPackage(descriptor.name()), factory.build())
        .skipJavaLangImports(true)
        .build()
        .writeTo(filer);
  }

  private static Iterable<ParameterSpec> parameters(Iterable<Parameter> parameters) {
    ImmutableList.Builder<ParameterSpec> builder = ImmutableList.builder();
    for (Parameter parameter : parameters) {
      builder.add(
          ParameterSpec.builder(TypeName.get(parameter.type()), parameter.name()).build());
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

  private static TypeName annotateIfPresent(
      TypeName typeName, Optional<AnnotationMirror> annotation) {
    if (annotation.isPresent()) {
      return typeName.annotated(AnnotationSpec.get(annotation.get()));
    }
    return typeName;
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
