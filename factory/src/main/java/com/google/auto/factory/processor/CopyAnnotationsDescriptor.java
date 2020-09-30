package com.google.auto.factory.processor;

import javax.lang.model.element.AnnotationMirror;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class CopyAnnotationsDescriptor {
  public abstract List<? extends AnnotationMirror> annotationType();
  public static CopyAnnotationsDescriptor create(List<? extends AnnotationMirror> annotationType) {
    return new AutoValue_CopyAnnotationsDescriptor(annotationType);
  }
}
