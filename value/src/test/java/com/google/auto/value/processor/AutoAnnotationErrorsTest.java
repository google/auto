/*
 * Copyright (C) 2014 Google Inc.
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
package com.google.auto.value.processor;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for compilation errors with the AutoAnnotation processor.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class AutoAnnotationErrorsTest {
  private static final JavaFileObject TEST_ANNOTATION = JavaFileObjects.forSourceLines(
      "com.example.TestAnnotation",
      "package com.example;",
      "",
      "public @interface TestAnnotation {",
      "  int value();",
      "}");

  @Test
  public void testCorrect() {
    assert_().about(javaSources())
        .that(ImmutableList.of(
            TEST_ANNOTATION,
            JavaFileObjects.forSourceLines(
            "com.foo.Test",

            "package com.foo;",
            "",
            "import com.example.TestAnnotation;",
            "import com.google.auto.value.AutoAnnotation;",
            "",
            "class Test {",
            "  @AutoAnnotation static TestAnnotation newTestAnnotation(int value) {",
            "    return new AutoAnnotation_Test_newTestAnnotation(value);",
            "  }",
            "}"
        )))
        .processedWith(new AutoAnnotationProcessor())
        .compilesWithoutError();
  }

  @Test
  public void testNotStatic() {
    JavaFileObject testSource = JavaFileObjects.forSourceLines(
        "com.foo.Test",
        "package com.foo;",
        "",
        "import com.example.TestAnnotation;",
        "import com.google.auto.value.AutoAnnotation;",
        "",
        "class Test {",
        "  @AutoAnnotation TestAnnotation newTestAnnotation(int value) {",
        "    return new AutoAnnotation_Test_newTestAnnotation(value);",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(TEST_ANNOTATION, testSource))
        .processedWith(new AutoAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining("must be static")
        .in(testSource).onLine(7);
  }

  @Test
  public void testDoesNotReturnAnnotation() {
    JavaFileObject testSource = JavaFileObjects.forSourceLines(
        "com.foo.Test",
        "package com.foo;",
        "",
        "import com.google.auto.value.AutoAnnotation;",
        "",
        "class Test {",
        "  @AutoAnnotation static String newString(int value) {",
        "    return new AutoAnnotation_Test_newString(value);",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(TEST_ANNOTATION, testSource))
        .processedWith(new AutoAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining("must be an annotation type, not java.lang.String")
        .in(testSource).onLine(6);
  }

  @Test
  public void testOverload() {
    JavaFileObject testSource = JavaFileObjects.forSourceLines(
        "com.foo.Test",
        "package com.foo;",
        "",
        "import com.example.TestAnnotation;",
        "import com.google.auto.value.AutoAnnotation;",
        "",
        "class Test {",
        "  @AutoAnnotation static TestAnnotation newTestAnnotation(int value) {",
        "    return new AutoAnnotation_Test_newTestAnnotation(value);",
        "  }",
        "",
        "  @AutoAnnotation static TestAnnotation newTestAnnotation(Integer value) {",
        "    return new AutoAnnotation_Test_newTestAnnotation(value);",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(TEST_ANNOTATION, testSource))
        .processedWith(new AutoAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining("@AutoAnnotation methods cannot be overloaded")
        .in(testSource).onLine(11);
  }
  
  // Overload detection used to detect all @AutoAnnotation methods that resulted in 
  // annotation class of the same SimpleName as being an overload. 
  // This verifies that implementations in different packages work correctly.
  @Test
  public void testSameNameDifferentPackagesDoesNotTriggerOverload() {
    
    JavaFileObject fooTestSource = JavaFileObjects.forSourceLines(
        "com.foo.Test",
        "package com.foo;",
        "",
        "import com.example.TestAnnotation;",
        "import com.google.auto.value.AutoAnnotation;",
        "",
        "class Test {",
        "  @AutoAnnotation static TestAnnotation newTestAnnotation(int value) {",
        "    return new AutoAnnotation_Test_newTestAnnotation(value);",
        "  }",
        "}"); 
    JavaFileObject barTestSource = JavaFileObjects.forSourceLines(
        "com.bar.Test",
        "package com.bar;",
        "",
        "import com.example.TestAnnotation;",
        "import com.google.auto.value.AutoAnnotation;",
        "",
        "class Test {",
        "  @AutoAnnotation static TestAnnotation newTestAnnotation(int value) {",
        "    return new AutoAnnotation_Test_newTestAnnotation(value);",
        "  }",
        "}");
    
    assert_().about(javaSources())
        .that(ImmutableList.of(TEST_ANNOTATION, fooTestSource, barTestSource))
        .processedWith(new AutoAnnotationProcessor())
        .compilesWithoutError();    
  }

  @Test
  public void testWrongName() {
    JavaFileObject testSource = JavaFileObjects.forSourceLines(
        "com.foo.Test",
        "package com.foo;",
        "",
        "import com.example.TestAnnotation;",
        "import com.google.auto.value.AutoAnnotation;",
        "",
        "class Test {",
        "  @AutoAnnotation static TestAnnotation newTestAnnotation(int fred) {",
        "    return new AutoAnnotation_Test_newTestAnnotation(fred);",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(TEST_ANNOTATION, testSource))
        .processedWith(new AutoAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining("method parameter 'fred' must have the same name")
        .in(testSource).onLine(7);
  }

  @Test
  public void testWrongType() {
    JavaFileObject testSource = JavaFileObjects.forSourceLines(
        "com.foo.Test",
        "package com.foo;",
        "",
        "import com.example.TestAnnotation;",
        "import com.google.auto.value.AutoAnnotation;",
        "",
        "class Test {",
        "  @AutoAnnotation static TestAnnotation newTestAnnotation(String value) {",
        "    return new AutoAnnotation_Test_newTestAnnotation(value);",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(TEST_ANNOTATION, testSource))
        .processedWith(new AutoAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining(
            "method parameter 'value' has type java.lang.String "
                + "but com.example.TestAnnotation.value has type int")
        .in(testSource).onLine(7);
  }

  @Test
  public void testWrongTypeCollection() {
    JavaFileObject testAnnotation = JavaFileObjects.forSourceLines(
        "com.example.TestAnnotation",
        "package com.example;",
        "",
        "public @interface TestAnnotation {",
        "  int[] value();",
        "}");
    String[] wrongTypes = {
        "java.util.List<java.lang.Long>",
        "java.util.List<java.lang.String>",
        "java.util.Set<java.lang.Long>",
        "java.util.Map<java.lang.Integer,java.lang.Integer>",
        "java.util.concurrent.Callable<java.lang.Integer>",
        "java.util.List<int[]>",
    };
    for (String wrongType : wrongTypes) {
      JavaFileObject testSource = JavaFileObjects.forSourceLines(
          "com.foo.Test",
          "package com.foo;",
          "",
          "import com.example.TestAnnotation;",
          "import com.google.auto.value.AutoAnnotation;",
          "",
          "class Test {",
          "  @AutoAnnotation static TestAnnotation newTestAnnotation(" + wrongType + " value) {",
          "    return new AutoAnnotation_Test_newTestAnnotation(value);",
          "  }",
          "}");
      assertWithMessage("For wrong type " + wrongType)
          .about(javaSources())
          .that(ImmutableList.of(testAnnotation, testSource))
          .processedWith(new AutoAnnotationProcessor())
          .failsToCompile()
          .withErrorContaining("method parameter 'value' has type " + wrongType
                               + " but com.example.TestAnnotation.value has type int[]")
          .in(testSource)
          .onLine(7);
    }
  }

  @Test
  public void testExtraParameters() {
    JavaFileObject testSource = JavaFileObjects.forSourceLines(
        "com.foo.Test",
        "package com.foo;",
        "",
        "import com.example.TestAnnotation;",
        "import com.google.auto.value.AutoAnnotation;",
        "",
        "class Test {",
        "  @AutoAnnotation static TestAnnotation newTestAnnotation(int value, int other) {",
        "    return new AutoAnnotation_Test_newTestAnnotation(value);",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(TEST_ANNOTATION, testSource))
        .processedWith(new AutoAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining(
            "method parameter 'other' must have the same name as a member of "
                + "com.example.TestAnnotation")
        .in(testSource).onLine(7);
  }

  @Test
  public void testMissingParameters() {
    JavaFileObject testSource = JavaFileObjects.forSourceLines(
        "com.foo.Test",
        "package com.foo;",
        "",
        "import com.example.TestAnnotation;",
        "import com.google.auto.value.AutoAnnotation;",
        "",
        "class Test {",
        "  @AutoAnnotation static TestAnnotation newTestAnnotation() {",
        "    return new AutoAnnotation_Test_newTestAnnotation();",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(TEST_ANNOTATION, testSource))
        .processedWith(new AutoAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining("method needs a parameter with name 'value' and type int")
        .in(testSource).onLine(7);
  }

  @Test
  public void testAnnotationValuedDefaultsNotSupportedYet() {
    JavaFileObject annotationSource = JavaFileObjects.forSourceLines(
        "com.example.TestAnnotation",
        "package com.example;",
        "",
        "public @interface TestAnnotation {",
        "  String value();",
        "  Override optionalAnnotation() default @Override;",
        "}");
    JavaFileObject testSource = JavaFileObjects.forSourceLines(
        "com.foo.Test",
        "package com.foo;",
        "",
        "import com.example.TestAnnotation;",
        "import com.google.auto.value.AutoAnnotation;",
        "",
        "class Test {",
        "  @AutoAnnotation static TestAnnotation newTestAnnotation(String value) {",
        "    return new AutoAnnotation_Test_newTestAnnotation(value);",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(annotationSource, testSource))
        .processedWith(new AutoAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining(
            "@AutoAnnotation cannot yet supply a default value for annotation-valued member "
                + "'optionalAnnotation'")
        .in(testSource).onLine(7);
  }

  @Test
  public void testAnnotationMemberNameConflictWithGeneratedLocal() {
    JavaFileObject annotationSource = JavaFileObjects.forSourceLines(
        "com.example.TestAnnotation",
        "package com.example;",
        "",
        "import java.lang.annotation.Annotation;",
        "",
        "public @interface TestAnnotation {",
        "  Class<? extends Annotation>[] value();",
        "  int value$();",
        "}");
    JavaFileObject testSource = JavaFileObjects.forSourceLines(
        "com.foo.Test",
        "package com.foo;",
        "",
        "import java.lang.annotation.Annotation;",
        "import java.util.Collection;",
        "",
        "import com.example.TestAnnotation;",
        "import com.google.auto.value.AutoAnnotation;",
        "",
        "class Test {",
        "  @AutoAnnotation static TestAnnotation newTestAnnotation(",
        "     Collection<Class<? extends Annotation>> value, int value$) {",
        "    return new AutoAnnotation_Test_newTestAnnotation(value, value$);",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(annotationSource, testSource))
        .processedWith(new AutoAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining("variable value$ is already defined in constructor");
  }
}
