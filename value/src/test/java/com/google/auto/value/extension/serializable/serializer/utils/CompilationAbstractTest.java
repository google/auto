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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.reflect.Reflection;
import com.google.testing.compile.CompilationRule;
import java.lang.reflect.InvocationHandler;
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

@RunWith(JUnit4.class)
public abstract class CompilationAbstractTest {

  @Rule public final CompilationRule compilationRule = new CompilationRule();

  protected ProcessingEnvironment mockProcessingEnvironment = mockProcessingEnvironment();
  protected Messager mockMessager = mockMessager();

  protected Types typeUtils;
  protected Elements elementUtils;

  private ProcessingEnvironment mockProcessingEnvironment() {
    InvocationHandler handler = (proxy, method, args) -> {
      switch (method.getName()) {
        case "getTypeUtils":
          return typeUtils;
        case "getElementUtils":
          return elementUtils;
        case "getMessager":
          return mockMessager;
        case "getOptions":
          return ImmutableMap.of();
        case "toString":
          return "MockProcessingEnvironment";
        default:
          throw new IllegalArgumentException("Unsupported method: " + method);
      }
    };
    return Reflection.newProxy(ProcessingEnvironment.class, handler);
  }

  private Messager mockMessager() {
    InvocationHandler handler = (proxy, method, args) -> null;
    return Reflection.newProxy(Messager.class, handler);
  }

  @Before
  public final void setUp() {
    typeUtils = compilationRule.getTypes();
    elementUtils = compilationRule.getElements();
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
