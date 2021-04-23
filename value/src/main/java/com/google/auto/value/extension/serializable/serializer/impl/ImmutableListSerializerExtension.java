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
import com.google.auto.value.extension.serializable.serializer.runtime.FunctionWithExceptions;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.CodeBlock;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * A {@link SerializerExtension} that deserializes objects inside an {@link ImmutableList}.
 *
 * <p>Enables unserializable objects inside an ImmutableList to be serializable.
 */
@AutoService(SerializerExtension.class)
public final class ImmutableListSerializerExtension implements SerializerExtension {

  public ImmutableListSerializerExtension() {}

  @Override
  public Optional<Serializer> getSerializer(
      TypeMirror typeMirror, SerializerFactory factory, ProcessingEnvironment processingEnv) {
    if (!isImmutableList(typeMirror)) {
      return Optional.empty();
    }

    // Extract the T of ImmutableList<T>.
    TypeMirror containedType = getContainedType(typeMirror);
    Serializer containedTypeSerializer = factory.getSerializer(containedType);

    // We don't need this serializer if the T of ImmutableList<T> is serializable.
    if (containedTypeSerializer.isIdentity()) {
      return Optional.empty();
    }

    return Optional.of(
        new ImmutableListSerializer(containedTypeSerializer, factory, processingEnv));
  }

  private static class ImmutableListSerializer implements Serializer {

    private final Serializer containedTypeSerializer;
    private final SerializerFactory factory;
    private final ProcessingEnvironment processingEnv;

    ImmutableListSerializer(
        Serializer containedTypeSerializer,
        SerializerFactory factory,
        ProcessingEnvironment processingEnv) {
      this.containedTypeSerializer = containedTypeSerializer;
      this.factory = factory;
      this.processingEnv = processingEnv;
    }

    @Override
    public TypeMirror proxyFieldType() {
      TypeElement immutableListTypeElement =
          processingEnv.getElementUtils().getTypeElement(ImmutableList.class.getCanonicalName());
      TypeMirror containedProxyType = containedTypeSerializer.proxyFieldType();
      return processingEnv
          .getTypeUtils()
          .getDeclaredType(immutableListTypeElement, containedProxyType);
    }

    @Override
    public CodeBlock toProxy(CodeBlock expression) {
      CodeBlock element = factory.newIdentifier("value");
      return CodeBlock.of(
          "$L.stream().map($T.wrapper($L -> $L)).collect($T.toImmutableList())",
          expression,
          FunctionWithExceptions.class,
          element,
          containedTypeSerializer.toProxy(element),
          ImmutableList.class);
    }

    @Override
    public CodeBlock fromProxy(CodeBlock expression) {
      CodeBlock element = factory.newIdentifier("value");
      return CodeBlock.of(
          "$L.stream().map($T.wrapper($L -> $L)).collect($T.toImmutableList())",
          expression,
          FunctionWithExceptions.class,
          element,
          containedTypeSerializer.fromProxy(element),
          ImmutableList.class);
    }
  }

  private static boolean isImmutableList(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }

    return MoreTypes.asTypeElement(type)
        .getQualifiedName()
        .contentEquals("com.google.common.collect.ImmutableList");
  }

  private static TypeMirror getContainedType(TypeMirror type) {
    return MoreTypes.asDeclared(type).getTypeArguments().get(0);
  }
}
