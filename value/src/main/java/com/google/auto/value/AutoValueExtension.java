package com.google.auto.value;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.Collection;
import java.util.Map;

/**
 * Created by rharter on 5/1/15.
 */
public interface AutoValueExtension {

  public interface Context {
    ProcessingEnvironment processingEnvironment();
    String packageName();
    TypeElement autoValueClass();
    Map<String, ExecutableElement> properties();
  }

  boolean applicable(Context context);

  boolean mustBeAtEnd();

  /**
   * Generates
   * @param context
   * @param className
   * @param classToExtend
   * @param classToImplement
   * @return
   */
  String generateClass(Context context, String className, String classToExtend, String classToImplement);
}
