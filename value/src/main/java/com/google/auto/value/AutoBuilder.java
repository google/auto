/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.auto.value;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies that the annotated interface or abstract class should be implemented as a builder.
 *
 * <p>A simple example:
 *
 * <pre>
 *
 *   {@code @}AutoBuilder(ofClass = Person.class)
 *   abstract class PersonBuilder {
 *     static PersonBuilder builder() {
 *       return new AutoBuilder_PersonBuilder();
 *     }
 *
 *     abstract PersonBuilder setName(String name);
 *     abstract PersonBuilder setId(int id);
 *     abstract Person build();
 *   }</pre>
 *
 * @see <a
 * href="https://github.com/google/auto/blob/main/value/userguide/autobuilder.md">AutoBuilder
 * User's Guide</a>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AutoBuilder {
  /**
   * The static method from {@link #ofClass} to call when the build-method of the builder is called.
   * By default this is empty, meaning that a constructor rather than a static method should be
   * called. There can be more than one method with the given name, or more than one constructor, in
   * which case the one to call is the one whose parameter names and types correspond to the
   * abstract methods of the class or interface with the {@code @AutoBuilder} annotation.
   */
  String callMethod() default "";

  /**
   * The class or interface containing the constructor or static method that the generated builder
   * will eventually call. By default this is the class or interface that <i>contains</i> the class
   * or interface with the {@code @AutoBuilder} annotation.
   */
  Class<?> ofClass() default Void.class;
}
