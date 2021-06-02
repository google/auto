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

import static java.util.stream.Collectors.joining;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;

class GwtCompatibility {
  private final Optional<AnnotationMirror> gwtCompatibleAnnotation;

  GwtCompatibility(TypeElement type) {
    Optional<AnnotationMirror> gwtCompatibleAnnotation = Optional.empty();
    List<? extends AnnotationMirror> annotations = type.getAnnotationMirrors();
    for (AnnotationMirror annotation : annotations) {
      Name name = annotation.getAnnotationType().asElement().getSimpleName();
      if (name.contentEquals("GwtCompatible")) {
        gwtCompatibleAnnotation = Optional.of(annotation);
      }
    }
    this.gwtCompatibleAnnotation = gwtCompatibleAnnotation;
  }

  Optional<AnnotationMirror> gwtCompatibleAnnotation() {
    return gwtCompatibleAnnotation;
  }

  // Get rid of the misconceived <? extends ExecutableElement, ? extends AnnotationValue>
  // in the return type of getElementValues().
  static Map<ExecutableElement, AnnotationValue> getElementValues(AnnotationMirror annotation) {
    return Collections.<ExecutableElement, AnnotationValue>unmodifiableMap(
        annotation.getElementValues());
  }

  String gwtCompatibleAnnotationString() {
    if (gwtCompatibleAnnotation.isPresent()) {
      AnnotationMirror annotation = gwtCompatibleAnnotation.get();
      TypeElement annotationElement = (TypeElement) annotation.getAnnotationType().asElement();
      String annotationArguments;
      if (annotation.getElementValues().isEmpty()) {
        annotationArguments = "";
      } else {
        annotationArguments =
            getElementValues(annotation).entrySet().stream()
                .map(e -> e.getKey().getSimpleName() + " = " + e.getValue())
                .collect(joining(", ", "(", ")"));
      }
      return "@" + annotationElement.getQualifiedName() + annotationArguments;
    } else {
      return "";
    }
  }
}
