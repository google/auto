package com.google.auto.value.processor;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

import com.google.auto.value.extension.AutoValueExtension;
import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;

import junit.framework.TestCase;

import java.util.Map;

import javax.lang.model.element.ExecutableElement;
import javax.tools.JavaFileObject;

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
        .processedWith(new AutoValueProcessor(ImmutableList.of(new FooExtension())))
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
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(ImmutableList.of(new FooExtension())))
        .compilesWithoutError()
        .and().generatesSources(expectedExtensionOutput);
  }

  static class FooExtension implements AutoValueExtension {

    @Override
    public boolean applicable(Context context) {
      return true;
    }

    @Override
    public boolean mustBeAtEnd(Context context) {
      return true;
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
