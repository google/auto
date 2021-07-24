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

import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
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

  String gwtCompatibleAnnotationString() {
    return gwtCompatibleAnnotation.map(AnnotationOutput::sourceFormForAnnotation).orElse("");
  }
}
