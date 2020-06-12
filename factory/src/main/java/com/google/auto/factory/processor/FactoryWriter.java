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

import static com.google.auto.common.GeneratedAnnotationSpecs.generatedAnnotationSpec;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.io.IOException;
import java.util.Iterator;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;

final class FactoryWriter {

  private final Filer filer;
  private final Elements elements;
  private final SourceVersion sourceVersion;
  private final ImmutableSetMultimap<String, PackageAndClass> factoriesBeingCreated;

  FactoryWriter(
      ProcessingEnvironment processingEnv,
      ImmutableSetMultimap<String, PackageAndClass> factoriesBeingCreated) {
    this.filer = processingEnv.getFiler();
    this.elements = processingEnv.getElementUtils();
    this.sourceVersion = processingEnv.getSourceVersion();
    this.factoriesBeingCreated = factoriesBeingCreated;
  }

  private static final Joiner ARGUMENT_JOINER = Joiner.on(", ");

  void writeFactory(FactoryDescriptor descriptor)
      throws IOException {
    String factoryName = descriptor.name().className();
    TypeSpec.Builder factory =
        classBuilder(factoryName)
            .addOriginatingElement(descriptor.declaration().targetType());
    generatedAnnotationSpec(
            elements,
            sourceVersion,
            AutoFactoryProcessor.class,
            "https://github.com/google/auto/tree/master/factory")
        .ifPresent(factory::addAnnotation);
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

    ImmutableSet<TypeVariableName> factoryTypeVariables = getFactoryTypeVariables(descriptor);

    addFactoryTypeParameters(factory, factoryTypeVariables);
    addConstructorAndProviderFields(factory, descriptor);
    addFactoryMethods(factory, descriptor, factoryTypeVariables);
    addImplementationMethods(factory, descriptor);
    addCheckNotNullMethod(factory, descriptor);

    JavaFile.builder(descriptor.name().packageName(), factory.build())
        .skipJavaLangImports(true)
        .build()
        .writeTo(filer);
  }

  private static void addFactoryTypeParameters(
      TypeSpec.Builder factory, ImmutableSet<TypeVariableName> typeVariableNames) {
    factory.addTypeVariables(typeVariableNames);
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
      TypeName typeName = resolveTypeName(provider.key().type().get()).box();
      TypeName providerType = ParameterizedTypeName.get(ClassName.get(Provider.class), typeName);
      factory.addField(providerType, provider.name(), PRIVATE, FINAL);
      if (provider.key().qualifier().isPresent()) {
        // only qualify the constructor parameter
        providerType = providerType.annotated(AnnotationSpec.get(provider.key().qualifier().get()));
      }
      constructor.addParameter(providerType, provider.name());
      constructor.addStatement("this.$1L = checkNotNull($1L, $2L)", provider.name(), argumentIndex);
    }

    factory.addMethod(constructor.build());
  }

  private void addFactoryMethods(
      TypeSpec.Builder factory,
      FactoryDescriptor descriptor,
      ImmutableSet<TypeVariableName> factoryTypeVariables) {
    for (FactoryMethodDescriptor methodDescriptor : descriptor.methodDescriptors()) {
      MethodSpec.Builder method =
          MethodSpec.methodBuilder(methodDescriptor.name())
              .addTypeVariables(getMethodTypeVariables(methodDescriptor, factoryTypeVariables))
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
          if (parameter.isPrimitive()) {
            checkNotNull = false;
          }
        } else {
          ProviderField provider = descriptor.providers().get(parameter.key());
          argument = CodeBlock.of(provider.name());
          if (parameter.isProvider()) {
            // Providers are checked for nullness in the Factory's constructor.
            checkNotNull = false;
          } else {
            argument = CodeBlock.of("$L.get()", argument);
          }
        }
        if (checkNotNull) {
          argument = CodeBlock.of("checkNotNull($L, $L)", argument, argumentIndex);
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

  private void addImplementationMethods(
      TypeSpec.Builder factory, FactoryDescriptor descriptor) {
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

  /**
   * {@link ParameterSpec}s to match {@code parameters}. Note that the type of the {@link
   * ParameterSpec}s match {@link Parameter#type()} and not {@link Key#type()}.
   */
  private ImmutableList<ParameterSpec> parameters(Iterable<Parameter> parameters) {
    ImmutableList.Builder<ParameterSpec> builder = ImmutableList.builder();
    for (Parameter parameter : parameters) {
      ParameterSpec.Builder parameterBuilder =
          ParameterSpec.builder(resolveTypeName(parameter.type().get()), parameter.name());
      for (AnnotationMirror annotation :
          Iterables.concat(parameter.nullable().asSet(), parameter.key().qualifier().asSet())) {
        parameterBuilder.addAnnotation(AnnotationSpec.get(annotation));
      }
      builder.add(parameterBuilder.build());
    }
    return builder.build();
  }

  private static void addCheckNotNullMethod(
      TypeSpec.Builder factory, FactoryDescriptor descriptor) {
    if (shouldGenerateCheckNotNull(descriptor)) {
      TypeVariableName typeVariable = TypeVariableName.get("T");
      factory.addMethod(
          methodBuilder("checkNotNull")
              .addModifiers(PRIVATE, STATIC)
              .addTypeVariable(typeVariable)
              .returns(typeVariable)
              .addParameter(typeVariable, "reference")
              .addParameter(TypeName.INT, "argumentIndex")
              .beginControlFlow("if (reference == null)")
              .addStatement(
                  "throw new $T($S + argumentIndex)",
                  NullPointerException.class,
                  "@AutoFactory method argument is null but is not marked @Nullable. Argument "
                      + "index: ")
              .endControlFlow()
              .addStatement("return reference")
              .build());
    }
  }

  private static boolean shouldGenerateCheckNotNull(FactoryDescriptor descriptor) {
    if (!descriptor.providers().isEmpty()) {
      return true;
    }
    for (FactoryMethodDescriptor method : descriptor.methodDescriptors()) {
      for (Parameter parameter : method.creationParameters()) {
        if (!parameter.nullable().isPresent() && !parameter.type().get().getKind().isPrimitive()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns an appropriate {@code TypeName} for the given type. If the type is an
   * {@code ErrorType}, and if it is a simple-name reference to one of the {@code *Factory}
   * classes that we are going to generate, then we return its fully-qualified name. In every other
   * case we just return {@code TypeName.get(type)}. Specifically, if it is an {@code ErrorType}
   * referencing some other type, or referencing one of the classes we are going to generate but
   * using its fully-qualified name, then we leave it as-is. JavaPoet treats {@code TypeName.get(t)}
   * the same for {@code ErrorType} as for {@code DeclaredType}, which means that if this is a name
   * that will eventually be generated then the code we write that references the type will in fact
   * compile.
   *
   * <p>A simpler alternative would be to defer processing to a later round if we find an
   * {@code @AutoFactory} class that references undefined types, under the assumption that something
   * else will generate those types in the meanwhile. However, this would fail if for example
   * {@code @AutoFactory class Foo} has a constructor parameter of type {@code BarFactory} and
   * {@code @AutoFactory class Bar} has a constructor parameter of type {@code FooFactory}. We did
   * in fact find instances of this in Google's source base.
   */
  private TypeName resolveTypeName(TypeMirror type) {
    if (type.getKind() != TypeKind.ERROR) {
      return TypeName.get(type);
    }
    ImmutableSet<PackageAndClass> factoryNames = factoriesBeingCreated.get(type.toString());
    if (factoryNames.size() == 1) {
      PackageAndClass packageAndClass = Iterables.getOnlyElement(factoryNames);
      return ClassName.get(packageAndClass.packageName(), packageAndClass.className());
    }
    return TypeName.get(type);
  }

  private static ImmutableSet<TypeVariableName> getFactoryTypeVariables(
      FactoryDescriptor descriptor) {
    ImmutableSet.Builder<TypeVariableName> typeVariables = ImmutableSet.builder();
    for (ProviderField provider : descriptor.providers().values()) {
      typeVariables.addAll(getReferencedTypeParameterNames(provider.key().type().get()));
    }
    return typeVariables.build();
  }

  private static ImmutableSet<TypeVariableName> getMethodTypeVariables(
      FactoryMethodDescriptor methodDescriptor,
      ImmutableSet<TypeVariableName> factoryTypeVariables) {
    ImmutableSet.Builder<TypeVariableName> typeVariables = ImmutableSet.builder();
    typeVariables.addAll(getReferencedTypeParameterNames(methodDescriptor.returnType()));
    for (Parameter parameter : methodDescriptor.passedParameters()) {
      typeVariables.addAll(getReferencedTypeParameterNames(parameter.type().get()));
    }
    return Sets.difference(typeVariables.build(), factoryTypeVariables).immutableCopy();
  }

  private static ImmutableSet<TypeVariableName> getReferencedTypeParameterNames(TypeMirror type) {
    ImmutableSet.Builder<TypeVariableName> typeVariableNames = ImmutableSet.builder();
    for (TypeVariable typeVariable : TypeVariables.getReferencedTypeVariables(type)) {
      typeVariableNames.add(TypeVariableName.get(typeVariable));
    }
    return typeVariableNames.build();
  }
}
