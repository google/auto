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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.auto.value.extension.serializable.serializer.interfaces.Serializer;
import com.google.auto.value.extension.serializable.serializer.utils.CompilationAbstractTest;
import com.google.auto.value.extension.serializable.serializer.utils.FakeSerializerFactory;
import com.squareup.javapoet.CodeBlock;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OptionalSerializerExtensionTest extends CompilationAbstractTest {

  private OptionalSerializerExtension extension;
  private FakeSerializerFactory fakeSerializerFactory;

  @Before
  public void setUpExtension() {
    extension = new OptionalSerializerExtension();
    fakeSerializerFactory = new FakeSerializerFactory();
  }

  @Test
  public void getSerializer_nonOptional_emptyReturned() {
    TypeMirror typeMirror = typeMirrorOf(String.class);

    Optional<Serializer> actualSerializer =
        extension.getSerializer(typeMirror, fakeSerializerFactory, mockProcessingEnvironment);

    assertThat(actualSerializer).isEmpty();
  }

  @Test
  public void getSerializer_optional_serializerReturned() {
    TypeMirror typeMirror = typeMirrorOf(Optional.class);

    Serializer actualSerializer =
        extension.getSerializer(typeMirror, fakeSerializerFactory, mockProcessingEnvironment).get();

    assertThat(actualSerializer.getClass().getName())
        .contains("OptionalSerializerExtension$OptionalSerializer");
  }

  @Test
  public void proxyFieldType() {
    TypeMirror typeMirror = declaredTypeOf(Optional.class, Integer.class);

    Serializer serializer =
        extension.getSerializer(typeMirror, fakeSerializerFactory, mockProcessingEnvironment).get();
    TypeMirror actualTypeMirror = serializer.proxyFieldType();

    assertThat(actualTypeMirror).isEqualTo(typeMirrorOf(Integer.class));
  }

  @Test
  public void toProxy() {
    TypeMirror typeMirror = declaredTypeOf(Optional.class, Integer.class);

    Serializer serializer =
        extension.getSerializer(typeMirror, fakeSerializerFactory, mockProcessingEnvironment).get();
    CodeBlock actualCodeBlock = serializer.toProxy(CodeBlock.of("x"));

    assertThat(actualCodeBlock.toString()).isEqualTo("x.isPresent() ? x.get() : null");
  }

  @Test
  public void fromProxy() {
    TypeMirror typeMirror = declaredTypeOf(Optional.class, Integer.class);

    Serializer serializer =
        extension.getSerializer(typeMirror, fakeSerializerFactory, mockProcessingEnvironment).get();
    CodeBlock actualCodeBlock = serializer.fromProxy(CodeBlock.of("x"));

    assertThat(actualCodeBlock.toString())
        .isEqualTo("java.util.Optional.ofNullable(x == null ? null : x)");
  }
}
