/*
 * Copyright 2018 Google LLC
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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.GeneratedAnnotations.generatedAnnotation;
import static com.google.auto.common.MoreElements.getPackage;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreStreams.toImmutableList;
import static com.google.auto.common.MoreStreams.toImmutableMap;
import static com.google.auto.common.MoreStreams.toImmutableSet;
import static com.google.auto.value.processor.ClassNames.AUTO_VALUE_PACKAGE_NAME;
import static com.google.auto.value.processor.ClassNames.COPY_ANNOTATIONS_NAME;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Sets.union;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.common.Visibility;
import com.google.auto.value.processor.MissingTypes.MissingTypeException;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Shared code between {@link AutoValueProcessor}, {@link AutoOneOfProcessor}, and {@link
 * AutoBuilderProcessor}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
abstract class AutoValueishProcessor extends AbstractProcessor {
  private final String annotationClassName;
  private final boolean appliesToInterfaces;

  /**
   * Qualified names of {@code @AutoValue} (etc) classes that we attempted to process but had to
   * abandon because we needed other types that they referenced and those other types were missing.
   * The corresponding value tells the name of the missing type, if known, or is empty otherwise.
   */
  private final Map<String, String> deferredTypeNames = new LinkedHashMap<>();

  AutoValueishProcessor(String annotationClassName, boolean appliesToInterfaces) {
    this.annotationClassName = annotationClassName;
    this.appliesToInterfaces = appliesToInterfaces;
  }

  /**
   * The annotation we are processing, for example {@code AutoValue} or {@code AutoBuilder}.
   */
  private TypeElement annotationType;
  /** The simple name of {@link #annotationType}. */
  private String simpleAnnotationName;

  private ErrorReporter errorReporter;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    errorReporter = new ErrorReporter(processingEnv);
    annotationType = elementUtils().getTypeElement(annotationClassName);
    if (annotationType != null) {
      simpleAnnotationName = annotationType.getSimpleName().toString();
    }
  }

  final ErrorReporter errorReporter() {
    return errorReporter;
  }

  final Types typeUtils() {
    return processingEnv.getTypeUtils();
  }

  final Elements elementUtils() {
    return processingEnv.getElementUtils();
  }

  /**
   * Qualified names of {@code @AutoValue} (etc) classes that we attempted to process but had to
   * abandon because we needed other types that they referenced and those other types were missing.
   * This is used by tests.
   */
  final ImmutableList<String> deferredTypeNames() {
    return ImmutableList.copyOf(deferredTypeNames.keySet());
  }

  @Override
  public final SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /**
   * A property of an {@code @AutoValue} (etc) class, defined by one of its abstract
   * methods. An instance of this class is made available to the Velocity template engine for each
   * property. The public methods of this class define JavaBeans-style properties that are
   * accessible from templates. For example {@link #getType()} means we can write {@code $p.type}
   * for a Velocity variable {@code $p} that is a {@code Property}.
   */
  public static class Property {
    private final String name;
    private final String identifier;
    private final String type;
    private final TypeMirror typeMirror;
    private final Optional<String> nullableAnnotation;
    private final ImmutableList<AnnotationMirror> availableNullableTypeAnnotations; // 0 or 1
    private final Optionalish optional;
    private final String getter;
    private final String builderInitializer; // empty, or with initial ` = `.
    private final boolean hasDefault;

    Property(
        String name,
        String identifier,
        String type,
        TypeMirror typeMirror,
        Optional<String> nullableAnnotation,
        Nullables nullables,
        String getter,
        Optional<String> maybeBuilderInitializer,
        boolean hasDefault) {
      this.name = name;
      this.identifier = identifier;
      this.type = type;
      this.typeMirror = typeMirror;
      this.nullableAnnotation = nullableAnnotation;
      this.availableNullableTypeAnnotations = nullables.nullableTypeAnnotations();
      this.optional = Optionalish.createIfOptional(typeMirror);
      this.builderInitializer =
          maybeBuilderInitializer.isPresent()
              ? " = " + maybeBuilderInitializer.get()
              : builderInitializer(typeMirror, nullableAnnotation);
      this.getter = getter;
      this.hasDefault = hasDefault;
    }

    /**
     * Returns the appropriate initializer for a builder property. The caller of the {@code
     * Property} constructor may have supplied an initializer, but otherwise we supply one only if
     * this property is an {@code Optional} and is not {@code @Nullable}. In that case the
     * initializer sets it to {@code Optional.empty()}.
     */
    private static String builderInitializer(
        TypeMirror typeMirror, Optional<String> nullableAnnotation) {
      if (nullableAnnotation.isPresent()) {
        return "";
      }
      Optionalish optional = Optionalish.createIfOptional(typeMirror);
      if (optional == null) {
        return "";
      }
      return " = " + optional.getEmpty();
    }

    /**
     * Returns the appropriate type for a builder field that will eventually be assigned to this
     * property. This is the same as the final property type, except that it may have an additional
     * {@code @Nullable} annotation. Some builder fields start off null and then acquire a value
     * when the corresponding setter is called. Builder fields should have an extra
     * {@code @Nullable} if all of the following conditions are met:
     *
     * <ul>
     *   <li>the property is not primitive;
     *   <li>the property is not already nullable;
     *   <li>there is no explicit initializer (for example {@code Optional} properties start off as
     *       {@code Optional.empty()});
     *   <li>we have found a {@code @Nullable} type annotation that can be applied.
     * </ul>
     */
    public String getBuilderFieldType() {
      if (typeMirror.getKind().isPrimitive()
          || nullableAnnotation.isPresent()
          || !builderInitializer.isEmpty()
          || availableNullableTypeAnnotations.isEmpty()) {
        return type;
      }
      return TypeEncoder.encodeWithAnnotations(typeMirror, availableNullableTypeAnnotations);
    }

    /**
     * Returns the name of the property as it should be used when declaring identifiers (fields and
     * parameters). If the original getter method was {@code foo()} then this will be {@code foo}.
     * If it was {@code getFoo()} then it will be {@code foo}. If it was {@code getPackage()} then
     * it will be something like {@code package0}, since {@code package} is a reserved word.
     */
    @Override
    public String toString() {
      return identifier;
    }

    /**
     * Returns the name of the property as it should be used in strings visible to users. This is
     * usually the same as {@code toString()}, except that if we had to use an identifier like
     * "package0" because "package" is a reserved word, the name here will be the original
     * "package".
     */
    public String getName() {
      return name;
    }

    TypeMirror getTypeMirror() {
      return typeMirror;
    }

    public String getType() {
      return type;
    }

    public TypeKind getKind() {
      return typeMirror.getKind();
    }

    /**
     * Returns an {@link Optionalish} representing the kind of Optional that this property's type
     * is, or null if the type is not an Optional of any kind.
     */
    public Optionalish getOptional() {
      return optional;
    }

    /**
     * Returns a string to be used as an initializer for a builder field for this property,
     * including the leading {@code =}, or an empty string if there is no explicit initializer.
     */
    public String getBuilderInitializer() {
      return builderInitializer;
    }

    /**
     * Returns the string to use as a method annotation to indicate the nullability of this
     * property. It is either the empty string, if the property is not nullable, or an annotation
     * string with a trailing space, such as {@code "@`javax.annotation.Nullable` "}, where the
     * {@code ``} is the encoding used by {@link TypeEncoder}. If the property is nullable by virtue
     * of its <i>type</i> rather than its method being {@code @Nullable}, this method returns the
     * empty string, because the {@code @Nullable} will appear when the type is spelled out. In this
     * case, {@link #nullableAnnotation} is present but empty.
     */
    public final String getNullableAnnotation() {
      return nullableAnnotation.orElse("");
    }

    public boolean isNullable() {
      return nullableAnnotation.isPresent();
    }

    /**
     * Returns the name of the getter method for this property as defined by the {@code @AutoValue}
     * or {@code @AutoBuilder} class. For property {@code foo}, this will be {@code foo} or {@code
     * getFoo} or {@code isFoo}. For AutoBuilder, the getter in question is the one that will be
     * called on the built type to derive the value of the property, in the copy constructor.
     */
    public String getGetter() {
      return getter;
    }

    boolean hasDefault() {
      return hasDefault;
    }
  }

  /** A {@link Property} that corresponds to an abstract getter method in the source. */
  public static class GetterProperty extends Property {
    private final ExecutableElement method;
    private final ImmutableList<String> fieldAnnotations;
    private final ImmutableList<String> methodAnnotations;

    GetterProperty(
        String name,
        String identifier,
        ExecutableElement method,
        TypeMirror typeMirror,
        String typeString,
        ImmutableList<String> fieldAnnotations,
        ImmutableList<String> methodAnnotations,
        Optional<String> nullableAnnotation,
        Nullables nullables) {
      super(
          name,
          identifier,
          typeString,
          typeMirror,
          nullableAnnotation,
          nullables,
          method.getSimpleName().toString(),
          Optional.empty(),
          /* hasDefault= */ false);
      this.method = method;
      this.fieldAnnotations = fieldAnnotations;
      this.methodAnnotations = methodAnnotations;
    }

    /**
     * Returns the annotations (in string form) that should be applied to the property's field
     * declaration.
     */
    public List<String> getFieldAnnotations() {
      return fieldAnnotations;
    }

    /**
     * Returns the annotations (in string form) that should be applied to the property's method
     * implementation.
     */
    public List<String> getMethodAnnotations() {
      return methodAnnotations;
    }

    public String getAccess() {
      return SimpleMethod.access(method);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof GetterProperty && ((GetterProperty) obj).method.equals(method);
    }

    @Override
    public int hashCode() {
      return method.hashCode();
    }
  }

  void addDeferredType(TypeElement type, String missingType) {
    // We save the name of the type containing the problem, rather than its TypeElement, because it
    // is not guaranteed that it will be represented by the same TypeElement on the next round. We
    // save the name of the missing type for better diagnostics. (It may be empty.)
    deferredTypeNames.put(type.getQualifiedName().toString(), missingType);
  }

  @Override
  public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (annotationType == null) {
      // This should not happen. If the annotation type is not found, how did the processor get
      // triggered?
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Did not process @"
                  + annotationClassName
                  + " because the annotation class was not found");
      return false;
    }
    ImmutableMap<TypeElement, String> deferredTypes =
        deferredTypeNames.entrySet().stream()
            .collect(
                toImmutableMap(
                    entry -> elementUtils().getTypeElement(entry.getKey()), Map.Entry::getValue));
    if (roundEnv.processingOver()) {
      // This means that the previous round didn't generate any new sources, so we can't have found
      // any new instances of @AutoValue; and we can't have any new types that are the reason a type
      // was in deferredTypes.
      deferredTypes.forEach(
          (type, missing) -> {
            String including = missing.isEmpty() ? "" : ("including " + missing);
            errorReporter.reportError(
                type,
                "[%sUndefined] Did not generate @%s class for %s because it references"
                    + " undefined types %s",
                simpleAnnotationName,
                simpleAnnotationName,
                type.getQualifiedName(),
                including);
          });
      return false;
    }
    Collection<? extends Element> annotatedElements =
        roundEnv.getElementsAnnotatedWith(annotationType);
    List<TypeElement> types =
        new ImmutableList.Builder<TypeElement>()
            .addAll(deferredTypes.keySet())
            .addAll(ElementFilter.typesIn(annotatedElements))
            .build();
    deferredTypeNames.clear();
    for (TypeElement type : types) {
      try {
        validateType(type);
        processType(type);
      } catch (AbortProcessingException e) {
        // We abandoned this type; continue with the next.
      } catch (MissingTypeException e) {
        // We abandoned this type, but only because we needed another type that it references and
        // that other type was missing. It is possible that the missing type will be generated by
        // further annotation processing, so we will try again on the next round (perhaps failing
        // again and adding it back to the list).
        addDeferredType(type, e.getMessage());
      } catch (RuntimeException e) {
        String trace = Throwables.getStackTraceAsString(e);
        errorReporter.reportError(
            type,
            "[%sException] @%s processor threw an exception: %s",
            simpleAnnotationName,
            simpleAnnotationName,
            trace);
        throw e;
      }
    }
    return false; // never claim annotation, because who knows what other processors want?
  }

  /**
   * Validations common to all the subclasses. An {@code @AutoFoo} type must be a class, or possibly
   * an interface for {@code @AutoBuilder}. If it is a class then it must have a non-private no-arg
   * constructor. And, since we'll be generating a subclass, it can't be final.
   */
  private void validateType(TypeElement type) {
    ElementKind kind = type.getKind();
    boolean kindOk =
        kind.equals(ElementKind.CLASS)
            || (appliesToInterfaces && kind.equals(ElementKind.INTERFACE));
    if (!kindOk) {
      String appliesTo = appliesToInterfaces ? "classes and interfaces" : "classes";
      errorReporter.abortWithError(
          type,
          "[%sWrongType] @%s only applies to %s",
          simpleAnnotationName,
          simpleAnnotationName,
          appliesTo);
    }
    checkModifiersIfNested(type);
    if (!hasVisibleNoArgConstructor(type)) {
      errorReporter.reportError(
          type,
          "[%sConstructor] @%s class must have a non-private no-arg constructor",
          simpleAnnotationName,
          simpleAnnotationName);
    }
    if (type.getModifiers().contains(Modifier.FINAL)) {
      errorReporter.abortWithError(
          type,
          "[%sFinal] @%s class must not be final",
          simpleAnnotationName,
          simpleAnnotationName);
    }
  }

  /**
   * Analyzes a single {@code @AutoValue} (etc) class, and outputs the corresponding implementation
   * class or classes.
   *
   * @param type the class with the {@code @AutoValue} or {@code @AutoOneOf} annotation.
   */
  abstract void processType(TypeElement type);

  /**
   * Returns the appropriate {@code @Nullable} annotation to put on the implementation of the given
   * property method, and indicates whether the property is in fact nullable. The annotation in
   * question is on the method, not its return type. If instead the return type is
   * {@code @Nullable}, this method returns {@code Optional.of("")}, to indicate that the property
   * is nullable but the <i>method</i> isn't. The {@code @Nullable} annotation will instead appear
   * when the return type of the method is spelled out in the implementation.
   */
  abstract Optional<String> nullableAnnotationForMethod(ExecutableElement propertyMethod);

  /**
   * Returns the ordered set of {@link Property} definitions for the given {@code @AutoValue} or
   * {@code AutoOneOf} type.
   *
   * @param annotatedPropertyMethods a map from property methods to the method annotations that
   *     should go on the implementation of those methods. These annotations are method annotations
   *     specifically. Type annotations do not appear because they are considered part of the return
   *     type and will appear when that is spelled out. Annotations that are excluded by {@code
   *     AutoValue.CopyAnnotations} also do not appear here.
   */
  final ImmutableSet<Property> propertySet(
      ImmutableMap<ExecutableElement, TypeMirror> propertyMethodsAndTypes,
      ImmutableListMultimap<ExecutableElement, AnnotationMirror> annotatedPropertyFields,
      ImmutableListMultimap<ExecutableElement, AnnotationMirror> annotatedPropertyMethods,
      Nullables nullables) {
    ImmutableBiMap<ExecutableElement, String> methodToPropertyName =
        propertyNameToMethodMap(propertyMethodsAndTypes.keySet()).inverse();
    Map<ExecutableElement, String> methodToIdentifier = new LinkedHashMap<>(methodToPropertyName);
    fixReservedIdentifiers(methodToIdentifier);

    ImmutableSet.Builder<Property> props = ImmutableSet.builder();
    propertyMethodsAndTypes.forEach(
        (propertyMethod, returnType) -> {
          String propertyTypeString =
              TypeEncoder.encodeWithAnnotations(
                  returnType, ImmutableList.of(), getExcludedAnnotationTypes(propertyMethod));
          String propertyName = methodToPropertyName.get(propertyMethod);
          String identifier = methodToIdentifier.get(propertyMethod);
          ImmutableList<String> fieldAnnotations =
              annotationStrings(annotatedPropertyFields.get(propertyMethod));
          ImmutableList<AnnotationMirror> methodAnnotationMirrors =
              annotatedPropertyMethods.get(propertyMethod);
          ImmutableList<String> methodAnnotations = annotationStrings(methodAnnotationMirrors);
          Optional<String> nullableAnnotation = nullableAnnotationForMethod(propertyMethod);
          Property p =
              new GetterProperty(
                  propertyName,
                  identifier,
                  propertyMethod,
                  returnType,
                  propertyTypeString,
                  fieldAnnotations,
                  methodAnnotations,
                  nullableAnnotation,
                  nullables);
          props.add(p);
          if (p.isNullable() && returnType.getKind().isPrimitive()) {
            errorReporter()
                .reportError(
                    propertyMethod,
                    "[%sNullPrimitive] Primitive types cannot be @Nullable",
                    simpleAnnotationName);
          }
        });
    return props.build();
  }

  /** Defines the template variables that are shared by AutoValue, AutoOneOf, and AutoBuilder. */
  final void defineSharedVarsForType(
      TypeElement type,
      ImmutableSet<ExecutableElement> methods,
      Nullables nullables,
      AutoValueishTemplateVars vars) {
    vars.pkg = TypeSimplifier.packageNameOf(type);
    vars.origClass = TypeSimplifier.classNameOf(type);
    vars.simpleClassName = TypeSimplifier.simpleNameOf(vars.origClass);
    vars.generated =
        generatedAnnotation(elementUtils(), processingEnv.getSourceVersion())
            .map(annotation -> TypeEncoder.encode(annotation.asType()))
            .orElse("");
    vars.formalTypes = TypeEncoder.typeParametersString(type.getTypeParameters());
    vars.actualTypes = TypeSimplifier.actualTypeParametersString(type);
    vars.wildcardTypes = wildcardTypeParametersString(type);
    vars.annotations = copiedClassAnnotations(type);
    Map<ObjectMethod, ExecutableElement> methodsToGenerate =
        determineObjectMethodsToGenerate(methods);
    vars.toString = methodsToGenerate.containsKey(ObjectMethod.TO_STRING);
    vars.equals = methodsToGenerate.containsKey(ObjectMethod.EQUALS);
    vars.hashCode = methodsToGenerate.containsKey(ObjectMethod.HASH_CODE);
    vars.equalsParameterType =
        equalsParameterType(methodsToGenerate, nullables);
    vars.serialVersionUID = getSerialVersionUID(type);
  }

  /** Returns the spelling to be used in the generated code for the given list of annotations. */
  static ImmutableList<String> annotationStrings(List<? extends AnnotationMirror> annotations) {
    return annotations.stream()
        .map(AnnotationOutput::sourceFormForAnnotation)
        .sorted() // ensures deterministic order
        .collect(toImmutableList());
  }

  /**
   * Returns the name of the generated {@code @AutoValue} (etc) class, for example {@code
   * AutoOneOf_TaskResult} or {@code $$AutoValue_SimpleMethod}.
   *
   * @param type the name of the type bearing the {@code @AutoValue} (etc) annotation.
   * @param prefix the prefix to use in the generated class. This may start with one or more dollar
   *     signs, for an {@code @AutoValue} implementation where there are AutoValue extensions.
   */
  static String generatedClassName(TypeElement type, String prefix) {
    String name = type.getSimpleName().toString();
    while (MoreElements.isType(type.getEnclosingElement())) {
      type = MoreElements.asType(type.getEnclosingElement());
      name = type.getSimpleName() + "_" + name;
    }
    String pkg = TypeSimplifier.packageNameOf(type);
    String dot = pkg.isEmpty() ? "" : ".";
    return pkg + dot + prefix + name;
  }

  private static boolean isJavaLangObject(TypeElement type) {
    return type.getSuperclass().getKind() == TypeKind.NONE && type.getKind() == ElementKind.CLASS;
  }

  enum ObjectMethod {
    NONE,
    TO_STRING,
    EQUALS,
    HASH_CODE
  }

  /**
   * Determines which of the three public non-final methods from {@code java.lang.Object}, if any,
   * is overridden by the given method.
   */
  static ObjectMethod objectMethodToOverride(ExecutableElement method) {
    String name = method.getSimpleName().toString();
    switch (method.getParameters().size()) {
      case 0:
        if (name.equals("toString")) {
          return ObjectMethod.TO_STRING;
        } else if (name.equals("hashCode")) {
          return ObjectMethod.HASH_CODE;
        }
        break;
      case 1:
        if (name.equals("equals")) {
          TypeMirror param = getOnlyElement(method.getParameters()).asType();
          if (param.getKind().equals(TypeKind.DECLARED)) {
            TypeElement paramType = MoreTypes.asTypeElement(param);
            if (paramType.getQualifiedName().contentEquals("java.lang.Object")) {
              return ObjectMethod.EQUALS;
            }
          }
        }
        break;
      default:
        // No relevant Object methods have more than one parameter.
    }
    return ObjectMethod.NONE;
  }

  /** Returns a bi-map between property names and the corresponding abstract property methods. */
  final ImmutableBiMap<String, ExecutableElement> propertyNameToMethodMap(
      Set<ExecutableElement> propertyMethods) {
    Map<String, ExecutableElement> map = new LinkedHashMap<>();
    Set<String> reportedDups = new HashSet<>();
    boolean allPrefixed = gettersAllPrefixed(propertyMethods);
    for (ExecutableElement method : propertyMethods) {
      String methodName = method.getSimpleName().toString();
      String name = allPrefixed ? nameWithoutPrefix(methodName) : methodName;
      ExecutableElement old = map.put(name, method);
      if (old != null) {
        List<ExecutableElement> contexts = new ArrayList<>(Arrays.asList(method));
        if (reportedDups.add(name)) {
          contexts.add(old);
        }
        // Report the error for both of the methods. If this is a third or further occurrence,
        // reportedDups prevents us from reporting more than one error for the same method.
        for (ExecutableElement context : contexts) {
          errorReporter.reportError(
              context,
              "[%sDupProperty] More than one @%s property called %s",
              simpleAnnotationName,
              simpleAnnotationName,
              name);
        }
      }
    }
    return ImmutableBiMap.copyOf(map);
  }

  private static boolean gettersAllPrefixed(Set<ExecutableElement> methods) {
    return prefixedGettersIn(methods).size() == methods.size();
  }

  /**
   * Returns an appropriate annotation spelling to indicate the nullability of an element. If the
   * return value is a non-empty Optional, that indicates that the element is nullable, and the
   * string should be used to annotate it. If the return value is an empty Optional, the element is
   * not nullable. The return value can be {@code Optional.of("")}, which indicates that the element
   * is nullable but that the nullability comes from a type annotation. In this case, the annotation
   * will appear when the type is written, and must not be specified again. If the Optional contains
   * a present non-empty string then that string will end with a space.
   *
   * @param element the element that might be {@code @Nullable}, either a method or a parameter.
   * @param elementType the relevant type of the element: the return type for a method, or the
   *     parameter type for a parameter.
   */
  static Optional<String> nullableAnnotationFor(Element element, TypeMirror elementType) {
    if (isNullable(elementType)) {
      return Optional.of("");
    }
    List<? extends AnnotationMirror> elementAnnotations = element.getAnnotationMirrors();
    OptionalInt nullableAnnotationIndex = nullableAnnotationIndex(elementAnnotations);
    if (nullableAnnotationIndex.isPresent()) {
      AnnotationMirror annotation = elementAnnotations.get(nullableAnnotationIndex.getAsInt());
      String annotationString = AnnotationOutput.sourceFormForAnnotation(annotation);
      return Optional.of(annotationString + " ");
    } else {
      return Optional.empty();
    }
  }

  private static OptionalInt nullableAnnotationIndex(List<? extends AnnotationMirror> annotations) {
    return IntStream.range(0, annotations.size())
        .filter(i -> isNullable(annotations.get(i)))
        .findFirst();
  }

  private static boolean isNullable(TypeMirror type) {
    return isNullable(type, 0);
  }

  private static boolean isNullable(TypeMirror type, int depth) {
    // Some versions of the Eclipse compiler can report that the upper bound of a type variable T
    // is another T, and if you ask for the upper bound of that other T you'll get a third T, and so
    // ad infinitum. To avoid StackOverflowError, we bottom out after 10 iterations.
    if (depth > 10) {
      return false;
    }
    List<? extends AnnotationMirror> typeAnnotations = type.getAnnotationMirrors();
    // TODO(emcmanus): also check if there is a @NonNull bound and return false if so.
    if (nullableAnnotationIndex(typeAnnotations).isPresent()) {
      return true;
    }
    if (type.getKind().equals(TypeKind.TYPEVAR)) {
      TypeVariable typeVariable = MoreTypes.asTypeVariable(type);
      TypeMirror bound = typeVariable.getUpperBound();
      if (bound.getKind().equals(TypeKind.INTERSECTION)) {
        return MoreTypes.asIntersection(bound).getBounds().stream()
            .allMatch(t -> isNullable(t, depth + 1));
      }
      return isNullable(bound, depth + 1);
    }
    return false;
  }

  private static boolean isNullable(AnnotationMirror annotation) {
    return annotation.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable");
  }

  /**
   * Returns the subset of the given zero-arg methods whose names begin with {@code get}. Also
   * includes {@code isFoo} methods if they return {@code boolean}. This corresponds to JavaBeans
   * conventions.
   */
  static ImmutableSet<ExecutableElement> prefixedGettersIn(Collection<ExecutableElement> methods) {
    return methods.stream()
        .filter(AutoValueishProcessor::isPrefixedGetter)
        .collect(toImmutableSet());
  }

  static boolean isPrefixedGetter(ExecutableElement method) {
    String name = method.getSimpleName().toString();
    // Note that getfoo() (without a capital) is still a getter.
    return (name.startsWith("get") && !name.equals("get"))
        || (name.startsWith("is")
            && !name.equals("is")
            && method.getReturnType().getKind() == TypeKind.BOOLEAN);
  }

  /**
   * Returns the name of the property defined by the given getter. A getter called {@code getFoo()}
   * or {@code isFoo()} defines a property called {@code foo}. For consistency with JavaBeans, a
   * getter called {@code getHTMLPage()} defines a property called {@code HTMLPage}. The <a
   * href="https://docs.oracle.com/javase/8/docs/api/java/beans/Introspector.html#decapitalize-java.lang.String-">
   * rule</a> is: the name of the property is the part after {@code get} or {@code is}, with the
   * first letter lowercased <i>unless</i> the first two letters are uppercase. This works well for
   * the {@code HTMLPage} example, but in these more enlightened times we use {@code HtmlPage}
   * anyway, so the special behaviour is not useful, and of course it behaves poorly with examples
   * like {@code OAuth}.
   */
  static String nameWithoutPrefix(String name) {
    if (name.startsWith("get")) {
      name = name.substring(3);
    } else {
      assert name.startsWith("is");
      name = name.substring(2);
    }
    return PropertyNames.decapitalizeLikeJavaBeans(name);
  }

  /**
   * Checks that, if the given {@code @AutoValue}, {@code @AutoOneOf}, or {@code @AutoBuilder} class
   * is nested, it is static and not private. This check is not necessary for correctness, since the
   * generated code would not compile if the check fails, but it produces better error messages for
   * the user.
   */
  final void checkModifiersIfNested(TypeElement type) {
    checkModifiersIfNested(type, type, simpleAnnotationName);
  }

  final void checkModifiersIfNested(TypeElement type, TypeElement reportedType, String what) {
    ElementKind enclosingKind = type.getEnclosingElement().getKind();
    if (enclosingKind.isClass() || enclosingKind.isInterface()) {
      if (type.getModifiers().contains(Modifier.PRIVATE)) {
        errorReporter.abortWithError(
            reportedType, "[%sPrivate] @%s class must not be private", simpleAnnotationName, what);
      } else if (Visibility.effectiveVisibilityOfElement(type).equals(Visibility.PRIVATE)) {
        // The previous case, where the class itself is private, is much commoner so it deserves
        // its own error message, even though it would be caught by the test here too.
        errorReporter.abortWithError(
            reportedType,
            "[%sInPrivate] @%s class must not be nested in a private class",
            simpleAnnotationName,
            what);
      }
      if (!type.getModifiers().contains(Modifier.STATIC)) {
        errorReporter.abortWithError(
            reportedType, "[%sInner] Nested @%s class must be static", simpleAnnotationName, what);
      }
    }
    // In principle type.getEnclosingElement() could be an ExecutableElement (for a class
    // declared inside a method), but since RoundEnvironment.getElementsAnnotatedWith doesn't
    // return such classes we won't see them here.
  }

  /**
   * Modifies the values of the given map to avoid reserved words. If we have a getter called {@code
   * getPackage()} then we can't use the identifier {@code package} to represent its value since
   * that's a reserved word.
   */
  static void fixReservedIdentifiers(Map<?, String> methodToIdentifier) {
    for (Map.Entry<?, String> entry : methodToIdentifier.entrySet()) {
      String name = entry.getValue();
      if (SourceVersion.isKeyword(name) || !Character.isJavaIdentifierStart(name.codePointAt(0))) {
        entry.setValue(disambiguate(name, methodToIdentifier.values()));
      }
    }
  }

  private static String disambiguate(String name, Collection<String> existingNames) {
    if (!Character.isJavaIdentifierStart(name.codePointAt(0))) {
      // You've defined a getter called get1st(). What were you thinking?
      name = "_" + name;
      if (!existingNames.contains(name)) {
        return name;
      }
    }
    for (int i = 0; ; i++) {
      String candidate = name + i;
      if (!existingNames.contains(candidate)) {
        return candidate;
      }
    }
  }

  /**
   * Given a list of all methods defined in or inherited by a class, returns a map indicating which
   * of equals, hashCode, and toString should be generated. Each value in the map is the method that
   * will be overridden by the generated method, which might be a method in {@code Object} or an
   * abstract method in the {@code @AutoValue} class or an ancestor.
   */
  private static Map<ObjectMethod, ExecutableElement> determineObjectMethodsToGenerate(
      Set<ExecutableElement> methods) {
    Map<ObjectMethod, ExecutableElement> methodsToGenerate = new EnumMap<>(ObjectMethod.class);
    for (ExecutableElement method : methods) {
      ObjectMethod override = objectMethodToOverride(method);
      boolean canGenerate =
          method.getModifiers().contains(Modifier.ABSTRACT)
              || isJavaLangObject(MoreElements.asType(method.getEnclosingElement()));
      if (!override.equals(ObjectMethod.NONE) && canGenerate) {
        methodsToGenerate.put(override, method);
      }
    }
    return methodsToGenerate;
  }

  /**
   * Returns the encoded parameter type of the {@code equals(Object)} method that is to be
   * generated, or an empty string if the method is not being generated. The parameter type includes
   * any type annotations, for example {@code @Nullable}.
   *
   * @param methodsToGenerate the Object methods that are being generated
   * @param nullable the type of a {@code @Nullable} type annotation that we have found, if any
   */
  static String equalsParameterType(
      Map<ObjectMethod, ExecutableElement> methodsToGenerate, Nullables nullables) {
    ExecutableElement equals = methodsToGenerate.get(ObjectMethod.EQUALS);
    if (equals == null) {
      return ""; // this will not be referenced because no equals method will be generated
    }
    TypeMirror parameterType = equals.getParameters().get(0).asType();
    // Add @Nullable if we know one and the parameter doesn't already have one.
    // The @Nullable we add will be a type annotation, but if the parameter already has @Nullable
    // then that might be a type annotation or an annotation on the parameter.
    ImmutableList<AnnotationMirror> extraAnnotations =
        nullableAnnotationFor(equals, parameterType).isPresent()
            ? ImmutableList.of()
            : nullables.nullableTypeAnnotations();
    return TypeEncoder.encodeWithAnnotations(parameterType, extraAnnotations);
  }

  /**
   * Returns the subset of all abstract methods in the given set of methods. A given method
   * signature is only mentioned once, even if it is inherited on more than one path. If any of the
   * abstract methods has a return type or parameter type that is not currently defined then this
   * method will throw an exception that will cause us to defer processing of the current class
   * until a later annotation-processing round.
   */
  static ImmutableSet<ExecutableElement> abstractMethodsIn(Iterable<ExecutableElement> methods) {
    Set<Name> noArgMethods = new HashSet<>();
    ImmutableSet.Builder<ExecutableElement> abstracts = ImmutableSet.builder();
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(Modifier.ABSTRACT)) {
        MissingTypes.deferIfMissingTypesIn(method);
        boolean hasArgs = !method.getParameters().isEmpty();
        if (hasArgs || noArgMethods.add(method.getSimpleName())) {
          // If an abstract method with the same signature is inherited on more than one path,
          // we only add it once. At the moment we only do this check for no-arg methods. All
          // methods that AutoValue will implement are either no-arg methods or equals(Object).
          // The former is covered by this check and the latter will lead to vars.equals being
          // set to true, regardless of how many times it appears. So the only case that is
          // covered imperfectly here is that of a method that is inherited on more than one path
          // and that will be consumed by an extension. We could check parameters as well, but that
          // can be a bit tricky if any of the parameters are generic.
          abstracts.add(method);
        }
      }
    }
    return abstracts.build();
  }

  /**
   * Returns the subset of property methods in the given set of abstract methods, with their actual
   * return types. A property method has no arguments, is not void, and is not {@code hashCode()} or
   * {@code toString()}.
   */
  ImmutableMap<ExecutableElement, TypeMirror> propertyMethodsIn(
      Set<ExecutableElement> abstractMethods, TypeElement autoValueOrOneOfType) {
    DeclaredType declaredType = MoreTypes.asDeclared(autoValueOrOneOfType.asType());
    ImmutableSet.Builder<ExecutableElement> properties = ImmutableSet.builder();
    for (ExecutableElement method : abstractMethods) {
      if (method.getParameters().isEmpty()
          && (method.getReturnType().getKind() != TypeKind.VOID || propertiesCanBeVoid())
          && objectMethodToOverride(method) == ObjectMethod.NONE) {
        properties.add(method);
      }
    }
    return new EclipseHack(processingEnv).methodReturnTypes(properties.build(), declaredType);
  }

  /** True if void properties are allowed. */
  boolean propertiesCanBeVoid() {
    return false;
  }

  /**
   * Checks that the return type of the given property method is allowed. Currently, this means that
   * it cannot be an array, unless it is a primitive array.
   */
  final void checkReturnType(TypeElement autoValueClass, ExecutableElement getter) {
    TypeMirror type = getter.getReturnType();
    if (type.getKind() == TypeKind.ARRAY) {
      TypeMirror componentType = MoreTypes.asArray(type).getComponentType();
      if (componentType.getKind().isPrimitive()) {
        warnAboutPrimitiveArrays(autoValueClass, getter);
      } else {
        errorReporter.reportError(
            getter,
            "[AutoValueArray] An @%s class cannot define an array-valued property unless it is a"
                + " primitive array",
            simpleAnnotationName);
      }
    }
  }

  private void warnAboutPrimitiveArrays(TypeElement autoValueClass, ExecutableElement getter) {
    boolean suppressed = false;
    Optional<AnnotationMirror> maybeAnnotation =
        getAnnotationMirror(getter, "java.lang.SuppressWarnings");
    if (maybeAnnotation.isPresent()) {
      AnnotationValue listValue = getAnnotationValue(maybeAnnotation.get(), "value");
      suppressed = listValue.accept(new ContainsMutableVisitor(), null);
    }
    if (!suppressed) {
      // If the primitive-array property method is defined directly inside the @AutoValue class,
      // then our error message should point directly to it. But if it is inherited, we don't
      // want to try to make the error message point to the inherited definition, since that would
      // be confusing (there is nothing wrong with the definition itself), and won't work if the
      // inherited class is not being recompiled. Instead, in this case we point to the @AutoValue
      // class itself, and we include extra text in the error message that shows the full name of
      // the inherited method.
      boolean sameClass = getter.getEnclosingElement().equals(autoValueClass);
      Element element = sameClass ? getter : autoValueClass;
      String context = sameClass ? "" : (" Method: " + getter.getEnclosingElement() + "." + getter);
      errorReporter.reportWarning(
          element,
          "[AutoValueMutable] An @%s property that is a primitive array returns the original"
              + " array, which can therefore be modified by the caller. If this is OK, you can"
              + " suppress this warning with @SuppressWarnings(\"mutable\"). Otherwise, you should"
              + " replace the property with an immutable type, perhaps a simple wrapper around the"
              + " original array.%s",
          simpleAnnotationName,
          context);
    }
  }

  // Detects whether the visited AnnotationValue is an array that contains the string "mutable".
  // The simpler approach using Element.getAnnotation(SuppressWarnings.class) doesn't work if
  // the annotation has an undefined reference, like @SuppressWarnings(UNDEFINED).
  // TODO(emcmanus): replace with a method from auto-common when that is available.
  private static class ContainsMutableVisitor extends SimpleAnnotationValueVisitor8<Boolean, Void> {
    @Override
    public Boolean visitArray(List<? extends AnnotationValue> list, Void p) {
      return list.stream().map(AnnotationValue::getValue).anyMatch("mutable"::equals);
    }
  }

  /**
   * Returns a string like {@code "private static final long serialVersionUID = 1234L"} if {@code
   * type instanceof Serializable} and defines {@code serialVersionUID = 1234L}; otherwise {@code
   * ""}.
   */
  final String getSerialVersionUID(TypeElement type) {
    TypeMirror serializable = elementUtils().getTypeElement(Serializable.class.getName()).asType();
    if (typeUtils().isAssignable(type.asType(), serializable)) {
      List<VariableElement> fields = ElementFilter.fieldsIn(type.getEnclosedElements());
      for (VariableElement field : fields) {
        if (field.getSimpleName().contentEquals("serialVersionUID")) {
          Object value = field.getConstantValue();
          if (field.getModifiers().containsAll(Arrays.asList(Modifier.STATIC, Modifier.FINAL))
              && field.asType().getKind() == TypeKind.LONG
              && value != null) {
            return "private static final long serialVersionUID = " + value + "L;";
          } else {
            errorReporter.reportError(
                field, "serialVersionUID must be a static final long compile-time constant");
            break;
          }
        }
      }
    }
    return "";
  }

  /** Implements the semantics of {@code AutoValue.CopyAnnotations}; see its javadoc. */
  static ImmutableList<AnnotationMirror> annotationsToCopy(
      Element autoValueType,
      Element typeOrMethod,
      Set<String> excludedAnnotations,
      Types typeUtils) {
    ImmutableList.Builder<AnnotationMirror> result = ImmutableList.builder();
    for (AnnotationMirror annotation : typeOrMethod.getAnnotationMirrors()) {
      String annotationFqName = getAnnotationFqName(annotation);
      // To be included, the annotation should not be in com.google.auto.value,
      // and it should not be in the excludedAnnotations set.
      if (!isInAutoValuePackage(annotationFqName)
          && !excludedAnnotations.contains(annotationFqName)
          && annotationVisibleFrom(annotation, autoValueType, typeUtils)) {
        result.add(annotation);
      }
    }

    return result.build();
  }

  /**
   * True if the given class name is in the com.google.auto.value package or a subpackage. False if
   * the class name contains {@code Test}, since many AutoValue tests under com.google.auto.value
   * define their own annotations.
   */
  private static boolean isInAutoValuePackage(String className) {
    return className.startsWith(AUTO_VALUE_PACKAGE_NAME) && !className.contains("Test");
  }

  ImmutableList<String> copiedClassAnnotations(TypeElement type) {
    // Only copy annotations from a class if it has @AutoValue.CopyAnnotations.
    if (hasAnnotationMirror(type, COPY_ANNOTATIONS_NAME)) {
      Set<String> excludedAnnotations =
          ImmutableSet.<String>builder()
              .addAll(getExcludedAnnotationClassNames(type))
              .addAll(getAnnotationsMarkedWithInherited(type))
              //
              // Kotlin classes have an intrinsic @Metadata annotation generated
              // onto them by kotlinc. This annotation is specific to the annotated
              // class and should not be implicitly copied. Doing so can mislead
              // static analysis or metaprogramming tooling that reads the data
              // contained in these annotations.
              //
              // It may be surprising to see AutoValue classes written in Kotlin
              // when they could be written as Kotlin data classes, but this can
              // come up in cases where consumers rely on AutoValue features or
              // extensions that are not available in data classes.
              //
              // See: https://github.com/google/auto/issues/1087
              //
              .add(ClassNames.KOTLIN_METADATA_NAME)
              .build();

      return copyAnnotations(type, type, excludedAnnotations);
    } else {
      return ImmutableList.of();
    }
  }

  /** Implements the semantics of {@code AutoValue.CopyAnnotations}; see its javadoc. */
  ImmutableList<String> copyAnnotations(
      Element autoValueType, Element typeOrMethod, Set<String> excludedAnnotations) {
    ImmutableList<AnnotationMirror> annotationsToCopy =
        annotationsToCopy(autoValueType, typeOrMethod, excludedAnnotations, typeUtils());
    return annotationStrings(annotationsToCopy);
  }

  /**
   * Returns the contents of the {@code AutoValue.CopyAnnotations.exclude} element, as a set of
   * {@code TypeMirror} where each type is an annotation type.
   */
  private static Set<TypeMirror> getExcludedAnnotationTypes(Element element) {
    Optional<AnnotationMirror> maybeAnnotation =
        getAnnotationMirror(element, COPY_ANNOTATIONS_NAME);
    if (!maybeAnnotation.isPresent()) {
      return ImmutableSet.of();
    }

    @SuppressWarnings("unchecked")
    List<AnnotationValue> excludedClasses =
        (List<AnnotationValue>) getAnnotationValue(maybeAnnotation.get(), "exclude").getValue();
    return excludedClasses.stream()
        .map(annotationValue -> (DeclaredType) annotationValue.getValue())
        .collect(toCollection(TypeMirrorSet::new));
  }

  /**
   * Returns the contents of the {@code AutoValue.CopyAnnotations.exclude} element, as a set of
   * strings that are fully-qualified class names.
   */
  static Set<String> getExcludedAnnotationClassNames(Element element) {
    return getExcludedAnnotationTypes(element).stream()
        .map(MoreTypes::asTypeElement)
        .map(typeElement -> typeElement.getQualifiedName().toString())
        .collect(toSet());
  }

  static Set<String> getAnnotationsMarkedWithInherited(Element element) {
    return element.getAnnotationMirrors().stream()
        .filter(a -> isAnnotationPresent(a.getAnnotationType().asElement(), Inherited.class))
        .map(a -> getAnnotationFqName(a))
        .collect(toSet());
  }

  /**
   * Returns the fully-qualified name of an annotation-mirror, e.g.
   * "com.google.auto.value.AutoValue".
   */
  private static String getAnnotationFqName(AnnotationMirror annotation) {
    return ((QualifiedNameable) annotation.getAnnotationType().asElement())
        .getQualifiedName()
        .toString();
  }

  static ImmutableListMultimap<ExecutableElement, AnnotationMirror> propertyMethodAnnotationMap(
      TypeElement type, ImmutableSet<ExecutableElement> propertyMethods, Types typeUtils) {
    ImmutableListMultimap.Builder<ExecutableElement, AnnotationMirror> builder =
        ImmutableListMultimap.builder();
    for (ExecutableElement propertyMethod : propertyMethods) {
      builder.putAll(propertyMethod, propertyMethodAnnotations(type, propertyMethod, typeUtils));
    }
    return builder.build();
  }

  static ImmutableList<AnnotationMirror> propertyMethodAnnotations(
      TypeElement type, ExecutableElement method, Types typeUtils) {
    ImmutableSet<String> excludedAnnotations =
        ImmutableSet.<String>builder()
            .addAll(getExcludedAnnotationClassNames(method))
            .add(Override.class.getCanonicalName())
            .build();

    // We need to exclude type annotations from the ones being output on the method, since
    // they will be output as part of the method's return type.
    Set<String> returnTypeAnnotations = getReturnTypeAnnotations(method, a -> true);
    Set<String> excluded = union(excludedAnnotations, returnTypeAnnotations);
    return annotationsToCopy(type, method, excluded, typeUtils);
  }

  final ImmutableListMultimap<ExecutableElement, AnnotationMirror> propertyFieldAnnotationMap(
      TypeElement type, ImmutableSet<ExecutableElement> propertyMethods) {
    ImmutableListMultimap.Builder<ExecutableElement, AnnotationMirror> builder =
        ImmutableListMultimap.builder();
    for (ExecutableElement propertyMethod : propertyMethods) {
      builder.putAll(propertyMethod, propertyFieldAnnotations(type, propertyMethod));
    }
    return builder.build();
  }

  private ImmutableList<AnnotationMirror> propertyFieldAnnotations(
      TypeElement type, ExecutableElement method) {
    if (!hasAnnotationMirror(method, COPY_ANNOTATIONS_NAME)) {
      return ImmutableList.of();
    }
    ImmutableSet<String> excludedAnnotations =
        ImmutableSet.<String>builder()
            .addAll(getExcludedAnnotationClassNames(method))
            .add(Override.class.getCanonicalName())
            .build();

    // We need to exclude type annotations from the ones being output on the method, since
    // they will be output as part of the field's type.
    Set<String> returnTypeAnnotations =
        getReturnTypeAnnotations(method, this::annotationAppliesToFields);
    Set<String> nonFieldAnnotations =
        method.getAnnotationMirrors().stream()
            .map(a -> a.getAnnotationType().asElement())
            .map(MoreElements::asType)
            .filter(a -> !annotationAppliesToFields(a))
            .map(e -> e.getQualifiedName().toString())
            .collect(toSet());

    Set<String> excluded =
        ImmutableSet.<String>builder()
            .addAll(excludedAnnotations)
            .addAll(returnTypeAnnotations)
            .addAll(nonFieldAnnotations)
            .build();
    return annotationsToCopy(type, method, excluded, typeUtils());
  }

  private static Set<String> getReturnTypeAnnotations(
      ExecutableElement method, Predicate<TypeElement> typeFilter) {
    return method.getReturnType().getAnnotationMirrors().stream()
        .map(a -> a.getAnnotationType().asElement())
        .map(MoreElements::asType)
        .filter(typeFilter)
        .map(e -> e.getQualifiedName().toString())
        .collect(toSet());
  }

  private boolean annotationAppliesToFields(TypeElement annotation) {
    Target target = annotation.getAnnotation(Target.class);
    return target == null || Arrays.asList(target.value()).contains(ElementType.FIELD);
  }

  private static boolean annotationVisibleFrom(
      AnnotationMirror annotation, Element from, Types typeUtils) {
    Element annotationElement = annotation.getAnnotationType().asElement();
    Visibility visibility = Visibility.effectiveVisibilityOfElement(annotationElement);
    switch (visibility) {
      case PUBLIC:
        return true;
      case PROTECTED:
        // If the annotation is protected, it must be inside another class, call it C. If our
        // @AutoValue class is Foo then, for the annotation to be visible, either Foo must be in the
        // same package as C or Foo must be a subclass of C. If the annotation is visible from Foo
        // then it is also visible from our generated subclass AutoValue_Foo.
        // The protected case only applies to method annotations. An annotation on the AutoValue_Foo
        // class itself can't be protected, even if AutoValue_Foo ultimately inherits from the
        // class that defines the annotation. The JLS says "Access is permitted only within the
        // body of a subclass":
        // https://docs.oracle.com/javase/specs/jls/se8/html/jls-6.html#jls-6.6.2.1
        // AutoValue_Foo is a top-level class, so an annotation on it cannot be in the body of a
        // subclass of anything.
        return getPackage(annotationElement).equals(getPackage(from))
            || typeUtils.isSubtype(from.asType(), annotationElement.getEnclosingElement().asType());
      case DEFAULT:
        return getPackage(annotationElement).equals(getPackage(from));
      default:
        return false;
    }
  }

  /**
   * Returns the {@code @AutoValue} or {@code @AutoOneOf} type parameters, with a ? for every type.
   * If we have {@code @AutoValue abstract class Foo<T extends Something>} then this method will
   * return just {@code <?>}.
   */
  private static String wildcardTypeParametersString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      return typeParameters.stream().map(e -> "?").collect(joining(", ", "<", ">"));
    }
  }

  // TODO(emcmanus,ronshapiro): move to auto-common
  static Optional<AnnotationMirror> getAnnotationMirror(Element element, String annotationName) {
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      TypeElement annotationElement = MoreTypes.asTypeElement(annotation.getAnnotationType());
      if (annotationElement.getQualifiedName().contentEquals(annotationName)) {
        return Optional.of(annotation);
      }
    }
    return Optional.empty();
  }

  static boolean hasAnnotationMirror(Element element, String annotationName) {
    return getAnnotationMirror(element, annotationName).isPresent();
  }

  /** True if the type is a class with a non-private no-arg constructor, or is an interface. */
  static boolean hasVisibleNoArgConstructor(TypeElement type) {
    return type.getKind().isInterface()
        || constructorsIn(type.getEnclosedElements()).stream()
            .anyMatch(
                c -> c.getParameters().isEmpty() && !c.getModifiers().contains(Modifier.PRIVATE));
  }

  final void writeSourceFile(String className, String text, TypeElement originatingType) {
    try {
      JavaFileObject sourceFile =
          processingEnv.getFiler().createSourceFile(className, originatingType);
      try (Writer writer = sourceFile.openWriter()) {
        writer.write(text);
      }
    } catch (IOException e) {
      // This should really be an error, but we make it a warning in the hope of resisting Eclipse
      // bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599. If that bug manifests, we may get
      // invoked more than once for the same file, so ignoring the ability to overwrite it is the
      // right thing to do. If we are unable to write for some other reason, we should get a compile
      // error later because user code will have a reference to the code we were supposed to
      // generate (new AutoValue_Foo() or whatever) and that reference will be undefined.
      errorReporter.reportWarning(
          originatingType,
          "[AutoValueCouldNotWrite] Could not write generated class %s: %s",
          className,
          e);
    }
  }
}
