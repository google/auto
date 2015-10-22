package com.google.auto.value.processor;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

import com.google.auto.value.extension.AutoValueExtension;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.JavaFileObjects;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.tools.JavaFileObject;

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
        "  public String dizzle() {\n",
        "    return \"dizzle\";\n",
        "  }",
        "}"
    );
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(ImmutableList.of(new FooExtension())))
        .compilesWithoutError()
        .and().generatesSources(expectedExtensionOutput);
  }

  public void testExtensionConsumesProperties() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  abstract String foo();",
        "  abstract String dizzle();",
        "}");
    JavaFileObject expectedExtensionOutput = JavaFileObjects.forSourceLines(
        "foo.bar.$AutoValue_Baz",
        "package foo.bar;\n"
        + "\n"
        + "import javax.annotation.Generated;\n"
        + "\n"
        + "@Generated(\"com.google.auto.value.processor.AutoValueProcessor\")\n"
        + " abstract class $AutoValue_Baz extends Baz {\n"
        + "\n"
        + "  private final String foo;\n"
        + "\n"
        + "  $AutoValue_Baz(\n"
        + "      String foo) {\n"
        + "    if (foo == null) {\n"
        + "      throw new NullPointerException(\"Null foo\");\n"
        + "    }\n"
        + "    this.foo = foo;\n"
        + "  }\n"
        + "\n"
        + "  @Override\n"
        + "  String foo() {\n"
        + "    return foo;\n"
        + "  }\n"
        + "\n"
        + "  @Override\n"
        + "  public String toString() {\n"
        + "    return \"Baz{\"\n"
        + "        + \"foo=\" + foo\n"
        + "        + \"}\";\n"
        + "  }\n"
        + "\n"
        + "  @Override\n"
        + "  public boolean equals(Object o) {\n"
        + "    if (o == this) {\n"
        + "      return true;\n"
        + "    }\n"
        + "    if (o instanceof Baz) {\n"
        + "      Baz that = (Baz) o;\n"
        + "      return (this.foo.equals(that.foo()));\n"
        + "    }\n"
        + "    return false;\n"
        + "  }\n"
        + "\n"
        + "  @Override\n"
        + "  public int hashCode() {\n"
        + "    int h = 1;\n"
        + "    h *= 1000003;\n"
        + "    h ^= this.foo.hashCode();\n"
        + "    return h;\n"
        + "  }\n"
        + "\n"
        + "}"
    );
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AutoValueProcessor(ImmutableList.<AutoValueExtension>of(new FooExtension())))
        .compilesWithoutError()
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
        "  @AutoValue.Builder public static abstract class Builder {",
        "    public abstract Builder foo(String foo);",
        "    public abstract Builder bar(String bar);",
        "    public abstract Baz build();",
        "  }",
        "}");
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
        "  public String dizzle() {\n",
        "    return \"dizzle\";\n",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(ImmutableList.of(new FooExtension())))
        .compilesWithoutError()
        .and().generatesSources(expectedExtensionOutput);
  }

  static class FooExtension extends AutoValueExtension {

    @Override
    public boolean applicable(Context context) {
      return true;
    }

    @Override
    public boolean mustBeFinal(Context context) {
      return true;
    }

    @Override
    public Set<String> consumeProperties(Context context) {
      if (context.properties().containsKey("dizzle")) {
        return ImmutableSet.of("dizzle");
      } else {
        return Collections.emptySet();
      }
    }

    @Override
    public String generateClass(
        Context context, String className, String classToExtend, boolean isFinal) {
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
        constructor.append("String ").append(el.getKey());
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
          "  public String dizzle() {\n" +
          "    return \"dizzle\";\n" +
          "  }\n" +
          "}", context.packageName(), isFinal ? "final" : "abstract", className, classToExtend);
    }
  }
}
