/*
 * Copyright 2023 Google LLC
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AutoValueModuleCompilationTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  /**
   * This test currently confirms that AutoValue has a bug with module handling. If your AutoValue
   * class inherits an abstract method from a module, and that abstract method has an annotation
   * that is not exported from the module, then AutoValue will incorrectly try to copy the
   * annotation onto the generated implementation of the abstract method.
   */
  @Test
  public void nonVisibleMethodAnnotationFromOtherModule() throws Exception {
    JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
    Path tempDir = temporaryFolder.newFolder().toPath();
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Path fooOut = outDir.resolve("foo");
    writeFile(srcDir, "foo/module-info.java", "module foo {", "  exports foo.exported;", "}");
    writeFile(
        srcDir,
        "foo/exported/Foo.java",
        "package foo.exported;",
        "",
        "import foo.unexported.UnexportedAnnotation;",
        "",
        "public interface Foo {",
        "  @ExportedAnnotation",
        "  @UnexportedAnnotation",
        "  String name();",
        "}");
    writeFile(
        srcDir,
        "foo/exported/ExportedAnnotation.java",
        "package foo.exported;",
        "",
        "import java.lang.annotation.*;",
        "",
        "@Retention(RetentionPolicy.RUNTIME)",
        "public @interface ExportedAnnotation {}");
    writeFile(
        srcDir,
        "foo/unexported/UnexportedAnnotation.java",
        "package foo.unexported;",
        "",
        "import java.lang.annotation.*;",
        "",
        "@Retention(RetentionPolicy.RUNTIME)",
        "public @interface UnexportedAnnotation {}");
    JavaCompiler.CompilationTask fooCompilationTask =
        javaCompiler.getTask(
            /* out= */ null,
            /* fileManager= */ null,
            /* diagnosticListener= */ null,
            /* options= */ ImmutableList.of(
                "--module",
                "foo",
                "-d",
                outDir.toString(),
                "--module-source-path",
                srcDir.toString()),
            /* classes= */ null,
            /* compilationUnits= */ null);
    fooCompilationTask.setProcessors(ImmutableList.of(new AutoValueProcessor()));
    boolean fooSuccess = fooCompilationTask.call();
    assertThat(fooSuccess).isTrue();

    JavaFileObject barFile =
        JavaFileObjects.forSourceLines(
            "bar.Value",
            "package bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import foo.exported.Foo;",
            "",
            "@AutoValue",
            "public abstract class Value implements Foo {",
            "  public static Value of(String name) {",
            "    return new AutoValue_Value(name);",
            "  }",
            "}");
    List<Diagnostic<?>> diagnostics = new ArrayList<>();
    JavaCompiler.CompilationTask barCompilationTask =
        javaCompiler.getTask(
            /* out= */ null,
            /* fileManager= */ null,
            /* diagnosticListener= */ diagnostics::add,
            /* options= */ ImmutableList.of(
                "-d",
                outDir.toString(),
                "--module-path",
                fooOut.toString(),
                "--add-modules",
                "foo"),
            /* classes= */ null,
            /* compilationUnits= */ ImmutableList.of(barFile));
    boolean barSuccess = barCompilationTask.call();
    assertThat(barSuccess).isFalse();
    assertThat(
            diagnostics.stream()
                .map(d -> d.getMessage(Locale.ROOT))
                .collect(toImmutableList())
                .toString())
        .contains("package foo.unexported is declared in module foo, which does not export it");
  }

  private static void writeFile(Path srcDir, String relativeName, String... lines)
      throws IOException {
    Path path = srcDir.resolve(relativeName);
    Files.createDirectories(path.getParent());
    Files.writeString(path, String.join("\n", lines));
  }
}
