/*
 * Copyright 2018 Google LLC
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
package com.google.auto.value.processor;

import static java.util.stream.Collectors.joining;

import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

/**
 * A method on an {@code @AutoValue} or {@code AutoOneOf} class that has no specific attached
 * information, such as a {@code toBuilder()} method, or a {@code build()} method, where only the
 * name and access type is needed in context.
 *
 * <p>It implements JavaBean-style getters which means it can be referenced from templates, for
 * example {@code $method.access}. This template access means that the class and its getters must be
 * public.
 */
public final class SimpleMethod {
  private final String access;
  private final String name;
  private final String throwsString;

  SimpleMethod(ExecutableElement method) {
    this.access = access(method);
    this.name = method.getSimpleName().toString();
    this.throwsString = throwsString(method);
  }

  public String getAccess() {
    return access;
  }

  public String getName() {
    return name;
  }

  public String getThrows() {
    return throwsString;
  }

  /**
   * Returns an appropriate string to be used in code for the access specification of the given
   * method. This will be {@code public} or {@code protected} followed by a space, or the empty
   * string for default access.
   */
  static String access(ExecutableElement method) {
    Set<Modifier> mods = method.getModifiers();
    if (mods.contains(Modifier.PUBLIC)) {
      return "public ";
    } else if (mods.contains(Modifier.PROTECTED)) {
      return "protected ";
    } else {
      return "";
    }
  }

  private static String throwsString(ExecutableElement method) {
    if (method.getThrownTypes().isEmpty()) {
      return "";
    }
    return "throws "
        + method.getThrownTypes().stream().map(TypeEncoder::encode).collect(joining(", "));
  }
}
