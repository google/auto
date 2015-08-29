package com.google.auto.value.processor;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.testing.compile.JavaFileObjects;

import junit.framework.TestCase;

import java.util.List;

import javax.tools.JavaFileObject;

/**
 * Tests to ensure annotations are kept on AutoValue generated classes
 *
 * @author jmcampanini
 */
public class PropertyAnnotationsTest extends TestCase {

  private static final String PROPERTY_ANNOTATION_TEST =
      new Object(){}.getClass().getEnclosingClass().getName();
  private static final String IMPORT_TEST_ANNOTATION =
      "import " + PROPERTY_ANNOTATION_TEST + ".TestAnnotation;";
  private static final String TEST_ANNOTATION =
      "@TestAnnotation";
  private static final String IMPORT_TEST_ARRAY_ANNOTATION =
      "import " + PROPERTY_ANNOTATION_TEST + ".TestArrayAnnotation;";
  private static final String TEST_ARRAY_ANNOTATION =
      "@TestArrayAnnotation";

  public static enum TestEnum {
    A, B;

    @Override
    public String toString() {
      // used to prove that the method we determine the value does not use the `toString()` method
      // of the enum
      return "not the same value";
    }
  }

  public static @interface TestAnnotation {
    byte testByte() default 1;
    short testShort() default 2;
    int testInt() default 3;
    long testLong() default 4L;
    float testFloat() default 5.6f;
    double testDouble() default 7.8d;
    char testChar() default 'a';
    String testString() default "10";
    boolean testBoolean() default false;
    Class<?> testClass() default TestEnum.class;
    TestEnum testEnum() default TestEnum.A;
    OtherAnnotation testAnnotation() default @OtherAnnotation(foo = 23, bar = "baz");
  }

  public static @interface OtherAnnotation {
    int foo() default 123;
    String bar() default "bar";
  }

  public static @interface TestArrayAnnotation {
    byte[] testBytes() default {1, 2};
    short[] testShorts() default {3, 4};
    int[] testInts() default {5, 6};
    long[] testLongs() default {7L, 8L};
    float[] testFloats() default {9.1f, 2.3f};
    double[] testDoubles() default {4.5d, 6.7d};
    char[] testChars() default {'a', 'b'};
    String[] testStrings() default {"cde", "fgh"};
    boolean[] testBooleans() default {true, false};
    Class<?>[] testClasses() default {TestEnum.class, TestEnum.class};
    TestEnum[] testEnums() default {TestEnum.A, TestEnum.B};
    OtherAnnotation[] testAnnotations()
        default {@OtherAnnotation(foo = 999), @OtherAnnotation(bar = "baz")};
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

  private JavaFileObject expectedCode(List<String> imports, List<String> annotations) {
    String nullable = annotations.contains("@Nullable") ? "@Nullable " : "";
    ImmutableSortedSet<String> allImports = ImmutableSortedSet.<String>naturalOrder()
        .add("import javax.annotation.Generated;")
        .addAll(imports)
        .build();
    ImmutableList<String> list = ImmutableList.<String>builder()
        .add(
            "package foo.bar;",
            ""
        )
        .addAll(allImports)
        .add(
            "",
            "@Generated(\"" + AutoValueProcessor.class.getName() + "\")",
            "final class AutoValue_Baz extends Baz {",
            "  private final int buh;",
            "  AutoValue_Baz(" + nullable + "int buh) {",
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
            "    h ^= this.buh;",
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
    JavaFileObject expectedOutput = expectedCode(imports, expectedAnnotations);

    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);

  }

  public void testSimpleAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of("import javax.annotation.Nullable;"),
        ImmutableList.of("@Nullable"),
        ImmutableList.of("@Nullable"));
  }

  public void testSingleStringValueAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of("@SuppressWarnings(\"a\")"),
        ImmutableList.of("@SuppressWarnings(value={\"a\"})"));
  }

  public void testMultiStringValueAnnotation() {
    assertGeneratedMatches(
        ImmutableList.<String>of(),
        ImmutableList.of("@SuppressWarnings({\"a\", \"b\"})"),
        ImmutableList.of("@SuppressWarnings(value={\"a\", \"b\"})"));
  }

  public void testNumberValueAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ANNOTATION),
        ImmutableList.of(TEST_ANNOTATION + "(testShort = 1, testInt = 2, testLong = 3L)"),
        ImmutableList.of(TEST_ANNOTATION + "(testShort = 1, testInt = 2, testLong = 3L)"));
  }

  public void testByteValueAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ANNOTATION),
        ImmutableList.of(TEST_ANNOTATION + "(testByte = 0)"),
        ImmutableList.of(TEST_ANNOTATION + "(testByte = 0)"));
  }

  public void testDecimalValueAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ANNOTATION),
        ImmutableList.of(TEST_ANNOTATION + "(testDouble = 1.2d, testFloat = 3.4f)"),
        ImmutableList.of(TEST_ANNOTATION + "(testDouble = 1.2d, testFloat = 3.4f)"));
  }

  public void testOtherValuesAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ANNOTATION),
        ImmutableList.of(TEST_ANNOTATION
            + "(testBoolean = true, testString = \"hallo\", testChar = 'a')"),
        ImmutableList.of(TEST_ANNOTATION
            + "(testBoolean = true, testString = \"hallo\", testChar = 'a')"));
  }

  public void testClassAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ANNOTATION),
        ImmutableList.of(TEST_ANNOTATION
            + "(testClass = String.class)"),
        ImmutableList.of(TEST_ANNOTATION
            + "(testClass = java.lang.String.class)"));
  }

  public void testEnumAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ANNOTATION),
        ImmutableList.of(TEST_ANNOTATION
            + "(testEnum = " + PROPERTY_ANNOTATION_TEST + ".TestEnum.A)"),
        ImmutableList.of(TEST_ANNOTATION
            + "(testEnum = " + PROPERTY_ANNOTATION_TEST + ".TestEnum.A)"));
  }

  public void testEmptyAnnotationAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ANNOTATION),
        ImmutableList.of(TEST_ANNOTATION
            + "(testAnnotation = @" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation)"),
        ImmutableList.of(TEST_ANNOTATION
            + "(testAnnotation = @" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation)"));
  }

  public void testValuedAnnotationAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ANNOTATION),
        ImmutableList.of(TEST_ANNOTATION
            + "(testAnnotation = @" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation(foo=999))"),
        ImmutableList.of(TEST_ANNOTATION
            + "(testAnnotation = @" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation(foo=999))"));
  }

  public void testNumberArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ARRAY_ANNOTATION),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testShorts = {2, 3}, testInts = {4, 5}, testLongs = {6L, 7L})"),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testShorts = {2, 3}, testInts = {4, 5}, testLongs = {6L, 7L})"));
  }

  public void testByteArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ARRAY_ANNOTATION),
        ImmutableList.of(TEST_ARRAY_ANNOTATION + "(testBytes = {0, 1})"),
        ImmutableList.of(TEST_ARRAY_ANNOTATION + "(testBytes = {0, 1})"));
  }

  public void testDecimalArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ARRAY_ANNOTATION),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testDoubles = {1.2d, 3.4d}, testFloats = {5.6f, 7.8f})"),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testDoubles = {1.2d, 3.4d}, testFloats = {5.6f, 7.8f})"));
  }

  public void testOtherArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ARRAY_ANNOTATION),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testBooleans = {false, false},"
            + " testStrings = {\"aaa\", \"bbb\"}, testChars={'x', 'y'})"),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testBooleans = {false, false},"
            + " testStrings = {\"aaa\", \"bbb\"}, testChars={'x', 'y'})"));
  }

  public void testClassArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ARRAY_ANNOTATION),
        ImmutableList.of(TEST_ARRAY_ANNOTATION + "(testClasses = {String.class, Long.class})"),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testClasses = {java.lang.String.class, java.lang.Long.class})"));
  }

  public void testImportedClassArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ARRAY_ANNOTATION),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testClasses = {javax.annotation.Nullable.class, Long.class})"),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testClasses = {javax.annotation.Nullable.class, java.lang.Long.class})"));
  }

  public void testEnumArrayAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ARRAY_ANNOTATION),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testEnums = {" + PROPERTY_ANNOTATION_TEST + ".TestEnum.A})"),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testEnums = {" + PROPERTY_ANNOTATION_TEST + ".TestEnum.A})"));
  }

  public void testArrayOfEmptyAnnotationAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ARRAY_ANNOTATION),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testAnnotations = {@" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation})"),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testAnnotations = {@" + PROPERTY_ANNOTATION_TEST + ".OtherAnnotation})"));
  }

  public void testArrayOfValuedAnnotationAnnotation() {
    assertGeneratedMatches(
        ImmutableList.of(IMPORT_TEST_ARRAY_ANNOTATION),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testAnnotations = {@" + PROPERTY_ANNOTATION_TEST
            + ".OtherAnnotation(foo = 999)})"),
        ImmutableList.of(TEST_ARRAY_ANNOTATION
            + "(testAnnotations = {@" + PROPERTY_ANNOTATION_TEST
            + ".OtherAnnotation(foo = 999)})"));
  }
}
