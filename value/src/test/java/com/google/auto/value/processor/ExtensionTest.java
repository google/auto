package com.google.auto.value.processor;

import com.google.auto.value.AutoValueExtension;
import com.google.testing.compile.JavaFileObjects;
import junit.framework.TestCase;

import javax.lang.model.element.ExecutableElement;
import javax.tools.JavaFileObject;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

/**
 * Created by rharter on 5/5/15.
 */
public class ExtensionTest extends TestCase {
  public void testExtensionCompilation() throws Exception {

    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  abstract String foo();",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.$AutoValue_Baz",
        "package foo.bar;",
        "",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"com.google.auto.value.processor.AutoValueProcessor\")",
        "abstract class $AutoValue_Baz extends Baz {",
        "",
        "  private final String foo;",
        "",
        "  AutoValue_Baz(",
        "      String foo) {",
        "    if (foo == null) {",
        "      throw new NullPointerException(\"Null foo\");",
        "    }",
        "    this.foo = foo;",
        "  }",
        "",
        "  @Override",
        "  String foo() {",
        "    return foo;",
        "  }",
        "",
        "  @Override",
        "  public String toString() {",
        "    return \"Baz{\"",
        "        + \"foo=\" + foo",
        "        + \"}\";",
        "  }",
        "",
        "  @Override",
        "  public boolean equals(Object o) {",
        "    if (o == this) {",
        "      return true;",
        "    }",
        "    if (o instanceof Baz) {",
        "      Baz that = (Baz) o;",
        "      return (this.foo.equals(that.foo()));",
        "    }",
        "    return false;",
        "  }",
        "",
        "  @Override",
        "  public int hashCode() {",
        "    int h = 1;",
        "    h *= 1000003;",
        "    h ^= foo.hashCode();",
        "    return h;",
        "  }",
        "",
        "}");
    JavaFileObject expectedExtensionOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
        "package foo.bar;",
        "",
        "final class AutoValue_Baz extends $AutoValue_Baz {",
        "  public AutoValue_Baz(String foo) {",
        "    super(foo);",
        "  }",
        "  @Override public String foo() {",
        "    return \"foo\";",
        "  }",
        "}"
    );
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(Collections.<AutoValueExtension>singletonList(new FooExtension())))
        .compilesWithoutError()
        .and().generatesSources(expectedOutput)
        .and().generatesSources(expectedExtensionOutput);
  }

  public void testExtensionWithBuilderCompilation() throws Exception {

    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  abstract String foo();",
        "  abstract String bar();",
        "",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.$AutoValue_Baz",
        "package foo.bar;",
        "",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"com.google.auto.value.processor.AutoValueProcessor\")",
        "abstract class $AutoValue_Baz extends Baz {",
        "",
        "  private final String foo;",
        "  private final String bar;",
        "",
        "  $AutoValue_Baz(",
        "      String foo,",
        "      String bar) {",
        "    if (foo == null) {",
        "      throw new NullPointerException(\"Null foo\");",
        "    }",
        "    this.foo = foo;",
        "    if (bar == null) {",
        "      throw new NullPointerException(\"Null bar\");",
        "    }",
        "    this.bar = bar;",
        "  }",
        "",
        "  @Override",
        "  String foo() {",
        "    return foo;",
        "  }",
        "",
        "  @Override",
        "  String bar() {",
        "    return bar;",
        "  }",
        "",
        "  @Override",
        "  public String toString() {",
        "    return \"Baz{\"",
        "        + \"foo=\" + foo + \", \"",
        "        + \"bar=\" + bar",
        "        + \"}\";",
        "  }",
        "",
        "  @Override",
        "  public boolean equals(Object o) {",
        "    if (o == this) {",
        "      return true;",
        "    }",
        "    if (o instanceof Baz) {",
        "      Baz that = (Baz) o;",
        "      return (this.foo.equals(that.foo()))",
        "           && (this.bar.equals(that.bar()));",
        "    }",
        "    return false;",
        "  }",
        "",
        "  @Override",
        "  public int hashCode() {",
        "    int h = 1;",
        "    h *= 1000003;",
        "    h ^= foo.hashCode();",
        "    h *= 1000003;",
        "    h ^= bar.hashCode();",
        "    return h;",
        "  }",
        "",
        "}"
    );
    JavaFileObject expectedExtensionOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
        "package foo.bar;",
        "",
        "final class AutoValue_Baz extends $AutoValue_Baz {",
        "  public AutoValue_Baz(String foo, String bar) {",
        "    super(foo, bar);",
        "  }",
        "  @Override public String foo() {",
        "    return \"foo\";",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(Collections.<AutoValueExtension>singletonList(new FooExtension())))
        .compilesWithoutError()
        .and().generatesSources(expectedOutput)
        .and().generatesSources(expectedExtensionOutput);
  }

  static class FooExtension implements AutoValueExtension {

    @Override
    public boolean applicable(Context context) {
      return true;
    }

    @Override
    public boolean mustBeAtEnd() {
      return true;
    }

    @Override
    public String generateClass(final Context context, final String className, final String classToExtend, boolean isFinal) {
      StringBuilder constructor = new StringBuilder()
          .append("  public ")
          .append(className)
          .append("(");

      boolean first = true;
      for (Map.Entry<String, ExecutableElement> el : context.properties().entrySet()) {
        if (first) {
          first = false;
        } else {
          constructor.append(", ");
        }

        // TODO How are we going to handle the constructor?
        constructor.append("String " + el.getKey());
      }

      constructor.append(") {\n");
      constructor.append("    super(");

      first = true;
      for (Map.Entry<String, ExecutableElement> el : context.properties().entrySet()) {
        if (first) {
          first = false;
        } else {
          constructor.append(", ");
        }

        // TODO How are we going to handle the constructor?
        constructor.append(el.getKey());
      }
      constructor.append(");\n");
      constructor.append("  }\n");

      return String.format("package %s;\n" +
          "\n" +
          "%s class %s extends %s {\n" +
          constructor +
          "  @Override public String foo() {\n" +
          "    return \"foo\";\n" +
          "  }\n" +
          "}", context.packageName(), isFinal ? "final" : "abstract", className, classToExtend);
    }
  }
}
