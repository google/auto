package com.google.auto.value.processor;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

/**
 * this class provides access to java 8 type annotations via reflection (to still allow running on
 * older java versions).
 * 
 * @author Till Brychcy
 */
public class Java8Support {
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
   * provides access to {@link javax.lang.model.AnnotatedConstruct#getAnnotationMirrors()} via
   * reflection
   * 
   * @param typeMirror
   * @return if possible, the result of typeMirror.getAnnotationMirrors(), otherwise an empty list.
   */
  @SuppressWarnings("unchecked")
  static List<? extends AnnotationMirror> getAnnotationMirrors(TypeMirror typeMirror) {
    try {
      if (getAnnotationsMirrorsMethod != null) {
        return (List<? extends AnnotationMirror>) getAnnotationsMirrorsMethod.invoke(typeMirror);
      } else {
        return Collections.emptyList();
      }
    } catch (Exception e) {
      throw new RuntimeException("exception during invocation of getAnnotationMirrors", e);
    }
  }
}
