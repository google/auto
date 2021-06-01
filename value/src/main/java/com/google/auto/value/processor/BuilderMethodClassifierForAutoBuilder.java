/*
 * Copyright 2021 Google LLC
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

import static com.google.auto.common.MoreStreams.toImmutableBiMap;
import static com.google.auto.common.MoreStreams.toImmutableMap;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Types;

class BuilderMethodClassifierForAutoBuilder extends BuilderMethodClassifier<VariableElement> {
  private final ExecutableElement executable;
  private final ImmutableBiMap<VariableElement, String> paramToPropertyName;

  private BuilderMethodClassifierForAutoBuilder(
      ErrorReporter errorReporter,
      ProcessingEnvironment processingEnv,
      ExecutableElement executable,
      TypeMirror builtType,
      TypeElement builderType,
      ImmutableBiMap<VariableElement, String> paramToPropertyName,
      ImmutableMap<String, TypeMirror> rewrittenPropertyTypes) {
    super(errorReporter, processingEnv, builtType, builderType, rewrittenPropertyTypes);
    this.executable = executable;
    this.paramToPropertyName = paramToPropertyName;
  }

  /**
   * Classifies the given methods from a builder type and its ancestors.
   *
   * @param methods the abstract methods in {@code builderType} and its ancestors.
   * @param errorReporter where to report errors.
   * @param processingEnv the ProcessingEnvironment for annotation processing.
   * @param executable the constructor or static method that AutoBuilder will call.
   * @param builtType the type to be built.
   * @param builderType the builder class or interface within {@code ofClass}.
   * @return an {@code Optional} that contains the results of the classification if it was
   *     successful or nothing if it was not.
   */
  static Optional<BuilderMethodClassifier<VariableElement>> classify(
      Iterable<ExecutableElement> methods,
      ErrorReporter errorReporter,
      ProcessingEnvironment processingEnv,
      ExecutableElement executable,
      TypeMirror builtType,
      TypeElement builderType) {
    ImmutableBiMap<VariableElement, String> paramToPropertyName =
        executable.getParameters().stream()
            .collect(toImmutableBiMap(v -> v, v -> v.getSimpleName().toString()));
    ImmutableMap<String, TypeMirror> rewrittenPropertyTypes =
        rewriteParameterTypes(executable, builderType, errorReporter, processingEnv.getTypeUtils());
    BuilderMethodClassifier<VariableElement> classifier =
        new BuilderMethodClassifierForAutoBuilder(
            errorReporter,
            processingEnv,
            executable,
            builtType,
            builderType,
            paramToPropertyName,
            rewrittenPropertyTypes);
    if (classifier.classifyMethods(methods, false)) {
      return Optional.of(classifier);
    } else {
      return Optional.empty();
    }
  }

  // Rewrites the parameter types of the executable so they use the type variables of the builder
  // where appropriate.
  //
  // Suppose we have something like this:
  //
  // static <E> Set<E> singletonSet(E elem) {...}
  //
  // @AutoBuilder(callMethod = "singletonSet")
  // interface SingletonSetBuilder<E> {
  //   SingletonSetBuilder<E> setElem(E elem);
  //   Set<E> build();
  // }
  //
  // We want to check that the type of the setter `setElem` matches the type of the
  // parameter it is setting. But in fact it doesn't: the type of the setter is
  // E-of-SingletonSetBuilder while the type of the parameter is E-of-singletonSet. So we
  // need to rewrite any type variables mentioned in parameters so that they use the corresponding
  // types from the builder. We want to return a map where "elem" is mapped to
  // E-of-SingletonSetBuilder, even though the `elem` that we get from the parameters of
  // singletonSet is going to be E-of-singletonSet. And we also want that to work if the parameter
  // is something more complicated, like List<? extends E>.
  //
  // For the corresponding situation with AutoValue, we have a way of dodging the problem somewhat.
  // For an @AutoValue class Foo<E> with a builder Builder<E>, we can craft a DeclaredType
  // Foo<E> where the E comes from Builder<E>, and we can use Types.asMemberOf to determine the
  // return types of methods (which are what we want to rewrite in that case). But that doesn't
  // work here because singletonSet is static and Types.asMemberOf would have no effect on it.
  //
  // So instead we take the type of each parameter and feed it through a TypeVisitor that rewrites
  // type variables, rewriting from E-of-singletonSet to E-of-SingletonSetBuilder. Then we can use
  // Types.isSameType or Types.isAssignable and it will work as we expect.
  //
  // In principle a similar situation arises with the return type Set<E> of singletonSet versus
  // the return type Set<E> of SingletonSetBuilder.build(). But in fact we only use
  // MoreTypes.equivalence to compare those, and that returns true for distinct type variables if
  // they have the same name and bounds.
  private static ImmutableMap<String, TypeMirror> rewriteParameterTypes(
      ExecutableElement executable,
      TypeElement builderType,
      ErrorReporter errorReporter,
      Types typeUtils) {
    ImmutableList<TypeParameterElement> executableTypeParams = executableTypeParams(executable);
    List<? extends TypeParameterElement> builderTypeParams = builderType.getTypeParameters();
    if (!BuilderSpec.sameTypeParameters(executableTypeParams, builderTypeParams)) {
      errorReporter.abortWithError(
          builderType,
          "[AutoBuilderTypeParams] Builder type parameters %s must match type parameters %s of %s",
          TypeEncoder.typeParametersString(builderTypeParams),
          TypeEncoder.typeParametersString(executableTypeParams),
          AutoBuilderProcessor.executableString(executable));
    }
    if (executableTypeParams.isEmpty()) {
      // Optimization for a common case. No point in doing all that type visiting if we have no
      // variables to substitute.
      return executable.getParameters().stream()
          .collect(toImmutableMap(v -> v.getSimpleName().toString(), Element::asType));
    }
    Map<Equivalence.Wrapper<TypeVariable>, TypeMirror> typeVariables = new LinkedHashMap<>();
    for (int i = 0; i < executableTypeParams.size(); i++) {
      TypeVariable from = MoreTypes.asTypeVariable(executableTypeParams.get(i).asType());
      TypeVariable to = MoreTypes.asTypeVariable(builderTypeParams.get(i).asType());
      typeVariables.put(MoreTypes.equivalence().wrap(from), to);
    }
    Function<TypeVariable, TypeMirror> substitute =
        v -> typeVariables.get(MoreTypes.equivalence().wrap(v));
    return executable.getParameters().stream()
        .collect(
            toImmutableMap(
                v -> v.getSimpleName().toString(),
                v -> TypeVariables.substituteTypeVariables(v.asType(), substitute, typeUtils)));
  }

  private static ImmutableList<TypeParameterElement> executableTypeParams(
      ExecutableElement executable) {
    switch (executable.getKind()) {
      case CONSTRUCTOR:
        // A constructor can have its own type parameters, in addition to any that its containing
        // class has. That's pretty unusual, but we allow it, requiring the builder to have type
        // parameters that are the concatenation of the class's and the constructor's.
        TypeElement container = MoreElements.asType(executable.getEnclosingElement());
        return ImmutableList.<TypeParameterElement>builder()
            .addAll(container.getTypeParameters())
            .addAll(executable.getTypeParameters())
            .build();
      case METHOD:
        return ImmutableList.copyOf(executable.getTypeParameters());
      default:
        throw new VerifyException("Unexpected executable kind " + executable.getKind());
    }
  }

  @Override
  Optional<String> propertyForBuilderGetter(ExecutableElement method) {
    String methodName = method.getSimpleName().toString();
    if (paramToPropertyName.containsValue(methodName)) {
      return Optional.of(methodName);
    }
    if (AutoValueishProcessor.isPrefixedGetter(method)) {
      int prefixLength = methodName.startsWith("get") ? 3 : 2; // "get" or "is"
      String unprefixed = methodName.substring(prefixLength);
      String propertyName = PropertyNames.decapitalizeLikeJavaBeans(unprefixed);
      if (paramToPropertyName.containsValue(propertyName)) {
        return Optional.of(propertyName);
      }
      propertyName = PropertyNames.decapitalizeNormally(unprefixed);
      if (paramToPropertyName.containsValue(propertyName)) {
        return Optional.of(propertyName);
      }
    }
    return Optional.empty();
  }

  @Override
  void checkForFailedJavaBean(ExecutableElement rejectedSetter) {}

  @Override
  ImmutableBiMap<String, VariableElement> propertyElements() {
    return paramToPropertyName.inverse();
  }

  @Override
  TypeMirror originalPropertyType(VariableElement propertyElement) {
    return propertyElement.asType();
  }

  @Override
  String propertyString(VariableElement propertyElement) {
    return "parameter \""
        + propertyElement.getSimpleName()
        + "\" of "
        + AutoBuilderProcessor.executableString(executable);
  }

  @Override
  String autoWhat() {
    return "AutoBuilder";
  }

  @Override
  String getterMustMatch() {
    return "a parameter of " + AutoBuilderProcessor.executableString(executable);
  }

  @Override
  String fooBuilderMustMatch() {
    return "foo";
  }
}
