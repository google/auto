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
 * A factory that returns a {@link Serializer} for any given {@link TypeMirror}.
 *
 * <p>Defaults to an identity serializer if no SerializerExtensions are suitable.
 */
public interface SerializerFactory {

  /** Returns a {@link Serializer} for the given {@link TypeMirror}. */
  Serializer getSerializer(TypeMirror type);

  /**
   * Returns an identifier beginning with the given prefix and that is distinct from any identifier
   * returned by another call to this method. The returned identifier will contain a {@code $},
   * which should also mean it is distinct from identifiers in user code that are in scope.
   *
   * <p>The default implementation of this method throws {@link UnsupportedOperationException} for
   * compatibility reasons.
   */
  default CodeBlock newIdentifier(String prefix) {
    throw new UnsupportedOperationException();
  }
}
