package com.google.auto.factory;


import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Target;

@Target({ TYPE, CONSTRUCTOR })
public @interface FactoryAnnotations {
  FactoryAnnotation[] value();
}

