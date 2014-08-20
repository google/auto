package com.google.auto.value.processor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.testing.compile.JavaFileObjects;
import junit.framework.TestCase;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static org.truth0.Truth.ASSERT;

/**
 * Tests to ensure annotations are kept on AutoValue generated classes
 *
 * @author jmcampanini
 */
public class PropertyAnnotationsTest extends TestCase {

  private static final String TEST_ANNOTATION_FULLNAME = "com.google.auto.value.processor.PropertyAnnotationsTest.TestAnnotation";

  public static enum TestEnum {
    A, B, C
  }

  public static @interface TestAnnotation {
    byte testByte() default 1;
    short testShort() default 2;
    int testInt() default 3;
    long testLong() default 4L;
    float testFloat() default 5.6f;
    double testDouble() default 7.8;
    char testChar() default 'a';
    String testString() default "10";
    boolean testBoolean() default false;
    Class testClass() default TestEnum.class;
    TestEnum testEnum() default TestEnum.A;
  }

  private JavaFileObject sourceCode(Iterable<String> imports, Iterable<String> annotations) {
    ImmutableList<String> list = ImmutableList.<String>builder()
        .add(
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;"
        )
        .addAll(imports)
        .add(
            "",
            "@AutoValue",
            "public abstract class Baz {"
        )
        .addAll(annotations)
        .add(
            "  public abstract int buh();",
            "",
            "  public static Baz create(int buh) {",
            "    return new AutoValue_Baz(buh);",
            "  }",
            "}"
        )
        .build();

    String[] lines = list.toArray(new String[list.size()]);
    return JavaFileObjects.forSourceLines("foo.bar.Baz", lines);
  }

  private JavaFileObject expectedCode(Iterable<String> annotations) {
    ImmutableList<String> list = ImmutableList.<String>builder()
        .add(
            "package foo.bar;",
            "",
            "import javax.annotation.Generated;",
            "",
            "@Generated(\"" + AutoValueProcessor.class.getName() + "\")",
            "final class AutoValue_Baz extends Baz {",
            "  private final int buh;",
            "",
            "  AutoValue_Baz(int buh) {",
            "    this.buh = buh;",
            "  }",
            ""
        )
        .addAll(annotations)
        .add(
            "  @Override public int buh() {",
            "    return buh;",
            "  }",
            "",
            "  @Override public String toString() {",
            "    return \"Baz{\"",
            "        + \"buh=\" + buh",
            "        + \"}\";",
            "  }",
            "",
            "  @Override public boolean equals(Object o) {",
            "    if (o == this) {",
            "      return true;",
            "    }",
            "    if (o instanceof Baz) {",
            "      Baz that = (Baz) o;",
            "      return (this.buh == that.buh());",
            "    }",
            "    return false;",
            "  }",
            "",
            "  @Override public int hashCode() {",
            "    int h = 1;",
            "    h *= 1000003;",
            "    h ^= buh;",
            "    return h;",
            "  }",
            "}"
        )
        .build();

    String[] lines = list.toArray(new String[list.size()]);
    return JavaFileObjects.forSourceLines("foo.bar.AutoValue_Baz", lines);
  }

  private void assertGeneratedMatches(
      Iterable<String> imports,
      Iterable<String> annotations,
      Iterable<String> expectedAnnotations) {

    JavaFileObject javaFileObject = sourceCode(imports, annotations);
    JavaFileObject expectedOutput = expectedCode(expectedAnnotations);

    ASSERT.about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  public void testSimpleAnnotation() {
    assertGeneratedMatches(
        Lists.newArrayList("import javax.annotation.Nullable;"),
        Lists.newArrayList("@Nullable"),
        Lists.newArrayList("@javax.annotation.Nullable"));
  }

  public void testSingleStringValueAnnotation() {
    assertGeneratedMatches(
        Lists.<String>newArrayList(),
        Lists.newArrayList("@SuppressWarnings(\"a\")"),
        Lists.newArrayList("@java.lang.SuppressWarnings(value={\"a\"})"));
  }

  public void testMultiStringValueAnnotation() {
    assertGeneratedMatches(
        Lists.<String>newArrayList(),
        Lists.newArrayList("@SuppressWarnings({\"a\", \"b\"})"),
        Lists.newArrayList("@java.lang.SuppressWarnings(value={\"a\", \"b\"})"));
  }

  public void testNumberValueAnnotation() {
    assertGeneratedMatches(
        Lists.<String>newArrayList(),
        Lists.newArrayList("@" + TEST_ANNOTATION_FULLNAME + "(testByte = 0, testShort = 1, testInt = 2, testLong = 3)"),
        Lists.newArrayList("@" + TEST_ANNOTATION_FULLNAME + "(testByte = 0, testShort = 1, testInt = 2, testLong = 3)"));
  }

  public void testDecimalValueAnnotation() {
    assertGeneratedMatches(
        Lists.<String>newArrayList(),
        Lists.newArrayList("@" + TEST_ANNOTATION_FULLNAME + "(testDouble = 1.2, testFloat = 3.4f)"),
        Lists.newArrayList("@" + TEST_ANNOTATION_FULLNAME + "(testDouble = 1.2, testFloat = 3.4f)"));
  }

  public void testOtherValuesAnnotation() {
    assertGeneratedMatches(
        Lists.<String>newArrayList(),
        Lists.newArrayList("@" + TEST_ANNOTATION_FULLNAME + "(testBoolean = true, testString = \"hallo\", testChar = 'a')"),
        Lists.newArrayList("@" + TEST_ANNOTATION_FULLNAME + "(testBoolean = true, testString = \"hallo\", testChar = 'a')"));
  }

  public void testClassAnnotation() {
    assertGeneratedMatches(
        Lists.<String>newArrayList(),
        Lists.newArrayList("@" + TEST_ANNOTATION_FULLNAME + "(testClass = String.class)"),
        Lists.newArrayList("@" + TEST_ANNOTATION_FULLNAME + "(testClass = java.lang.String.class)"));
  }

  public void testEnumAnnotation() {
    assertGeneratedMatches(
        Lists.<String>newArrayList(),
        Lists.newArrayList("@" + TEST_ANNOTATION_FULLNAME + "(testEnum = com.google.auto.value.processor.PropertyAnnotationsTest.TestEnum.A)"),
        Lists.newArrayList("@" + TEST_ANNOTATION_FULLNAME + "(testEnum = com.google.auto.value.processor.PropertyAnnotationsTest.TestEnum.A)"));
  }
}
