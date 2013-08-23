/*
 * Copyright (C) 2012 Google, Inc.
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
 * Annotation indicating that the annotated type is a value type with an automatically-generated
 * implementation.
 *
 * <p>Here is a simple example of a value type using {@code @AutoValue}:
 *
 * <pre>
 * {@code @AutoValue}
 * {@code public abstract class Contact {
 *   public abstract String name();
 *   public abstract List<String> phoneNumbers();
 *   public abstract int sortOrder();
 *
 *   public static Contact create(String name, List<String> phoneNumbers, int sortOrder) {
 *     return new AutoValue_Contact(name, phoneNumbers, sortOrder);
 *   }
 * }}</pre>
 *
 * <p>The generated subclass is called {@code AutoValue_Contact} but users of {@code Contact} do not
 * need to know that. Only the implementation of the {@code create} method does.
 *
 * <p>As an alternative to instantiating the generated subclass in the {@code create} method, you
 * can define a {@code Factory} interface like this:</p>
 *
 * <pre>
 * {@code @AutoValue}
 * {@code public abstract class Contact {
 *   public abstract String name();
 *   public abstract List<String> phoneNumbers();
 *   public abstract int sortOrder();
 *
 *   public static Contact create(String name, List<String> phoneNumbers, int sortOrder) {
 *     return AutoValues.using(Factory.class).create(name, phoneNumbers, sortOrder);
 *   }
 *
 *   interface Factory {
 *     Contact create(String name, List<String> phoneNumbers, int sortOrder);
 *   }
 * }}</pre>
 *
 * @author Éamonn McManus
 */
/*
 *  TODO(gak): We should obviously try to figure out whether this whole factory mechanism can be
 *  replaced with @AutoFactory
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AutoValue {
}