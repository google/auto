package com.google.auto.factory;


import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

@Target({ TYPE, CONSTRUCTOR })
@Repeatable(FactoryAnnotations.class)
public @interface FactoryAnnotation {
  String value();
}
