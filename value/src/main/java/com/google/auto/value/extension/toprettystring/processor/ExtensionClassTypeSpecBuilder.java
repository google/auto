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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.GeneratedAnnotationSpecs.generatedAnnotationSpec;
import static com.google.auto.common.MoreElements.getPackage;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreStreams.toImmutableList;
import static com.google.auto.common.MoreStreams.toImmutableSet;
import static com.google.auto.value.extension.toprettystring.processor.Annotations.getAnnotationMirror;
import static com.google.common.collect.Sets.union;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;

import com.google.auto.common.MoreTypes;
import com.google.auto.common.Visibility;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.auto.value.extension.AutoValueExtension.Context;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.lang.annotation.Inherited;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A factory for {@link TypeSpec}s used in {@link AutoValueExtension} implementations.
 *
 * <p>This is copied from {@link
 * com.google.auto.value.extension.memoized.processor.MemoizeExtension} until we find a better
 * location to consolidate the code.
 */
final class ExtensionClassTypeSpecBuilder {
  private static final String AUTO_VALUE_PACKAGE_NAME = "com.google.auto.value.";
  private static final String AUTO_VALUE_NAME = AUTO_VALUE_PACKAGE_NAME + "AutoValue";
  private static final String COPY_ANNOTATIONS_NAME = AUTO_VALUE_NAME + ".CopyAnnotations";

  private final Context context;
  private final String className;
  private final String classToExtend;
  private final boolean isFinal;
  private final Types types;
  private final Elements elements;
  private final SourceVersion sourceVersion;

  private ExtensionClassTypeSpecBuilder(
      Context context, String className, String classToExtend, boolean isFinal) {
    this.context = context;
    this.className = className;
    this.classToExtend = classToExtend;
    this.isFinal = isFinal;
    this.types = context.processingEnvironment().getTypeUtils();
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
            .addAnnotations(copiedClassAnnotations(context.autoValueClass()))
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
    context
        .propertyTypes()
        .forEach((name, type) -> constructor.addParameter(annotatedType(type), name + "$"));
    String superParams =
        context.properties().keySet().stream().map(n -> n + "$").collect(joining(", "));
    constructor.addStatement("super($L)", superParams);
    return constructor.build();
  }

  /**
   * True if the given class name is in the com.google.auto.value package or a subpackage. False if
   * the class name contains {@code Test}, since many AutoValue tests under com.google.auto.value
   * define their own annotations.
   */
  // TODO(b/122509249): Move code copied from com.google.auto.value.processor to auto-common.
  private boolean isInAutoValuePackage(String className) {
    return className.startsWith(AUTO_VALUE_PACKAGE_NAME) && !className.contains("Test");
  }

  /**
   * Returns the fully-qualified name of an annotation-mirror, e.g.
   * "com.google.auto.value.AutoValue".
   */
  // TODO(b/122509249): Move code copied from com.google.auto.value.processor to auto-common.
  private static String getAnnotationFqName(AnnotationMirror annotation) {
    return ((QualifiedNameable) annotation.getAnnotationType().asElement())
        .getQualifiedName()
        .toString();
  }

  // TODO(b/122509249): Move code copied from com.google.auto.value.processor to auto-common.
  private boolean annotationVisibleFrom(AnnotationMirror annotation, Element from) {
    Element annotationElement = annotation.getAnnotationType().asElement();
    Visibility visibility = Visibility.effectiveVisibilityOfElement(annotationElement);
    switch (visibility) {
      case PUBLIC:
        return true;
      case PROTECTED:
        // If the annotation is protected, it must be inside another class, call it C. If our
        // @AutoValue class is Foo then, for the annotation to be visible, either Foo must be in
        // the same package as C or Foo must be a subclass of C. If the annotation is visible from
        // Foo then it is also visible from our generated subclass AutoValue_Foo.
        // The protected case only applies to method annotations. An annotation on the
        // AutoValue_Foo class itself can't be protected, even if AutoValue_Foo ultimately
        // inherits from the class that defines the annotation. The JLS says "Access is permitted
        // only within the body of a subclass":
        // https://docs.oracle.com/javase/specs/jls/se8/html/jls-6.html#jls-6.6.2.1
        // AutoValue_Foo is a top-level class, so an annotation on it cannot be in the body of a
        // subclass of anything.
        return getPackage(annotationElement).equals(getPackage(from))
            || types.isSubtype(from.asType(), annotationElement.getEnclosingElement().asType());
      case DEFAULT:
        return getPackage(annotationElement).equals(getPackage(from));
      default:
        return false;
    }
  }

  /** Implements the semantics of {@code AutoValue.CopyAnnotations}; see its javadoc. */
  // TODO(b/122509249): Move code copied from com.google.auto.value.processor to auto-common.
  private ImmutableList<AnnotationMirror> annotationsToCopy(
      Element autoValueType, Element typeOrMethod, Set<String> excludedAnnotations) {
    ImmutableList.Builder<AnnotationMirror> result = ImmutableList.builder();
    for (AnnotationMirror annotation : typeOrMethod.getAnnotationMirrors()) {
      String annotationFqName = getAnnotationFqName(annotation);
      // To be included, the annotation should not be in com.google.auto.value,
      // and it should not be in the excludedAnnotations set.
      if (!isInAutoValuePackage(annotationFqName)
          && !excludedAnnotations.contains(annotationFqName)
          && annotationVisibleFrom(annotation, autoValueType)) {
        result.add(annotation);
      }
    }

    return result.build();
  }

  /** Implements the semantics of {@code AutoValue.CopyAnnotations}; see its javadoc. */
  // TODO(b/122509249): Move code copied from com.google.auto.value.processor to auto-common.
  private ImmutableList<AnnotationSpec> copyAnnotations(
      Element autoValueType, Element typeOrMethod, Set<String> excludedAnnotations) {
    ImmutableList<AnnotationMirror> annotationsToCopy =
        annotationsToCopy(autoValueType, typeOrMethod, excludedAnnotations);
    return annotationsToCopy.stream().map(AnnotationSpec::get).collect(toImmutableList());
  }

  // TODO(b/122509249): Move code copied from com.google.auto.value.processor to auto-common.
  private static boolean hasAnnotationMirror(Element element, String annotationName) {
    return getAnnotationMirror(element, annotationName).isPresent();
  }

  /**
   * Returns the contents of the {@code AutoValue.CopyAnnotations.exclude} element, as a set of
   * {@code TypeMirror} where each type is an annotation type.
   */
  // TODO(b/122509249): Move code copied from com.google.auto.value.processor to auto-common.
  private ImmutableSet<TypeMirror> getExcludedAnnotationTypes(Element element) {
    Optional<AnnotationMirror> maybeAnnotation =
        getAnnotationMirror(element, COPY_ANNOTATIONS_NAME);
    if (!maybeAnnotation.isPresent()) {
      return ImmutableSet.of();
    }

    @SuppressWarnings("unchecked")
    List<AnnotationValue> excludedClasses =
        (List<AnnotationValue>) getAnnotationValue(maybeAnnotation.get(), "exclude").getValue();
    return excludedClasses.stream()
        .map(
            annotationValue ->
                MoreTypes.equivalence().wrap((TypeMirror) annotationValue.getValue()))
        // TODO(b/122509249): Move TypeMirrorSet to common package instead of doing this.
        .distinct()
        .map(Wrapper::get)
        .collect(toImmutableSet());
  }

  /**
   * Returns the contents of the {@code AutoValue.CopyAnnotations.exclude} element, as a set of
   * strings that are fully-qualified class names.
   */
  // TODO(b/122509249): Move code copied from com.google.auto.value.processor to auto-common.
  private Set<String> getExcludedAnnotationClassNames(Element element) {
    return getExcludedAnnotationTypes(element).stream()
        .map(MoreTypes::asTypeElement)
        .map(typeElement -> typeElement.getQualifiedName().toString())
        .collect(toSet());
  }

  // TODO(b/122509249): Move code copied from com.google.auto.value.processor to auto-common.
  private static Set<String> getAnnotationsMarkedWithInherited(Element element) {
    return element.getAnnotationMirrors().stream()
        .filter(a -> isAnnotationPresent(a.getAnnotationType().asElement(), Inherited.class))
        .map(ExtensionClassTypeSpecBuilder::getAnnotationFqName)
        .collect(toSet());
  }

  private ImmutableList<AnnotationSpec> copiedClassAnnotations(TypeElement type) {
    // Only copy annotations from a class if it has @AutoValue.CopyAnnotations.
    if (hasAnnotationMirror(type, COPY_ANNOTATIONS_NAME)) {
      Set<String> excludedAnnotations =
          union(getExcludedAnnotationClassNames(type), getAnnotationsMarkedWithInherited(type));

      return copyAnnotations(type, type, excludedAnnotations);
    } else {
      return ImmutableList.of();
    }
  }

  /** Translate a {@link TypeMirror} into a {@link TypeName}, including type annotations. */
  private static TypeName annotatedType(TypeMirror type) {
    List<AnnotationSpec> annotations =
        type.getAnnotationMirrors().stream().map(AnnotationSpec::get).collect(toList());

    return TypeName.get(type).annotated(annotations);
  }
}
