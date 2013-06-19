/*
 * Copyright (C) 2013 Google, Inc.
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
package com.google.autofactory;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Target;
import java.util.Formatter;

/**
 * An annotation to be applied to elements for which a factory should be automatically generated.
 *
 * @author Gregory Kick
 */
@Target({ TYPE, CONSTRUCTOR })
public @interface AutoFactory {
  /**
   * The pattern for the fully qualified name of the factory implementation. The pattern syntax is
   * that of {@link Formatter}. There are two arguments passed to the pattern: the package and the
   * simple name of the type enclosing the target of the annotation.
   */
  String named() default "%s.%sFactory";

  /**
   * A list of interfaces that the generated factory is required to implement.
   */
  Class<?>[] implementing() default { };

  /**
   * The type that the generated factory is require to extend.
   */
  Class<?> extending() default Object.class;
}
