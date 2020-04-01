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

import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.auto.value.extension.serializable.serializer.interfaces.Serializer;
import com.google.auto.value.extension.serializable.serializer.interfaces.SerializerExtension;
import com.google.auto.value.extension.serializable.serializer.interfaces.SerializerFactory;
import com.squareup.javapoet.CodeBlock;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * A {@link SerializerExtension} that enables {@link Optional} types to be serialized.
 *
 * <p>The type argument {@code T} of {@code Optional<T>} is queried against the {@link
 * SerializerFactory}.
 */
@AutoService(SerializerExtension.class)
public final class OptionalSerializerExtension implements SerializerExtension {

  public OptionalSerializerExtension() {}

  /** Creates a {@link Serializer} that supports {@link Optional} types. */
  @Override
  public Optional<Serializer> getSerializer(
      TypeMirror typeMirror, SerializerFactory factory, ProcessingEnvironment processingEnv) {
    if (!isOptional(typeMirror)) {
      return Optional.empty();
    }

    // Extract the T of Optional<T>.
    TypeMirror containedType = getContainedType(typeMirror);
    Serializer containedTypeSerializer = factory.getSerializer(containedType);

    return Optional.of(new OptionalSerializer(containedTypeSerializer));
  }

  private static class OptionalSerializer implements Serializer {

    private final Serializer containedTypeSerializer;

    OptionalSerializer(Serializer containedTypeSerializer) {
      this.containedTypeSerializer = containedTypeSerializer;
    }

    @Override
    public TypeMirror proxyFieldType() {
      // If this is an Optional<String> then the proxy field type is String.
      // If this is an Optional<Foo>, and the proxy field type for Foo is Bar, then the proxy field
      // type for Optional<Foo> is Bar.
      return containedTypeSerializer.proxyFieldType();
    }

    @Override
    public CodeBlock toProxy(CodeBlock expression) {
      return CodeBlock.of(
          "$L.isPresent() ? $L : null",
          expression,
          containedTypeSerializer.toProxy(CodeBlock.of("$L.get()", expression)));
    }

    @Override
    public CodeBlock fromProxy(CodeBlock expression) {
      return CodeBlock.of(
          "$T.ofNullable($L == null ? null : $L)",
          Optional.class,
          expression,
          containedTypeSerializer.fromProxy(expression));
    }
  }

  /** Checks if the given type is an {@link Optional}. */
  private static boolean isOptional(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }

    return MoreTypes.asTypeElement(type).getQualifiedName().contentEquals("java.util.Optional");
  }

  /**
   * Gets the given type's first type argument.
   *
   * <p>Returns the {@code T} in {@code Optional<T>}.
   */
  private static TypeMirror getContainedType(TypeMirror type) {
    return MoreTypes.asDeclared(type).getTypeArguments().get(0);
  }
}
