/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Support for AutoValue builders.
 *
 * @author Éamonn McManus
 */
class BuilderSpec {
  private final TypeElement autoValueClass;
  private final Elements elementUtils;
  private final ErrorReporter errorReporter;

  BuilderSpec(
      TypeElement autoValueClass,
      ProcessingEnvironment processingEnv,
      ErrorReporter errorReporter) {
    this.autoValueClass = autoValueClass;
    this.elementUtils = processingEnv.getElementUtils();
    this.errorReporter = errorReporter;
  }

  private static final Set<ElementKind> CLASS_OR_INTERFACE =
      Sets.immutableEnumSet(ElementKind.CLASS, ElementKind.INTERFACE);

  /**
   * Determines if the {@code @AutoValue} class for this instance has a correct nested
   * {@code @AutoValue.Builder} class or interface and return a representation of it in an
   * {@code Optional} if so.
   */
  Optional<Builder> getBuilder() {
    Optional<TypeElement> builderTypeElement = Optional.absent();
    for (TypeElement containedClass : ElementFilter.typesIn(autoValueClass.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(containedClass, AutoValue.Builder.class)) {
        if (!CLASS_OR_INTERFACE.contains(containedClass.getKind())) {
          errorReporter.reportError(
              "@AutoValue.Builder can only apply to a class or an interface", containedClass);
        } else if (builderTypeElement.isPresent()) {
          errorReporter.reportError(
              autoValueClass + " already has a Builder: " + builderTypeElement.get(),
              containedClass);
        } else {
          builderTypeElement = Optional.of(containedClass);
        }
      }
    }

    Optional<ExecutableElement> validateMethod = Optional.absent();
    for (ExecutableElement containedMethod :
        ElementFilter.methodsIn(autoValueClass.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(containedMethod, AutoValue.Validate.class)) {
        if (containedMethod.getModifiers().contains(Modifier.STATIC)) {
          errorReporter.reportError(
              "@AutoValue.Validate cannot apply to a static method", containedMethod);
        } else if (!containedMethod.getParameters().isEmpty()) {
          errorReporter.reportError(
              "@AutoValue.Validate method must not have parameters", containedMethod);
        } else if (containedMethod.getReturnType().getKind() != TypeKind.VOID) {
          errorReporter.reportError(
              "Return type of @AutoValue.Validate method must be void", containedMethod);
        } else if (validateMethod.isPresent()) {
          errorReporter.reportError(
              "There can only be one @AutoValue.Validate method", containedMethod);
        } else {
          validateMethod = Optional.of(containedMethod);
        }
      }
    }

    if (builderTypeElement.isPresent()) {
      return builderFrom(builderTypeElement.get(), validateMethod);
    } else {
      if (validateMethod.isPresent()) {
        errorReporter.reportError(
            "@AutoValue.Validate is only meaningful if there is an @AutoValue.Builder",
            validateMethod.get());
      }
      return Optional.absent();
    }
  }

  /**
   * Representation of an {@code AutoValue.Builder} class or interface.
   */
  class Builder {
    private final TypeElement builderTypeElement;
    private final Optional<ExecutableElement> validateMethod;

    Builder(
        TypeElement builderTypeElement,
        Optional<ExecutableElement> validateMethod) {
      this.builderTypeElement = builderTypeElement;
      this.validateMethod = validateMethod;
    }

    /**
     * Finds any methods in the set that return the builder type. If the builder has type parameters
     * {@code <A, B>}, then the return type of the method must be {@code Builder<A, B>} with
     * the same parameter names. We enforce elsewhere that the names and bounds of the builder
     * parameters must be the same as those of the @AutoValue class. Here's a correct example:
     * <pre>
     * {@code @AutoValue abstract class Foo<A extends Number, B> {
     *   abstract int someProperty();
     *
     *   abstract Builder<A, B> toBuilder();
     *
     *   interface Builder<A extends Number, B> {...}
     * }}
     * </pre>
     *
     * <p>We currently impose that there cannot be more than one such method.</p>
     */
    ImmutableSet<ExecutableElement> toBuilderMethods(
        Types typeUtils, Set<ExecutableElement> abstractMethods) {

      ImmutableList<String> builderTypeParamNames =
          FluentIterable.from(builderTypeElement.getTypeParameters())
              .transform(SimpleNameFunction.INSTANCE)
              .toList();

      ImmutableSet.Builder<ExecutableElement> methods = ImmutableSet.builder();
      for (ExecutableElement method : abstractMethods) {
        if (builderTypeElement.equals(typeUtils.asElement(method.getReturnType()))) {
          methods.add(method);
          DeclaredType returnType = MoreTypes.asDeclared(method.getReturnType());
          ImmutableList.Builder<String> typeArguments = ImmutableList.builder();
          for (TypeMirror typeArgument : returnType.getTypeArguments()) {
            if (typeArgument.getKind().equals(TypeKind.TYPEVAR)) {
              typeArguments.add(typeUtils.asElement(typeArgument).getSimpleName().toString());
            }
          }
          if (!builderTypeParamNames.equals(typeArguments.build())) {
            errorReporter.reportError(
                "Builder converter method should return "
                    + builderTypeElement
                    + TypeSimplifier.actualTypeParametersString(builderTypeElement),
                method);
          }
        }
      }
      ImmutableSet<ExecutableElement> builderMethods = methods.build();
      if (builderMethods.size() > 1) {
        errorReporter.reportError(
            "There can be at most one builder converter method", builderMethods.iterator().next());
      }
      return builderMethods;
    }

    void defineVars(
        AutoValueTemplateVars vars,
        TypeSimplifier typeSimplifier,
        ImmutableBiMap<ExecutableElement, String> getterToPropertyName) {
      Iterable<ExecutableElement> builderMethods = abstractMethods(builderTypeElement);
      Optional<BuilderMethodClassifier> optionalClassifier = BuilderMethodClassifier.classify(
          builderMethods, errorReporter, autoValueClass, builderTypeElement, getterToPropertyName);
      if (!optionalClassifier.isPresent()) {
        return;
      }
      BuilderMethodClassifier classifier = optionalClassifier.get();
      Set<ExecutableElement> buildMethods = classifier.buildMethods();
      if (buildMethods.size() != 1) {
        Set<? extends Element> errorElements = buildMethods.isEmpty()
            ? ImmutableSet.of(builderTypeElement)
            : buildMethods;
        for (Element buildMethod : errorElements) {
          errorReporter.reportError(
              "Builder must have a single no-argument method returning "
                  + autoValueClass + typeParamsString(),
              buildMethod);
        }
        return;
      }
      ExecutableElement buildMethod = Iterables.getOnlyElement(buildMethods);
      vars.builderIsInterface = builderTypeElement.getKind() == ElementKind.INTERFACE;
      vars.builderTypeName = TypeSimplifier.classNameOf(builderTypeElement);
      vars.builderFormalTypes = typeSimplifier.formalTypeParametersString(builderTypeElement);
      vars.builderActualTypes = TypeSimplifier.actualTypeParametersString(builderTypeElement);
      vars.buildMethodName = buildMethod.getSimpleName().toString();
      if (validateMethod.isPresent()) {
        vars.validators = ImmutableSet.of(validateMethod.get().getSimpleName().toString());
      } else {
        vars.validators = ImmutableSet.of();
      }
      vars.propertiesWithBuilderGetters = classifier.propertiesWithBuilderGetters();

      ImmutableMap.Builder<String, String> setterNameBuilder = ImmutableMap.builder();
      for (Map.Entry<String, ExecutableElement> entry :
          classifier.propertyNameToSetter().entrySet()) {
        setterNameBuilder.put(entry.getKey(), entry.getValue().getSimpleName().toString());
      }
      vars.builderSetterNames = setterNameBuilder.build();
    }
  }

  /**
   * Returns a representation of the given {@code @AutoValue.Builder} class or interface. If the
   * class or interface has abstract methods that could not be part of any builder, emits error
   * messages and returns null.
   */
  private Optional<Builder> builderFrom(
      TypeElement builderTypeElement, Optional<ExecutableElement> validateMethod) {

    // We require the builder to have the same type parameters as the @AutoValue class, meaning the
    // same names and bounds. In principle the type parameters could have different names, but that
    // would be confusing, and our code would reject it anyway because it wouldn't consider that
    // the return type of Foo<U> build() was really the same as the declaration of Foo<T>. This
    // check produces a better error message in that case and similar ones.

    boolean ok = true;
    int nTypeParameters = autoValueClass.getTypeParameters().size();
    if (nTypeParameters != builderTypeElement.getTypeParameters().size()) {
      ok = false;
    } else {
      for (int i = 0; i < nTypeParameters; i++) {
        TypeParameterElement autoValueParam = autoValueClass.getTypeParameters().get(i);
        TypeParameterElement builderParam = builderTypeElement.getTypeParameters().get(i);
        if (!autoValueParam.getSimpleName().equals(builderParam.getSimpleName())) {
          ok = false;
          break;
        }
        Set<TypeMirror> autoValueBounds = new TypeMirrorSet(autoValueParam.getBounds());
        Set<TypeMirror> builderBounds = new TypeMirrorSet(builderParam.getBounds());
        if (!autoValueBounds.equals(builderBounds)) {
          ok = false;
          break;
        }
      }
    }
    if (!ok) {
      errorReporter.reportError(
          "Type parameters of " + builderTypeElement + " must have same names and bounds as "
              + "type parameters of " + autoValueClass, builderTypeElement);
      return Optional.absent();
    }
    return Optional.of(new Builder(builderTypeElement, validateMethod));
  }

  // Return a list of all abstract methods in the given TypeElement or inherited from ancestors.
  private List<ExecutableElement> abstractMethods(TypeElement typeElement) {
    List<ExecutableElement> methods = new ArrayList<ExecutableElement>();
    addAbstractMethods(typeElement.asType(), methods);
    return methods;
  }

  private void addAbstractMethods(
      TypeMirror typeMirror, List<ExecutableElement> abstractMethods) {
    if (typeMirror.getKind() != TypeKind.DECLARED) {
      return;
    }

    TypeElement typeElement = MoreTypes.asTypeElement(typeMirror);
    addAbstractMethods(typeElement.getSuperclass(), abstractMethods);
    for (TypeMirror interfaceMirror : typeElement.getInterfaces()) {
      addAbstractMethods(interfaceMirror, abstractMethods);
    }
    for (ExecutableElement method : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
      for (Iterator<ExecutableElement> it = abstractMethods.iterator(); it.hasNext(); ) {
        ExecutableElement maybeOverridden = it.next();
        if (elementUtils.overrides(method, maybeOverridden, typeElement)) {
          it.remove();
        }
      }
      if (method.getModifiers().contains(Modifier.ABSTRACT)) {
        abstractMethods.add(method);
      }
    }
  }

  private String typeParamsString() {
    return TypeSimplifier.actualTypeParametersString(autoValueClass);
  }
}
