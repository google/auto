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

import static com.google.common.truth.Truth8.assertThat;

import java.util.Optional;
import javax.annotation.Nullable;
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
  // This produced the following error with JDT 4.6:
  // Internal compiler error: java.lang.Exception: java.lang.IllegalArgumentException: element
  // public abstract B setOptional(T)  is not a member of the containing type
  // com.google.auto.value.AutoValueTest.ConcreteOptional.Builder nor any of its superclasses at
  // org.eclipse.jdt.internal.compiler.apt.dispatch.RoundDispatcher.handleProcessor(RoundDispatcher.java:169)
  interface AbstractOptional<T> {
    Optional<T> optional();

    interface Builder<T, B extends Builder<T, B>> {
      B setOptional(@Nullable T t);
    }
  }

  @AutoValue
  abstract static class ConcreteOptional implements AbstractOptional<String> {
    static Builder builder() {
      return new AutoValue_AutoValueNotEclipseTest_ConcreteOptional.Builder();
    }

    @AutoValue.Builder
    interface Builder extends AbstractOptional.Builder<String, Builder> {
      ConcreteOptional build();
    }
  }

  @Test
  public void genericOptionalOfNullable() {
    ConcreteOptional empty = ConcreteOptional.builder().build();
    assertThat(empty.optional()).isEmpty();
    ConcreteOptional notEmpty = ConcreteOptional.builder().setOptional("foo").build();
    assertThat(notEmpty.optional()).hasValue("foo");
  }
}
