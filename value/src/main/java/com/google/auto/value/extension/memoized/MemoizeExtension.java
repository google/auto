/*
 * Copyright (C) 2016 Google, Inc.
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
package com.google.auto.value.extension.memoized;

import static com.google.auto.common.GeneratedAnnotationSpecs.generatedAnnotationSpec;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.VOLATILE;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;

/** An extension that implements the {@link Memoized} contract. */
@AutoService(AutoValueExtension.class)
public final class MemoizeExtension extends AutoValueExtension {
  private static final ImmutableSet<String> DO_NOT_PULL_DOWN_ANNOTATIONS =
      ImmutableSet.of(Override.class.getCanonicalName(), Memoized.class.getCanonicalName());

  private static final ClassName LAZY_INIT =
      ClassName.get("com.google.errorprone.annotations.concurrent", "LazyInit");

  private static final AnnotationSpec SUPPRESS_WARNINGS =
      AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "Immutable").build();

  @Override
  public boolean applicable(Context context) {
    return !memoizedMethods(context).isEmpty();
  }

  @Override
  public String generateClass(
      Context context, String className, String classToExtend, boolean isFinal) {
    return new Generator(context, className, classToExtend, isFinal).generate();
  }

  private static ImmutableSet<ExecutableElement> memoizedMethods(Context context) {
    ImmutableSet.Builder<ExecutableElement> memoizedMethods = ImmutableSet.builder();
    for (ExecutableElement method : methodsIn(context.autoValueClass().getEnclosedElements())) {
      if (isAnnotationPresent(method, Memoized.class)) {
        memoizedMethods.add(method);
      }
    }
    return memoizedMethods.build();
  }

  static final class Generator {
    private final Context context;
    private final String className;
    private final String classToExtend;
    private final boolean isFinal;
    private final Elements elements;
    private final Messager messager;
    private final Optional<AnnotationSpec> lazyInitAnnotation;
    private boolean hasErrors;

    Generator(Context context, String className, String classToExtend, boolean isFinal) {
      this.context = context;
      this.className = className;
      this.classToExtend = classToExtend;
      this.isFinal = isFinal;
      this.elements = context.processingEnvironment().getElementUtils();
      this.messager = context.processingEnvironment().getMessager();
      this.lazyInitAnnotation = getLazyInitAnnotation(elements);
    }

    String generate() {
      TypeSpec.Builder generated =
          classBuilder(className)
              .superclass(superType())
              .addTypeVariables(typeVariableNames())
              .addModifiers(isFinal ? FINAL : ABSTRACT)
              .addMethod(constructor());
      generatedAnnotationSpec(elements, MemoizeExtension.class).ifPresent(generated::addAnnotation);
      for (ExecutableElement method : memoizedMethods(context)) {
        MethodOverrider methodOverrider = new MethodOverrider(method);
        generated.addFields(methodOverrider.fields());
        generated.addMethod(methodOverrider.method());
      }
      if (hasErrors) {
        return null;
      }
      return JavaFile.builder(context.packageName(), generated.build()).build().toString();
    }

    private TypeName superType() {
      ClassName superType = ClassName.get(context.packageName(), classToExtend);
      ImmutableList<TypeVariableName> typeVariableNames = typeVariableNames();

      return typeVariableNames.isEmpty()
          ? superType
          : ParameterizedTypeName.get(superType, typeVariableNames.toArray(new TypeName[] {}));
    }

    private ImmutableList<TypeVariableName> typeVariableNames() {
      ImmutableList.Builder<TypeVariableName> typeVariableNamesBuilder = ImmutableList.builder();
      for (TypeParameterElement typeParameter : context.autoValueClass().getTypeParameters()) {
        typeVariableNamesBuilder.add(TypeVariableName.get(typeParameter));
      }
      return typeVariableNamesBuilder.build();
    }

    private MethodSpec constructor() {
      MethodSpec.Builder constructor = constructorBuilder();
      for (Map.Entry<String, ExecutableElement> property : context.properties().entrySet()) {
        constructor.addParameter(
            TypeName.get(property.getValue().getReturnType()), property.getKey() + "$");
      }
      List<String> namesWithDollars = new ArrayList<String>();
      for (String property : context.properties().keySet()) {
        namesWithDollars.add(property + "$");
      }
      constructor.addStatement("super($L)", Joiner.on(", ").join(namesWithDollars));
      return constructor.build();
    }

    /**
     * Determines the required fields and overriding method for a {@link Memoized @Memoized} method.
     */
    private final class MethodOverrider {
      private final ExecutableElement method;
      private final MethodSpec.Builder override;
      private final FieldSpec cacheField;
      private final ImmutableList.Builder<FieldSpec> fields = ImmutableList.builder();

      MethodOverrider(ExecutableElement method) {
        this.method = method;
        validate();
        cacheField =
            buildCacheField(
                TypeName.get(method.getReturnType()), method.getSimpleName().toString());
        fields.add(cacheField);
        override =
            methodBuilder(method.getSimpleName().toString())
                .addAnnotation(Override.class)
                .returns(cacheField.type)
                .addExceptions(
                    method.getThrownTypes().stream().map(TypeName::get).collect(toList()))
                .addModifiers(filter(method.getModifiers(), not(equalTo(ABSTRACT))));
        for (AnnotationMirror annotation : method.getAnnotationMirrors()) {
          AnnotationSpec annotationSpec = AnnotationSpec.get(annotation);
          if (pullDownMethodAnnotation(annotation)) {
            override.addAnnotation(annotationSpec);
          }
        }

        InitializationStrategy checkStrategy = strategy();
        fields.addAll(checkStrategy.additionalFields());
        override
            .beginControlFlow("if ($L)", checkStrategy.checkMemoized())
            .beginControlFlow("synchronized (this)")
            .beginControlFlow("if ($L)", checkStrategy.checkMemoized())
            .addStatement("$N = super.$L()", cacheField, method.getSimpleName())
            .addCode(checkStrategy.setMemoized())
            .endControlFlow()
            .endControlFlow()
            .endControlFlow()
            .addStatement("return $N", cacheField);
      }

      /** The fields that should be added to the subclass. */
      Iterable<FieldSpec> fields() {
        return fields.build();
      }

      /** The overriding method that should be added to the subclass. */
      MethodSpec method() {
        return override.build();
      }

      private void validate() {
        if (method.getReturnType().getKind().equals(VOID)) {
          printMessage(ERROR, "@Memoized methods cannot be void");
        }
        if (!method.getParameters().isEmpty()) {
          printMessage(ERROR, "@Memoized methods cannot have parameters");
        }
        checkIllegalModifier(PRIVATE);
        checkIllegalModifier(FINAL);
        checkIllegalModifier(STATIC);

        if (!overridesObjectMethod("hashCode") && !overridesObjectMethod("toString")) {
          checkIllegalModifier(ABSTRACT);
        }
      }

      private void checkIllegalModifier(Modifier modifier) {
        if (method.getModifiers().contains(modifier)) {
          printMessage(ERROR, "@Memoized methods cannot be " + modifier.toString());
        }
      }

      private void printMessage(Kind kind, String format, Object... args) {
        if (kind.equals(ERROR)) {
          hasErrors = true;
        }
        messager.printMessage(kind, String.format(format, args), method);
      }

      private boolean overridesObjectMethod(String methodName) {
        return elements.overrides(method, objectMethod(methodName), context.autoValueClass());
      }

      private ExecutableElement objectMethod(final String methodName) {
        TypeElement object = elements.getTypeElement(Object.class.getName());
        for (ExecutableElement method : methodsIn(object.getEnclosedElements())) {
          if (method.getSimpleName().contentEquals(methodName)) {
            return method;
          }
        }
        throw new IllegalArgumentException(
            String.format("No method in Object named \"%s\"", methodName));
      }

      private boolean pullDownMethodAnnotation(AnnotationMirror annotation) {
        return !DO_NOT_PULL_DOWN_ANNOTATIONS.contains(
            MoreElements.asType(annotation.getAnnotationType().asElement())
                .getQualifiedName()
                .toString());
      }

      /**
       * Builds a {@link FieldSpec} for use in property caching. Field will be {@code private
       * volatile} and have the given type and name. If the @LazyInit annotation is available it is
       * added as well.
       */
      private FieldSpec buildCacheField(TypeName type, String name) {
        FieldSpec.Builder builder = FieldSpec.builder(type, name, PRIVATE, VOLATILE);
        if (lazyInitAnnotation.isPresent()) {
          builder.addAnnotation(lazyInitAnnotation.get());
          builder.addAnnotation(SUPPRESS_WARNINGS);
        }
        return builder.build();
      }

      InitializationStrategy strategy() {
        if (method.getReturnType().getKind().isPrimitive()) {
          return new CheckBooleanField();
        }
        for (AnnotationMirror annotationMirror : method.getAnnotationMirrors()) {
          if (annotationMirror
              .getAnnotationType()
              .asElement()
              .getSimpleName()
              .contentEquals("Nullable")) {
            return new CheckBooleanField();
          }
        }
        return new NullMeansUninitialized();
      }

      private abstract class InitializationStrategy {

        abstract Iterable<FieldSpec> additionalFields();

        abstract CodeBlock checkMemoized();

        abstract CodeBlock setMemoized();
      }

      private final class NullMeansUninitialized extends InitializationStrategy {
        @Override
        Iterable<FieldSpec> additionalFields() {
          return ImmutableList.of();
        }

        @Override
        CodeBlock checkMemoized() {
          return CodeBlock.of("$N == null", cacheField);
        }

        @Override
        CodeBlock setMemoized() {
          return CodeBlock.builder()
              .beginControlFlow("if ($N == null)", cacheField)
              .addStatement(
                  "throw new NullPointerException($S)",
                  method.getSimpleName() + "() cannot return null")
              .endControlFlow()
              .build();
        }
      }

      private final class CheckBooleanField extends InitializationStrategy {

        private final FieldSpec field =
            buildCacheField(TypeName.BOOLEAN, method.getSimpleName() + "$Memoized");

        @Override
        Iterable<FieldSpec> additionalFields() {
          return ImmutableList.of(field);
        }

        @Override
        CodeBlock checkMemoized() {
          return CodeBlock.of("!$N", field);
        }

        @Override
        CodeBlock setMemoized() {
          return CodeBlock.builder().addStatement("$N = true", field).build();
        }
      }
    }
  }

  /** Returns the errorprone {@code @LazyInit} annotation if it is found on the classpath. */
  private static Optional<AnnotationSpec> getLazyInitAnnotation(Elements elements) {
    if (elements.getTypeElement(LAZY_INIT.toString()) == null) {
      return Optional.empty();
    }
    return Optional.of(AnnotationSpec.builder(LAZY_INIT).build());
  }
}
