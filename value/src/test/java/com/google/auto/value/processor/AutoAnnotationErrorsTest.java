package com.google.auto.value.processor;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;

import junit.framework.TestCase;

import javax.tools.JavaFileObject;

/**
 * Tests for compilation errors with the AutoAnnotation processor.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class AutoAnnotationErrorsTest extends TestCase {
  private final JavaFileObject TEST_ANNOTATION = JavaFileObjects.forSourceLines(
      "com.example.TestAnnotation",
      "package com.example;",
      "",
      "public @interface TestAnnotation {",
      "  int value();",
      "}");

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
      assert_().withFailureMessage("For wrong type " + wrongType).about(javaSources())
          .that(ImmutableList.of(testAnnotation, testSource))
          .processedWith(new AutoAnnotationProcessor())
          .failsToCompile()
          .withErrorContaining(
              "method parameter 'value' has type " + wrongType
                  + " but com.example.TestAnnotation.value has type int[]")
          .in(testSource).onLine(7);
    }
  }

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
}
