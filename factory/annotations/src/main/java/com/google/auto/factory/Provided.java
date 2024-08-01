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

import static java.lang.annotation.ElementType.PARAMETER;

import java.lang.annotation.Target;

/**
 * An annotation to be applied to parameters that should be provided by an injected {@code Provider}
 * in a generated factory.
 *
 * <p>The {@code @Inject} and {@code Provider} classes come from either the legacy package {@code
 * javax.inject} or the updated package {@code jakarta.inject}. {@code jakarta.inject} is used if it
 * is on the classpath. Compile with {@code -Acom.google.auto.factory.InjectApi=javax} if you want
 * to use {@code javax.inject} even when {@code jakarta.inject} is available.
 *
 * @author Gregory Kick
 */
@Target(PARAMETER)
public @interface Provided {}
