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

import com.google.auto.value.extension.serializable.serializer.utils.CompilationAbstractTest;
import com.squareup.javapoet.CodeBlock;
import javax.lang.model.type.TypeMirror;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class IdentitySerializerFactoryTest extends CompilationAbstractTest {

  @Test
  public void proxyFieldType_isUnchanged() throws Exception {
    TypeMirror typeMirror = typeMirrorOf(String.class);

    TypeMirror actualTypeMirror =
        IdentitySerializerFactory.getSerializer(typeMirror).proxyFieldType();

    assertThat(actualTypeMirror).isSameInstanceAs(typeMirror);
  }

  @Test
  public void toProxy_isUnchanged() throws Exception {
    TypeMirror typeMirror = typeMirrorOf(String.class);
    CodeBlock inputExpression = CodeBlock.of("x");

    CodeBlock outputExpression =
        IdentitySerializerFactory.getSerializer(typeMirror).toProxy(inputExpression);

    assertThat(outputExpression).isSameInstanceAs(inputExpression);
  }

  @Test
  public void fromProxy_isUnchanged() throws Exception {
    TypeMirror typeMirror = typeMirrorOf(String.class);
    CodeBlock inputExpression = CodeBlock.of("x");

    CodeBlock outputExpression =
        IdentitySerializerFactory.getSerializer(typeMirror).fromProxy(inputExpression);

    assertThat(outputExpression).isSameInstanceAs(inputExpression);
  }

  @Test
  public void isIdentity() throws Exception {
    TypeMirror typeMirror = typeMirrorOf(String.class);

    boolean actualIsIdentity = IdentitySerializerFactory.getSerializer(typeMirror).isIdentity();

    assertThat(actualIsIdentity).isTrue();
  }
}
