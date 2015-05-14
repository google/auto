package com.google.auto.value.processor;

import com.google.auto.value.AutoValueExtension;
import com.google.testing.compile.JavaFileObjects;
import junit.framework.TestCase;

import javax.lang.model.element.ExecutableElement;
import javax.tools.JavaFileObject;
import java.util.Collection;
import java.util.Collections;

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
        "foo.bar.AutoValue_Baz",
        "package foo.bar;",
        "",
        "abstract class AutoValue_Baz extends Baz {",
        "  @Override public String foo() {",
        "    return \"foo\";",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(Collections.<AutoValueExtension>singletonList(new FooExtension())))
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
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
        "  @AutoValue.Builder",
        "  public static abstract class Builder {",
        "    public abstract Builder bar(String bar);",
        "    public abstract Baz build();",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
        "package foo.bar;",
        "",
        "abstract class AutoValue_Baz extends Baz {",
        "  @Override public String foo() {",
        "    return \"foo\";",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(Collections.<AutoValueExtension>singletonList(new FooExtension())))
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  public void testImplementedInterfaceCompilation() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "interface IBaz {",
        "  String baz();",
        "}",
        "",
        "@AutoValue",
        "public abstract class Baz implements IBaz {",
        "  abstract String foo();",
        "  abstract String bar();",
        "",
        "  @AutoValue.Builder",
        "  public static abstract class Builder {",
        "    public abstract Builder foo(String foo);",
        "    public abstract Builder bar(String bar);",
        "    public abstract Baz build();",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
        "package foo.bar;",
        "",
        "import foo.bar.IBaz;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"com.google.auto.value.processor.AutoValueProcessor\")",
        "final class AutoValue_Baz extends Baz implements IBaz {",
        "",
        "  private final String foo;",
        "  private final String bar;",
        "",
        "  private AutoValue_Baz(",
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
        "  static final class Builder extends Baz.Builder {",
        "    private String foo;",
        "    private String bar;",
        "    Builder() {",
        "    }",
        "    Builder(Baz source) {",
        "      foo(source.foo());",
        "      bar(source.bar());",
        "    }",
        "    @Override",
        "    public Baz.Builder foo(String foo) {",
        "      this.foo = foo;",
        "      return this;",
        "    }",
        "    @Override",
        "    public Baz.Builder bar(String bar) {",
        "      this.bar = bar;",
        "      return this;",
        "    }",
        "    @Override",
        "    public Baz build() {",
        "      String missing = \"\";",
        "      if (foo == null) {",
        "        missing += \" foo\";",
        "      }",
        "      if (bar == null) {",
        "        missing += \" bar\";",
        "      }",
        "      if (!missing.isEmpty()) {",
        "        throw new IllegalStateException(\"Missing required properties:\" + missing);",
        "      }",
        "      return new AutoValue_Baz(",
        "          this.foo,",
        "          this.bar);",
        "    }",
        "  }",
        "",
        "  @Override public String baz() {",
        "    return \"baz\";",
        "  }",
        "",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(Collections.<AutoValueExtension>singletonList(new InPlaceExtension())))
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  public void testAltersSubclass() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "interface IBaz {",
        "  String baz();",
        "}",
        "",
        "@AutoValue",
        "public abstract class Baz implements IBaz {",
        "  abstract String foo();",
        "  abstract String bar();",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
        "package foo.bar;",
        "",
        "import foo.bar.IBaz;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"com.google.auto.value.processor.AutoValueProcessor\")",
        "final class AutoValue_Baz extends Baz",
        "         implements IBaz",
        "           {",
        "",
        "  private final String foo;",
        "  private final String bar;",
        "",
        "  AutoValue_Baz(",
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
        "  @Override public String baz() {",
        "    return \"baz\";",
        "  }",
        "",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(Collections.<AutoValueExtension>singletonList(new InPlaceExtension())))
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  static class InPlaceExtension implements AutoValueExtension {

    @Override
    public boolean applicable(Context context) {
      return true;
    }

    @Override
    public GeneratedClass generateClass(final Context context, String className, String classToExtend, String classToImplement) {
      return new GeneratedClass() {
        @Override
        public String className() {
          return null;
        }

        @Override
        public String source() {
          return null;
        }

        @Override
        public Collection<String> additionalImports() {
          return Collections.singleton("foo.bar.IBaz");
        }

        @Override
        public Collection<ExecutableElement> consumedProperties() {
          return Collections.singleton(context.properties().get("baz"));
        }

        @Override
        public Collection<String> additionalInterfaces() {
          return Collections.singleton("IBaz");
        }

        @Override
        public Collection<String> additionalCode() {
          return Collections.singleton("  @Override public String baz() {\n" +
              "    return \"baz\";\n" +
              "  }");
        }
      };
    }
  }

  static class FooExtension implements AutoValueExtension {

    @Override
    public boolean applicable(Context context) {
      return true;
    }

    @Override
    public GeneratedClass generateClass(final Context context, final String className, final String classToExtend, String classToImplement) {
      return new GeneratedClass() {
        @Override
        public String className() {
          return className;
        }

        @Override
        public String source() {
          return String.format("package %s;\n" +
              "\n" +
              "abstract class %s extends %s {\n" +
              "  @Override public String foo() {\n" +
              "    return \"foo\";\n" +
              "  }\n" +
              "}", context.packageName(), className, classToExtend);
        }

        @Override
        public Collection<String> additionalImports() {
          return null;
        }

        @Override
        public Collection<ExecutableElement> consumedProperties() {
          return Collections.singletonList(context.properties().get("foo"));
        }

        @Override
        public Collection<String> additionalInterfaces() {
          return null;
        }

        @Override
        public Collection<String> additionalCode() {
          return null;
        }
      };
    }
  }
}
