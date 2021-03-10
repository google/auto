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
 * Specifies that the annotated interface or abstract class should be implemented as a builder;
 * THIS IS NOT YET READY FOR USE.
 *
 * <p>A simple example:
 *
 * <pre>
 *
 *   {@code @}AutoBuilder
 *   abstract class PersonBuilder(ofClass = Person.class) {
 *     static PersonBuilder builder() {
 *       return new AutoBuilder_PersonBuilder();
 *     }
 *
 *     abstract PersonBuilder setName(String name);
 *     abstract PersonBuilder setId(int id);
 *     abstract Person build();
 *   }</pre>
 *
 * @see <a href="https://github.com/google/auto/tree/master/value">AutoValue User's Guide</a>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AutoBuilder {
  Class<?> ofClass() default Void.class;

  // TODO(b/183005059): support calling static methods as well as constructors.
}
