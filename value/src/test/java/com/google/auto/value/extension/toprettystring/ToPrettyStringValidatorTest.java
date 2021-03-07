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

package com.google.auto.value.extension.toprettystring;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.auto.value.extension.toprettystring.processor.ToPrettyStringValidator;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ToPrettyStringValidatorTest {
  @Test
  public void cannotBeStatic() {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.Test",
            "package test;",
            "",
            "import com.google.auto.value.extension.toprettystring.ToPrettyString;",
            "",
            "class Test {",
            "  @ToPrettyString",
            "  static String toPretty() {",
            "   return new String();",
            "  }",
            "}",
            "");
    Compilation compilation = compile(file);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("must be instance methods")
        .inFile(file)
        .onLineContaining("static String toPretty()");
  }

  @Test
  public void mustReturnString() {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.Test",
            "package test;",
            "",
            "import com.google.auto.value.extension.toprettystring.ToPrettyString;",
            "",
            "class Test {",
            "  @ToPrettyString",
            "  CharSequence toPretty() {",
            "   return new String();",
            "  }",
            "}",
            "");
    Compilation compilation = compile(file);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("must return String")
        .inFile(file)
        .onLineContaining("CharSequence toPretty()");
  }

  @Test
  public void noParameters() {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.Test",
            "package test;",
            "",
            "import com.google.auto.value.extension.toprettystring.ToPrettyString;",
            "",
            "class Test {",
            "  @ToPrettyString",
            "  String toPretty(String value) {",
            "   return value;",
            "  }",
            "}",
            "");
    Compilation compilation = compile(file);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("cannot have parameters")
        .inFile(file)
        .onLineContaining("String toPretty(String value)");
  }

  @Test
  public void onlyOneToPrettyStringMethod_sameClass() {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.Test",
            "package test;",
            "",
            "import com.google.auto.value.extension.toprettystring.ToPrettyString;",
            "",
            "class Test {",
            "  @ToPrettyString",
            "  String toPretty1() {",
            "   return new String();",
            "  }",
            "",
            "  @ToPrettyString",
            "  String toPretty2() {",
            "   return new String();",
            "  }",
            "}",
            "");
    Compilation compilation = compile(file);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            error(
                "test.Test has multiple @ToPrettyString methods:",
                "  - test.Test.toPretty1()",
                "  - test.Test.toPretty2()"))
        .inFile(file)
        .onLineContaining("class Test");
  }

  @Test
  public void onlyOneToPrettyStringMethod_superclass() {
    JavaFileObject superclass =
        JavaFileObjects.forSourceLines(
            "test.Superclass",
            "package test;",
            "",
            "import com.google.auto.value.extension.toprettystring.ToPrettyString;",
            "",
            "class Superclass {",
            "  @ToPrettyString",
            "  String toPretty1() {",
            "   return new String();",
            "  }",
            "}",
            "");
    JavaFileObject subclass =
        JavaFileObjects.forSourceLines(
            "test.Subclass",
            "package test;",
            "",
            "import com.google.auto.value.extension.toprettystring.ToPrettyString;",
            "",
            "class Subclass extends Superclass {",
            "  @ToPrettyString",
            "  String toPretty2() {",
            "   return new String();",
            "  }",
            "}",
            "");
    Compilation compilation = compile(superclass, subclass);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            error(
                "test.Subclass has multiple @ToPrettyString methods:",
                "  - test.Superclass.toPretty1()",
                "  - test.Subclass.toPretty2()"))
        .inFile(subclass)
        .onLineContaining("class Subclass");
  }

  @Test
  public void onlyOneToPrettyStringMethod_superinterface() {
    JavaFileObject superinterface =
        JavaFileObjects.forSourceLines(
            "test.Superinterface",
            "package test;",
            "",
            "import com.google.auto.value.extension.toprettystring.ToPrettyString;",
            "",
            "interface Superinterface {",
            "  @ToPrettyString",
            "  default String toPretty1() {",
            "   return new String();",
            "  }",
            "}",
            "");
    JavaFileObject subclass =
        JavaFileObjects.forSourceLines(
            "test.Subclass",
            "package test;",
            "",
            "import com.google.auto.value.extension.toprettystring.ToPrettyString;",
            "",
            "class Subclass implements Superinterface {",
            "  @ToPrettyString",
            "  String toPretty2() {",
            "   return new String();",
            "  }",
            "}",
            "");
    Compilation compilation = compile(superinterface, subclass);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            error(
                "test.Subclass has multiple @ToPrettyString methods:",
                "  - test.Superinterface.toPretty1()",
                "  - test.Subclass.toPretty2()"))
        .inFile(subclass)
        .onLineContaining("class Subclass");
  }

  private static Compilation compile(JavaFileObject... javaFileObjects) {
    return javac().withProcessors(new ToPrettyStringValidator()).compile(javaFileObjects);
  }

  private static String error(String... lines) {
    return String.join("\n  ", lines);
  }
}
