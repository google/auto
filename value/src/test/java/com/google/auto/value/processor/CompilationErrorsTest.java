/*
 * Copyright (C) 2012 The Guava Authors
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

import com.google.common.base.Joiner;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Pattern;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class CompilationErrorsTest extends TestCase {

  // TODO(emcmanus): add tests for:
  // - superclass in a different package with nonpublic abstract methods (this must fail but
  //   is it clean?)

  private JavaCompiler javac;
  private DiagnosticCollector<JavaFileObject> diagnosticCollector;
  private StandardJavaFileManager fileManager;
  private File tmpDir;

  @Override
  protected void setUp() {
    javac = ToolProvider.getSystemJavaCompiler();
    diagnosticCollector = new DiagnosticCollector<JavaFileObject>();
    fileManager = javac.getStandardFileManager(diagnosticCollector, null, null);
    tmpDir = Files.createTempDir();
  }

  @Override
  protected void tearDown() {
    boolean deletedAll = deleteDirectory(tmpDir);
    assertTrue(deletedAll);
  }

  // Files.deleteRecursively has been deprecated because Dr Evil could put a symlink in the
  // temporary directory while this test is running and make you delete a bunch of unrelated stuff.
  // That's surely not much of a problem here, but just in case, we check that anything we're going
  // to delete is either a directory or ends with .java or .class.
  // TODO(emcmanus): simplify now that we are only using this to test compilation failure.
  // It should be straightforward to know exactly what files will be generated.
  private boolean deleteDirectory(File dir) {
    File[] files = dir.listFiles();
    boolean deletedAll = true;
    for (File file : files) {
      if (file.isDirectory()) {
        deletedAll &= deleteDirectory(file);
      } else if (file.getName().endsWith(".java") || file.getName().endsWith(".class")) {
        deletedAll &= file.delete();
      } else {
        fail("Not deleting unexpected file " + file);
      }
    }
    return dir.delete() && deletedAll;
  }

  // Ensure that assertCompilationFails does in fact throw AssertionError when compilation succeeds.
  public void testAssertCompilationFails() throws Exception {
    String testSourceCode = Joiner.on('\n').join(
        "package foo.bar;",
        "import com.google.auto.value.AutoValue;",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract int integer();",
        "  public static Baz create(int integer) {",
        "    return new AutoValue_Baz(integer);",
        "  }\n",
        "}\n");
    boolean compiled = false;
    try {
      assertCompilationFails(ImmutableList.of(testSourceCode));
      compiled = true;
    } catch (AssertionError expected) {
    }
    assertFalse(compiled);
  }

  public void testNoWarningsFromGenerics() throws Exception {
    String testSourceCode = Joiner.on('\n').join(
        "package foo.bar;",
        "import com.google.auto.value.AutoValue;",
        "@AutoValue",
        "public abstract class Baz<T extends Number, U extends T> {",
        "  public abstract T t();",
        "  public abstract U u();",
        "  public static <T extends Number, U extends T> Baz<T, U> create(T t, U u) {",
        "    return new AutoValue_Baz<T, U>(t, u);",
        "  }",
        "}");
    assertCompilationSucceedsWithoutWarning(ImmutableList.of(testSourceCode));
  }

  private static final Pattern CANNOT_HAVE_NON_PROPERTIES = Pattern.compile(
      "@AutoValue classes cannot have abstract methods other than property getters");

  public void testAbstractVoid() throws Exception {
    String testSourceCode = Joiner.on('\n').join(
        "package foo.bar;",
        "import com.google.auto.value.AutoValue;",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract void foo();",
        "}");
    ImmutableTable<Diagnostic.Kind, Integer, Pattern> expectedDiagnostics =
        new ImmutableTable.Builder<Diagnostic.Kind, Integer, Pattern>()
        .put(Diagnostic.Kind.WARNING, 5, CANNOT_HAVE_NON_PROPERTIES)
        .put(Diagnostic.Kind.ERROR, 0, Pattern.compile("AutoValue_Baz"))
        .build();
    assertCompilationResultIs(expectedDiagnostics, ImmutableList.of(testSourceCode));
  }

  public void testAbstractWithParams() throws Exception {
    String testSourceCode = Joiner.on('\n').join(
        "package foo.bar;",
        "import com.google.auto.value.AutoValue;",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract int foo(int bar);",
        "}");
    ImmutableTable<Diagnostic.Kind, Integer, Pattern> expectedDiagnostics =
        new ImmutableTable.Builder<Diagnostic.Kind, Integer, Pattern>()
        .put(Diagnostic.Kind.WARNING, 5, CANNOT_HAVE_NON_PROPERTIES)
        .put(Diagnostic.Kind.ERROR, 0, Pattern.compile("AutoValue_Baz"))
        .build();
    assertCompilationResultIs(expectedDiagnostics, ImmutableList.of(testSourceCode));
  }

  public void testPrimitiveArrayWarning() throws Exception {
    String testSourceCode = Joiner.on('\n').join(
        "package foo.bar;",
        "import com.google.auto.value.AutoValue;",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract byte[] bytes();",
        "  public static Baz create(byte[] bytes) {",
        "    return new AutoValue_Baz(bytes);",
        "  }",
        "}");
    Pattern warningPattern = Pattern.compile(
        "An @AutoValue property that is a primitive array returns the original array");
    ImmutableTable<Diagnostic.Kind, Integer, Pattern> expectedDiagnostics = ImmutableTable.of(
        Diagnostic.Kind.WARNING, 5, warningPattern);
    assertCompilationResultIs(expectedDiagnostics, ImmutableList.of(testSourceCode));
  }

  public void testPrimitiveArrayWarningFromParent() throws Exception {
    // If the array-valued property is defined by an ancestor then we shouldn't try to attach
    // the warning to the method that defined it, but rather to the @AutoValue class itself.
    String testSourceCode = Joiner.on('\n').join(
        "package foo.bar;",
        "import com.google.auto.value.AutoValue;",
        "public abstract class Baz {",
        "  public abstract byte[] bytes();",
        "",
        "  @AutoValue",
        "  public abstract static class BazChild extends Baz {",
        "    public static BazChild create(byte[] bytes) {",
        "      return new AutoValue_Baz_BazChild(bytes);",
        "    }",
        "  }",
        "}");
    Pattern warningPattern = Pattern.compile(
        "An @AutoValue property that is a primitive array returns the original array"
        + ".*foo\\.bar\\.Baz\\.bytes");
    ImmutableTable<Diagnostic.Kind, Integer, Pattern> expectedDiagnostics = ImmutableTable.of(
        Diagnostic.Kind.WARNING, 7, warningPattern);
    assertCompilationResultIs(expectedDiagnostics, ImmutableList.of(testSourceCode));
  }

  public void testPrimitiveArrayWarningSuppressed() throws Exception {
    String testSourceCode = Joiner.on('\n').join(
        "package foo.bar;",
        "import com.google.auto.value.AutoValue;",
        "@AutoValue",
        "public abstract class Baz {",
        "  @SuppressWarnings(\"mutable\")",
        "  public abstract byte[] bytes();",
        "  public static Baz create(byte[] bytes) {",
        "    return new AutoValue_Baz(bytes);",
        "  }",
        "}");
    assertCompilationSucceedsWithoutWarning(ImmutableList.of(testSourceCode));
  }
  // We compile the test classes by writing the source out to our temporary directory and invoking
  // the compiler on them. An earlier version of this test used an in-memory JavaFileManager, but
  // that is probably overkill, and in any case led to a problem that I gave up trying to fix,
  // where a bunch of classes were somehow failing to load, such as junit.framework.TestFailure
  // and the local classes that are defined in the various test methods. The TestFailure class in
  // particular worked fine if I instantiated it before running any test code, but something in the
  // invocation of javac.getTask with the MemoryFileManager broke things. I don't know how to
  // explain what I saw other than as a bug in the JDK and the simplest fix was just to use
  // the standard JavaFileManager.
  private void assertCompilationFails(List<String> testSourceCode) throws IOException {
    assertCompilationResultIs(ImmutableTable.of(Diagnostic.Kind.ERROR, 0, Pattern.compile("")),
        testSourceCode);
  }

  private void assertCompilationSucceedsWithoutWarning(List<String> testSourceCode)
      throws IOException {
    assertCompilationResultIs(ImmutableTable.<Diagnostic.Kind, Integer, Pattern>of(),
        testSourceCode);
  }

  /**
   * Assert that the result of compiling the source file whose lines are {@code testSourceCode}
   * corresponds to the diagnostics in {@code expectedDiagnostics}. Each row of
   * {@expectedDiagnostics} specifies a diagnostic kind (such as warning or error), a line number
   * on which the diagnostic is expected, and a Pattern that is expected to match the diagnostic
   * text. If the line number is 0 it is not checked.
   */
  private void assertCompilationResultIs(
      Table<Diagnostic.Kind, Integer, Pattern> expectedDiagnostics,
      List<String> testSourceCode) throws IOException {
    assertFalse(testSourceCode.isEmpty());

    StringWriter compilerOut = new StringWriter();

    List<String> options = ImmutableList.of(
        "-sourcepath", tmpDir.getPath(),
        "-d", tmpDir.getPath(),
        "-processor", AutoValueProcessor.class.getName(),
        "-Xlint");
    javac.getTask(compilerOut, fileManager, diagnosticCollector, options, null, null);
    // This doesn't compile anything but communicates the paths to the JavaFileManager.

    // Convert the strings containing the source code of the test classes into files that we
    // can feed to the compiler.
    List<String> classNames = Lists.newArrayList();
    List<JavaFileObject> sourceFiles = Lists.newArrayList();
    for (String source : testSourceCode) {
      ClassName className = ClassName.extractFromSource(source);
      File dir = new File(tmpDir, className.sourceDirectoryName());
      dir.mkdirs();
      assertTrue(dir.isDirectory());  // True if we just made it, or it was already there.
      String sourceName = className.simpleName + ".java";
      Files.write(source, new File(dir, sourceName), Charset.forName("UTF-8"));
      classNames.add(className.fullName());
      JavaFileObject sourceFile = fileManager.getJavaFileForInput(
          StandardLocation.SOURCE_PATH, className.fullName(), Kind.SOURCE);
      sourceFiles.add(sourceFile);
    }
    assertEquals(classNames.size(), sourceFiles.size());

    // Compile the classes.
    JavaCompiler.CompilationTask javacTask = javac.getTask(
        compilerOut, fileManager, diagnosticCollector, options, classNames, sourceFiles);
    boolean compiledOk = javacTask.call();

    // Check that there were no compilation errors unless we were expecting there to be.
    // We ignore "notes", typically debugging output from the annotation processor
    // when that is enabled.
    Table<Diagnostic.Kind, Integer, String> diagnostics = HashBasedTable.create();
    for (Diagnostic<?> diagnostic : diagnosticCollector.getDiagnostics()) {
      boolean ignore = (diagnostic.getKind() == Diagnostic.Kind.NOTE
          || (diagnostic.getKind() == Diagnostic.Kind.WARNING
              && diagnostic.getMessage(null).contains(
                  "No processor claimed any of these annotations")));
      if (!ignore) {
        diagnostics.put(
            diagnostic.getKind(), (int) diagnostic.getLineNumber(), diagnostic.getMessage(null));
      }
    }
    assertEquals(diagnostics.containsRow(Diagnostic.Kind.ERROR), !compiledOk);
    assertEquals("Diagnostic kinds should match: " + diagnostics,
        expectedDiagnostics.rowKeySet(), diagnostics.rowKeySet());
    for (Table.Cell<Diagnostic.Kind, Integer, Pattern> expectedDiagnostic :
             expectedDiagnostics.cellSet()) {
      boolean match = false;
      for (Table.Cell<Diagnostic.Kind, Integer, String> diagnostic : diagnostics.cellSet()) {
        if (expectedDiagnostic.getValue().matcher(diagnostic.getValue()).find()) {
          int expectedLine = expectedDiagnostic.getColumnKey();
          if (expectedLine != 0) {
            int actualLine = diagnostic.getColumnKey();
            if (actualLine != expectedLine) {
              fail("Diagnostic matched pattern but on line " + actualLine
                  + " not line " + expectedLine + ": " + diagnostic.getValue());
            }
          }
          match = true;
          break;
        }
      }
      assertTrue("Diagnostics should contain " + expectedDiagnostic + ": " + diagnostics, match);
    }
  }

  private static class ClassName {
    final String packageName; // Package name with trailing dot. May be empty but not null.
    final String simpleName;

    private ClassName(String packageName, String simpleName) {
      this.packageName = packageName;
      this.simpleName = simpleName;
    }

    // Extract the package and simple name of the top-level class defined in the given string,
    // which is a Java sourceUnit unit.
    static ClassName extractFromSource(String sourceUnit) {
      String pkg;
      if (sourceUnit.contains("package ")) {
        // (?s) means that . matches everything including \n
        pkg = sourceUnit.replaceAll("(?s).*?package ([a-z.]+);.*", "$1") + ".";
      } else {
        pkg = "";
      }
      String cls = sourceUnit.replaceAll("(?s).*?(class|interface|enum) ([A-Za-z0-9_$]+).*", "$2");
      assertTrue(cls, cls.matches("[A-Za-z0-9_$]+"));
      return new ClassName(pkg, cls);
    }

    String fullName() {
      return packageName + simpleName;
    }

    String sourceDirectoryName() {
      return packageName.replace('.', '/');
    }
  }
}
