package com.google.auto.value.annotation;
/**
 * NOTE: Testing with org.eclipse.jdt.annotation.Nullable (v2) doesn't work because it has 
 * RetentionPolicy.CLASS, but RUNTIME is needed for 
 * com.google.auto.value.AutoValueTest.testNullablePropertyConstructorParameterIsNullable()
 */
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE_USE })
public @interface Nullable {
    // empty body
}
