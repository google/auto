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

package com.google.auto.value.extension.toprettystring.processor;

import static com.google.auto.value.extension.toprettystring.processor.ClassNames.TO_PRETTY_STRING_NAME;
import static com.google.auto.value.extension.toprettystring.processor.ToPrettyStringMethods.toPrettyStringMethods;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType;

/**
 * An annotation processor that validates {@link
 * com.google.auto.value.extension.toprettystring.ToPrettyString} usage.
 */
@AutoService(Processor.class)
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.ISOLATING)
@SupportedAnnotationTypes(TO_PRETTY_STRING_NAME)
public final class ToPrettyStringValidator extends AbstractProcessor {
  @Override
  public boolean process(
      Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
    Types types = processingEnv.getTypeUtils();
    Elements elements = processingEnv.getElementUtils();
    TypeElement toPrettyString = elements.getTypeElement(TO_PRETTY_STRING_NAME);

    Set<ExecutableElement> annotatedMethods =
        methodsIn(roundEnvironment.getElementsAnnotatedWith(toPrettyString));
    for (ExecutableElement method : annotatedMethods) {
      validateMethod(method, elements);
    }

    validateSingleToPrettyStringMethod(annotatedMethods, types, elements);

    return false;
  }

  private void validateMethod(ExecutableElement method, Elements elements) {
    ErrorReporter errorReporter = new ErrorReporter(method, processingEnv.getMessager());
    if (method.getModifiers().contains(STATIC)) {
      errorReporter.reportError("@ToPrettyString methods must be instance methods");
    }

    TypeMirror stringType = elements.getTypeElement("java.lang.String").asType();
    if (!MoreTypes.equivalence().equivalent(method.getReturnType(), stringType)) {
      errorReporter.reportError("@ToPrettyString methods must return String");
    }

    if (!method.getParameters().isEmpty()) {
      errorReporter.reportError("@ToPrettyString methods cannot have parameters");
    }
  }

  private void validateSingleToPrettyStringMethod(
      Set<ExecutableElement> annotatedMethods, Types types, Elements elements) {
    Set<TypeElement> enclosingTypes =
        annotatedMethods.stream()
            .map(Element::getEnclosingElement)
            .map(MoreElements::asType)
            .collect(toCollection(LinkedHashSet::new));
    for (TypeElement enclosingType : enclosingTypes) {
      ImmutableList<ExecutableElement> methods =
          toPrettyStringMethods(enclosingType, types, elements);
      if (methods.size() > 1) {
        processingEnv
            .getMessager()
            .printMessage(
                ERROR,
                String.format(
                    "%s has multiple @ToPrettyString methods:%s",
                    enclosingType.getQualifiedName(), formatMethodList(methods)),
                enclosingType);
      }
    }
  }

  private String formatMethodList(ImmutableList<ExecutableElement> methods) {
    return methods.stream().map(this::formatMethodInList).collect(joining());
  }

  private String formatMethodInList(ExecutableElement method) {
    return String.format(
        "\n  - %s.%s()",
        MoreElements.asType(method.getEnclosingElement()).getQualifiedName(),
        method.getSimpleName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  private static final class ErrorReporter {
    private final ExecutableElement method;
    private final Messager messager;

    ErrorReporter(ExecutableElement method, Messager messager) {
      this.method = method;
      this.messager = messager;
    }

    void reportError(String error) {
      messager.printMessage(ERROR, error, method);
    }
  }
}
