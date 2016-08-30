/*
 * Copyright (C) 2016 Google Inc.
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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

/**
 * Provides access to Java 8 type annotations via reflection, to allow running on
 * older Java versions.
 *
 * @author Till Brychcy
 */
class Java8Support {
  private static Method determineAnnotationsMirrorsMethod() {
    try {
      return Class.forName("javax.lang.model.AnnotatedConstruct").getMethod("getAnnotationMirrors");
    } catch (Exception e) {
      // method and type only exist on java 8 and later
      return null;
    }
  }

  static Method getAnnotationsMirrorsMethod = determineAnnotationsMirrorsMethod();

  /**
   * Provides access to {@link javax.lang.model.AnnotatedConstruct#getAnnotationMirrors()} via
   * reflection.
   *
   * @param typeMirror the type whose annotations are to be returned.
   * @return if possible, the result of {@code typeMirror.getAnnotationMirrors()},
   *     otherwise an empty list.
   */
  static List<? extends AnnotationMirror> getAnnotationMirrors(TypeMirror typeMirror) {
    if (getAnnotationsMirrorsMethod == null) {
      return Collections.emptyList();
    }
    try {
      @SuppressWarnings("unchecked")
      List<? extends AnnotationMirror> annotations =
          (List<? extends AnnotationMirror>) getAnnotationsMirrorsMethod.invoke(typeMirror);
      return annotations;
    } catch (Exception e) {
      throw new RuntimeException("exception during invocation of getAnnotationMirrors", e);
    }
  }

  private Java8Support() {}
}
