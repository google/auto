/*
 * Copyright 2016 Google LLC
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
package com.google.auto.value.extension.memoized.processor;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.GeneratedAnnotationSpecs.generatedAnnotationSpec;
import static com.google.auto.common.MoreElements.getPackage;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreStreams.toImmutableList;
import static com.google.auto.common.MoreStreams.toImmutableSet;
import static com.google.auto.value.extension.memoized.processor.ClassNames.MEMOIZED_NAME;
import static com.google.auto.value.extension.memoized.processor.MemoizedValidator.getAnnotationMirror;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Sets.union;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.TRANSIENT;
import static javax.lang.model.element.Modifier.VOLATILE;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.common.Visibility;
import com.google.auto.service.AutoService;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.FormatMethod;
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
import java.lang.annotation.Inherited;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

/**
 * An extension that implements the {@link com.google.auto.value.extension.memoized.Memoized}
 * contract.
 */
@AutoService(AutoValueExtension.class)
public final class MemoizeExtension extends AutoValueExtension {
  private static final ImmutableSet<String> DO_NOT_PULL_DOWN_ANNOTATIONS =
      ImmutableSet.of(Override.class.getCanonicalName(), MEMOIZED_NAME);

  // TODO(b/122509249): Move code copied from com.google.auto.value.processor to auto-common.
  private static final String AUTO_VALUE_PACKAGE_NAME = "com.google.auto.value.";
  private static final String AUTO_VALUE_NAME = AUTO_VALUE_PACKAGE_NAME + "AutoValue";
  private static final String COPY_ANNOTATIONS_NAME = AUTO_VALUE_NAME + ".CopyAnnotations";

  // Maven is configured to shade (rewrite) com.google packages to prevent dependency conflicts.
  // Split up the package here with a call to concat to prevent Maven from finding and rewriting it,
  // so that this will be able to find the LazyInit annotation if it's on the classpath.
  private static final ClassName LAZY_INIT =
      ClassName.get("com".concat(".google.errorprone.annotations.concurrent"), "LazyInit");

  private static final AnnotationSpec SUPPRESS_WARNINGS =
      AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "Immutable").build();

  @Override
  public IncrementalExtensionType incrementalType(ProcessingEnvironment processingEnvironment) {
    return IncrementalExtensionType.ISOLATING;
  }

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
    return methodsIn(context.autoValueClass().getEnclosedElements()).stream()
        .filter(m -> getAnnotationMirror(m, MEMOIZED_NAME).isPresent())
        .collect(toImmutableSet());
  }

  static final class Generator {
    private final Context context;
    private final String className;
    private final String classToExtend;
    private final boolean isFinal;
    private final Elements elements;
    private final Types types;
    private final SourceVersion sourceVersion;
    private final Messager messager;
    private final Optional<AnnotationSpec> lazyInitAnnotation;
    private boolean hasErrors;

    Generator(Context context, String className, String classToExtend, boolean isFinal) {
      this.context = context;
      this.className = className;
      this.classToExtend = classToExtend;
      this.isFinal = isFinal;
      this.elements = context.processingEnvironment().getElementUtils();
      this.types = context.processingEnvironment().getTypeUtils();
      this.sourceVersion = context.processingEnvironment().getSourceVersion();
      this.messager = context.processingEnvironment().getMessager();
      this.lazyInitAnnotation = getLazyInitAnnotation(elements);
    }

    String generate() {
      TypeSpec.Builder generated =
          classBuilder(className)
              .superclass(superType())
              .addAnnotations(copiedClassAnnotations(context.autoValueClass()))
              .addTypeVariables(annotatedTypeVariableNames())
              .addModifiers(isFinal ? FINAL : ABSTRACT)
              .addMethod(constructor());
      generatedAnnotationSpec(elements, sourceVersion, MemoizeExtension.class)
          .ifPresent(generated::addAnnotation);
      for (ExecutableElement method : memoizedMethods(context)) {
        MethodOverrider methodOverrider = new MethodOverrider(method);
        generated.addFields(methodOverrider.fields());
        generated.addMethod(methodOverrider.method());
      }
      if (isHashCodeMemoized() && !isEqualsFinal()) {
        generated.addMethod(equalsWithHashCodeCheck());
      }
      if (hasErrors) {
        return null;
      }
      return JavaFile.builder(context.packageName(), generated.build()).build().toString();
    }

    // LINT.IfChange
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

    private boolean isHashCodeMemoized() {
      return memoizedMethods(context).stream()
          .anyMatch(method -> method.getSimpleName().contentEquals("hashCode"));
    }

    private boolean isEqualsFinal() {
      TypeMirror objectType = elements.getTypeElement(Object.class.getCanonicalName()).asType();
      ExecutableElement equals =
          MoreElements.getLocalAndInheritedMethods(context.autoValueClass(), types, elements)
              .stream()
              .filter(method -> method.getSimpleName().contentEquals("equals"))
              .filter(method -> method.getParameters().size() == 1)
              .filter(
                  method ->
                      types.isSameType(getOnlyElement(method.getParameters()).asType(), objectType))
              .findFirst()
              .get();
      return equals.getModifiers().contains(FINAL);
    }

    private MethodSpec equalsWithHashCodeCheck() {
      return methodBuilder("equals")
          .addModifiers(PUBLIC)
          .returns(TypeName.BOOLEAN)
          .addAnnotation(Override.class)
          .addParameter(TypeName.OBJECT, "that")
          .beginControlFlow("if (this == that)")
          .addStatement("return true")
          .endControlFlow()
          .addStatement(
              "return that instanceof $N "
                  + "&& this.hashCode() == that.hashCode() "
                  + "&& super.equals(that)",
              className)
          .build();
    }

    // LINT.IfChange
    /**
     * True if the given class name is in the com.google.auto.value package or a subpackage. False
     * if the class name contains {@code Test}, since many AutoValue tests under
     * com.google.auto.value define their own annotations.
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
          .map(Generator::getAnnotationFqName)
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

    /**
     * Determines the required fields and overriding method for a {@link
     * com.google.auto.value.extension.memoized.Memoized @Memoized} method.
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
                annotatedType(method.getReturnType()), method.getSimpleName().toString());
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
          printMessage(ERROR, "@Memoized methods cannot be %s", modifier.toString());
        }
      }

      @FormatMethod
      private void printMessage(Kind kind, String format, Object... args) {
        if (kind.equals(ERROR)) {
          hasErrors = true;
        }
        messager.printMessage(kind, String.format(format, args), method);
      }

      private boolean overridesObjectMethod(String methodName) {
        return elements.overrides(method, objectMethod(methodName), context.autoValueClass());
      }

      private ExecutableElement objectMethod(String methodName) {
        TypeElement object = elements.getTypeElement(Object.class.getName());
        return methodsIn(object.getEnclosedElements()).stream()
            .filter(m -> m.getSimpleName().contentEquals(methodName))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        String.format("No method in Object named \"%s\"", methodName)));
      }

      private boolean pullDownMethodAnnotation(AnnotationMirror annotation) {
        return !DO_NOT_PULL_DOWN_ANNOTATIONS.contains(
            MoreElements.asType(annotation.getAnnotationType().asElement())
                .getQualifiedName()
                .toString());
      }

      /**
       * Builds a {@link FieldSpec} for use in property caching. Field will be {@code private
       * transient volatile} and have the given type and name. If the @LazyInit annotation is
       * available it is added as well.
       */
      private FieldSpec buildCacheField(TypeName type, String name) {
        FieldSpec.Builder builder = FieldSpec.builder(type, name, PRIVATE, TRANSIENT, VOLATILE);
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
        if (containsNullable(method.getAnnotationMirrors())
            || containsNullable(method.getReturnType().getAnnotationMirrors())) {
          return new CheckBooleanField();
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

  /** True if one of the given annotations is {@code @Nullable} in any package. */
  private static boolean containsNullable(List<? extends AnnotationMirror> annotations) {
    return annotations.stream()
        .map(a -> a.getAnnotationType().asElement().getSimpleName())
        .anyMatch(n -> n.contentEquals("Nullable"));
  }

  /** Translate a {@link TypeMirror} into a {@link TypeName}, including type annotations. */
  private static TypeName annotatedType(TypeMirror type) {
    List<AnnotationSpec> annotations =
        type.getAnnotationMirrors().stream().map(AnnotationSpec::get).collect(toList());
    return TypeName.get(type).annotated(annotations);
  }
}
