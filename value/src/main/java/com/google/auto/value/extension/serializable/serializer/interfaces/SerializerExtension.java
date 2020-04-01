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

import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

/**
 * A SerializerExtension allows unserializable types to be serialized by SerializableAutoValue.
 *
 * <p>Extensions are discovered at compile time using the {@link java.util.ServiceLoader} APIs,
 * allowing them to run without any additional annotations. To be found by {@code ServiceLoader}, an
 * extension class must be public with a public no-arg constructor, and its fully-qualified name
 * must appear in a file called {@code
 * META-INF/services/com.google.auto.value.extension.serializable.serializer.interfaces.SerializerExtension}
 * in a jar that is on the compiler's {@code -classpath} or {@code -processorpath}.
 *
 * <p>When SerializableAutoValue maps each field in an AutoValue to a serializable proxy object, it
 * asks each SerializerExtension whether it can generate code to make the given type serializable. A
 * SerializerExtension replies that it can by returning a non-empty {@link Serializer}.
 *
 * <p>A SerializerExtension is also provided with a SerializerFactory, which it can use to query
 * nested types.
 */
public interface SerializerExtension {

  /**
   * Returns a {@link Serializer} if this {@link SerializerExtension} applies to the given {@code
   * type}. Otherwise, {@code Optional.empty} is returned.
   *
   * @param type the type being serialized
   * @param factory a {@link SerializerFactory} that can be used to serialize nested types
   * @param processingEnv the processing environment provided by the annotation processing framework
   */
  Optional<Serializer> getSerializer(
      TypeMirror type, SerializerFactory factory, ProcessingEnvironment processingEnv);
}
