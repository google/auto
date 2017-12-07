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
import static java.util.stream.Collectors.toList;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.auto.value.processor.AutoValueProcessor.Property;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  private static final ImmutableSet<ElementKind> CLASS_OR_INTERFACE =
      Sets.immutableEnumSet(ElementKind.CLASS, ElementKind.INTERFACE);

  /**
   * Determines if the {@code @AutoValue} class for this instance has a correct nested
   * {@code @AutoValue.Builder} class or interface and return a representation of it in an
   * {@code Optional} if so.
   */
  Optional<Builder> getBuilder() {
    Optional<TypeElement> builderTypeElement = Optional.empty();
    for (TypeElement containedClass : ElementFilter.typesIn(autoValueClass.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(containedClass, AutoValue.Builder.class)) {
        if (!CLASS_OR_INTERFACE.contains(containedClass.getKind())) {
          errorReporter.reportError(
              "@AutoValue.Builder can only apply to a class or an interface", containedClass);
        } else if (!containedClass.getModifiers().contains(Modifier.STATIC)) {
          errorReporter.reportError(
              "@AutoValue.Builder cannot be applied to a non-static class", containedClass);
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
      return Optional.empty();
    }
  }

  /**
   * Representation of an {@code AutoValue.Builder} class or interface.
   */
  class Builder {
    private final TypeElement builderTypeElement;
    private ImmutableSet<ExecutableElement> toBuilderMethods;

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

      List<String> builderTypeParamNames =
          builderTypeElement.getTypeParameters().stream()
              .map(e -> e.getSimpleName().toString())
              .collect(toList());

      ImmutableSet.Builder<ExecutableElement> methods = ImmutableSet.builder();
      for (ExecutableElement method : abstractMethods) {
        if (builderTypeElement.equals(typeUtils.asElement(method.getReturnType()))) {
          methods.add(method);
          DeclaredType returnType = MoreTypes.asDeclared(method.getReturnType());
          List<String> typeArguments = returnType.getTypeArguments().stream()
              .filter(t -> t.getKind().equals(TypeKind.TYPEVAR))
              .map(t -> typeUtils.asElement(t).getSimpleName().toString())
              .collect(toList());
          if (!builderTypeParamNames.equals(typeArguments)) {
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
      this.toBuilderMethods = builderMethods;
      return builderMethods;
    }

    void defineVars(
        AutoValueTemplateVars vars,
        ImmutableBiMap<ExecutableElement, String> getterToPropertyName) {
      Iterable<ExecutableElement> builderMethods = abstractMethods(builderTypeElement);
      boolean autoValueHasToBuilder = !toBuilderMethods.isEmpty();
      Optional<BuilderMethodClassifier> optionalClassifier = BuilderMethodClassifier.classify(
          builderMethods,
          errorReporter,
          processingEnv,
          autoValueClass,
          builderTypeElement,
          getterToPropertyName,
          autoValueHasToBuilder);
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
      vars.builderFormalTypes = TypeEncoder.formalTypeParametersString(builderTypeElement);
      vars.builderActualTypes = TypeSimplifier.actualTypeParametersString(builderTypeElement);
      vars.buildMethod = Optional.of(new AutoValueProcessor.SimpleMethod(buildMethod));
      vars.builderGetters = classifier.builderGetters();

      ImmutableMultimap.Builder<String, PropertySetter> setterBuilder = ImmutableMultimap.builder();
      for (Map.Entry<String, ExecutableElement> entry :
          classifier.propertyNameToSetters().entries()) {
        String property = entry.getKey();
        ExecutableElement setter = entry.getValue();
        TypeMirror propertyType = getterToPropertyName.inverse().get(property).getReturnType();
        setterBuilder.put(property, new PropertySetter(setter, propertyType));
      }
      vars.builderSetters = setterBuilder.build();

      vars.builderPropertyBuilders =
          ImmutableMap.copyOf(classifier.propertyNameToPropertyBuilder());

      Set<Property> required = Sets.newLinkedHashSet(vars.props);
      for (Property property : vars.props) {
        if (property.isNullable()
            || property.getOptional() != null
            || vars.builderPropertyBuilders.containsKey(property.getName())) {
          required.remove(property);
        }
      }
      vars.builderRequiredProperties = ImmutableSet.copyOf(required);
    }
  }

  /**
   * Information about a builder property getter, referenced from the autovalue.vm template. A
   * property called foo (defined by a method {@code T foo()} or {@code T getFoo()}) can have a
   * getter method in the builder with the same name ({@code foo()} or {@code getFoo()}) and a
   * return type of either {@code T} or {@code Optional<T>}. The {@code Optional<T>} form can be
   * used to tell whether the property has been set. Here, {@code Optional<T>} can be either
   * {@code java.util.Optional} or {@code com.google.common.base.Optional}. If {@code T} is {@code
   * int}, {@code long}, or {@code double}, then instead of {@code Optional<T>} we can have {@code
   * OptionalInt} etc. If {@code T} is a primitive type (including these ones but also the other
   * five) then {@code Optional<T>} can be the corresponding boxed type.
   */
  public static class PropertyGetter {
    private final String access;
    private final String type;
    private final Optionalish optional;

    /**
     * Makes a new {@code PropertyGetter} instance.
     *
     * @param method the source method which this getter is implementing.
     * @param type the type that the getter returns. This is written to take imports into account,
     *     so it might be {@code List<String>} for example. It is either identical to the type of
     *     the corresponding getter in the {@code @AutoValue} class, or it is an optional wrapper,
     *     like {@code Optional<List<String>>}.
     * @param optional a representation of the {@code Optional} type that the getter returns, if
     *     this is an optional getter, or null otherwise. An optional getter is one that returns
     *     {@code Optional<T>} rather than {@code T}, as explained above.
     */
    PropertyGetter(ExecutableElement method, String type, Optionalish optional) {
      this.access = AutoValueProcessor.access(method);
      this.type = type;
      this.optional = optional;
    }

    public String getAccess() {
      return access;
    }

    public String getType() {
      return type;
    }

    public Optionalish getOptional() {
      return optional;
    }
  }

  /**
   * Information about a property setter, referenced from the autovalue.vm template. A property
   * called foo (defined by a method {@code T foo()} or {@code T getFoo()}) can have a setter
   * method {@code foo(T)} or {@code setFoo(T)} that returns the builder type. Additionally, it
   * can have a setter with a type that can be copied to {@code T} through a {@code copyOf} method;
   * for example a property {@code foo} of type {@code ImmutableSet<String>} can be set with a
   * method {@code setFoo(Collection<String> foos)}. And, if {@code T} is {@code Optional},
   * it can have a setter with a type that can be copied to {@code T} through {@code Optional.of}.
   */
  public class PropertySetter {
    private final String access;
    private final String name;
    private final String parameterTypeString;
    private final boolean primitiveParameter;
    private final String copyOf;

    public PropertySetter(ExecutableElement setter, TypeMirror propertyType) {
      this.access = AutoValueProcessor.access(setter);
      this.name = setter.getSimpleName().toString();
      TypeMirror parameterType = Iterables.getOnlyElement(setter.getParameters()).asType();
      primitiveParameter = parameterType.getKind().isPrimitive();
      this.parameterTypeString = parameterTypeString(setter, parameterType);
      Types typeUtils = processingEnv.getTypeUtils();
      TypeMirror erasedPropertyType = typeUtils.erasure(propertyType);
      boolean sameType = typeUtils.isSameType(typeUtils.erasure(parameterType), erasedPropertyType);
      if (sameType) {
        this.copyOf = null;
      } else {
        String rawTarget = TypeEncoder.encodeRaw(erasedPropertyType);
        String of = Optionalish.isOptional(propertyType) ? "of" : "copyOf";
        this.copyOf = rawTarget + "." + of + "(%s)";
      }
    }

    private String parameterTypeString(ExecutableElement setter, TypeMirror parameterType) {
      if (setter.isVarArgs()) {
        TypeMirror componentType = MoreTypes.asArray(parameterType).getComponentType();
        // This is a bit ugly. It's OK to annotate just the component type, because if it is
        // say `@Nullable String` then we will end up with `@Nullable String...`. Unlike the
        // normal array case, we can't have the situation where the array itself is annotated;
        // you can write `String @Nullable []` to mean that, but you can't write
        // `String @Nullable ...`.
        return TypeEncoder.encodeWithAnnotations(componentType) + "...";
      } else {
        return TypeEncoder.encodeWithAnnotations(parameterType);
      }
    }

    public String getAccess() {
      return access;
    }

    public String getName() {
      return name;
    }

    public String getParameterType() {
      return parameterTypeString;
    }

    public boolean getPrimitiveParameter() {
      return primitiveParameter;
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
   * Returns a representation of the given {@code @AutoValue.Builder} class or interface. If the
   * class or interface has abstract methods that could not be part of any builder, emits error
   * messages and returns Optional.empty().
   */
  private Optional<Builder> builderFrom(TypeElement builderTypeElement) {

    // We require the builder to have the same type parameters as the @AutoValue class, meaning the
    // same names and bounds. In principle the type parameters could have different names, but that
    // would be confusing, and our code would reject it anyway because it wouldn't consider that
    // the return type of Foo<U> build() was really the same as the declaration of Foo<T>. This
    // check produces a better error message in that case and similar ones.

    if (!sameTypeParameters(autoValueClass, builderTypeElement)) {
      errorReporter.reportError(
          "Type parameters of " + builderTypeElement + " must have same names and bounds as "
              + "type parameters of " + autoValueClass, builderTypeElement);
      return Optional.empty();
    }
    return Optional.of(new Builder(builderTypeElement));
  }

  private static boolean sameTypeParameters(TypeElement a, TypeElement b) {
    int nTypeParameters = a.getTypeParameters().size();
    if (nTypeParameters != b.getTypeParameters().size()) {
      return false;
    }
    for (int i = 0; i < nTypeParameters; i++) {
      TypeParameterElement aParam = a.getTypeParameters().get(i);
      TypeParameterElement bParam = b.getTypeParameters().get(i);
      if (!aParam.getSimpleName().equals(bParam.getSimpleName())) {
        return false;
      }
      Set<TypeMirror> autoValueBounds = new TypeMirrorSet(aParam.getBounds());
      Set<TypeMirror> builderBounds = new TypeMirrorSet(bParam.getBounds());
      if (!autoValueBounds.equals(builderBounds)) {
        return false;
      }
    }
    return true;
  }

  // Return a set of all abstract methods in the given TypeElement or inherited from ancestors.
  private Set<ExecutableElement> abstractMethods(TypeElement typeElement) {
    Set<ExecutableElement> methods = getLocalAndInheritedMethods(
        typeElement, processingEnv.getTypeUtils(), processingEnv.getElementUtils());
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
