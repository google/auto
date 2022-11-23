/*
 * Copyright 2016 Google LLC
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
package com.google.auto.value.extension.memoized;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates methods in {@link com.google.auto.value.AutoValue @AutoValue} classes for which the
 * generated subclass will <a href="https://en.wikipedia.org/wiki/Memoization">memoize</a> the
 * returned value.
 *
 * <p>Methods annotated with {@code @Memoized} cannot:
 *
 * <ul>
 *   <li>be {@code abstract} (except for {@link #hashCode()} and {@link #toString()}), {@code
 *       private}, {@code final}, or {@code static}
 *   <li>return {@code void}
 *   <li>have any parameters
 * </ul>
 *
 * <p>If you want to memoize {@link #hashCode()} or {@link #toString()}, you can redeclare them,
 * keeping them {@code abstract}, and annotate them with {@code @Memoized}.
 *
 * <p>If a {@code @Memoized} method is annotated with an annotation whose simple name is {@code
 * Nullable}, then {@code null} values will also be memoized. Otherwise, if the method returns
 * {@code null}, the overriding method will throw a {@link NullPointerException}.
 *
 * <p>The overriding method uses <a
 * href="https://errorprone.info/bugpattern/DoubleCheckedLocking">double-checked locking</a> to
 * ensure that the annotated method is called at most once.
 *
 * <h3>Example</h3>
 *
 * <pre>
 *   {@code @AutoValue}
 *   abstract class Value {
 *     abstract String stringProperty();
 *
 *     {@code @Memoized}
 *     String derivedProperty() {
 *       return someCalculationOn(stringProperty());
 *     }
 *   }
 *
 *   {@code @Generated}
 *   class AutoValue_Value {
 *     // …
 *
 *     private volatile String derivedProperty;
 *
 *     {@code Override}
 *     String derivedProperty() {
 *       if (derivedProperty == null) {
 *         synchronized (this) {
 *           if (derivedProperty == null) {
 *             derivedProperty = super.derivedProperty();
 *           }
 *         }
 *       }
 *       return derivedProperty;
 *     }
 *   }</pre>
 */
@Documented
@Retention(CLASS)
@Target(METHOD)
public @interface Memoized {}
