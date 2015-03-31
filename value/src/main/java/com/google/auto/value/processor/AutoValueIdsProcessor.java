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

import com.google.auto.common.MoreElements;
import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

import static com.google.auto.common.MoreElements.isAnnotationPresent;

/**
 * Annotation processor that checks that the type that {@link com.google.auto.value.AutoValue.Id}
 * is applied to is nested inside an {@code @AutoValue} class. The actual code generation for ids
 * is done in {@link AutoValueProcessor}.
 *
 * @author Rafael Torres
 */
@AutoService(Processor.class)
public class AutoValueIdsProcessor extends AbstractProcessor {
  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(
        AutoValue.Id.class.getCanonicalName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<? extends Element> idsMethods =
        roundEnv.getElementsAnnotatedWith(AutoValue.Id.class);
    if (!SuperficialValidation.validateElements(idsMethods)) {
      return false;
    }
    for (Element annotatedMethod : idsMethods) {
      if (isAnnotationPresent(annotatedMethod, AutoValue.Id.class)) {
        validate(
            annotatedMethod,
            "@AutoValue.Id can only be applied to a method inside an @AutoValue class");
      }
    }
    return false;
  }

  private void validate(Element annotatedType, String errorMessage) {
    Element container = annotatedType.getEnclosingElement();
    if (!MoreElements.isAnnotationPresent(container, AutoValue.class)) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR, errorMessage, annotatedType);
    }
  }
}
