/*
 * Copyright 2008 Google LLC
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
package com.google.auto.service;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An annotation for service providers as described in {@link java.util.ServiceLoader}. The
 * annotation processor generates the configuration files that allow the annotated class to be
 * loaded with {@link java.util.ServiceLoader#load(Class)}.
 *
 * <p>The annotated class must conform to the service provider specification. Specifically, it must:
 *
 * <ul>
 *   <li>be a non-inner, non-anonymous, concrete class
 *   <li>have a publicly accessible no-arg constructor
 *   <li>implement the interface type returned by {@code value()}
 * </ul>
 */
@Documented
@Retention(CLASS)
@Target(TYPE)
public @interface AutoService {
  /** Returns the interfaces implemented by this service provider. */
  Class<?>[] value();
}
