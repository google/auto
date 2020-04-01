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

import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.testing.compile.CompilationRule;
import java.util.Arrays;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public abstract class CompilationAbstractTest {

  @Rule public final CompilationRule compilationRule = new CompilationRule();
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock protected ProcessingEnvironment mockProcessingEnvironment;
  @Mock protected Messager mockMessager;

  protected Types typeUtils;
  protected Elements elementUtils;

  @Before
  public final void setUp() {
    typeUtils = compilationRule.getTypes();
    elementUtils = compilationRule.getElements();

    when(mockProcessingEnvironment.getTypeUtils()).thenReturn(typeUtils);
    when(mockProcessingEnvironment.getElementUtils()).thenReturn(elementUtils);
    when(mockProcessingEnvironment.getMessager()).thenReturn(mockMessager);
  }

  protected TypeElement typeElementOf(Class<?> c) {
    return elementUtils.getTypeElement(c.getCanonicalName());
  }

  protected TypeMirror typeMirrorOf(Class<?> c) {
    return typeElementOf(c).asType();
  }

  protected DeclaredType declaredTypeOf(Class<?> enclosingClass, Class<?> containedClass) {
    return typeUtils.getDeclaredType(typeElementOf(enclosingClass), typeMirrorOf(containedClass));
  }

  protected DeclaredType declaredTypeOf(Class<?> enclosingClass, Class<?>... classArgs) {
    return typeUtils.getDeclaredType(
        typeElementOf(enclosingClass),
        Iterables.toArray(
            Arrays.stream(classArgs)
                .map(this::typeMirrorOf)
                .collect(ImmutableList.toImmutableList()),
            TypeMirror.class));
  }
}
