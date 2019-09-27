/*
 * Copyright 2019 Google LLC
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
package com.google.auto.value;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Like {@link AutoValueTest}, but with code that doesn't build with at least some versions of
 * Eclipse, and should therefore not be included in {@link CompileWithEclipseTest}. (The latter is
 * not currently present in the open-source build.)
 */
@RunWith(JUnit4.class)
public class AutoValueNotEclipseTest {
  interface ImmutableListOf<T> {
    ImmutableList<T> list();
  }

  // This provoked the following with the Eclipse compiler:
  // java.lang.NullPointerException
  //   at org.eclipse.jdt.internal.compiler.lookup.ParameterizedTypeBinding.readableName(ParameterizedTypeBinding.java:1021)
  //   at org.eclipse.jdt.internal.compiler.apt.model.DeclaredTypeImpl.toString(DeclaredTypeImpl.java:118)
  //   at java.lang.String.valueOf(String.java:2996)
  //   at java.lang.StringBuilder.append(StringBuilder.java:131)
  //   at org.eclipse.jdt.internal.compiler.apt.model.TypesImpl.asMemberOf(TypesImpl.java:130)
  //   at com.google.auto.value.processor.EclipseHack.methodReturnType(EclipseHack.java:124)
  //   at com.google.auto.value.processor.TypeVariables.lambda$rewriteReturnTypes$1(TypeVariables.java:106)
  @AutoValue
  abstract static class PropertyBuilderInheritsType implements ImmutableListOf<String> {
    static Builder builder() {
      return new AutoValue_AutoValueNotEclipseTest_PropertyBuilderInheritsType.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract ImmutableList.Builder<String> listBuilder();
      abstract PropertyBuilderInheritsType build();
    }
  }

  @Test
  public void propertyBuilderInheritsType() {
    PropertyBuilderInheritsType.Builder builder = PropertyBuilderInheritsType.builder();
    builder.listBuilder().add("foo", "bar");
    PropertyBuilderInheritsType x = builder.build();
    assertThat(x.list()).containsExactly("foo", "bar").inOrder();
  }
}
