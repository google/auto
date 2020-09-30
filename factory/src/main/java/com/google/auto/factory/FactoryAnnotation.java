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


import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

/**
 * Specifies optional annotations that shall be added onto the factory class.
 * 
 * E.g. for null annotations, it might be useful to specify to have the
 * factory to be generated like:
 * <pre>
 * &#064;NonNullByDefault
 * public class FooFactory { ...
 * </pre>
 * 
 * To achieve this, annotate the <code>Foo</class>:
 * 
 * <pre>
 * 
 * &#064;AutoFactory
 * &#064;FactoryAnnotation(NonNullByDefault.class)
 * public class Foo { ...
 * </pre>
 * 
 * @author Frank Benoit
 */
@Target({ TYPE })
@Repeatable(FactoryAnnotations.class)
public @interface FactoryAnnotation {

  /**
   * The class of the annotation to add
   */
  Class<? extends Annotation> value();
}
