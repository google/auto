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

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.auto.value.processor.AutoValueProcessor.Property;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

/**
 * Support for AutoValue builders.
 *
 * @author Éamonn McManus
 */
class BuilderSpec {
  private final TypeElement autoValueClass;
  private final ProcessingEnvironment processingEnv;
  private final ErrorReporter errorReporter;

  BuilderSpec(
      TypeElement autoValueClass,
      ProcessingEnvironment processingEnv,
      ErrorReporter errorReporter) {
    this.autoValueClass = autoValueClass;
    this.processingEnv = processingEnv;
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

    if (builderTypeElement.isPresent()) {
      return builderFrom(builderTypeElement.get());
    } else {
      return Optional.absent();
    }
  }

  /**
   * Representation of an {@code AutoValue.Builder} class or interface.
   */
  class Builder {
    private final TypeElement builderTypeElement;

    Builder(TypeElement builderTypeElement) {
      this.builderTypeElement = builderTypeElement;
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

    Set<TypeMirror> referencedTypes() {
      Set<TypeMirror> types = new TypeMirrorSet();
      for (ExecutableElement method :
          ElementFilter.methodsIn(builderTypeElement.getEnclosedElements())) {
        types.add(method.getReturnType());
        for (VariableElement parameter : method.getParameters()) {
          types.add(parameter.asType());
        }
      }
      return types;
    }

    void defineVars(
        AutoValueTemplateVars vars,
        TypeSimplifier typeSimplifier,
        ImmutableBiMap<ExecutableElement, String> getterToPropertyName) {
      Iterable<ExecutableElement> builderMethods = abstractMethods(builderTypeElement);
      Optional<BuilderMethodClassifier> optionalClassifier = BuilderMethodClassifier.classify(
          builderMethods,
          errorReporter,
          processingEnv,
          autoValueClass,
          builderTypeElement,
          getterToPropertyName);
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
      vars.propertiesWithBuilderGetters = classifier.propertiesWithBuilderGetters();

      ImmutableMultimap.Builder<String, PropertySetter> setterBuilder = ImmutableMultimap.builder();
      for (Map.Entry<String, ExecutableElement> entry :
          classifier.propertyNameToSetters().entries()) {
        String property = entry.getKey();
        ExecutableElement setter = entry.getValue();
        TypeMirror propertyType = getterToPropertyName.inverse().get(property).getReturnType();
        setterBuilder.put(property, new PropertySetter(setter, propertyType, typeSimplifier));
      }
      vars.builderSetters = setterBuilder.build();

      vars.builderPropertyBuilders =
          makeBuilderPropertyBuilderMap(classifier, typeSimplifier, getterToPropertyName);

      Set<Property> required = Sets.newLinkedHashSet(vars.props);
      for (Property property : vars.props) {
        if (property.isNullable() || vars.builderPropertyBuilders.containsKey(property.getName())) {
          required.remove(property);
        }
      }
      vars.builderRequiredProperties = ImmutableSet.copyOf(required);
    }

    private ImmutableMap<String, PropertyBuilder> makeBuilderPropertyBuilderMap(
        BuilderMethodClassifier classifier,
        TypeSimplifier typeSimplifier,
        ImmutableBiMap<ExecutableElement, String> getterToPropertyName) {
      ImmutableMap.Builder<String, PropertyBuilder> map = ImmutableMap.builder();
      for (Map.Entry<String, ExecutableElement> entry :
          classifier.propertyNameToPropertyBuilder().entrySet()) {
        String property = entry.getKey();
        ExecutableElement autoValuePropertyMethod = getterToPropertyName.inverse().get(property);
        ExecutableElement propertyBuilderMethod = entry.getValue();
        PropertyBuilder propertyBuilder = new PropertyBuilder(
            autoValuePropertyMethod, propertyBuilderMethod, typeSimplifier);
        map.put(property, propertyBuilder);
      }
      return map.build();
    }
  }

  /**
   * Information about a property setter, referenced from the autovalue.vm template. A property
   * called foo (defined by a method {@code T foo()} or {@code T getFoo()}) can have a setter
   * method {@code foo(T)} or {@code setFoo(T)} that returns the builder type. Additionally, it
   * can have a setter with a type that can be copied to {@code T} through a {@code copyOf} method;
   * for example a property {@code foo} of type {@code ImmutableSet<String>} can be set with a
   * method {@code setFoo(Collection<String> foos)}.
   */
  public class PropertySetter {
    private final String name;
    private final String parameterTypeString;
    private final String copyOf;

    public PropertySetter(
        ExecutableElement setter, TypeMirror propertyType, TypeSimplifier typeSimplifier) {
      this.name = setter.getSimpleName().toString();
      TypeMirror parameterType = Iterables.getOnlyElement(setter.getParameters()).asType();
      String simplifiedParameterType = typeSimplifier.simplify(parameterType);
      if (setter.isVarArgs()) {
        simplifiedParameterType = simplifiedParameterType.replaceAll("\\[\\]$", "...");
      }
      this.parameterTypeString = simplifiedParameterType;
      Types typeUtils = processingEnv.getTypeUtils();
      TypeMirror erasedPropertyType = typeUtils.erasure(propertyType);
      boolean sameType = typeUtils.isSameType(typeUtils.erasure(parameterType), erasedPropertyType);
      this.copyOf = sameType
          ? null
          : typeSimplifier.simplifyRaw(erasedPropertyType) + ".copyOf(%s)";
    }

    public String getName() {
      return name;
    }

    public String getParameterType() {
      return parameterTypeString;
    }

    public String copy(AutoValueProcessor.Property property) {
      if (copyOf == null) {
        return property.toString();
      }
      
      String copy = String.format(copyOf, property);
      
      // Add a null guard only in cases where we are using copyOf and the property is @Nullable.
      if (property.isNullable()) {
        copy = String.format("(%s == null ? null : %s)", property, copy);
      }
      
      return copy;
    }
  }

  /**
   * Information about a property builder, referenced from the autovalue.vm template. A property
   * called foo (defined by a method foo() or getFoo()) can have a property builder called
   * fooBuilder(). The type of foo must be an immutable Guava type, like ImmutableSet, and
   * fooBuilder() must return the corresponding builder, like ImmutableSet.Builder.
   */
  public class PropertyBuilder {
    private final String name;
    private final String builderType;
    private final String initializer;
    private final String copyAll;
    private final String empty;

    PropertyBuilder(
        ExecutableElement autoValuePropertyMethod,
        ExecutableElement propertyBuilderMethod,
        TypeSimplifier typeSimplifier) {
      this.name = propertyBuilderMethod.getSimpleName() + "$";
      String immutableType = typeSimplifier.simplify(autoValuePropertyMethod.getReturnType());
      int typeParamIndex = immutableType.indexOf('<');
      checkState(typeParamIndex > 0, immutableType);
      String rawImmutableType = immutableType.substring(0, typeParamIndex);
      this.builderType =
          rawImmutableType + ".Builder" + immutableType.substring(typeParamIndex);
      this.initializer = rawImmutableType + ".builder()";
      this.empty = rawImmutableType + ".of()";
      // TODO(emcmanus): clean up TypeSimplifier and remove this hack.
      // We want it to simplify com.google.common.collect.ImmutableSet.Builder<E>
      // the same way it would simplify com.google.common.collect.ImmutableSet<E>.
      // The issue is that getEnclosingElement tends to do the wrong thing in Eclipse
      // so we end up with ImmutableSet<E>.Builder<E>.
      TypeElement builderTypeElement = MoreElements.asType(
          processingEnv.getTypeUtils().asElement(propertyBuilderMethod.getReturnType()));
      Set<String> methodNames = Sets.newHashSet();
      for (ExecutableElement builderMethod :
          ElementFilter.methodsIn(builderTypeElement.getEnclosedElements())) {
        methodNames.add(builderMethod.getSimpleName().toString());
      }
      if (methodNames.contains("addAll")) {
        this.copyAll = "addAll";
      } else if (methodNames.contains("putAll")) {
        this.copyAll = "putAll";
      } else {
        throw new AssertionError("Builder contains neither addAll nor putAll: " + methodNames);
      }
    }

    /** The name of the field to hold this builder. */
    public String getName() {
      return name;
    }

    /** The type of the builder, for example {@code ImmutableSet.Builder<String>}. */
    public String getBuilderType() {
      return builderType;
    }

    /** An initializer for the builder field, for example {@code ImmutableSet.builder()}. */
    public String getInitializer() {
      return initializer;
    }

    /**
     * A method to return an empty collection of the type that this builder builds. For example,
     * if this is an {@code ImmutableList<String>} then the method {@code ImmutableList.of()} will
     * correctly return an empty {@code ImmutableList<String>}, assuming the appropriate context for
     * type inference.
     */
    public String getEmpty() {
      return empty;
    }

    /**
     * The method to copy another collection into this builder. It is {@code addAll} for
     * one-dimensional collections like {@code ImmutableList} and {@code ImmutableSet}, and it is
     * {@code putAll} for two-dimensional collections like {@code ImmutableMap} and
     * {@code ImmutableTable}.
     */
    public String getCopyAll() {
      return copyAll;
    }
  }

  /**
   * Returns a representation of the given {@code @AutoValue.Builder} class or interface. If the
   * class or interface has abstract methods that could not be part of any builder, emits error
   * messages and returns null.
   */
  private Optional<Builder> builderFrom(TypeElement builderTypeElement) {

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
    return Optional.of(new Builder(builderTypeElement));
  }

  // Return a set of all abstract methods in the given TypeElement or inherited from ancestors.
  private Set<ExecutableElement> abstractMethods(TypeElement typeElement) {
    Set<ExecutableElement> methods = getLocalAndInheritedMethods(
        typeElement, processingEnv.getElementUtils());
    ImmutableSet.Builder<ExecutableElement> abstractMethods = ImmutableSet.builder();
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(Modifier.ABSTRACT)) {
        abstractMethods.add(method);
      }
    }
    return abstractMethods.build();
  }

  private String typeParamsString() {
    return TypeSimplifier.actualTypeParametersString(autoValueClass);
  }
}
