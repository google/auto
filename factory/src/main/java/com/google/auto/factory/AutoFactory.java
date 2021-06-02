/*
 * Copyright 2013 Google LLC
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
package com.google.auto.factory;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Target;

/**
 * An annotation to be applied to elements for which a factory should be automatically generated.
 *
 * <h2>Visibility</h2>
 * <p>The visibility of the generated factories will always be either {@code public} or default
 * visibility. The visibility of any given factory method is determined by the visibility of the
 * type being created. The generated factory is {@code public} if any of the factory methods are.
 * Any method that implements an interface method is necessarily public and any method that
 * overrides an abstract method has the same visibility as that method.
 *
 * @author Gregory Kick
 */
@Target({TYPE, CONSTRUCTOR})
public @interface AutoFactory {
  /**
   * The <i>simple</i> name of the generated factory; the factory is always generated in the same
   * package as the annotated type.  The default value (the empty string) will result in a factory
   * with the name of the type being created with {@code Factory} appended to the end. For example,
   * the default name for a factory for {@code MyType} will be {@code MyTypeFactory}.
   *
   * <p>If the annotated type is nested, then the generated factory's name will start with the
   * enclosing type names, separated by underscores. For example, the default name for a factory for
   * {@code Outer.Inner.ReallyInner} is {@code Outer_Inner_ReallyInnerFactory}. If {@code className}
   * is {@code Foo}, then the factory name is {@code Outer_Inner_Foo}.
   */
  String className() default "";

  /**
   * A list of interfaces that the generated factory is required to implement.
   */
  Class<?>[] implementing() default {};

  /**
   * The type that the generated factory is require to extend.
   */
  Class<?> extending() default Object.class;

  /**
   * Whether or not the generated factory should be final.
   * Defaults to disallowing subclasses (generating the factory as final).
   */
  boolean allowSubclasses() default false;
}
