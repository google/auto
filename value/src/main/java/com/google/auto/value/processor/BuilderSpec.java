/*
 * Copyright 2014 Google LLC
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
import static com.google.auto.common.MoreStreams.toImmutableSet;
import static com.google.auto.value.processor.AutoValueishProcessor.hasAnnotationMirror;
import static com.google.auto.value.processor.AutoValueishProcessor.nullableAnnotationFor;
import static com.google.auto.value.processor.ClassNames.AUTO_VALUE_BUILDER_NAME;
import static com.google.common.collect.Sets.immutableEnumSet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.lang.model.util.ElementFilter.typesIn;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.auto.value.processor.AutoValueishProcessor.Property;
import com.google.auto.value.processor.PropertyBuilderClassifier.PropertyBuilder;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
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
      immutableEnumSet(ElementKind.CLASS, ElementKind.INTERFACE);

  /**
   * Determines if the {@code @AutoValue} class for this instance has a correct nested
   * {@code @AutoValue.Builder} class or interface and return a representation of it in an {@code
   * Optional} if so.
   */
  Optional<Builder> getBuilder() {
    Optional<TypeElement> builderTypeElement = Optional.empty();
    for (TypeElement containedClass : typesIn(autoValueClass.getEnclosedElements())) {
      if (hasAnnotationMirror(containedClass, AUTO_VALUE_BUILDER_NAME)) {
        if (!CLASS_OR_INTERFACE.contains(containedClass.getKind())) {
          errorReporter.reportError(
              containedClass,
              "[AutoValueBuilderClass] @AutoValue.Builder can only apply to a class or an"
                  + " interface");
        } else if (!containedClass.getModifiers().contains(Modifier.STATIC)) {
          errorReporter.reportError(
              containedClass,
              "[AutoValueInnerBuilder] @AutoValue.Builder cannot be applied to a non-static class");
        } else if (builderTypeElement.isPresent()) {
          errorReporter.reportError(
              containedClass,
              "[AutoValueTwoBuilders] %s already has a Builder: %s",
              autoValueClass,
              builderTypeElement.get());
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

  /** Representation of an {@code AutoValue.Builder} class or interface. */
  class Builder implements AutoValueExtension.BuilderContext {
    private final TypeElement builderTypeElement;
    private ImmutableSet<ExecutableElement> toBuilderMethods;
    private ExecutableElement buildMethod;
    private BuilderMethodClassifier<?> classifier;

    Builder(TypeElement builderTypeElement) {
      this.builderTypeElement = builderTypeElement;
    }

    @Override
    public TypeElement builderType() {
      return builderTypeElement;
    }

    @Override
    public Set<ExecutableElement> builderMethods() {
      return methodsIn(autoValueClass.getEnclosedElements()).stream()
          .filter(
              m ->
                  m.getParameters().isEmpty()
                      && m.getModifiers().contains(Modifier.STATIC)
                      && !m.getModifiers().contains(Modifier.PRIVATE)
                      && erasedTypeIs(m.getReturnType(), builderTypeElement))
          .collect(toSet());
    }

    @Override
    public Optional<ExecutableElement> buildMethod() {
      Types typeUtils = processingEnv.getTypeUtils();
      DeclaredType builderTypeMirror = MoreTypes.asDeclared(builderTypeElement.asType());
      return MoreElements.getLocalAndInheritedMethods(
              builderTypeElement, typeUtils, processingEnv.getElementUtils())
          .stream()
          .filter(
              m ->
                  m.getSimpleName().contentEquals("build")
                      && !m.getModifiers().contains(Modifier.PRIVATE)
                      && !m.getModifiers().contains(Modifier.STATIC)
                      && m.getParameters().isEmpty())
          .filter(
              m -> {
                ExecutableType methodMirror =
                    MoreTypes.asExecutable(typeUtils.asMemberOf(builderTypeMirror, m));
                return erasedTypeIs(methodMirror.getReturnType(), autoValueClass);
              })
          .findFirst();
    }

    @Override
    public ExecutableElement autoBuildMethod() {
      return buildMethod;
    }

    @Override
    public Map<String, Set<ExecutableElement>> setters() {
      return Maps.transformValues(
          classifier.propertyNameToSetters().asMap(),
          propertySetters ->
              propertySetters.stream().map(PropertySetter::getSetter).collect(toSet()));
    }

    @Override
    public Map<String, ExecutableElement> propertyBuilders() {
      return Maps.transformValues(
          classifier.propertyNameToPropertyBuilder(), PropertyBuilder::getPropertyBuilderMethod);
    }

    private boolean erasedTypeIs(TypeMirror type, TypeElement baseType) {
      return type.getKind().equals(TypeKind.DECLARED)
          && MoreTypes.asDeclared(type).asElement().equals(baseType);
    }

    @Override
    public Set<ExecutableElement> toBuilderMethods() {
      return toBuilderMethods;
    }

    /**
     * Finds any methods in the set that return the builder type. If the builder has type parameters
     * {@code <A, B>}, then the return type of the method must be {@code Builder<A, B>} with the
     * same parameter names. We enforce elsewhere that the names and bounds of the builder
     * parameters must be the same as those of the {@code @AutoValue} class. Here's a correct
     * example:
     *
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
     * <p>We currently impose that there cannot be more than one such method.
     */
    ImmutableSet<ExecutableElement> toBuilderMethods(
        Types typeUtils, TypeElement autoValueType, Set<ExecutableElement> abstractMethods) {

      List<String> builderTypeParamNames =
          builderTypeElement.getTypeParameters().stream()
              .map(e -> e.getSimpleName().toString())
              .collect(toList());

      DeclaredType autoValueTypeMirror = MoreTypes.asDeclared(autoValueType.asType());
      ImmutableSet.Builder<ExecutableElement> methods = ImmutableSet.builder();
      for (ExecutableElement method : abstractMethods) {
        if (!method.getParameters().isEmpty()) {
          continue;
        }
        ExecutableType methodMirror =
            MoreTypes.asExecutable(typeUtils.asMemberOf(autoValueTypeMirror, method));
        TypeMirror returnTypeMirror = methodMirror.getReturnType();
        if (builderTypeElement.equals(typeUtils.asElement(returnTypeMirror))) {
          methods.add(method);
          DeclaredType returnType = MoreTypes.asDeclared(returnTypeMirror);
          List<String> typeArguments =
              returnType.getTypeArguments().stream()
                  .filter(t -> t.getKind().equals(TypeKind.TYPEVAR))
                  .map(t -> typeUtils.asElement(t).getSimpleName().toString())
                  .collect(toList());
          if (!builderTypeParamNames.equals(typeArguments)) {
            errorReporter.reportError(
                method,
                "[AutoValueBuilderConverterReturn] Builder converter method should return %s%s",
                builderTypeElement,
                TypeSimplifier.actualTypeParametersString(builderTypeElement));
          }
        }
      }
      ImmutableSet<ExecutableElement> builderMethods = methods.build();
      if (builderMethods.size() > 1) {
        errorReporter.reportError(
            builderMethods.iterator().next(),
            "[AutoValueTwoBuilderConverters] There can be at most one builder converter method");
      }
      this.toBuilderMethods = builderMethods;
      return builderMethods;
    }

    void defineVarsForAutoValue(
        AutoValueOrBuilderTemplateVars vars,
        ImmutableBiMap<ExecutableElement, String> getterToPropertyName) {
      Iterable<ExecutableElement> builderMethods =
          abstractMethods(builderTypeElement, processingEnv);
      boolean autoValueHasToBuilder = toBuilderMethods != null && !toBuilderMethods.isEmpty();
      ImmutableMap<ExecutableElement, TypeMirror> getterToPropertyType =
          TypeVariables.rewriteReturnTypes(
              processingEnv.getElementUtils(),
              processingEnv.getTypeUtils(),
              getterToPropertyName.keySet(),
              autoValueClass,
              builderTypeElement);
      ImmutableMap.Builder<String, TypeMirror> rewrittenPropertyTypes = ImmutableMap.builder();
      getterToPropertyType.forEach(
          (getter, type) -> rewrittenPropertyTypes.put(getterToPropertyName.get(getter), type));
      Optional<BuilderMethodClassifier<ExecutableElement>> optionalClassifier =
          BuilderMethodClassifierForAutoValue.classify(
              builderMethods,
              errorReporter,
              processingEnv,
              autoValueClass,
              builderTypeElement,
              getterToPropertyName,
              rewrittenPropertyTypes.build(),
              autoValueHasToBuilder);
      if (!optionalClassifier.isPresent()) {
        return;
      }
      for (ExecutableElement method : methodsIn(builderTypeElement.getEnclosedElements())) {
        if (method.getSimpleName().contentEquals("builder")
            && method.getModifiers().contains(Modifier.STATIC)
            && method.getAnnotationMirrors().isEmpty()
            && !(vars instanceof AutoBuilderTemplateVars)) {
          // For now we don't warn for methods with annotations, because for example we do want to
          // allow Jackson's @JsonCreator. We also don't warn if this is an @AutoBuilder.
          errorReporter.reportWarning(
              method,
              "[AutoValueBuilderInBuilder] Static builder() method should be in the containing"
                  + " class");
        }
      }
      defineVars(vars, optionalClassifier.get());
    }

    void defineVars(AutoValueOrBuilderTemplateVars vars, BuilderMethodClassifier<?> classifier) {
      this.classifier = classifier;
      Set<ExecutableElement> buildMethods = classifier.buildMethods();
      if (buildMethods.size() != 1) {
        Set<? extends Element> errorElements =
            buildMethods.isEmpty() ? ImmutableSet.of(builderTypeElement) : buildMethods;
        for (Element buildMethod : errorElements) {
          errorReporter.reportError(
              buildMethod,
              "[AutoValueBuilderBuild] Builder must have a single no-argument method, typically"
                  + " called build(), that returns %s%s",
              autoValueClass,
              typeParamsString());
        }
        return;
      }
      this.buildMethod = Iterables.getOnlyElement(buildMethods);
      vars.builderIsInterface = builderTypeElement.getKind() == ElementKind.INTERFACE;
      vars.builderTypeName = TypeSimplifier.classNameOf(builderTypeElement);
      vars.builderFormalTypes =
          TypeEncoder.typeParametersString(builderTypeElement.getTypeParameters());
      vars.builderActualTypes = TypeSimplifier.actualTypeParametersString(builderTypeElement);
      vars.buildMethod = Optional.of(new SimpleMethod(buildMethod));
      vars.builderGetters = classifier.builderGetters();
      vars.builderSetters = classifier.propertyNameToSetters();

      vars.builderPropertyBuilders =
          ImmutableMap.copyOf(classifier.propertyNameToPropertyBuilder());

      vars.builderRequiredProperties =
          vars.props.stream()
              .filter(p -> !p.isNullable())
              .filter(p -> p.getOptional() == null)
              .filter(p -> !vars.builderPropertyBuilders.containsKey(p.getName()))
              .collect(toImmutableSet());
    }
  }

  /**
   * Information about a builder property getter, referenced from the autovalue.vm template. A
   * property called foo (defined by a method {@code T foo()} or {@code T getFoo()}) can have a
   * getter method in the builder with the same name ({@code foo()} or {@code getFoo()}) and a
   * return type of either {@code T} or {@code Optional<T>}. The {@code Optional<T>} form can be
   * used to tell whether the property has been set. Here, {@code Optional<T>} can be either {@code
   * java.util.Optional} or {@code com.google.common.base.Optional}. If {@code T} is {@code int},
   * {@code long}, or {@code double}, then instead of {@code Optional<T>} we can have {@code
   * OptionalInt} etc. If {@code T} is a primitive type (including these ones but also the other
   * five) then {@code Optional<T>} can be the corresponding boxed type.
   */
  public static class PropertyGetter {
    private final String name;
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
      this.name = method.getSimpleName().toString();
      this.access = SimpleMethod.access(method);
      this.type = type;
      this.optional = optional;
    }

    // Not accessed from templates so doesn't have to be public.
    String getName() {
      return name;
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
   * Specifies how to copy a parameter value into the target type. This might be the identity, or it
   * might be something like {@code ImmutableList.of(...)} or {@code Optional.ofNullable(...)}.
   */
  static class Copier {
    static final Copier IDENTITY = acceptingNull(x -> x);

    private final Function<String, String> copy;
    private final boolean acceptsNull;

    private Copier(Function<String, String> copy, boolean acceptsNull) {
      this.copy = copy;
      this.acceptsNull = acceptsNull;
    }

    static Copier acceptingNull(Function<String, String> copy) {
      return new Copier(copy, true);
    }

    static Copier notAcceptingNull(Function<String, String> copy) {
      return new Copier(copy, false);
    }
  }

  /**
   * Information about a property setter, referenced from the autovalue.vm template. A property
   * called foo (defined by a method {@code T foo()} or {@code T getFoo()}) can have a setter method
   * {@code foo(T)} or {@code setFoo(T)} that returns the builder type. Additionally, it can have a
   * setter with a type that can be copied to {@code T} through a {@code copyOf} method; for example
   * a property {@code foo} of type {@code ImmutableSet<String>} can be set with a method {@code
   * setFoo(Collection<String> foos)}. And, if {@code T} is {@code Optional}, it can have a setter
   * with a type that can be copied to {@code T} through {@code Optional.of}.
   */
  public static class PropertySetter {
    private final ExecutableElement setter;
    private final String access;
    private final String name;
    private final String parameterTypeString;
    private final boolean primitiveParameter;
    private final String nullableAnnotation;
    private final Copier copier;

    PropertySetter(ExecutableElement setter, TypeMirror parameterType, Copier copier) {
      this.setter = setter;
      this.copier = copier;
      this.access = SimpleMethod.access(setter);
      this.name = setter.getSimpleName().toString();
      primitiveParameter = parameterType.getKind().isPrimitive();
      this.parameterTypeString = parameterTypeString(setter, parameterType);
      VariableElement parameterElement = Iterables.getOnlyElement(setter.getParameters());
      Optional<String> maybeNullable = nullableAnnotationFor(parameterElement, parameterType);
      this.nullableAnnotation = maybeNullable.orElse("");
    }

    ExecutableElement getSetter() {
      return setter;
    }

    private static String parameterTypeString(ExecutableElement setter, TypeMirror parameterType) {
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

    public String getNullableAnnotation() {
      return nullableAnnotation;
    }

    public String copy(Property property) {
      String copy = copier.copy.apply(property.toString());
      if (property.isNullable() && !copier.acceptsNull) {
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
          builderTypeElement,
          "[AutoValueTypeParamMismatch] Type parameters of %s must have same names and bounds as"
              + " type parameters of %s",
          builderTypeElement,
          autoValueClass);
      return Optional.empty();
    }
    return Optional.of(new Builder(builderTypeElement));
  }

  private static boolean sameTypeParameters(TypeElement a, TypeElement b) {
    return sameTypeParameters(a.getTypeParameters(), b.getTypeParameters());
  }

  static boolean sameTypeParameters(
      List<? extends TypeParameterElement> aParams, List<? extends TypeParameterElement> bParams) {
    int nTypeParameters = aParams.size();
    if (nTypeParameters != bParams.size()) {
      return false;
    }
    for (int i = 0; i < nTypeParameters; i++) {
      TypeParameterElement aParam = aParams.get(i);
      TypeParameterElement bParam = bParams.get(i);
      if (!aParam.getSimpleName().equals(bParam.getSimpleName())) {
        return false;
      }
      Set<TypeMirror> aBounds = new TypeMirrorSet(aParam.getBounds());
      Set<TypeMirror> bBounds = new TypeMirrorSet(bParam.getBounds());
      if (!aBounds.equals(bBounds)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns a set of all abstract methods in the given TypeElement or inherited from ancestors. If
   * any of the abstract methods has a return type or parameter type that is not currently defined
   * then this method will throw an exception that will cause us to defer processing of the current
   * class until a later annotation-processing round.
   */
  static ImmutableSet<ExecutableElement> abstractMethods(
      TypeElement typeElement, ProcessingEnvironment processingEnv) {
    Set<ExecutableElement> methods =
        getLocalAndInheritedMethods(
            typeElement, processingEnv.getTypeUtils(), processingEnv.getElementUtils());
    ImmutableSet.Builder<ExecutableElement> abstractMethods = ImmutableSet.builder();
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(Modifier.ABSTRACT)) {
        MissingTypes.deferIfMissingTypesIn(method);
        abstractMethods.add(method);
      }
    }
    return abstractMethods.build();
  }

  private String typeParamsString() {
    return TypeSimplifier.actualTypeParametersString(autoValueClass);
  }
}
