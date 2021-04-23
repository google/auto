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
import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.CodeBlock;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * A {@link SerializerExtension} that deserializes objects inside an {@link ImmutableMap}.
 *
 * <p>Enables unserializable objects inside an ImmutableMap to be serializable.
 */
@AutoService(SerializerExtension.class)
public final class ImmutableMapSerializerExtension implements SerializerExtension {

  public ImmutableMapSerializerExtension() {}

  @Override
  public Optional<Serializer> getSerializer(
      TypeMirror typeMirror, SerializerFactory factory, ProcessingEnvironment processingEnv) {
    if (!isImmutableMap(typeMirror)) {
      return Optional.empty();
    }

    // Extract the K, V of ImmutableMap<K, V>.
    TypeMirror keyType = getKeyType(typeMirror);
    TypeMirror valueType = getValueType(typeMirror);
    Serializer keyTypeSerializer = factory.getSerializer(keyType);
    Serializer valueTypeSerializer = factory.getSerializer(valueType);

    // We don't need this serializer if the K and V of ImmutableMap<K, V> are serializable.
    if (keyTypeSerializer.isIdentity() && valueTypeSerializer.isIdentity()) {
      return Optional.empty();
    }

    return Optional.of(
        new ImmutableMapSerializer(
            keyType, valueType, keyTypeSerializer, valueTypeSerializer, factory, processingEnv));
  }

  private static class ImmutableMapSerializer implements Serializer {

    private final TypeMirror keyType;
    private final TypeMirror valueType;
    private final TypeMirror keyProxyType;
    private final TypeMirror valueProxyType;
    private final Serializer keyTypeSerializer;
    private final Serializer valueTypeSerializer;
    private final SerializerFactory factory;
    private final ProcessingEnvironment processingEnv;

    ImmutableMapSerializer(
        TypeMirror keyType,
        TypeMirror valueType,
        Serializer keyTypeSerializer,
        Serializer valueTypeSerializer,
        SerializerFactory factory,
        ProcessingEnvironment processingEnv) {
      this.keyType = keyType;
      this.valueType = valueType;
      this.keyProxyType = keyTypeSerializer.proxyFieldType();
      this.valueProxyType = valueTypeSerializer.proxyFieldType();
      this.keyTypeSerializer = keyTypeSerializer;
      this.valueTypeSerializer = valueTypeSerializer;
      this.factory = factory;
      this.processingEnv = processingEnv;
    }

    @Override
    public TypeMirror proxyFieldType() {
      TypeElement immutableMapTypeElement =
          processingEnv.getElementUtils().getTypeElement(ImmutableMap.class.getCanonicalName());
      return processingEnv
          .getTypeUtils()
          .getDeclaredType(immutableMapTypeElement, keyProxyType, valueProxyType);
    }

    @Override
    public CodeBlock toProxy(CodeBlock expression) {
      return CodeBlock.of(
          "$L.entrySet().stream().collect($T.toImmutableMap($L, $L))",
          expression,
          ImmutableMap.class,
          generateKeyMapFunction(keyType, keyProxyType, keyTypeSerializer::toProxy),
          generateValueMapFunction(valueType, valueProxyType, valueTypeSerializer::toProxy));
    }

    @Override
    public CodeBlock fromProxy(CodeBlock expression) {
      return CodeBlock.of(
          "$L.entrySet().stream().collect($T.toImmutableMap($L, $L))",
          expression,
          ImmutableMap.class,
          generateKeyMapFunction(keyProxyType, keyType, keyTypeSerializer::fromProxy),
          generateValueMapFunction(valueProxyType, valueType, valueTypeSerializer::fromProxy));
    }

    private CodeBlock generateKeyMapFunction(
        TypeMirror originalType,
        TypeMirror transformedType,
        Function<CodeBlock, CodeBlock> proxyMap) {
      CodeBlock element = factory.newIdentifier("element");
      CodeBlock value = factory.newIdentifier("value");
      return CodeBlock.of(
          "$1L -> $2T.<$3T, $4T>wrapper($5L -> $6L).apply($1L.getKey())",
          value,
          FunctionWithExceptions.class,
          originalType,
          transformedType,
          element,
          proxyMap.apply(element));
    }

    private CodeBlock generateValueMapFunction(
        TypeMirror originalType,
        TypeMirror transformedType,
        Function<CodeBlock, CodeBlock> proxyMap) {
      CodeBlock element = factory.newIdentifier("element");
      CodeBlock value = factory.newIdentifier("value");
      return CodeBlock.of(
          "$1L -> $2T.<$3T, $4T>wrapper($5L -> $6L).apply($1L.getValue())",
          value,
          FunctionWithExceptions.class,
          originalType,
          transformedType,
          element,
          proxyMap.apply(element));
    }
  }

  private static boolean isImmutableMap(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }

    return MoreTypes.asTypeElement(type)
        .getQualifiedName()
        .contentEquals("com.google.common.collect.ImmutableMap");
  }

  private static TypeMirror getKeyType(TypeMirror type) {
    return MoreTypes.asDeclared(type).getTypeArguments().get(0);
  }

  private static TypeMirror getValueType(TypeMirror type) {
    return MoreTypes.asDeclared(type).getTypeArguments().get(1);
  }
}
