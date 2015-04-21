/*
 * Copyright (C) 2015 Google, Inc.
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
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.beans.Introspector;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * Classifies methods inside builder types, based on their names and parameter and return types.
 *
 * @author Éamonn McManus
 */
class BuilderMethodClassifier {
  private static final Equivalence<TypeMirror> TYPE_EQUIVALENCE = MoreTypes.equivalence();

  private final ErrorReporter errorReporter;
  private final Types typeUtils;
  private final TypeElement autoValueClass;
  private final TypeElement builderType;
  private final ImmutableBiMap<ExecutableElement, String> getterToPropertyName;
  private final ImmutableMap<String, ExecutableElement> getterNameToGetter;

  private final Set<ExecutableElement> buildMethods = Sets.newLinkedHashSet();
  private final Set<String> propertiesWithBuilderGetters = Sets.newLinkedHashSet();
  private final Map<String, ExecutableElement> propertyNameToPrefixedSetter =
      Maps.newLinkedHashMap();
  private final Map<String, ExecutableElement> propertyNameToUnprefixedSetter =
      Maps.newLinkedHashMap();
  private final Map<String, ExecutableElement> propertyNameToPropertyBuilder =
      Maps.newLinkedHashMap();
  private boolean settersPrefixed;

  private BuilderMethodClassifier(
      ErrorReporter errorReporter,
      ProcessingEnvironment processingEnv,
      TypeElement autoValueClass,
      TypeElement builderType,
      ImmutableBiMap<ExecutableElement, String> getterToPropertyName) {
    this.errorReporter = errorReporter;
    this.typeUtils = processingEnv.getTypeUtils();
    this.autoValueClass = autoValueClass;
    this.builderType = builderType;
    this.getterToPropertyName = getterToPropertyName;
    ImmutableMap.Builder<String, ExecutableElement> getterToPropertyNameBuilder =
        ImmutableMap.builder();
    for (ExecutableElement getter : getterToPropertyName.keySet()) {
      getterToPropertyNameBuilder.put(getter.getSimpleName().toString(), getter);
    }
    this.getterNameToGetter = getterToPropertyNameBuilder.build();
  }

  /**
   * Classify the given methods from a builder type and its ancestors.
   *
   * @param methods the methods in {@code builderType} and its ancestors.
   * @param errorReporter where to report errors.
   * @param autoValueClass the {@code AutoValue} class containing the builder.
   * @param builderType the builder class or interface within {@code autoValueClass}.
   * @param getterToPropertyName a map from getter methods to the properties they get.
   *
   * @return an {@code Optional} that contains the results of the classification if it was
   *     successful or nothing if it was not.
   */
  static Optional<BuilderMethodClassifier> classify(
      Iterable<ExecutableElement> methods,
      ErrorReporter errorReporter,
      ProcessingEnvironment processingEnv,
      TypeElement autoValueClass,
      TypeElement builderType,
      ImmutableBiMap<ExecutableElement, String> getterToPropertyName) {
    BuilderMethodClassifier classifier = new BuilderMethodClassifier(
        errorReporter, processingEnv, autoValueClass, builderType, getterToPropertyName);
    if (classifier.classifyMethods(methods)) {
      return Optional.of(classifier);
    } else {
      return Optional.absent();
    }
  }

  /**
   * Returns a map from the name of a property to the method that sets it. If the property is
   * defined by an abstract method in the {@code @AutoValue} class called {@code foo()} or
   * {@code getFoo()} then the name of the property is {@code foo} and there will be an entry in
   * the map where the key is {@code "foo"} and the value is a method in the builder called
   * {@code foo} or {@code setFoo}.
   */
  Map<String, ExecutableElement> propertyNameToSetter() {
    return ImmutableMap.copyOf(
        settersPrefixed ? propertyNameToPrefixedSetter : propertyNameToUnprefixedSetter);
  }

  Map<String, ExecutableElement> propertyNameToPropertyBuilder() {
    return propertyNameToPropertyBuilder;
  }

  /**
   * Returns the set of properties that have getters in the builder. If a property is defined by
   * an abstract method in the {@code @AutoValue} class called {@code foo()} or {@code getFoo()}
   * then the name of the property is {@code foo}, If the builder also has a method of the same name
   * ({@code foo()} or {@code getFoo()}) then the set returned here will contain {@code foo}.
   */
  ImmutableSet<String> propertiesWithBuilderGetters() {
    return ImmutableSet.copyOf(propertiesWithBuilderGetters);
  }

  /**
   * Returns the methods that were identified as {@code build()} methods. These are methods that
   * have no parameters and return the {@code @AutoValue} type, conventionally called
   * {@code build()}.
   */
  Set<ExecutableElement> buildMethods() {
    return ImmutableSet.copyOf(buildMethods);
  }

  /**
   * Classifies the given methods and sets the state of this object based on what is found.
   */
  private boolean classifyMethods(Iterable<ExecutableElement> methods) {
    boolean ok = true;
    for (ExecutableElement method : methods) {
      ok &= classifyMethod(method);
    }
    if (!ok) {
      return false;
    }
    Map<String, ExecutableElement> propertyNameToSetter;
    if (propertyNameToPrefixedSetter.isEmpty()) {
      propertyNameToSetter = propertyNameToUnprefixedSetter;
      this.settersPrefixed = false;
    } else if (propertyNameToUnprefixedSetter.isEmpty()) {
      propertyNameToSetter = propertyNameToPrefixedSetter;
      this.settersPrefixed = true;
    } else {
      errorReporter.reportError(
          "If any setter methods use the setFoo convention then all must",
          propertyNameToUnprefixedSetter.values().iterator().next());
      return false;
    }
    for (Map.Entry<ExecutableElement, String> getterEntry : getterToPropertyName.entrySet()) {
      String property = getterEntry.getValue();
      boolean hasSetter = propertyNameToSetter.containsKey(property);
      boolean hasBuilder = propertyNameToPropertyBuilder.containsKey(property);
      if (hasSetter && hasBuilder) {
        String error =
            String.format("Property %s cannot have both a setter and a builder", property);
        errorReporter.reportError(error, builderType);
      } else if (!hasSetter && !hasBuilder) {
        // TODO(emcmanus): also mention the possible builder method if the property type allows one
        String setterName = settersPrefixed ? prefixWithSet(property) : property;
        String error = String.format("Expected a method with this signature: %s%s %s(%s)",
            builderType,
            typeParamsString(),
            setterName,
            getterEntry.getKey().getReturnType());
        errorReporter.reportError(error, builderType);
        ok = false;
      }
    }
    return ok;
  }

  /**
   * Classify a method and update the state of this object based on what is found.
   *
   * @return true if the method was successfully classified, false if an error has been reported.
   */
  private boolean classifyMethod(ExecutableElement method) {
    switch (method.getParameters().size()) {
      case 0:
        return classifyMethodNoArgs(method);
      case 1:
        return classifyMethodOneArg(method);
      default:
        errorReporter.reportError("Builder methods must have 0 or 1 parameters", method);
        return false;
    }
  }

  /**
   * Classify a method given that it has no arguments. Currently a method with no
   * arguments can only be a {@code build()} method, meaning that its return type must be the
   * {@code @AutoValue} class.
   *
   * @return true if the method was successfully classified, false if an error has been reported.
   */
  private boolean classifyMethodNoArgs(ExecutableElement method) {
    String methodName = method.getSimpleName().toString();
    TypeMirror returnType = method.getReturnType();

    ExecutableElement getter = getterNameToGetter.get(methodName);
    if (getter != null) {
      return classifyGetter(method, getter);
    }

    if (methodName.endsWith("Builder")) {
      String property = methodName.substring(0, methodName.length() - "Builder".length());
      if (getterToPropertyName.containsValue(property)) {
        return classifyPropertyBuilder(method, property);
      }
    }

    if (TYPE_EQUIVALENCE.equivalent(returnType, autoValueClass.asType())) {
      buildMethods.add(method);
      return true;
    }

    String error = String.format(
        "Method without arguments should be a build method returning %1$s%2$s"
            + " or a getter method with the same name and type as a getter method of %1$s",
        autoValueClass, typeParamsString());
    errorReporter.reportError(error, method);
    return false;
  }

  private boolean classifyGetter(
      ExecutableElement builderGetter, ExecutableElement originalGetter) {
    if (!TYPE_EQUIVALENCE.equivalent(
        builderGetter.getReturnType(), originalGetter.getReturnType())) {
      String error = String.format(
          "Method matches a property of %s but has return type %s instead of %s",
          autoValueClass, builderGetter.getReturnType(), originalGetter.getReturnType());
      errorReporter.reportError(error, builderGetter);
      return false;
    }
    propertiesWithBuilderGetters.add(getterToPropertyName.get(originalGetter));
    return true;
  }

  // Construct this string so it won't be found by Maven shading and renamed, which is not what
  // we want.
  private static final String COM_GOOGLE_COMMON_COLLECT_IMMUTABLE =
      new StringBuilder("com.").append("google.common.collect.Immutable").toString();

  private boolean classifyPropertyBuilder(ExecutableElement method, String property) {
    TypeMirror builderTypeMirror = method.getReturnType();
    TypeElement builderTypeElement = MoreTypes.asTypeElement(builderTypeMirror);
    String builderTypeString = builderTypeElement.getQualifiedName().toString();
    boolean isGuavaBuilder = (builderTypeString.startsWith(COM_GOOGLE_COMMON_COLLECT_IMMUTABLE)
        && builderTypeString.endsWith(".Builder"));
    if (!isGuavaBuilder) {
      errorReporter.reportError("Method looks like a property builder, but its return type "
          + "is not a builder for an immutable type in com.google.common.collect", method);
      return false;
    }
    // Given, e.g. ImmutableSet.Builder<String>, construct ImmutableSet<String> and check that
    // it is indeed the type of the property.
    DeclaredType builderTypeDeclared = MoreTypes.asDeclared(builderTypeMirror);
    TypeMirror[] builderTypeArgs =
        builderTypeDeclared.getTypeArguments().toArray(new TypeMirror[0]);
    if (builderTypeArgs.length == 0) {
      errorReporter.reportError("Property builder type cannot be raw (missing <...>)", method);
      return false;
    }
    TypeElement enclosingTypeElement =
        MoreElements.asType(builderTypeElement.getEnclosingElement());
    TypeMirror expectedPropertyType =
        typeUtils.getDeclaredType(enclosingTypeElement, builderTypeArgs);
    TypeMirror actualPropertyType = getterToPropertyName.inverse().get(property).getReturnType();
    if (!TYPE_EQUIVALENCE.equivalent(expectedPropertyType, actualPropertyType)) {
      String error = String.format(
          "Return type of property-builder method implies a property of type %s, but property "
              + "%s has type %s",
          expectedPropertyType, property, actualPropertyType);
      errorReporter.reportError(error, method);
      return false;
    }
    propertyNameToPropertyBuilder.put(property, method);
    return true;
  }

  /**
   * Classify a method given that it has one argument. Currently, a method with one argument can
   * only be a setter, meaning that it must look like {@code foo(T)} or {@code setFoo(T)}, where
   * the {@code AutoValue} class has a property called {@code foo} of type {@code T}.
   *
   * @return true if the method was successfully classified, false if an error has been reported.
   */
  private boolean classifyMethodOneArg(ExecutableElement method) {
    String methodName = method.getSimpleName().toString();
    Map<String, ExecutableElement> propertyNameToGetter = getterToPropertyName.inverse();
    String propertyName = null;
    ExecutableElement valueGetter = propertyNameToGetter.get(methodName);
    Map<String, ExecutableElement> propertyNameToSetter = null;
    if (valueGetter != null) {
      propertyName = methodName;
      propertyNameToSetter = propertyNameToUnprefixedSetter;
    } else if (valueGetter == null && methodName.startsWith("set") && methodName.length() > 3) {
      propertyName = Introspector.decapitalize(methodName.substring(3));
      propertyNameToSetter = propertyNameToPrefixedSetter;
      valueGetter = propertyNameToGetter.get(propertyName);
    }
    if (valueGetter == null || propertyNameToSetter == null) {
      // The second disjunct isn't needed but convinces control-flow checkers that
      // propertyNameToSetter can't be null when we call put on it below.
      errorReporter.reportError(
          "Method does not correspond to a property of " + autoValueClass, method);
      return false;
    }
    TypeMirror parameterType = method.getParameters().get(0).asType();
    TypeMirror expectedType = valueGetter.getReturnType();
    if (!TYPE_EQUIVALENCE.equivalent(parameterType, expectedType)) {
      errorReporter.reportError(
          "Parameter type of setter method should be " + expectedType + " to match getter "
          + autoValueClass + "." + valueGetter.getSimpleName(), method);
      return false;
    } else if (!TYPE_EQUIVALENCE.equivalent(method.getReturnType(), builderType.asType())) {
      errorReporter.reportError(
          "Setter methods must return " + builderType + typeParamsString(), method);
      return false;
    } else {
      propertyNameToSetter.put(propertyName, method);
      return true;
    }
  }

  private String prefixWithSet(String propertyName) {
    // This is not internationalizationally correct, but it corresponds to what
    // Introspector.decapitalize does.
    return "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
  }

  private String typeParamsString() {
    return TypeSimplifier.actualTypeParametersString(autoValueClass);
  }
}
