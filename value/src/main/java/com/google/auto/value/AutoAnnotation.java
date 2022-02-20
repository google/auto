/*
 * Copyright 2014 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.auto.value;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;

/**
 * Annotation that causes an implementation of an annotation interface to be generated. The
 * annotation is applied to a method whose return type is an annotation interface. The method can
 * then create and return an instance of the generated class that conforms to the specification of
 * {@link Annotation}, in particular as regards {@link Annotation#equals equals} and {@link
 * Annotation#hashCode hashCode}. These instances behave essentially the same as instances returned
 * by {@link AnnotatedElement#getAnnotation}.
 *
 * <p>For example, suppose you have an annotation like this:
 *
 * <pre>
 * package com.google.inject.name;
 *
 * public &#64;interface Named {
 *   String value();
 * }</pre>
 *
 * <p>You could write a method like this to construct implementations of the interface:
 *
 * <pre>
 * package com.example;
 *
 * public class Names {
 *   &#64;AutoAnnotation public static Named named(String value) {
 *     return new AutoAnnotation_Names_named(value);
 *   }
 * }</pre>
 *
 * <p>Because the annotated method is called {@code Names.named}, the generated class is called
 * {@code AutoAnnotation_Names_named} in the same package. If the annotated method were in a nested
 * class, for example {@code Outer.Names.named}, then the generated class would be called {@code
 * AutoAnnotation_Outer_Names_named}. The generated class is package-private and it is not expected
 * that it will be referenced outside the {@code @AutoAnnotation} method.
 *
 * <p>The names and types of the parameters in the annotated method must be the same as the names
 * and types of the annotation elements, except that elements which have default values can be
 * omitted. The parameters do not need to be in any particular order.
 *
 * <p>The annotated method does not need to be public. It can have any visibility, including
 * private. This means the method can be a private implementation called by a public method with a
 * different API, for example using a builder.
 *
 * <p>It is a compile-time error if more than one method with the same name in the same class is
 * annotated {@code @AutoAnnotation}.
 *
 * <p>The constructor of the generated class has the same parameters as the {@code @AutoAnnotation}
 * method. It will throw {@code NullPointerException} if any parameter is null. In order to
 * guarantee that the constructed object is immutable, the constructor will clone each array
 * parameter corresponding to an array-valued annotation member, and the implementation of each such
 * member will also return a clone of the array.
 *
 * <p>If your annotation has many elements, you may consider using {@code @AutoBuilder} instead of
 * {@code @AutoAnnotation} to make it easier to construct instances. In that case, {@code default}
 * values from the annotation will become default values for the values in the builder. For example:
 *
 * <pre>
 * class Example {
 *   {@code @interface} MyAnnotation {
 *     String name() default "foo";
 *     int number() default 23;
 *   }
 *
 *   {@code @AutoBuilder(ofClass = MyAnnotation.class)}
 *   interface MyAnnotationBuilder {
 *     MyAnnotationBuilder name(String name);
 *     MyAnnotationBuilder number(int number);
 *     MyAnnotation build();
 *   }
 *
 *   static MyAnnotationBuilder myAnnotationBuilder() {
 *     return new AutoBuilder_Example_MyAnnotationBuilder();
 *   }
 * }
 * </pre>
 *
 * Here, {@code myAnnotationBuilder().build()} is the same as {@code
 * myAnnotationBuilder().name("foo").number(23).build()} because those are the defaults in the
 * annotation definition.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface AutoAnnotation {}
