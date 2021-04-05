/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.auto.value.processor;

import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AutoBuilderCompilationTest {
  private static final JavaFileObject EXPECTED_SIMPLE_OUTPUT =
      JavaFileObjects.forSourceLines(
          "foo.bar.AutoBuilder_Baz_Builder",
          "package foo.bar;",
          "",
          GeneratedImport.importGeneratedAnnotationType(),
          "",
          "@Generated(\"" + AutoBuilderProcessor.class.getName() + "\")",
          "class AutoBuilder_Baz_Builder implements Baz.Builder {",
          "  private Integer anInt;",
          "  private String aString;",
          "",
          "  AutoBuilder_Baz_Builder() {}",
          "",
          "  @Override public Baz.Builder setAnInt(int anInt) {",
          "    this.anInt = anInt;",
          "    return this;",
          "  }",
          "",
          "  @Override public Baz.Builder setAString(String aString) {",
          "    if (aString == null) {",
          "      throw new NullPointerException(\"Null aString\");",
          "    }",
          "    this.aString = aString;",
          "    return this;",
          "  }",
          "",
          "  @Override",
          "  public Baz build() {",
          "    String missing = \"\";",
          "    if (this.anInt == null) {",
          "      missing += \" anInt\";",
          "    }",
          "    if (this.aString == null) {",
          "      missing += \" aString\";",
          "    }",
          "    if (!missing.isEmpty()) {",
          "      throw new IllegalStateException(\"Missing required properties:\" + missing);",
          "    }",
          "    return new Baz(",
          "        this.anInt,",
          "        this.aString);",
          "  }",
          "}");

  @Test
  public void simpleSuccess() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoBuilder;",
            "",
            "public class Baz {",
            "  private final int anInt;",
            "  private final String aString;",
            "",
            "  public Baz(int anInt, String aString) {",
            "    this.anInt = anInt;",
            "    this.aString = aString;",
            "  }",
            "",
            "  public int anInt() {",
            "    return anInt;",
            "  }",
            "",
            "  public String aString() {",
            "    return aString;",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new AutoBuilder_Baz_Builder();",
            "  }",
            "",
            "  @AutoBuilder",
            "  public interface Builder {",
            "    Builder setAnInt(int x);",
            "    Builder setAString(String x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoBuilderProcessor())
            .withOptions("-Acom.google.auto.value.AutoBuilderIsUnstable")
            .compile(javaFileObject);
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoBuilder_Baz_Builder")
        .hasSourceEquivalentTo(EXPECTED_SIMPLE_OUTPUT);
  }

  @Test
  public void simpleRecord() {
    double version = Double.parseDouble(JAVA_SPECIFICATION_VERSION.value());
    assume().that(version).isAtLeast(16.0);
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoBuilder;",
            "",
            "public record Baz(int anInt, String aString) {",
            "  public static Builder builder() {",
            "    return new AutoBuilder_Baz_Builder();",
            "  }",
            "",
            "  @AutoBuilder",
            "  public interface Builder {",
            "    Builder setAnInt(int x);",
            "    Builder setAString(String x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoBuilderProcessor())
            .withOptions("-Acom.google.auto.value.AutoBuilderIsUnstable")
            .compile(javaFileObject);
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoBuilder_Baz_Builder")
        .hasSourceEquivalentTo(EXPECTED_SIMPLE_OUTPUT);
  }

  // TODO(b/183005059): error tests
}
