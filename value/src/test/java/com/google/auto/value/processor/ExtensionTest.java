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
        public Collection<ExecutableElement> consumedProperties() {
          return Collections.singletonList(context.properties().get("foo"));
        }
      };
    }
  }
}
