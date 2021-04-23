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
package com.google.auto.value.extension.serializable.serializer.utils;

import com.google.auto.value.extension.serializable.serializer.interfaces.Serializer;
import com.google.auto.value.extension.serializable.serializer.interfaces.SerializerFactory;
import com.squareup.javapoet.CodeBlock;
import javax.lang.model.type.TypeMirror;

/** A fake {@link SerializerFactory} that returns an identity serializer used for tests. */
public final class FakeSerializerFactory implements SerializerFactory {

  private boolean isIdentity = true;

  public FakeSerializerFactory() {}

  /**
   * Set if this factory should return a serializer that is considered an identity serializer.
   *
   * <p>The underlying fake serializer implementation will always be an identity serializer. This
   * only changes the {@link Serializer#isIdentity} return value.
   */
  public void setReturnIdentitySerializer(boolean isIdentity) {
    this.isIdentity = isIdentity;
  }

  @Override
  public Serializer getSerializer(TypeMirror type) {
    return new FakeIdentitySerializer(type, isIdentity);
  }

  // This doesn't follow the contract, and always returns the same string for a given prefix.
  // That means it will be wrong if two identifiers with the same prefix are in the same scope in
  // the generated code, but for our purposes in this fake it is OK, and means we don't have to
  // hardwire knowledge of the uniqueness algorithm into golden text in tests.
  @Override
  public CodeBlock newIdentifier(String prefix) {
    return CodeBlock.of("$L$$", prefix);
  }

  private static class FakeIdentitySerializer implements Serializer {

    private final TypeMirror typeMirror;
    private final boolean isIdentity;

    FakeIdentitySerializer(TypeMirror typeMirror, boolean isIdentity) {
      this.typeMirror = typeMirror;
      this.isIdentity = isIdentity;
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
      return isIdentity;
    }
  }
}
