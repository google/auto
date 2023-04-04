/*
 * Copyright 2021 Google LLC
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

package com.google.auto.value.extension.toprettystring.processor;

import static com.google.auto.common.GeneratedAnnotationSpecs.generatedAnnotationSpec;
import static com.google.auto.common.MoreStreams.toImmutableList;
import static com.google.auto.common.MoreStreams.toImmutableMap;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;

import com.google.auto.value.extension.AutoValueExtension;
import com.google.auto.value.extension.AutoValueExtension.Context;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/**
 * A factory for {@link TypeSpec}s used in {@link AutoValueExtension} implementations.
 *
 * <p>This is copied from {@link
 * com.google.auto.value.extension.memoized.processor.MemoizeExtension} until we find a better
 * location to consolidate the code.
 */
final class ExtensionClassTypeSpecBuilder {

  private final Context context;
  private final String className;
  private final String classToExtend;
  private final boolean isFinal;
  private final Elements elements;
  private final SourceVersion sourceVersion;

  private ExtensionClassTypeSpecBuilder(
      Context context, String className, String classToExtend, boolean isFinal) {
    this.context = context;
    this.className = className;
    this.classToExtend = classToExtend;
    this.isFinal = isFinal;
    this.elements = context.processingEnvironment().getElementUtils();
    this.sourceVersion = context.processingEnvironment().getSourceVersion();
  }

  static TypeSpec.Builder extensionClassTypeSpecBuilder(
      Context context, String className, String classToExtend, boolean isFinal) {
    return new ExtensionClassTypeSpecBuilder(context, className, classToExtend, isFinal)
        .extensionClassBuilder();
  }

  TypeSpec.Builder extensionClassBuilder() {
    TypeSpec.Builder builder =
        classBuilder(className)
            .superclass(superType())
            .addAnnotations(
                context.classAnnotationsToCopy(context.autoValueClass()).stream()
                    .map(AnnotationSpec::get)
                    .collect(toImmutableList()))
            .addTypeVariables(annotatedTypeVariableNames())
            .addModifiers(isFinal ? FINAL : ABSTRACT)
            .addMethod(constructor());
    generatedAnnotationSpec(elements, sourceVersion, ToPrettyStringExtension.class)
        .ifPresent(builder::addAnnotation);
    return builder;
  }

  private TypeName superType() {
    ClassName superType = ClassName.get(context.packageName(), classToExtend);
    ImmutableList<TypeVariableName> typeVariableNames = typeVariableNames();

    return typeVariableNames.isEmpty()
        ? superType
        : ParameterizedTypeName.get(superType, typeVariableNames.toArray(new TypeName[] {}));
  }

  private ImmutableList<TypeVariableName> typeVariableNames() {
    return context.autoValueClass().getTypeParameters().stream()
        .map(TypeVariableName::get)
        .collect(toImmutableList());
  }

  private ImmutableList<TypeVariableName> annotatedTypeVariableNames() {
    return context.autoValueClass().getTypeParameters().stream()
        .map(
            p ->
                TypeVariableName.get(p)
                    .annotated(
                        p.getAnnotationMirrors().stream()
                            .map(AnnotationSpec::get)
                            .collect(toImmutableList())))
        .collect(toImmutableList());
  }

  private MethodSpec constructor() {
    MethodSpec.Builder constructor = constructorBuilder();
    // TODO(b/35944623): Replace this with a standard way of avoiding keywords.
    Set<String> propertyNames = context.properties().keySet();
    ImmutableMap<String, String> parameterNames =
        propertyNames.stream()
            .collect(toImmutableMap(name -> name, name -> generateIdentifier(name, propertyNames)));
    context
        .propertyTypes()
        .forEach(
            (name, type) ->
                constructor.addParameter(annotatedType(type), parameterNames.get(name)));
    String superParams =
        context.properties().keySet().stream().map(parameterNames::get).collect(joining(", "));
    constructor.addStatement("super($L)", superParams);
    return constructor.build();
  }

  private static String generateIdentifier(String name, Set<String> existingNames) {
    if (!SourceVersion.isKeyword(name)) {
      return name;
    }
    for (int i = 0; ; i++) {
      String newName = name + i;
      if (!existingNames.contains(newName)) {
        return newName;
      }
    }
  }

  /** Translate a {@link TypeMirror} into a {@link TypeName}, including type annotations. */
  private static TypeName annotatedType(TypeMirror type) {
    List<AnnotationSpec> annotations =
        type.getAnnotationMirrors().stream().map(AnnotationSpec::get).collect(toList());

    return TypeName.get(type).annotated(annotations);
  }
}
