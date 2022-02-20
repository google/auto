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
import static com.google.common.truth.Truth8.assertThat;

import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Like {@link AutoValueTest}, but with code that doesn't build with at least some versions of
 * Eclipse, and should therefore not be included in {@link CompileWithEclipseTest}.
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

  enum Truthiness {
    FALSY,
    TRUTHY
  }

  @interface MyAnnotation {
    String value();

    int DEFAULT_ID = -1;

    int id() default DEFAULT_ID;

    Truthiness DEFAULT_TRUTHINESS = Truthiness.FALSY;

    Truthiness truthiness() default Truthiness.FALSY;
  }

  @AutoBuilder(ofClass = MyAnnotation.class)
  public interface MyAnnotationSimpleBuilder {
    MyAnnotationSimpleBuilder value(String x);
    MyAnnotationSimpleBuilder id(int x);
    MyAnnotationSimpleBuilder truthiness(Truthiness x);
    MyAnnotation build();
  }

  public static MyAnnotationSimpleBuilder myAnnotationSimpleBuilder() {
    return new AutoBuilder_AutoValueNotEclipseTest_MyAnnotationSimpleBuilder();
  }

  // Using AutoBuilder to build an annotation does not work with the Eclipse compiler. The problem
  // appears to be https://bugs.eclipse.org/bugs/show_bug.cgi?id=527420. We generate a .java file
  // which has its own @AutoBuilder annotation, and we expect this annotation to be processed by the
  // next round, but it never is. It *may* be that this works when the Eclipse compiler is invoked
  // in certain ways, but it doesn't work with CompileWithEclipseTest.
  @Test
  public void buildWithoutAutoAnnotation() {
    // We haven't supplied a value for `truthiness`, so AutoBuilder should use the default one in
    // the annotation.
    MyAnnotation annotation1 = myAnnotationSimpleBuilder().value("foo").build();
    assertThat(annotation1.value()).isEqualTo("foo");
    assertThat(annotation1.id()).isEqualTo(MyAnnotation.DEFAULT_ID);
    assertThat(annotation1.truthiness()).isEqualTo(MyAnnotation.DEFAULT_TRUTHINESS);
    MyAnnotation annotation2 =
        myAnnotationSimpleBuilder().value("bar").truthiness(Truthiness.TRUTHY).build();
    assertThat(annotation2.value()).isEqualTo("bar");
    assertThat(annotation2.id()).isEqualTo(MyAnnotation.DEFAULT_ID);
    assertThat(annotation2.truthiness()).isEqualTo(Truthiness.TRUTHY);
    MyAnnotation annotation3 =
        myAnnotationSimpleBuilder().value("foo").id(23).truthiness(Truthiness.TRUTHY).build();
    assertThat(annotation3.value()).isEqualTo("foo");
    assertThat(annotation3.id()).isEqualTo(23);
    assertThat(annotation3.truthiness()).isEqualTo(Truthiness.TRUTHY);
  }
}
