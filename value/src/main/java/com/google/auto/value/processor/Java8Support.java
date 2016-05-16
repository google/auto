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
