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
package com.google.auto.value.processor;

import static java.util.stream.Collectors.joining;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Classifies methods inside builder types that return builders for properties. For example, if
 * {@code @AutoValue} class Foo has a method {@code ImmutableList<String> bar()} then Foo.Builder
 * can have a method {@code ImmutableList.Builder<String> barBuilder()}. This class checks that a
 * method like {@code barBuilder()} follows the rules, and if so constructs a {@link
 * PropertyBuilder} instance with information about {@code barBuilder}.
 *
 * @author Éamonn McManus
 */
class PropertyBuilderClassifier {
    private final ErrorReporter errorReporter;
    private final Types typeUtils;
    private final Elements elementUtils;
    private final BuilderMethodClassifier builderMethodClassifier;
    private final ImmutableBiMap<ExecutableElement, String> getterToPropertyName;
    private final ImmutableMap<ExecutableElement, TypeMirror> getterToPropertyType;
    private final EclipseHack eclipseHack;

    PropertyBuilderClassifier(
            ErrorReporter errorReporter,
            Types typeUtils,
            Elements elementUtils,
            BuilderMethodClassifier builderMethodClassifier,
            ImmutableBiMap<ExecutableElement, String> getterToPropertyName,
            ImmutableMap<ExecutableElement, TypeMirror> getterToPropertyType,
            EclipseHack eclipseHack) {
        this.errorReporter = errorReporter;
        this.typeUtils = typeUtils;
        this.elementUtils = elementUtils;
        this.builderMethodClassifier = builderMethodClassifier;
        this.getterToPropertyName = getterToPropertyName;
        this.getterToPropertyType = getterToPropertyType;
        this.eclipseHack = eclipseHack;
    }

    /**
     * Information about a property builder, referenced from the autovalue.vm template. A property
     * called bar (defined by a method bar() or getBar()) can have a property builder called
     * barBuilder(). For example, if {@code bar()} returns {@code ImmutableSet<String>} then {@code
     * barBuilder()} might return {@code ImmutableSet.Builder<String>}.
     */
    public static class PropertyBuilder {
        private final ExecutableElement propertyBuilderMethod;
        private final String name;
        private final String builderType;
        private final TypeMirror builderTypeMirror;
        private final String initializer;
        private final String beforeInitDefault;
        private final String initDefault;
        private final String builtToBuilder;
        private final String copyAll;

        PropertyBuilder(
                ExecutableElement propertyBuilderMethod,
                String builderType,
                TypeMirror builderTypeMirror,
                String initializer,
                String beforeInitDefault,
                String initDefault,
                String builtToBuilder,
                String copyAll) {
            this.propertyBuilderMethod = propertyBuilderMethod;
            this.name = propertyBuilderMethod.getSimpleName() + "$";
            this.builderType = builderType;
            this.builderTypeMirror = builderTypeMirror;
            this.initializer = initializer;
            this.beforeInitDefault = beforeInitDefault;
            this.initDefault = initDefault;
            this.builtToBuilder = builtToBuilder;
            this.copyAll = copyAll;
        }

    /** The property builder method, for example {@code barBuilder()}. */
        public ExecutableElement getPropertyBuilderMethod() {
            return propertyBuilderMethod;
        }

        /**
         * The property builder method parameters, for example {@code Comparator<T> comparator}
         */
        public String getPropertyBuilderMethodParameters() {
            return propertyBuilderMethod.getParameters().stream()
                    .map(parameter -> TypeEncoder.encode(parameter.asType()) + " " + parameter.getSimpleName())
                    .collect(joining(", "));
        }

        public String getAccess() {
            return SimpleMethod.access(propertyBuilderMethod);
        }

    /** The name of the field to hold this builder. */
        public String getName() {
            return name;
        }

    /** The type of the builder, for example {@code ImmutableSet.Builder<String>}. */
        public String getBuilderType() {
            return builderType;
        }

        TypeMirror getBuilderTypeMirror() {
            return builderTypeMirror;
        }

    /** An initializer for the builder field, for example {@code ImmutableSet.builder()}. */
        public String getInitializer() {
            return initializer;
        }

        /**
         * An empty string, or a complete statement to be included before the expression returned by
         * {@link #getInitDefault()}.
         */
        public String getBeforeInitDefault() {
            return beforeInitDefault;
        }

        /**
         * An expression to return a default instance of the type that this builder builds. For example,
         * if this is an {@code ImmutableList<String>} then the method {@code ImmutableList.of()} will
         * correctly return an empty {@code ImmutableList<String>}, assuming the appropriate context for
         * type inference. The expression here can assume that the statement from {@link
         * #getBeforeInitDefault} has preceded it.
         */
        public String getInitDefault() {
            return initDefault;
        }

        /**
         * A method to convert the built type back into a builder. Unfortunately Guava collections don't
         * have this (you can't say {@code myImmutableMap.toBuilder()}), but for other types such as
         * {@code @AutoValue} types this is {@code toBuilder()}.
         */
        public String getBuiltToBuilder() {
            return builtToBuilder;
        }

        /**
         * The method to copy another collection into this builder. It is {@code addAll} for
         * one-dimensional collections like {@code ImmutableList} and {@code ImmutableSet}, and it is
         * {@code putAll} for two-dimensional collections like {@code ImmutableMap} and {@code
         * ImmutableTable}.
         */
        public String getCopyAll() {
            return copyAll;
        }
    }

    // Our @AutoValue class `Foo` has a property `Bar bar()` or `Bar getBar()` and we've encountered
    // a builder method like `BarBuilder barBuilder()`. Here `BarBuilder` can have any name (its name
    // doesn't have to be the name of `Bar` with `Builder` stuck on the end), but `barBuilder()` does
    // have to be the name of the property with `Builder` stuck on the end. The requirements for the
    // `BarBuilder` type are:
    // (1) It must have an instance method called `build()` that returns `Bar`. If the type of
    //     `bar()` is `Bar<String>` then the type of `build()` must be `Bar<String>`.
    // (2) `BarBuilder` must have a public no-arg constructor, or `Bar` must have a static method
    //     `naturalOrder(), `builder()`, or `newBuilder()` that returns `BarBuilder`. The
    //     `naturalOrder()` case is specifically for ImmutableSortedSet and ImmutableSortedMap.
    // (3) If `Foo` has a `toBuilder()` method, or if we have both `barBuilder()` and `setBar(Bar)`
    //     methods, then `Bar` must have an instance method `BarBuilder toBuilder()`, or `BarBuilder`
    //     must have an `addAll` or `putAll` method that accepts an argument of type `Bar`.
    //
    // This method outputs an error and returns Optional.empty() if the barBuilder() method has a
    // problem.
    Optional<PropertyBuilder> makePropertyBuilder(ExecutableElement method, String property) {
        TypeMirror barBuilderTypeMirror = builderMethodClassifier.builderMethodReturnType(method);
        if (barBuilderTypeMirror.getKind() != TypeKind.DECLARED) {
            errorReporter.reportError(
                    method,
                    "[AutoValueOddBuilderMethod] Method looks like a property builder, but its return type"
                            + " is not a class or interface");
            return Optional.empty();
        }
        DeclaredType barBuilderDeclaredType = MoreTypes.asDeclared(barBuilderTypeMirror);
        TypeElement barBuilderTypeElement = MoreTypes.asTypeElement(barBuilderTypeMirror);
        Map<String, ExecutableElement> barBuilderNoArgMethods = noArgMethodsOf(barBuilderTypeElement);

        ExecutableElement barGetter = getterToPropertyName.inverse().get(property);
        TypeMirror barTypeMirror = getterToPropertyType.get(barGetter);
        if (barTypeMirror.getKind() != TypeKind.DECLARED) {
            errorReporter.reportError(
                    method,
                    "[AutoValueBadBuilderMethod] Method looks like a property builder, but the type of"
                            + " property %s is not a class or interface",
                    property);
            return Optional.empty();
        }
        if (isNullable(barGetter)) {
            errorReporter.reportError(
                    barGetter,
                    "[AutoValueNullBuilder] Property %s has a property builder so it cannot be @Nullable",
                    property);
        }
        TypeElement barTypeElement = MoreTypes.asTypeElement(barTypeMirror);
        Map<String, ExecutableElement> barNoArgMethods = noArgMethodsOf(barTypeElement);

        // Condition (1), must have build() method returning Bar.
        ExecutableElement build = barBuilderNoArgMethods.get("build");
        if (build == null || build.getModifiers().contains(Modifier.STATIC)) {
            errorReporter.reportError(
                    method,
                    "[AutoValueBuilderNotBuildable] Method looks like a property builder, but it returns %s"
                            + " which does not have a non-static build() method",
                    barBuilderTypeElement);
            return Optional.empty();
        }

        // We've determined that `BarBuilder` has a method `build()`. But it must return `Bar`.
        // And if the type of `bar()` is Bar<String> then `BarBuilder.build()` must return Bar<String>.
        TypeMirror buildType = eclipseHack.methodReturnType(build, barBuilderDeclaredType);
        if (!MoreTypes.equivalence().equivalent(barTypeMirror, buildType)) {
            errorReporter.reportError(
                    method,
                    "[AutoValueBuilderWrongType] Property builder for %s has type %s whose build() method"
                            + " returns %s instead of %s",
                    property,
                    barBuilderTypeElement,
                    buildType,
                    barTypeMirror);
            return Optional.empty();
        }

        Optional<ExecutableElement> maybeBuilderMaker;
        if (method.getParameters().isEmpty()) {
            maybeBuilderMaker = builderMaker(barNoArgMethods, barBuilderTypeElement);
        } else {
            maybeBuilderMaker = withComparatorBuilderMaker(barTypeElement, barBuilderTypeElement);
        }

        if (!maybeBuilderMaker.isPresent()) {
            errorReporter.reportError(
                    method,
                    "[AutoValueCantMakePropertyBuilder] Method looks like a property builder, but its type"
                            + " %s does not have a public constructor and %s does not have a static builder() or"
                            + " newBuilder() method that returns %s",
                    barBuilderTypeElement,
                    barTypeElement,
                    barBuilderTypeElement);
            return Optional.empty();
        }
        ExecutableElement builderMaker = maybeBuilderMaker.get();

        String barBuilderType = TypeEncoder.encodeWithAnnotations(barBuilderTypeMirror);
        String rawBarType = TypeEncoder.encodeRaw(barTypeMirror);
        String factoryInitializer = rawBarType + "." + builderMaker.getSimpleName()
                + "("
                + (method.getParameters().isEmpty() ? "" : method.getParameters().get(0).getSimpleName())
                + ")";
        String initializer = (builderMaker.getKind() == ElementKind.CONSTRUCTOR) ? "new " + barBuilderType + "()" : factoryInitializer;
        String builtToBuilder = null;
        String copyAll = null;
        ExecutableElement toBuilder = barNoArgMethods.get("toBuilder");
        if (toBuilder != null
                && !toBuilder.getModifiers().contains(Modifier.STATIC)
                && typeUtils.isAssignable(
                typeUtils.erasure(toBuilder.getReturnType()),
                typeUtils.erasure(barBuilderTypeMirror))) {
            builtToBuilder = toBuilder.getSimpleName().toString();
        } else {
            Optional<ExecutableElement> maybeCopyAll =
                    addAllPutAll(barBuilderTypeElement, barBuilderDeclaredType, barTypeMirror);
            if (maybeCopyAll.isPresent()) {
                copyAll = maybeCopyAll.get().getSimpleName().toString();
            }
        }
        ExecutableElement barOf = barNoArgMethods.get("of");
        boolean hasOf = (barOf != null && barOf.getModifiers().contains(Modifier.STATIC));
        // An expression (initDefault) to make a default one of these, plus optionally a statement
        // (beforeInitDefault) that prepares the expression. For a collection, beforeInitDefault is
        // empty and initDefault is (e.g.) `ImmutableList.of()`. For a nested value type,
        // beforeInitDefault is (e.g.)
        //   `NestedAutoValueType.Builder foo$builder = NestedAutoValueType.builder();`
        // and initDefault is `foo$builder.build();`. The reason for the separate statement is to
        // exploit type inference rather than having to write `NestedAutoValueType.<Bar>build();`.
        String beforeInitDefault;
        String initDefault;
        if (hasOf) {
            beforeInitDefault = "";
            initDefault = rawBarType + ".of()";
        } else {
            String localBuilder = property + "$builder";
            beforeInitDefault = barBuilderType + " " + localBuilder + " = " + initializer + ";";
            initDefault = localBuilder + ".build()";
        }

        PropertyBuilder propertyBuilder =
                new PropertyBuilder(
                        method,
                        barBuilderType,
                        barBuilderTypeMirror,
                        initializer,
                        beforeInitDefault,
                        initDefault,
                        builtToBuilder,
                        copyAll);
        return Optional.of(propertyBuilder);
    }

    private static final ImmutableSet<String> BUILDER_METHOD_NAMES =
            ImmutableSet.of("naturalOrder", "builder", "newBuilder");

    // (2) `BarBuilder must have a public no-arg constructor, or `Bar` must have a visible static
    //      method `naturalOrder(), `builder()`, or `newBuilder()` that returns `BarBuilder`.
    private Optional<ExecutableElement> builderMaker(
            Map<String, ExecutableElement> barNoArgMethods, TypeElement barBuilderTypeElement) {
        for (String builderMethodName : BUILDER_METHOD_NAMES) {
            ExecutableElement method = barNoArgMethods.get(builderMethodName);
            if (method != null
                    && method.getModifiers().contains(Modifier.STATIC)
                    && typeUtils.isSameType(
                    typeUtils.erasure(method.getReturnType()),
                    typeUtils.erasure(barBuilderTypeElement.asType()))) {
                // TODO(emcmanus): check visibility. We don't want to require public for @AutoValue
                // builders. By not checking visibility we risk accepting something as a builder maker
                // and then failing when the generated code tries to call Bar.builder(). But the risk
                // seems small.
                return Optional.of(method);
            }
        }
        return ElementFilter.constructorsIn(barBuilderTypeElement.getEnclosedElements())
                .stream()
                .filter(c -> c.getParameters().isEmpty())
                .filter(c -> c.getModifiers().contains(Modifier.PUBLIC))
                .findFirst();
    }

    private Optional<ExecutableElement> withComparatorBuilderMaker(TypeElement barTypeElement, TypeElement barBuilderTypeElement) {
        return getOneArgumentMethod(barTypeElement, "orderedBy").map(method -> {
                    if (!method.getModifiers().contains(Modifier.STATIC)) {return null;}
                    if (!typeUtils.isSameType(typeUtils.erasure(method.getReturnType()), typeUtils.erasure(barBuilderTypeElement.asType()))) {return null;}
                    return method;
                }
        );
    }

    private Optional<ExecutableElement> getOneArgumentMethod(TypeElement barTypeElement, String methodName) {
        return ElementFilter.methodsIn(elementUtils.getAllMembers(barTypeElement)).stream()
                .filter(method -> method.getParameters().size() == 1)
                .filter(method -> methodName.equals(method.getSimpleName().toString()))
                .filter(method -> !isStaticInterfaceMethodNotIn(method, barTypeElement))
                .findFirst();
    }

    private Map<String, ExecutableElement> noArgMethodsOf(TypeElement type) {
        // Can't easily use ImmutableMap here because getAllMembers could return more than one method
        // with the same name.
        Map<String, ExecutableElement> methods = new LinkedHashMap<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elementUtils.getAllMembers(type))) {
            if (method.getParameters().isEmpty() && !isStaticInterfaceMethodNotIn(method, type)) {
                methods.put(method.getSimpleName().toString(), method);
            }
        }
        return methods;
    }

    // Work around an Eclipse compiler bug: https://bugs.eclipse.org/bugs/show_bug.cgi?id=547185
    // The result of Elements.getAllMembers includes static methods declared in superinterfaces.
    // That's wrong because those aren't inherited. So this method checks whether the given method is
    // a static interface method not in the given type.
    private static boolean isStaticInterfaceMethodNotIn(ExecutableElement method, TypeElement type) {
        return method.getModifiers().contains(Modifier.STATIC)
                && !method.getEnclosingElement().equals(type)
                && method.getEnclosingElement().getKind().equals(ElementKind.INTERFACE);
    }

    private static final ImmutableSet<String> ADD_ALL_PUT_ALL = ImmutableSet.of("addAll", "putAll");

    // We have `Bar bar()` and `Foo.Builder toBuilder()` in the @AutoValue type Foo, and we have
    // `BarBuilder barBuilder()` in Foo.Builder. That means that we need to be able to make a
    // `BarBuilder` from a `Bar` as part of the implementation of `Foo.toBuilder()`. We can do that
    // if `Bar` has a method `BarBuilder toBuilder()`, but what if it doesn't? For example, Guava's
    // `ImmutableList` doesn't have a method `ImmutableList.Builder toBuilder()`. So we also allow it
    // to work if `BarBuilder` has a method `addAll(T)` or `putAll(T)`, where `Bar` is assignable to
    // `T`. `ImmutableList.Builder<E>` does have a method `addAll(Iterable<? extends E>)` and
    // `ImmutableList<E>` is assignable to `Iterable<? extends E>`, so that works.
    private Optional<ExecutableElement> addAllPutAll(
            TypeElement barBuilderTypeElement,
            DeclaredType barBuilderDeclaredType,
            TypeMirror barTypeMirror) {
        return MoreElements.getLocalAndInheritedMethods(barBuilderTypeElement, typeUtils, elementUtils)
                .stream()
                .filter(
                        method ->
                                ADD_ALL_PUT_ALL.contains(method.getSimpleName().toString())
                                        && method.getParameters().size() == 1)
                .filter(
                        method -> {
                            ExecutableType methodMirror =
                                    MoreTypes.asExecutable(typeUtils.asMemberOf(barBuilderDeclaredType, method));
                            return typeUtils.isAssignable(barTypeMirror, methodMirror.getParameterTypes().get(0));
                        })
                .findFirst();
    }

    private static boolean isNullable(ExecutableElement getter) {
        List<List<? extends AnnotationMirror>> annotationLists =
                ImmutableList.of(
                        getter.getAnnotationMirrors(), getter.getReturnType().getAnnotationMirrors());
        for (List<? extends AnnotationMirror> annotations : annotationLists) {
            for (AnnotationMirror annotation : annotations) {
                if (annotation.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable")) {
                    return true;
                }
            }
        }
        return false;
    }
}
