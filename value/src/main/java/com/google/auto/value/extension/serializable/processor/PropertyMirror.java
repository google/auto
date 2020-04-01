/*
 * Copyright 2020 Google LLC
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
package com.google.auto.value.extension.serializable.processor;

import javax.lang.model.type.TypeMirror;

/**
 * A POJO containing information about an AutoValue's property.
 *
 * <p>For example, given this AutoValue property: <code>abstract T getX();</code>
 *
 * <ol>
 *   <li>The type would be T.
 *   <li>The name would be x.
 *   <li>The method would be getX.
 */
final class PropertyMirror {

  private final TypeMirror type;
  private final String name;
  private final String method;

  PropertyMirror(TypeMirror type, String name, String method) {
    this.type = type;
    this.name = name;
    this.method = method;
  }

  /** Gets the AutoValue property's type. */
  TypeMirror getType() {
    return type;
  }

  /** Gets the AutoValue property's name. */
  String getName() {
    return name;
  }

  /** Gets the AutoValue property accessor method. */
  String getMethod() {
    return method;
  }
}
