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
package com.google.auto.value.extension.serializable.serializer.interfaces;

import com.squareup.javapoet.CodeBlock;
import javax.lang.model.type.TypeMirror;

/**
 * A Serializer, at compile time, generates code to map an unserializable type to a serializable
 * type. It also generates the reverse code to re-create the original type.
 */
public interface Serializer {

  /** The proxy type the original unserializable type will be mapped to. */
  TypeMirror proxyFieldType();

  /** Creates an expression that converts the original type to the proxy type. */
  CodeBlock toProxy(CodeBlock expression);

  /** Creates an expression that converts the proxy type back to the original type. */
  CodeBlock fromProxy(CodeBlock expression);

  /** Returns true if this is an identity {@link Serializer}. */
  default boolean isIdentity() {
    return false;
  }
}
