/*
 * Copyright (C) 2018 Google, Inc.
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

import static com.google.auto.common.GeneratedAnnotations.generatedAnnotation;
import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.value.processor.ClassNames.AUTO_ONE_OF_NAME;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Javac annotation processor (compiler plugin) for one-of types; user code never references this
 * class.
 *
 * @see AutoOneOf
 * @see <a href="https://github.com/google/auto/tree/master/value">AutoValue User's Guide</a>
 *
 * @author Éamonn McManus
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes(AUTO_ONE_OF_NAME)
@SupportedOptions("com.google.auto.value.OmitIdentifiers")
public class AutoOneOfProcessor extends AutoValueOrOneOfProcessor {
  public AutoOneOfProcessor() {
    super(AUTO_ONE_OF_NAME);
  }

  @Override
  void processType(TypeElement autoOneOfType) {
    if (autoOneOfType.getKind() != ElementKind.CLASS) {
      errorReporter().abortWithError(
          "@" + AUTO_ONE_OF_NAME + " only applies to classes", autoOneOfType);
    }
    checkModifiersIfNested(autoOneOfType);
    DeclaredType kindMirror = mirrorForKindType(autoOneOfType);

    // We are going to classify the methods of the @AutoOneOf class into several categories.
    // This covers the methods in the class itself and the ones it inherits from supertypes.
    // First, the only concrete (non-abstract) methods we are interested in are overrides of
    // Object methods (equals, hashCode, toString), which signal that we should not generate
    // an implementation of those methods.
    // Then, each abstract method is one of the following:
    // (1) A property getter, like "abstract String foo()" or "abstract String getFoo()".
    // (2) A kind getter, which is a method that returns the enum in @AutoOneOf. For
    //     example if we have @AutoOneOf(PetKind.class), this would be a method that returns
    //     PetKind.
    // If there are abstract methods that don't fit any of the categories above, that is an error
    // which we signal explicitly to avoid confusion.

    ImmutableSet<ExecutableElement> methods = getLocalAndInheritedMethods(
        autoOneOfType, processingEnv.getTypeUtils(), processingEnv.getElementUtils());
    ImmutableSet<ExecutableElement> abstractMethods = abstractMethodsIn(methods);
    ExecutableElement kindGetter =
        findKindGetterOrAbort(autoOneOfType, kindMirror, abstractMethods);
    Set<ExecutableElement> otherMethods = new LinkedHashSet<>(abstractMethods);
    otherMethods.remove(kindGetter);

    ImmutableSet<ExecutableElement> propertyMethods = propertyMethodsIn(otherMethods);
    ImmutableBiMap<String, ExecutableElement> properties = propertyNameToMethodMap(propertyMethods);
    validateMethods(autoOneOfType, abstractMethods, propertyMethods, kindGetter);
    ImmutableMap<String, String> propertyToKind =
        propertyToKindMap(kindMirror, properties.keySet());

    String subclass = generatedClassName(autoOneOfType, "AutoOneOf_");
    AutoOneOfTemplateVars vars = new AutoOneOfTemplateVars();
    vars.pkg = TypeSimplifier.packageNameOf(autoOneOfType);
    vars.origClass = TypeSimplifier.classNameOf(autoOneOfType);
    vars.simpleClassName = TypeSimplifier.simpleNameOf(vars.origClass);
    vars.generatedClass = TypeSimplifier.simpleNameOf(subclass);
    vars.types = processingEnv.getTypeUtils();
    vars.propertyToKind = propertyToKind;
    Map<ObjectMethod, ExecutableElement> methodsToGenerate =
        determineObjectMethodsToGenerate(methods);
    vars.toString = methodsToGenerate.containsKey(ObjectMethod.TO_STRING);
    vars.equals = methodsToGenerate.containsKey(ObjectMethod.EQUALS);
    vars.hashCode = methodsToGenerate.containsKey(ObjectMethod.HASH_CODE);
    vars.equalsParameterType = equalsParameterType(methodsToGenerate);
    defineVarsForType(autoOneOfType, vars, propertyMethods, kindGetter);

    String text = vars.toText();
    text = TypeEncoder.decode(text, processingEnv, vars.pkg, autoOneOfType.asType());
    text = Reformatter.fixup(text);
    writeSourceFile(subclass, text, autoOneOfType);
  }

  private DeclaredType mirrorForKindType(TypeElement autoOneOfType) {
    Optional<AnnotationMirror> oneOfAnnotation =
        getAnnotationMirror(autoOneOfType, AUTO_ONE_OF_NAME);
    if (!oneOfAnnotation.isPresent()) {
      // This shouldn't happen unless the compilation environment is buggy,
      // but it has happened in the past and can crash the compiler.
      errorReporter().abortWithError("annotation processor for @AutoOneOf was invoked with a type"
          + " that does not have that annotation; this is probably a compiler bug", autoOneOfType);
    }
    AnnotationValue kindValue =
        AnnotationMirrors.getAnnotationValue(oneOfAnnotation.get(), "value");
    Object value = kindValue.getValue();
    if (value instanceof TypeMirror && ((TypeMirror) value).getKind().equals(TypeKind.DECLARED)) {
      return MoreTypes.asDeclared((TypeMirror) value);
    } else {
      // This is presumably because the referenced type doesn't exist.
      throw new MissingTypeException();
    }
  }

  private ImmutableMap<String, String> propertyToKindMap(
      DeclaredType kindMirror, ImmutableSet<String> propertyNames) {
    // We require a one-to-one correspondence between the property names and the enum constants.
    // We must have transformName(propertyName) = transformName(constantName) for each one.
    // So we build two maps, transformName(propertyName) → propertyName and
    // transformName(constantName) → constant. The key sets of the two maps must match, and we
    // can then join them to make propertyName → constantName.
    TypeElement kindElement = MoreElements.asType(kindMirror.asElement());
    Map<String, String> transformedPropertyNames = propertyNames
        .stream()
        .collect(toMap(this::transformName, s -> s));
    Map<String, Element> transformedEnumConstants = kindElement.getEnclosedElements()
        .stream()
        .filter(e -> e.getKind().equals(ElementKind.ENUM_CONSTANT))
        .collect(toMap(e -> transformName(e.getSimpleName().toString()), e -> e));

    if (transformedPropertyNames.keySet().equals(transformedEnumConstants.keySet())) {
      ImmutableMap.Builder<String, String> mapBuilder = ImmutableMap.builder();
      for (String transformed : transformedPropertyNames.keySet()) {
        mapBuilder.put(
            transformedPropertyNames.get(transformed),
            transformedEnumConstants.get(transformed).getSimpleName().toString());
      }
      return mapBuilder.build();
    }

    // The names don't match. Emit errors for the differences.
    // Properties that have no enum constant
    transformedPropertyNames.forEach(
        (transformed, property) -> {
          if (!transformedEnumConstants.containsKey(transformed)) {
            errorReporter().reportError(
                "Enum has no constant with name corresponding to property '" + property + "'",
                kindElement);
          }
        });
    // Enum constants that have no property
    transformedEnumConstants.forEach(
        (transformed, constant) -> {
          if (!transformedPropertyNames.containsKey(transformed)) {
            errorReporter().reportError(
                "Name of enum constant '" + constant.getSimpleName()
                    + "' does not correspond to any property name",
                constant);
          }
        });
    throw new AbortProcessingException();
  }

  private String transformName(String s) {
    return s.toLowerCase(Locale.ROOT).replace("_", "");
  }

  private ExecutableElement findKindGetterOrAbort(
      TypeElement autoOneOfType,
      TypeMirror kindMirror,
      ImmutableSet<ExecutableElement> abstractMethods) {
    Set<ExecutableElement> kindGetters = abstractMethods
        .stream()
        .filter(e -> sameType(kindMirror, e.getReturnType()))
        .filter(e -> e.getParameters().isEmpty())
        .collect(toSet());
    switch (kindGetters.size()) {
      case 0:
        errorReporter().reportError(
            autoOneOfType + " must have a no-arg abstract method returning " + kindMirror,
            autoOneOfType);
        break;
      case 1:
        return Iterables.getOnlyElement(kindGetters);
      default:
        for (ExecutableElement getter : kindGetters) {
          errorReporter().reportError(
              "More than one abstract method returns " + kindMirror, getter);
        }
    }
    throw new AbortProcessingException();
  }

  private void validateMethods(
      TypeElement type,
      ImmutableSet<ExecutableElement> abstractMethods,
      ImmutableSet<ExecutableElement> propertyMethods,
      ExecutableElement kindGetter) {
    for (ExecutableElement method : abstractMethods) {
      if (propertyMethods.contains(method)) {
        checkReturnType(type, method);
      } else if (!method.equals(kindGetter)
          && objectMethodToOverride(method) == ObjectMethod.NONE) {
        // This could reasonably be an error, were it not for an Eclipse bug in
        // ElementUtils.override that sometimes fails to recognize that one method overrides
        // another, and therefore leaves us with both an abstract method and the subclass method
        // that overrides it. This shows up in AutoValueTest.LukesBase for example.
        // The compilation will fail anyway because the generated concrete classes won't
        // implement this alien method.
        errorReporter().reportWarning(
            "Abstract methods in @AutoOneOf classes must be non-void with no parameters",
            method);
      }
    }
    errorReporter().abortIfAnyError();
  }

  private void defineVarsForType(
      TypeElement type,
      AutoOneOfTemplateVars vars,
      ImmutableSet<ExecutableElement> propertyMethods,
      ExecutableElement kindGetter) {
    vars.generated =
        generatedAnnotation(elementUtils(), processingEnv.getSourceVersion())
            .map(annotation -> TypeEncoder.encode(annotation.asType()))
            .orElse("");
    Map<ExecutableElement, ImmutableList<AnnotationMirror>> annotatedPropertyMethods =
        emptyListForEachMethod(propertyMethods);
    vars.props = propertySet(type, annotatedPropertyMethods);
    vars.formalTypes = TypeEncoder.formalTypeParametersString(type);
    vars.actualTypes = TypeSimplifier.actualTypeParametersString(type);
    vars.wildcardTypes = wildcardTypeParametersString(type);
    vars.kindGetter = kindGetter.getSimpleName().toString();
    vars.kindType = TypeEncoder.encode(kindGetter.getReturnType());
  }

  Map<ExecutableElement, ImmutableList<AnnotationMirror>> emptyListForEachMethod(
      ImmutableSet<ExecutableElement> propertyMethods) {
    return propertyMethods
        .stream()
        .collect(toMap(m -> m, m -> ImmutableList.of(), (a, b) -> a, () -> new LinkedHashMap<>()));
  }

  @Override
  Optional<String> nullableAnnotationForMethod(
      ExecutableElement propertyMethod, ImmutableList<AnnotationMirror> methodAnnotations) {
    return Optional.empty();
  }

  private static boolean sameType(TypeMirror t1, TypeMirror t2) {
    return MoreTypes.equivalence().equivalent(t1, t2);
  }
}
