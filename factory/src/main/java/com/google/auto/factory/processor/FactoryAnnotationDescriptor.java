package com.google.auto.factory.processor;

import javax.lang.model.type.TypeMirror;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class FactoryAnnotationDescriptor {
  public abstract TypeMirror annotationType();
  public static FactoryAnnotationDescriptor create(TypeMirror annotationType) {
    return new AutoValue_FactoryAnnotationDescriptor(annotationType);
  }
}
