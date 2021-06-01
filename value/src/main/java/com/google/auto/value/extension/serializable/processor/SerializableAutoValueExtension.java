/*
 * Copyright 2020 Google LLC
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
package com.google.auto.value.extension.serializable.processor;

import static com.google.auto.common.MoreStreams.toImmutableList;
import static com.google.auto.common.MoreStreams.toImmutableMap;
import static com.google.auto.value.extension.serializable.processor.ClassNames.SERIALIZABLE_AUTO_VALUE_NAME;
import static java.util.stream.Collectors.joining;

import com.google.auto.common.GeneratedAnnotationSpecs;
import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.auto.value.extension.serializable.serializer.SerializerFactoryLoader;
import com.google.auto.value.extension.serializable.serializer.interfaces.Serializer;
import com.google.auto.value.extension.serializable.serializer.interfaces.SerializerFactory;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * An AutoValue extension that enables classes with unserializable fields to be serializable.
 *
 * <p>For this extension to work:
 *
 * <ul>
 *   <li>The AutoValue class must implement {@link Serializable}.
 *   <li>Unserializable fields in the AutoValue class must be supported by a {@link
 *       com.google.auto.value.extension.serializable.serializer.interfaces.SerializerExtension}.
 * </ul>
 */
@AutoService(AutoValueExtension.class)
public final class SerializableAutoValueExtension extends AutoValueExtension {

  @Override
  public boolean applicable(Context context) {
    return hasSerializableInterface(context) && hasSerializableAutoValueAnnotation(context);
  }

  @Override
  public IncrementalExtensionType incrementalType(ProcessingEnvironment processingEnvironment) {
    return IncrementalExtensionType.ISOLATING;
  }

  @Override
  public String generateClass(
      Context context, String className, String classToExtend, boolean isFinal) {
    return new Generator(context, className, classToExtend, isFinal).generate();
  }

  private static final class Generator {
    private final Context context;
    private final String className;
    private final String classToExtend;
    private final boolean isFinal;
    private final ImmutableList<PropertyMirror> propertyMirrors;
    private final ImmutableList<TypeVariableName> typeVariableNames;
    private final ProxyGenerator proxyGenerator;

    Generator(Context context, String className, String classToExtend, boolean isFinal) {
      this.context = context;
      this.className = className;
      this.classToExtend = classToExtend;
      this.isFinal = isFinal;

      this.propertyMirrors =
          context.propertyTypes().entrySet().stream()
              .map(
                  entry ->
                      new PropertyMirror(
                          /* type= */ entry.getValue(),
                          /* name= */ entry.getKey(),
                          /* method= */ context
                              .properties()
                              .get(entry.getKey())
                              .getSimpleName()
                              .toString()))
              .collect(toImmutableList());
      this.typeVariableNames =
          context.autoValueClass().getTypeParameters().stream()
              .map(TypeVariableName::get)
              .collect(toImmutableList());

      TypeName classTypeName =
          getClassTypeName(ClassName.get(context.packageName(), className), typeVariableNames);
      this.proxyGenerator =
          new ProxyGenerator(
              classTypeName, typeVariableNames, propertyMirrors, buildSerializersMap());
    }

    private String generate() {
      ClassName superclass = ClassName.get(context.packageName(), classToExtend);
      Optional<AnnotationSpec> generatedAnnotationSpec =
          GeneratedAnnotationSpecs.generatedAnnotationSpec(
              context.processingEnvironment().getElementUtils(),
              context.processingEnvironment().getSourceVersion(),
              SerializableAutoValueExtension.class);

      TypeSpec.Builder subclass =
          TypeSpec.classBuilder(className)
              .superclass(getClassTypeName(superclass, typeVariableNames))
              .addTypeVariables(typeVariableNames)
              .addModifiers(isFinal ? Modifier.FINAL : Modifier.ABSTRACT)
              .addMethod(constructor())
              .addMethod(writeReplace())
              .addType(proxyGenerator.generate());
      generatedAnnotationSpec.ifPresent(subclass::addAnnotation);

      return JavaFile.builder(context.packageName(), subclass.build()).build().toString();
    }

    /** Creates a constructor that calls super with all the AutoValue fields. */
    private MethodSpec constructor() {
      MethodSpec.Builder constructor =
          MethodSpec.constructorBuilder()
              .addStatement(
                  "super($L)",
                  propertyMirrors.stream().map(PropertyMirror::getName).collect(joining(", ")));

      for (PropertyMirror propertyMirror : propertyMirrors) {
        constructor.addParameter(TypeName.get(propertyMirror.getType()), propertyMirror.getName());
      }

      return constructor.build();
    }

    /**
     * Creates an implementation of writeReplace that delegates serialization to its inner Proxy
     * class.
     */
    private MethodSpec writeReplace() {
      ImmutableList<CodeBlock> properties =
          propertyMirrors.stream()
              .map(propertyMirror -> CodeBlock.of("$L()", propertyMirror.getMethod()))
              .collect(toImmutableList());

      return MethodSpec.methodBuilder("writeReplace")
          .returns(Object.class)
          .addStatement(
              "return new $T($L)",
              getClassTypeName(
                  ClassName.get(
                      context.packageName(),
                      className,
                      SerializableAutoValueExtension.ProxyGenerator.PROXY_CLASS_NAME),
                  typeVariableNames),
              CodeBlock.join(properties, ", "))
          .build();
    }

    private ImmutableMap<Equivalence.Wrapper<TypeMirror>, Serializer> buildSerializersMap() {
      SerializerFactory factory =
          SerializerFactoryLoader.getFactory(context.processingEnvironment());
      return propertyMirrors.stream()
          .map(PropertyMirror::getType)
          .map(MoreTypes.equivalence()::wrap)
          .distinct()
          .collect(
              toImmutableMap(
                  Function.identity(), equivalence -> factory.getSerializer(equivalence.get())));
    }

    /** Adds type parameters to the given {@link ClassName}, if available. */
    private static TypeName getClassTypeName(
        ClassName className, List<TypeVariableName> typeVariableNames) {
      return typeVariableNames.isEmpty()
          ? className
          : ParameterizedTypeName.get(className, typeVariableNames.toArray(new TypeName[] {}));
    }
  }

  /** A generator of nested serializable Proxy classes. */
  private static final class ProxyGenerator {
    private static final String PROXY_CLASS_NAME = "Proxy$";

    private final TypeName outerClassTypeName;
    private final ImmutableList<TypeVariableName> typeVariableNames;
    private final ImmutableList<PropertyMirror> propertyMirrors;
    private final ImmutableMap<Equivalence.Wrapper<TypeMirror>, Serializer> serializersMap;

    ProxyGenerator(
        TypeName outerClassTypeName,
        ImmutableList<TypeVariableName> typeVariableNames,
        ImmutableList<PropertyMirror> propertyMirrors,
        ImmutableMap<Equivalence.Wrapper<TypeMirror>, Serializer> serializersMap) {
      this.outerClassTypeName = outerClassTypeName;
      this.typeVariableNames = typeVariableNames;
      this.propertyMirrors = propertyMirrors;
      this.serializersMap = serializersMap;
    }

    private TypeSpec generate() {
      TypeSpec.Builder proxy =
          TypeSpec.classBuilder(PROXY_CLASS_NAME)
              .addModifiers(Modifier.STATIC)
              .addTypeVariables(typeVariableNames)
              .addSuperinterface(Serializable.class)
              .addField(serialVersionUid())
              .addFields(properties())
              .addMethod(constructor())
              .addMethod(readResolve());

      return proxy.build();
    }

    private static FieldSpec serialVersionUid() {
      return FieldSpec.builder(
              long.class, "serialVersionUID", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
          .initializer("0")
          .build();
    }

    /** Maps each AutoValue property to a serializable type. */
    private List<FieldSpec> properties() {
      return propertyMirrors.stream()
          .map(
              propertyMirror ->
                  FieldSpec.builder(
                          TypeName.get(
                              serializersMap
                                  .get(MoreTypes.equivalence().wrap(propertyMirror.getType()))
                                  .proxyFieldType()),
                          propertyMirror.getName(),
                          Modifier.PRIVATE)
                      .build())
          .collect(toImmutableList());
    }

    /** Creates a constructor that converts the AutoValue's properties to serializable values. */
    private MethodSpec constructor() {
      MethodSpec.Builder constructor = MethodSpec.constructorBuilder();

      for (PropertyMirror propertyMirror : propertyMirrors) {
        Serializer serializer =
            serializersMap.get(MoreTypes.equivalence().wrap(propertyMirror.getType()));
        String name = propertyMirror.getName();

        constructor.addParameter(TypeName.get(propertyMirror.getType()), name);
        constructor.addStatement(
            CodeBlock.of("this.$L = $L", name, serializer.toProxy(CodeBlock.of(name))));
      }

      return constructor.build();
    }

    /**
     * Creates an implementation of {@code readResolve} that returns the serializable values in the
     * Proxy object back to their original types.
     */
    private MethodSpec readResolve() {
      return MethodSpec.methodBuilder("readResolve")
          .returns(Object.class)
          .addException(Exception.class)
          .addStatement(
              "return new $T($L)",
              outerClassTypeName,
              CodeBlock.join(
                  propertyMirrors.stream().map(this::resolve).collect(toImmutableList()), ", "))
          .build();
    }

    /** Maps a serializable type back to its original AutoValue property. */
    private CodeBlock resolve(PropertyMirror propertyMirror) {
      return serializersMap
          .get(MoreTypes.equivalence().wrap(propertyMirror.getType()))
          .fromProxy(CodeBlock.of(propertyMirror.getName()));
    }
  }

  private static boolean hasSerializableInterface(Context context) {
    final TypeMirror serializableTypeMirror =
        context
            .processingEnvironment()
            .getElementUtils()
            .getTypeElement(Serializable.class.getCanonicalName())
            .asType();

    return context
        .processingEnvironment()
        .getTypeUtils()
        .isAssignable(context.autoValueClass().asType(), serializableTypeMirror);
  }

  private static boolean hasSerializableAutoValueAnnotation(Context context) {
    return context.autoValueClass().getAnnotationMirrors().stream()
        .map(AnnotationMirror::getAnnotationType)
        .map(MoreTypes::asTypeElement)
        .map(TypeElement::getQualifiedName)
        .anyMatch(name -> name.contentEquals(SERIALIZABLE_AUTO_VALUE_NAME));
  }
}
