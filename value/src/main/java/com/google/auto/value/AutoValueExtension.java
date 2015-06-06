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
   * Generates the source code of the class named <code>className</code> to extend
   * <code>classToExtend</code>, with the original annotated class of
   * <code>classToImplement</code>.  The generated class should be final if <code>isFinal</code>
   * is true, otherwise it should be abstract.
   *
   * @param context The {@link com.google.auto.value.AutoValueExtension.Context} of the code
   *                generation for this class.
   * @param className The name of the resulting class. The returned code will be written to a
   *                  file named accordingly.
   * @param classToExtend The direct parent of the generated class. Could be the AutoValue
   *                      generated class, or a class generated as the result of another
   *                      extension.
   * @param isFinal True if this class is the last class in the chain, meaning it should be
   *                marked as final, otherwise it should be marked as abstract.
   * @return The source code of the generated class
   */
  String generateClass(Context context, String className, String classToExtend, boolean isFinal);
}
