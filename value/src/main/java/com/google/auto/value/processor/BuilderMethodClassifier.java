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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.beans.Introspector;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
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
  private final TypeSimplifier typeSimplifier;

  private final Set<ExecutableElement> buildMethods = Sets.newLinkedHashSet();
  private final Map<String, BuilderSpec.PropertyGetter> builderGetters =
      Maps.newLinkedHashMap();
  private final Multimap<String, ExecutableElement> propertyNameToPrefixedSetters =
      LinkedListMultimap.create();
  private final Multimap<String, ExecutableElement> propertyNameToUnprefixedSetters =
      LinkedListMultimap.create();
  private final Map<String, ExecutableElement> propertyNameToPropertyBuilder =
      Maps.newLinkedHashMap();
  private boolean settersPrefixed;

  private BuilderMethodClassifier(
      ErrorReporter errorReporter,
      ProcessingEnvironment processingEnv,
      TypeElement autoValueClass,
      TypeElement builderType,
      ImmutableBiMap<ExecutableElement, String> getterToPropertyName,
      TypeSimplifier typeSimplifier) {
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
    this.typeSimplifier = typeSimplifier;
  }

  /**
   * Classifies the given methods from a builder type and its ancestors.
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
      ImmutableBiMap<ExecutableElement, String> getterToPropertyName,
      TypeSimplifier typeSimplifier) {
    BuilderMethodClassifier classifier = new BuilderMethodClassifier(
        errorReporter,
        processingEnv,
        autoValueClass,
        builderType,
        getterToPropertyName,
        typeSimplifier);
    if (classifier.classifyMethods(methods)) {
      return Optional.of(classifier);
    } else {
      return Optional.absent();
    }
  }

  /**
   * Returns a multimap from the name of a property to the methods that set it. If the property is
   * defined by an abstract method in the {@code @AutoValue} class called {@code foo()} or
   * {@code getFoo()} then the name of the property is {@code foo} and there will be an entry in
   * the map where the key is {@code "foo"} and the value is a method in the builder called
   * {@code foo} or {@code setFoo}.
   */
  ImmutableMultimap<String, ExecutableElement> propertyNameToSetters() {
    return ImmutableMultimap.copyOf(
        settersPrefixed ? propertyNameToPrefixedSetters : propertyNameToUnprefixedSetters);
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
  ImmutableMap<String, BuilderSpec.PropertyGetter> builderGetters() {
    return ImmutableMap.copyOf(builderGetters);
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
    Multimap<String, ExecutableElement> propertyNameToSetter;
    if (propertyNameToPrefixedSetters.isEmpty()) {
      propertyNameToSetter = propertyNameToUnprefixedSetters;
      this.settersPrefixed = false;
    } else if (propertyNameToUnprefixedSetters.isEmpty()) {
      propertyNameToSetter = propertyNameToPrefixedSetters;
      this.settersPrefixed = true;
    } else {
      errorReporter.reportError("If any setter methods use the setFoo convention then all must",
          propertyNameToUnprefixedSetters.values().iterator().next());
      return false;
    }
    for (Map.Entry<ExecutableElement, String> getterEntry : getterToPropertyName.entrySet()) {
      String property = getterEntry.getValue();
      boolean hasSetter = propertyNameToSetter.containsKey(property);
      boolean hasBuilder = propertyNameToPropertyBuilder.containsKey(property);
      if (!hasSetter && !hasBuilder) {
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
   * Classifies a method and update the state of this object based on what is found.
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
   * Classifies a method given that it has no arguments. Currently a method with no
   * arguments can be a {@code build()} method, meaning that its return type must be the
   * {@code @AutoValue} class; it can be a getter, with the same signature as one of
   * the property getters in the {@code @AutoValue} class; or it can be a property builder,
   * like {@code ImmutableList.Builder<String> foosBuilder()} for the property defined by
   * {@code ImmutableList<String> foos()} or {@code getFoos()}.
   *
   * @return true if the method was successfully classified, false if an error has been reported.
   */
  private boolean classifyMethodNoArgs(ExecutableElement method) {
    String methodName = method.getSimpleName().toString();
    TypeMirror returnType = builderMethodReturnType(method);

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
    String propertyName = getterToPropertyName.get(originalGetter);
    TypeMirror builderGetterType = builderMethodReturnType(builderGetter);
    String builderGetterTypeString = typeSimplifier.simplify(builderGetterType);
    TypeMirror originalGetterType = originalGetter.getReturnType();
    if (TYPE_EQUIVALENCE.equivalent(builderGetterType, originalGetterType)) {
      builderGetters.put(
          propertyName, new BuilderSpec.PropertyGetter(builderGetterTypeString, null));
      return true;
    }
    Optionalish optional = Optionalish.createIfOptional(
        builderGetterType, typeSimplifier.simplifyRaw(builderGetterType));
    if (optional != null) {
      TypeMirror containedType = optional.getContainedType(typeUtils);
      // If the original method is int getFoo() then we allow Optional<Integer> here.
      // boxedOriginalType is Integer, and containedType is also Integer.
      // We don't need any special code for OptionalInt because containedType will be int then.
      TypeMirror boxedOriginalType = (originalGetterType.getKind().isPrimitive())
          ? typeUtils.boxedClass(MoreTypes.asPrimitiveType(originalGetterType))
              .asType()
          : null;
      if (TYPE_EQUIVALENCE.equivalent(containedType, originalGetterType)
          || TYPE_EQUIVALENCE.equivalent(containedType, boxedOriginalType)) {
        builderGetters.put(
            propertyName,
            new BuilderSpec.PropertyGetter(builderGetterTypeString, optional));
        return true;
      }
    }
    String error = String.format(
        "Method matches a property of %1$s but has return type %2$s instead of %3$s "
            + "or an Optional wrapping of %3$s",
        autoValueClass, builderGetterType, originalGetter.getReturnType());
    errorReporter.reportError(error, builderGetter);
    return false;
  }

  // Construct this string so it won't be found by Maven shading and renamed, which is not what
  // we want.
  private static final String COM_GOOGLE_COMMON_COLLECT_IMMUTABLE =
      "com.".concat("google.common.collect.Immutable");

  private boolean classifyPropertyBuilder(ExecutableElement method, String property) {
    TypeMirror builderTypeMirror = builderMethodReturnType(method);
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
   * Classifies a method given that it has one argument. Currently, a method with one argument can
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
    Multimap<String, ExecutableElement> propertyNameToSetters = null;
    if (valueGetter != null) {
      propertyName = methodName;
      propertyNameToSetters = propertyNameToUnprefixedSetters;
    } else if (valueGetter == null && methodName.startsWith("set") && methodName.length() > 3) {
      propertyName = Introspector.decapitalize(methodName.substring(3));
      propertyNameToSetters = propertyNameToPrefixedSetters;
      valueGetter = propertyNameToGetter.get(propertyName);
    }
    if (valueGetter == null || propertyNameToSetters == null) {
      // The second disjunct isn't needed but convinces control-flow checkers that
      // propertyNameToSetters can't be null when we call put on it below.
      errorReporter.reportError(
          "Method does not correspond to a property of " + autoValueClass, method);
      checkForFailedJavaBean(method);
      return false;
    }
    if (!checkSetterParameter(valueGetter, method)) {
      return false;
    } else if (
        !TYPE_EQUIVALENCE.equivalent(builderMethodReturnType(method), builderType.asType())) {
      errorReporter.reportError(
          "Setter methods must return " + builderType + typeParamsString(), method);
      return false;
    } else {
      propertyNameToSetters.put(propertyName, method);
      return true;
    }
  }

  // A frequence source of problems is where the JavaBeans conventions have been followed for
  // most but not all getters. Then AutoValue considers that they haven't been followed at all,
  // so you might have a property called getFoo where you thought it was called just foo, and
  // you might not understand why your setter called setFoo is rejected (it would have to be called
  // setGetFoo).
  private void checkForFailedJavaBean(ExecutableElement rejectedSetter) {
    ImmutableSet<ExecutableElement> allGetters = getterToPropertyName.keySet();
    ImmutableSet<ExecutableElement> prefixedGetters =
        AutoValueProcessor.prefixedGettersIn(allGetters);
    if (prefixedGetters.size() < allGetters.size()
        && prefixedGetters.size() >= allGetters.size() / 2) {
      String note =
          "This might be because you are using the getFoo() convention"
              + " for some but not all methods. These methods don't follow the convention: "
              + Sets.difference(allGetters, prefixedGetters);
      errorReporter.reportNote(note, rejectedSetter);
    }
  }

  /**
   * Checks that the given setter method has a parameter type that is compatible with the return
   * type of the given getter. Compatible means either that it is the same, or that it is a type
   * that can be copied using a method like {@code ImmutableList.copyOf} or {@code Optional.of}.
   *
   * @return true if the types correspond, false if an error has been reported.
   */
  private boolean checkSetterParameter(ExecutableElement valueGetter, ExecutableElement setter) {
    TypeMirror targetType = valueGetter.getReturnType();
    TypeMirror parameterType = setter.getParameters().get(0).asType();
    if (TYPE_EQUIVALENCE.equivalent(parameterType, targetType)) {
      return true;
    }
    ImmutableList<ExecutableElement> copyOfMethods = copyOfMethods(targetType);
    if (!copyOfMethods.isEmpty()) {
      return canMakeCopyUsing(copyOfMethods, valueGetter, setter);
    }
    String error = String.format(
        "Parameter type %s of setter method should be %s to match getter %s.%s",
        parameterType, targetType, autoValueClass, valueGetter.getSimpleName());
    errorReporter.reportError(error, setter);
    return false;
  }

  /**
   * Checks that the given setter method has a parameter type that can be copied to the return type
   * of the given getter using one of the given {@code copyOf} methods.
   *
   * @return true if the copy can be made, false if an error has been reported.
   */
  private boolean canMakeCopyUsing(
      ImmutableList<ExecutableElement> copyOfMethods,
      ExecutableElement valueGetter,
      ExecutableElement setter) {
    TypeMirror targetType = valueGetter.getReturnType();
    TypeMirror parameterType = setter.getParameters().get(0).asType();
    for (ExecutableElement copyOfMethod : copyOfMethods) {
      if (canMakeCopyUsing(copyOfMethod, targetType, parameterType)) {
        return true;
      }
    }
    DeclaredType targetDeclaredType = MoreTypes.asDeclared(targetType);
    String targetTypeSimpleName = targetDeclaredType.asElement().getSimpleName().toString();
    String error = String.format(
        "Parameter type of setter method should be %s to match getter %s.%s, or it should be a "
            + "type that can be passed to %s.copyOf",
        targetType, autoValueClass, valueGetter.getSimpleName(), targetTypeSimpleName);
    errorReporter.reportError(error, setter);
    return false;
  }

  /**
   * Returns true if {@code copyOfMethod} can be used to copy the {@code parameterType}
   * to the {@code targetType}.
   */
  private boolean canMakeCopyUsing(
      ExecutableElement copyOfMethod, TypeMirror targetType, TypeMirror parameterType) {
    // We have a parameter type, for example Set<? extends T>, and we want to know if it can be
    // passed to the given copyOf method, which might for example be one of these methods from
    // ImmutableSet:
    //    public static <E> ImmutableSet<E> copyOf(Collection<? extends E> elements)
    //    public static <E> ImmutableSet<E> copyOf(E[] elements)
    // Additionally, if it can indeed be passed to the method, we want to know whether the result
    // (here ImmutableSet<? extends T>) is compatible with the property to be set, bearing in mind
    // that the T in question is the one from the @AutoValue class and not the Builder class.
    // The logic to do that properly would be quite complex, and we don't get much help from the
    // annotation processing API, so for now we simply check that the erased types correspond.
    // This means that an incorrect type will lead to a compilation error in the generated code,
    // which is less than ideal.
    // TODO(b/20691134): make this work properly
    TypeMirror erasedParameterType = typeUtils.erasure(parameterType);
    TypeMirror erasedCopyOfParameterType =
        typeUtils.erasure(Iterables.getOnlyElement(copyOfMethod.getParameters()).asType());
    // erasedParameterType is Set in the example and erasedCopyOfParameterType is Collection
    if (!typeUtils.isAssignable(erasedParameterType, erasedCopyOfParameterType)) {
      return false;
    }
    TypeMirror erasedCopyOfReturnType = typeUtils.erasure(copyOfMethod.getReturnType());
    TypeMirror erasedTargetType = typeUtils.erasure(targetType);
    // erasedCopyOfReturnType and erasedTargetType are both ImmutableSet in the example.
    // In fact for Guava immutable collections the check could be for equality.
    return typeUtils.isAssignable(erasedCopyOfReturnType, erasedTargetType);
  }

  /**
   * Returns {@code copyOf} methods from the given type. These are static methods called
   * {@code copyOf} with a single parameter. All of Guava's concrete immutable collection types have
   * at least one such method, but we will also accept other classes with an appropriate method,
   * such as {@link java.util.EnumSet}.
   */
  private ImmutableList<ExecutableElement> copyOfMethods(TypeMirror targetType) {
    if (!targetType.getKind().equals(TypeKind.DECLARED)) {
      return ImmutableList.of();
    }
    String copyOf = Optionalish.isOptional(targetType) ? "of" : "copyOf";
    TypeElement immutableTargetType = MoreElements.asType(typeUtils.asElement(targetType));
    ImmutableList.Builder<ExecutableElement> copyOfMethods = ImmutableList.builder();
    for (ExecutableElement method :
        ElementFilter.methodsIn(immutableTargetType.getEnclosedElements())) {
      if (method.getSimpleName().contentEquals(copyOf)
          && method.getParameters().size() == 1
          && method.getModifiers().contains(Modifier.STATIC)) {
        copyOfMethods.add(method);
      }
    }
    return copyOfMethods.build();
  }

  /**
   * Returns the return type of the given method from the builder. This should be the final type of
   * the method when any bound type variables are substituted. Consider this example:
   * <pre>{@code
   * abstract static class ParentBuilder<B extends ParentBuilder> {
   *   B setFoo(String s);
   * }
   * abstract static class ChildBuilder extends ParentBuilder<ChildBuilder> {
   *   ...
   * }
   * }</pre>
   * If the builder is {@code ChildBuilder} then the return type of {@code setFoo} is also
   * {@code ChildBuilder}, and not {@code B} as its {@code getReturnType()} method would claim.
   *
   * <p>If the caller is in a version of Eclipse with
   * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=382590">this bug</a> then the
   * {@code asMemberOf} call will fail if the method is inherited from an interface. We work around
   * that for methods in the {@code @AutoValue} class using {@link EclipseHack#methodReturnTypes}
   * but we don't try to do so here because it should be much less likely. You might need to change
   * {@code ParentBuilder} from an interface to an abstract class to make it work, but you'll often
   * need to do that anyway.
   */
  private TypeMirror builderMethodReturnType(ExecutableElement builderMethod) {
    DeclaredType builderTypeMirror = MoreTypes.asDeclared(builderType.asType());
    TypeMirror methodMirror;
    try {
      methodMirror = typeUtils.asMemberOf(builderTypeMirror, builderMethod);
    } catch (IllegalArgumentException e) {
      // Presumably we've hit the Eclipse bug cited.
      return builderMethod.getReturnType();
    }
    return MoreTypes.asExecutable(methodMirror).getReturnType();
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
