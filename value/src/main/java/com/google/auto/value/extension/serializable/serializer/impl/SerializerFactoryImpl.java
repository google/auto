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
import com.google.auto.value.extension.serializable.serializer.interfaces.SerializerExtension;
import com.google.auto.value.extension.serializable.serializer.interfaces.SerializerFactory;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.CodeBlock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

/** A concrete implementation of {@link SerializerFactory}. */
public final class SerializerFactoryImpl implements SerializerFactory {

  private final ImmutableList<SerializerExtension> extensions;
  private final ProcessingEnvironment env;
  private final AtomicInteger idCount = new AtomicInteger();

  public SerializerFactoryImpl(
      ImmutableList<SerializerExtension> extensions, ProcessingEnvironment env) {
    this.extensions = extensions;
    this.env = env;
  }

  @Override
  public Serializer getSerializer(TypeMirror typeMirror) {
    for (SerializerExtension extension : extensions) {
      Optional<Serializer> serializer = extension.getSerializer(typeMirror, this, env);
      if (serializer.isPresent()) {
        return serializer.get();
      }
    }
    return IdentitySerializerFactory.getSerializer(typeMirror);
  }

  @Override
  public CodeBlock newIdentifier(String prefix) {
    return CodeBlock.of("$L$$$L", prefix, idCount.incrementAndGet());
  }
}
