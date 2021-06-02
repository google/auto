/*
 * Copyright 2012 Google LLC
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

/**
 * Specifies that <a href="https://github.com/google/auto/tree/master/value">AutoValue</a> should
 * generate an implementation class for the annotated abstract class, implementing the standard
 * {@link Object} methods like {@link Object#equals equals} to have conventional value semantics. A
 * simple example:
 *
 * <pre>
 *
 *   {@code @}AutoValue
 *   abstract class Person {
 *     static Person create(String name, int id) {
 *       return new AutoValue_Person(name, id);
 *     }
 *
 *     abstract String name();
 *     abstract int id();
 *   }</pre>
 *
 * @see <a href="https://github.com/google/auto/tree/master/value">AutoValue User's Guide</a>
 * @author Éamonn McManus
 * @author Kevin Bourrillion
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AutoValue {

  /**
   * Specifies that AutoValue should generate an implementation of the annotated class or interface,
   * to serve as a <i>builder</i> for the value-type class it is nested within. As a simple example,
   * here is an alternative way to write the {@code Person} class mentioned in the {@link AutoValue}
   * example:
   *
   * <pre>
   *
   *   {@code @}AutoValue
   *   abstract class Person {
   *     static Builder builder() {
   *       return new AutoValue_Person.Builder();
   *     }
   *
   *     abstract String name();
   *     abstract int id();
   *
   *     {@code @}AutoValue.Builder
   *     interface Builder {
   *       Builder name(String x);
   *       Builder id(int x);
   *       Person build();
   *     }
   *   }</pre>
   *
   * @author Éamonn McManus
   */
  @Retention(RetentionPolicy.CLASS)
  @Target(ElementType.TYPE)
  public @interface Builder {}

  /**
   * Specifies that AutoValue should copy any annotations from the annotated element to the
   * generated class. This annotation supports classes and methods.
   *
   * <p>The following annotations are excluded:
   *
   * <ol>
   *   <li>AutoValue and its nested annotations;
   *   <li>any annotation appearing in the {@link AutoValue.CopyAnnotations#exclude} field;
   *   <li>any class annotation which is itself annotated with the {@link
   *       java.lang.annotation.Inherited} meta-annotation.
   * </ol>
   *
   * <p>For historical reasons, annotations are always copied from an {@code @AutoValue} property
   * method to its implementation, unless {@code @CopyAnnotations} is present and explicitly
   * {@linkplain CopyAnnotations#exclude excludes} that annotation. But annotations are not copied
   * from the {@code @AutoValue} class itself to its implementation unless {@code @CopyAnnotations}
   * is present.
   *
   * <p>If you want to copy annotations from your {@literal @}AutoValue-annotated class's methods to
   * the generated fields in the AutoValue_... implementation, annotate your method
   * with {@literal @}AutoValue.CopyAnnotations. For example, if Example.java is:<pre>
   *
   *   {@code @}Immutable
   *   {@code @}AutoValue
   *   abstract class Example {
   *     {@code @}CopyAnnotations
   *     {@code @}SuppressWarnings("Immutable") // justification ...
   *     abstract Object getObject();
   *     // other details ...
   *   }</pre>
   *
   * <p>Then AutoValue will generate the following AutoValue_Example.java:<pre>
   *
   *   final class AutoValue_Example extends Example {
   *     {@code @}SuppressWarnings("Immutable")
   *     private final Object object;
   *
   *     {@code @}SuppressWarnings("Immutable")
   *     {@code @}Override
   *     Object getObject() {
   *       return object;
   *     }
   *
   *     // other details ...
   *   }</pre>
   *
   * <p>When the <i>type</i> of an {@code @AutoValue} property method has annotations, those are
   * part of the type, so by default they are copied to the implementation of the method. But if
   * a type annotation is mentioned in {@code exclude} then it is not copied.
   *
   * <p>For example, suppose {@code @Confidential} is a
   * {@link java.lang.annotation.ElementType#TYPE_USE TYPE_USE} annotation:
   *
   * <pre>
   *
   *   {@code @}AutoValue
   *   abstract class Person {
   *     static Person create({@code @}Confidential String name, int id) {
   *       return new AutoValue_Person(name, id);
   *     }
   *
   *     abstract {@code @}Confidential String name();
   *     abstract int id();
   *   }</pre>
   *
   * Then the implementation of the {@code name()} method will also have return type
   * {@code @Confidential String}. But if {@code name()} were written like this...
   *
   * <pre>
   *
   *     {@code @AutoValue.CopyAnnotations(exclude = Confidential.class)}
   *     abstract {@code @}Confidential String name();</pre>
   *
   * <p>...then the implementation of {@code name()} would have return type {@code String} without
   * the annotation.
   *
   * @author Carmi Grushko
   */
  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.TYPE, ElementType.METHOD})
  public @interface CopyAnnotations {
    Class<? extends Annotation>[] exclude() default {};
  }
}
