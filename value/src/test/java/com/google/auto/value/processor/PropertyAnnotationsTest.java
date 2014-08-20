package com.google.auto.value.processor;

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

  public void testCompilationWithBasicAnnotation() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "import javax.annotation.Nullable;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  @Nullable",
        "  public abstract int buh();",
        "",
        "  public static Baz create(int buh) {",
        "    return new AutoValue_Baz(buh);",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
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
        "",
        "  @javax.annotation.Nullable",
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
    );

    ASSERT.about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  public void testCompilationWithSingleValueAnnotation() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "import javax.annotation.Nullable;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  @SuppressWarnings(\"a\")",
        "  public abstract int buh();",
        "",
        "  public static Baz create(int buh) {",
        "    return new AutoValue_Baz(buh);",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
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
        "",
        "  @java.lang.SuppressWarnings(value={\"a\"})",
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
    );

    ASSERT.about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  public void testCompilationWithManyValuedAnnotation() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "import javax.annotation.Nullable;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  @SuppressWarnings({\"a\", \"b\"})",
        "  public abstract int buh();",
        "",
        "  public static Baz create(int buh) {",
        "    return new AutoValue_Baz(buh);",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
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
        "",
        "  @java.lang.SuppressWarnings(value={\"a\", \"b\"})",
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
    );

    ASSERT.about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }
}
