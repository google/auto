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

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import junit.framework.TestCase;

import com.google.auto.value.processor.AutoValueProcessor;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;


/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class CompilationErrorsTest extends TestCase {

  // TODO(emcmanus): add tests for:
  // - forbidding abstract methods other than getters
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
    String testSourceCode =
        "package foo.bar;\n" +
        "import com.google.auto.value.AutoValue;\n" +
        "@AutoValue\n" +
        "public abstract class Baz {\n" +
        "  public abstract int integer();\n" +
        "  public static Baz create(int integer) {\n" +
        "    return new AutoValue_Baz(integer);\n" +
        "  }\n" +
        "}\n";
    boolean compiled = false;
    try {
      assertCompilationFails(ImmutableList.of(testSourceCode));
      compiled = true;
    } catch (AssertionError expected) {
    }
    assertFalse(compiled);
  }

  public void testNoPrimitiveArrays() throws Exception {
    String testSourceCode =
        "package foo.bar;\n" +
        "import com.google.auto.value.AutoValue;\n" +
        "@AutoValue\n" +
        "public abstract class Baz {\n" +
        "  public abstract int[] ints();\n" +
        "  public static Baz create(int[] ints) {\n" +
        "    return new AutoValue_Baz(ints);\n" +
        "  }\n" +
        "}\n";
    assertCompilationFails(ImmutableList.of(testSourceCode));
  }

  public void testNoObjectArrays() throws Exception {
    String testSourceCode =
        "package foo.bar;\n" +
        "import com.google.auto.value.AutoValue;\n" +
        "@AutoValue\n" +
        "public abstract class Baz {\n" +
        "  public abstract String[] strings();\n" +
        "  public static Baz create(String[] strings) {\n" +
        "    return new AutoValue_Baz(strings);\n" +
        "  }\n" +
        "}\n";
    assertCompilationFails(ImmutableList.of(testSourceCode));
  }

  public void testNoWarningsFromGenerics() throws Exception {
    String testSourceCode =
        "package foo.bar;\n" +
        "import com.google.auto.value.AutoValue;\n" +
        "@AutoValue\n" +
        "public abstract class Baz<T extends Number, U extends T> {\n" +
        "  public abstract T t();\n" +
        "  public abstract U u();\n" +
        "  public static <T extends Number, U extends T> Baz<T, U> create(T t, U u) {\n" +
        "    return new AutoValue_Baz<T, U>(t, u);\n" +
        "  }\n" +
        "}\n";
    assertCompilationSucceedsWithoutWarning(ImmutableList.of(testSourceCode));
  }

  public void testAnnotationOnInterface() throws Exception {
    String testSourceCode =
        "package foo.bar;\n" +
        "import com.google.auto.value.AutoValue;\n" +
        "@AutoValue\n" +
        "public interface Baz {}\n";
    assertCompilationFails(ImmutableList.of(testSourceCode));
  }

  public void testAnnotationOnEnum() throws Exception {
    String testSourceCode =
        "package foo.bar;\n" +
        "import com.google.auto.value.AutoValue;\n" +
        "@AutoValue\n" +
        "public enum Baz {}\n";
    assertCompilationFails(ImmutableList.of(testSourceCode));
  }

  public void testAbstractVoid() throws Exception {
    String testSourceCode =
        "package foo.bar;\n" +
        "import com.google.auto.value.AutoValue;\n" +
        "@AutoValue\n" +
        "public abstract class Baz {\n" +
        "  public abstract void foo();\n" +
        "}\n";
    assertCompilationFails(ImmutableList.of(testSourceCode));
  }

  public void testAbstractWithParams() throws Exception {
    String testSourceCode =
        "package foo.bar;\n" +
        "import com.google.auto.value.AutoValue;\n" +
        "@AutoValue\n" +
        "public abstract class Baz {\n" +
        "  public abstract int foo(int bar);\n" +
        "}\n";
    assertCompilationFails(ImmutableList.of(testSourceCode));
  }

  public void testFactoryWithMoreThanOneMethod() throws Exception {
    String testSourceCode =
        "package foo.bar;\n" +
        "import com.google.auto.value.AutoValue;\n" +
        "import com.google.auto.value.AutoValues;\n" +
        "@AutoValue\n" +
        "public abstract class Baz {\n" +
        "  public abstract int foo();\n" +
        "  public static Baz create(int foo) {\n" +
        "    return AutoValues.using(Factory.class).create(foo);\n" +
        "  }\n" +
        "  interface Factory {\n" +
        "    public Baz create(int foo);\n" +
        "    public Baz createAnotherWay(int foo);\n" +
        "  }\n" +
        "}\n";
    assertCompilationFails(ImmutableList.of(testSourceCode));
  }

  public void testFactoryWithWrongNumberOfParameters() throws Exception {
    String testSourceCode =
        "package foo.bar;\n" +
        "import com.google.auto.value.AutoValue;\n" +
        "import com.google.auto.value.AutoValues;\n" +
        "@AutoValue\n" +
        "public abstract class Baz {\n" +
        "  public abstract int foo();\n" +
        "  public abstract String bar();\n" +
        "  public static Baz create(int foo) {\n" +
        "    return AutoValues.using(Factory.class).create(foo);\n" +
        "  }\n" +
        "  interface Factory {\n" +
        "    public Baz create(int foo);\n" +
        "  }\n" +
        "}\n";
    assertCompilationFails(ImmutableList.of(testSourceCode));
  }

  public void testFactoryWithWrongNamesForParameters() throws Exception {
    String testSourceCode =
        "package foo.bar;\n" +
        "import com.google.auto.value.AutoValue;\n" +
        "import com.google.auto.value.AutoValues;\n" +
        "@AutoValue\n" +
        "public abstract class Baz {\n" +
        "  public abstract int foo();\n" +
        "  public abstract String bar();\n" +
        "  public static Baz create(int foo, String bar) {\n" +
        "    return AutoValues.using(Factory.class).create(foo, bar);\n" +
        "  }\n" +
        "  interface Factory {\n" +
        "    public Baz create(int foo, String barbarella);\n" +
        "  }\n" +
        "}\n";
    assertCompilationFails(ImmutableList.of(testSourceCode));
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
    assertCompilationResultIs(EnumSet.of(Diagnostic.Kind.ERROR), testSourceCode);
  }

  private void assertCompilationSucceedsWithoutWarning(List<String> testSourceCode)
      throws IOException {
    assertCompilationResultIs(EnumSet.noneOf(Diagnostic.Kind.class), testSourceCode);
  }

  private void assertCompilationResultIs(
      Set<Diagnostic.Kind> expectedDiagnosticKinds,
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
      Files.write(source, new File(dir, sourceName), Charsets.UTF_8);
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
    Set<Diagnostic.Kind> diagnosticKinds = EnumSet.noneOf(Diagnostic.Kind.class);
    for (Diagnostic<?> diagnostic : diagnosticCollector.getDiagnostics()) {
      boolean ignore = (diagnostic.getKind() == Diagnostic.Kind.NOTE
          || (diagnostic.getKind() == Diagnostic.Kind.WARNING
              && diagnostic.getMessage(null).contains(
                  "No processor claimed any of these annotations")));
      if (!ignore) {
        diagnosticKinds.add(diagnostic.getKind());
      }
    }
    assertEquals("Compilation result: " + diagnosticCollector.getDiagnostics(),
        expectedDiagnosticKinds, diagnosticKinds);
    assertEquals(diagnosticKinds.contains(Diagnostic.Kind.ERROR), !compiledOk);
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
