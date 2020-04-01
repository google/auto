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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.auto.value.extension.serializable.serializer.interfaces.Serializer;
import com.google.auto.value.extension.serializable.serializer.interfaces.SerializerExtension;
import com.google.auto.value.extension.serializable.serializer.interfaces.SerializerFactory;
import com.squareup.javapoet.CodeBlock;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** A test implementation of {@link SerializerExtension} that overwrites a string field's value. */
@AutoService(SerializerExtension.class)
public final class TestStringSerializerFactory implements SerializerExtension {

  public TestStringSerializerFactory() {}

  @Override
  public Optional<Serializer> getSerializer(
      TypeMirror typeMirror, SerializerFactory factory, ProcessingEnvironment processingEnv) {
    if (typeMirror.getKind() != TypeKind.DECLARED) {
      return Optional.empty();
    }

    DeclaredType declaredType = MoreTypes.asDeclared(typeMirror);
    TypeElement typeElement = MoreElements.asType(declaredType.asElement());
    if (typeElement.getQualifiedName().contentEquals("java.lang.String")) {
      return Optional.of(new TestStringSerializer(typeMirror));
    }

    return Optional.empty();
  }

  private static class TestStringSerializer implements Serializer {

    private final TypeMirror typeMirror;

    TestStringSerializer(TypeMirror typeMirror) {
      this.typeMirror = typeMirror;
    }

    @Override
    public TypeMirror proxyFieldType() {
      return typeMirror;
    }

    @Override
    public CodeBlock toProxy(CodeBlock expression) {
      return CodeBlock.of("$S", "test");
    }

    @Override
    public CodeBlock fromProxy(CodeBlock expression) {
      return CodeBlock.of("$S", "test");
    }
  }
}
