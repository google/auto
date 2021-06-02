/*
 * Copyright 2017 Google LLC
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
package com.google.auto.value.extension.memoized.processor;

import static com.google.auto.value.extension.memoized.processor.ClassNames.MEMOIZED_NAME;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType;

/**
 * An annotation {@link Processor} that reports errors for {@code @Memoized} methods that are not
 * inside {@code AutoValue}-annotated classes.
 */
@AutoService(Processor.class)
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.ISOLATING)
@SupportedAnnotationTypes(MEMOIZED_NAME)
public final class MemoizedValidator extends AbstractProcessor {
  @Override
  public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Messager messager = processingEnv.getMessager();
    TypeElement memoized = processingEnv.getElementUtils().getTypeElement(MEMOIZED_NAME);
    for (ExecutableElement method : methodsIn(roundEnv.getElementsAnnotatedWith(memoized))) {
      if (!isAutoValue(method.getEnclosingElement())) {
        messager.printMessage(
            ERROR,
            "@Memoized methods must be declared only in @AutoValue classes",
            method,
            getAnnotationMirror(method, MEMOIZED_NAME).get());
      }
    }
    return false;
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  private static boolean isAutoValue(Element element) {
    return element.getAnnotationMirrors().stream()
        .map(annotation -> MoreTypes.asTypeElement(annotation.getAnnotationType()))
        .anyMatch(type -> type.getQualifiedName().contentEquals("com.google.auto.value.AutoValue"));
  }

  static Optional<AnnotationMirror> getAnnotationMirror(Element element, String annotationName) {
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      TypeElement annotationElement = MoreTypes.asTypeElement(annotation.getAnnotationType());
      if (annotationElement.getQualifiedName().contentEquals(annotationName)) {
        return Optional.of(annotation);
      }
    }
    return Optional.empty();
  }
}
