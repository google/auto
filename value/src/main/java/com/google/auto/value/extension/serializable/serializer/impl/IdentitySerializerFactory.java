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
package com.google.auto.value.extension.serializable.serializer.impl;

import com.google.auto.value.extension.serializable.serializer.interfaces.Serializer;
import com.squareup.javapoet.CodeBlock;
import javax.lang.model.type.TypeMirror;

/** Creates identity {@link Serializer} instances. */
public final class IdentitySerializerFactory {

  /** Returns a {@link Serializer} that leaves the type as is. */
  public static Serializer getSerializer(TypeMirror typeMirror) {
    return new IdentitySerializer(typeMirror);
  }

  private static class IdentitySerializer implements Serializer {

    private final TypeMirror typeMirror;

    IdentitySerializer(TypeMirror typeMirror) {
      this.typeMirror = typeMirror;
    }

    @Override
    public TypeMirror proxyFieldType() {
      return typeMirror;
    }

    @Override
    public CodeBlock toProxy(CodeBlock expression) {
      return expression;
    }

    @Override
    public CodeBlock fromProxy(CodeBlock expression) {
      return expression;
    }

    @Override
    public boolean isIdentity() {
      return true;
    }
  }

  private IdentitySerializerFactory() {}
}
