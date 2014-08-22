package com.google.auto.value.processor;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import junit.framework.TestCase;

import javax.tools.JavaFileObject;
import java.util.List;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static org.truth0.Truth.ASSERT;

/**
 * Tests to ensure annotations are kept on AutoValue generated classes
 *
 * @author jmcampanini
 */
public class PropertyAnnotationsTest extends TestCase {

  private static final String PROPERTY_ANNOTATION_TEST = "com.google.auto.value.processor.PropertyAnnotationsTest";
  private static final String TEST_ANNOTATION = "@com.google.auto.value.processor.PropertyAnnotationsTest.TestAnnotation";

  public static enum TestEnum {
    A, B;

    @Override
    public String toString() {
      // used to prove that the method we determine the value does not use the `toString()` method of the enum
      return "not the same value";
    }
  }

  public static @interface TestAnnotation {
    byte testByte() default (byte) 1;
    short testShort() default 2;
    int testInt() default 3;
    long testLong() default 4L;
    float testFloat() default 5.6f;
    double testDouble() default 7.8d;
    char testChar() default 'a';
    String testString() default "10";
    boolean testBoolean() default false;
    Class testClass() default TestEnum.class;
    TestEnum testEnum() default TestEnum.A;
    OtherAnnotation testAnnotation() default @OtherAnnotation(foo = 23, bar = "baz");
  }

  public static @interface OtherAnnotation {
    int foo() default 123;
    String bar() default "bar";
  }

  public static @interface TestAnnotationArray {
    byte[] testBytes() default {(byte) 1, (byte) 2};
    short[] testShorts() default {3, 4};
    int[] testInts() default {5, 6};
    long[] testLongs() default {7L, 8L};
    float[] testFloats() default {9.1f, 2.3f};
    double[] testDoubles() default {4.5d, 6.7d};
    char[] testChars() default {'a', 'b'};
    String[] testStrings() default {"cde", "fgh"};
    boolean[] testBooleans() default {true, false};
    Class[] testClasses() default {TestEnum.class, TestEnum.class};
    TestEnum[] testEnums() default {TestEnum.A, TestEnum.B};
    OtherAnnotation[] testAnnotations() default {@OtherAnnotation(foo = 999), @OtherAnnotation(bar = "baz")};
  }

  private JavaFileObject sourceCode(List<String> imports, List<String> annotations) {
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

  private JavaFileObject expectedCode(List<String> annotations) {
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
      List<String> imports,
      List<String> annotations,
      List<String> expectedAnnotations) {

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
        ImmutableList.of("import javax.annotation.Nullable;"),
        ImmutableList.of("@Nullable"),
        ImmutableList.of("@javax.annotation.Nullable"));
  }

  public void testSingleStringValueAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of("@SuppressWarnings(\"a\")"),
        ImmutableList.of("@java.lang.SuppressWarnings(value={\"a\"})"));
  }

  public void testMultiStringValueAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of("@SuppressWarnings({\"a\", \"b\"})"),
        ImmutableList.of("@java.lang.SuppressWarnings(value={\"a\", \"b\"})"));
  }

  public void testNumberValueAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "(testShort = 1, testInt = 2, testLong = 3L)"),
        ImmutableList.of(TEST_ANNOTATION + "(testShort = 1, testInt = 2, testLong = 3L)"));
  }

  public void testByteValueAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "(testByte = (byte)0)"),
        ImmutableList.of(TEST_ANNOTATION + "(testByte = (byte)0)"));
  }

  public void testDecimalValueAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "(testDouble = 1.2d, testFloat = 3.4f)"),
        ImmutableList.of(TEST_ANNOTATION + "(testDouble = 1.2d, testFloat = 3.4f)"));
  }

  public void testOtherValuesAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "(testBoolean = true, testString = \"hallo\", testChar = 'a')"),
        ImmutableList.of(TEST_ANNOTATION + "(testBoolean = true, testString = \"hallo\", testChar = 'a')"));
  }

  public void testClassAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "(testClass = String.class)"),
        ImmutableList.of(TEST_ANNOTATION + "(testClass = java.lang.String.class)"));
  }

  public void testEnumAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "(testEnum = " + PROPERTY_ANNOTATION_TEST + ".TestEnum.A)"),
        ImmutableList.of(TEST_ANNOTATION + "(testEnum = " + PROPERTY_ANNOTATION_TEST + ".TestEnum.A)"));
  }

  public void testEmptyAnnotationAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "(testAnnotation = @" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation)"),
        ImmutableList.of(TEST_ANNOTATION + "(testAnnotation = @" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation)"));
  }

  public void testValuedAnnotationAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "(testAnnotation = @" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation(foo=999))"),
        ImmutableList.of(TEST_ANNOTATION + "(testAnnotation = @" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation(foo=999))"));
  }

  public void testNumberArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "Array(testShorts = {2, 3}, testInts = {4, 5}, testLongs = {6L, 7L})"),
        ImmutableList.of(TEST_ANNOTATION + "Array(testShorts = {2, 3}, testInts = {4, 5}, testLongs = {6L, 7L})"));
  }

  public void testByteArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "Array(testBytes = {(byte) 0, (byte) 1})"),
        ImmutableList.of(TEST_ANNOTATION + "Array(testBytes = {(byte) 0, (byte) 1})"));
  }

  public void testDecimalArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "Array(testDoubles = {1.2d, 3.4d}, testFloats = {5.6f, 7.8f})"),
        ImmutableList.of(TEST_ANNOTATION + "Array(testDoubles = {1.2d, 3.4d}, testFloats = {5.6f, 7.8f})"));
  }

  public void testOtherArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "Array(testBooleans = {false, false}, testStrings = {\"aaa\", \"bbb\"}, testChars={'x', 'y'})"),
        ImmutableList.of(TEST_ANNOTATION + "Array(testBooleans = {false, false}, testStrings = {\"aaa\", \"bbb\"}, testChars={'x', 'y'})"));
  }

  public void testClassArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "Array(testClasses = {String.class, Long.class})"),
        ImmutableList.of(TEST_ANNOTATION + "Array(testClasses = {java.lang.String.class, java.lang.Long.class})"));
  }

  public void testImportedClassArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of("import javax.annotation.Nullable;"),
        ImmutableList.of(TEST_ANNOTATION + "Array(testClasses = {Nullable.class, Long.class})"),
        ImmutableList.of(TEST_ANNOTATION + "Array(testClasses = {javax.annotation.Nullable.class, java.lang.Long.class})"));
  }

  public void testEnumArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "Array(testEnums = {" + PROPERTY_ANNOTATION_TEST + ".TestEnum.A})"),
        ImmutableList.of(TEST_ANNOTATION + "Array(testEnums = {" + PROPERTY_ANNOTATION_TEST + ".TestEnum.A})"));
  }

  public void testArrayOfEmptyAnnotationAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "Array(testAnnotations = {@" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation})"),
        ImmutableList.of(TEST_ANNOTATION + "Array(testAnnotations = {@" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation})"));
  }

  public void testArrayOfValuedAnnotationAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of(TEST_ANNOTATION + "Array(testAnnotations = {@" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation(foo = 999)})"),
        ImmutableList.of(TEST_ANNOTATION + "Array(testAnnotations = {@" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation(foo = 999)})"));
  }
}
