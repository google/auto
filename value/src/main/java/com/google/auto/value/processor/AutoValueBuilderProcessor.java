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

import static com.google.auto.value.processor.AutoValueishProcessor.hasAnnotationMirror;
import static com.google.auto.value.processor.ClassNames.AUTO_VALUE_BUILDER_NAME;
import static com.google.auto.value.processor.ClassNames.AUTO_VALUE_NAME;

import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType;

/**
 * Annotation processor that checks that the type that {@code AutoValue.Builder} is applied to is
 * nested inside an {@code @AutoValue} class. The actual code generation for builders is done in
 * {@link AutoValueProcessor}.
 *
 * @author Éamonn McManus
 */
@AutoService(Processor.class)
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.ISOLATING)
@SupportedAnnotationTypes(AUTO_VALUE_BUILDER_NAME)
public class AutoValueBuilderProcessor extends AbstractProcessor {
  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    TypeElement autoValueBuilder =
        processingEnv.getElementUtils().getTypeElement(AUTO_VALUE_BUILDER_NAME);
    Set<? extends Element> builderTypes = roundEnv.getElementsAnnotatedWith(autoValueBuilder);
    if (!SuperficialValidation.validateElements(builderTypes)) {
      return false;
    }
    for (Element annotatedType : builderTypes) {
      // Double-check that the annotation is there. Sometimes the compiler gets confused in case of
      // erroneous source code. SuperficialValidation should protect us against this but it doesn't
      // cost anything to check again.
      if (hasAnnotationMirror(annotatedType, AUTO_VALUE_BUILDER_NAME)) {
        validate(
            annotatedType,
            "@AutoValue.Builder can only be applied to a class or interface inside an"
                + " @AutoValue class");
      }
    }
    return false;
  }

  private void validate(Element annotatedType, String errorMessage) {
    Element container = annotatedType.getEnclosingElement();
    if (!hasAnnotationMirror(container, AUTO_VALUE_NAME)) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, errorMessage, annotatedType);
    }
  }
}
