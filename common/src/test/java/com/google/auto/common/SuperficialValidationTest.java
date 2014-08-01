/*
 * Copyright (C) 2014 Google, Inc.
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
package com.google.auto.common;

import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.JavaFileObjects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

@RunWith(JUnit4.class)
public class SuperficialValidationTest {
  @Test
  public void missingReturnType() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "test.TestClass",
        "package test;",
        "",
        "abstract class TestClass {",
        "  abstract MissingType blah();",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AssertingProcessor() {
          @Override void runAssertions() {
            TypeElement testClassElement =
                processingEnv.getElementUtils().getTypeElement("test.TestClass");
            assert_().that(SuperficialValidation.validateElement(testClassElement)).isFalse();
          }
        })
        .failsToCompile();
  }

  @Test
  public void missingGenericReturnType() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "test.TestClass",
        "package test;",
        "",
        "abstract class TestClass {",
        "  abstract MissingType<?> blah();",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AssertingProcessor() {
          @Override void runAssertions() {
            TypeElement testClassElement =
                processingEnv.getElementUtils().getTypeElement("test.TestClass");
            assert_().that(SuperficialValidation.validateElement(testClassElement)).isFalse();
          }
        })
        .failsToCompile();
  }

  @Test
  public void missingReturnTypeTypeParameter() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "test.TestClass",
        "package test;",
        "",
        "import java.util.Map;",
        "import java.util.Set;",
        "",
        "abstract class TestClass {",
        "  abstract Map<Set<?>, MissingType<?>> blah();",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AssertingProcessor() {
          @Override void runAssertions() {
            TypeElement testClassElement =
                processingEnv.getElementUtils().getTypeElement("test.TestClass");
            assert_().that(SuperficialValidation.validateElement(testClassElement)).isFalse();
          }
        })
        .failsToCompile();
  }

  @Test
  public void missingTypeParameter() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "test.TestClass",
        "package test;",
        "",
        "class TestClass<T extends MissingType> {}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AssertingProcessor() {
          @Override void runAssertions() {
            TypeElement testClassElement =
                processingEnv.getElementUtils().getTypeElement("test.TestClass");
            assert_().that(SuperficialValidation.validateElement(testClassElement)).isFalse();
          }
        })
        .failsToCompile();
  }

  @Test
  public void missingAnnotation() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "test.TestClass",
        "package test;",
        "",
        "@MissingAnnotation",
        "class TestClass {}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AssertingProcessor() {
          @Override void runAssertions() {
            TypeElement testClassElement =
                processingEnv.getElementUtils().getTypeElement("test.TestClass");
            assert_().that(SuperficialValidation.validateElement(testClassElement)).isFalse();
          }
        })
        .failsToCompile();
  }

  @Test
  public void handlesRecurisiveTypeParams() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "test.TestClass",
        "package test;",
        "",
        "class TestClass<T extends Comparable<T>> {}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AssertingProcessor() {
          @Override void runAssertions() {
            TypeElement testClassElement =
                processingEnv.getElementUtils().getTypeElement("test.TestClass");
            assert_().that(SuperficialValidation.validateElement(testClassElement)).isTrue();
          }
        })
        .compilesWithoutError();
  }

  @Test
  public void missingWildcardBound() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "test.TestClass",
        "package test;",
        "",
        "import java.util.Set;",
        "",
        "class TestClass {",
        "  Set<? extends MissingType> extendsTest() {",
        "    return null;",
        "  }",
        "",
        "  Set<? super MissingType> superTest() {",
        "    return null;",
        "  }",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AssertingProcessor() {
          @Override void runAssertions() {
            TypeElement testClassElement =
                processingEnv.getElementUtils().getTypeElement("test.TestClass");
            assert_().that(SuperficialValidation.validateElement(testClassElement)).isFalse();
          }
        })
        .failsToCompile();
  }

  @Test
  public void missingIntersection() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "test.TestClass",
        "package test;",
        "",
        "class TestClass<T extends Number & Missing> {}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AssertingProcessor() {
          @Override void runAssertions() {
            TypeElement testClassElement =
                processingEnv.getElementUtils().getTypeElement("test.TestClass");
            assert_().that(SuperficialValidation.validateElement(testClassElement)).isFalse();
          }
        })
        .failsToCompile();
  }

  private static abstract class AssertingProcessor extends AbstractProcessor {
    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("*");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      try {
        runAssertions();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return false;
    }

    abstract void runAssertions() throws Exception;
  }
}
