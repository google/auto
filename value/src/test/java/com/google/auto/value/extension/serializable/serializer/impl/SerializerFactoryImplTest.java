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

import com.google.auto.value.extension.serializable.serializer.interfaces.Serializer;
import com.google.auto.value.extension.serializable.serializer.utils.CompilationAbstractTest;
import com.google.auto.value.extension.serializable.serializer.utils.TestStringSerializerFactory;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SerializerFactoryImplTest extends CompilationAbstractTest {

  @Test
  public void getSerializer_emptyFactories_identitySerializerReturned() throws Exception {
    SerializerFactoryImpl factory =
        new SerializerFactoryImpl(ImmutableList.of(), mockProcessingEnvironment);

    Serializer actualSerializer = factory.getSerializer(typeMirrorOf(String.class));

    assertThat(actualSerializer.getClass().getName())
        .contains("IdentitySerializerFactory$IdentitySerializer");
  }

  @Test
  public void getSerializer_factoriesProvided_factoryReturned() throws Exception {
    SerializerFactoryImpl factory =
        new SerializerFactoryImpl(
            ImmutableList.of(new TestStringSerializerFactory()), mockProcessingEnvironment);

    Serializer actualSerializer = factory.getSerializer(typeMirrorOf(String.class));

    assertThat(actualSerializer.getClass().getName())
        .contains("TestStringSerializerFactory$TestStringSerializer");
  }
}
