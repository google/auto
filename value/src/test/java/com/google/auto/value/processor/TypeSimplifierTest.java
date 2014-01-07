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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Tests for {@link TypeSimplifier}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class TypeSimplifierTest extends TestCase {
  private static final String TEST_JAVA_CLASS_NAME = "Test";
  private static final String TEST_JAVA_FILE_NAME = "Test.java";
  private static final String TEST_JAVA_CLASS_FILE_NAME = "Test.class";
  private static final String TEST_JAVA_CONTENTS = "public class Test {}\n";

  // This test is a bit unusual. The reason is that TypeSimplifier relies on interfaces such as
  // Types, TypeMirror, and TypeElement whose implementations are provided by the annotation
  // processing environment. While we could make fake or mock implementations of those interfaces,
  // the resulting test would be very verbose and would not obviously be testing the right thing.
  // Instead, we run the compiler with a simple annotation-processing environment that allows us
  // to capture the real implementations of these interfaces. Since those implementations are not
  // necessarily valid when the compiler has exited, we run all our test cases from within our
  // annotation processor, converting test failures into compiler errors. Then testTypeSimplifier()
  // passes if there were no compiler errors, and otherwise fails with a message that is a
  // concatenation of all the individual failures.
  public void testTypeSimplifier() throws Exception {
    File tmpDir = Files.createTempDir();
    File testJava = new File(tmpDir, TEST_JAVA_FILE_NAME);
    Files.write(TEST_JAVA_CONTENTS, testJava, Charsets.UTF_8);
    try {
      doTestTypeSimplifier(tmpDir);
    } finally {
      assertTrue(testJava.delete());
      new File(tmpDir, TEST_JAVA_CLASS_FILE_NAME).delete();
      assertTrue(tmpDir.delete());
    }
  }

  private void doTestTypeSimplifier(File tmpDir) throws Exception {
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnosticCollector =
        new DiagnosticCollector<JavaFileObject>();
    StandardJavaFileManager fileManager =
        javac.getStandardFileManager(diagnosticCollector, null, null);

    StringWriter compilerOut = new StringWriter();

    List<String> options = ImmutableList.of(
        "-sourcepath", tmpDir.getPath(),
        "-d", tmpDir.getPath(),
        "-processor", TestProcessor.class.getName(),
        "-Xlint");
    javac.getTask(compilerOut, fileManager, diagnosticCollector, options, null, null);
    // This doesn't compile anything but communicates the paths to the JavaFileManager.

    JavaFileObject sourceFile = fileManager.getJavaFileForInput(
        StandardLocation.SOURCE_PATH, TEST_JAVA_CLASS_NAME, Kind.SOURCE);

    // Compile the empty source file to trigger the annotation processor.
    // (Annotation processors are somewhat misnamed because they run even on classes with no
    // annotations.)
    JavaCompiler.CompilationTask javacTask = javac.getTask(
        compilerOut, fileManager, diagnosticCollector, options,
        ImmutableList.of(TEST_JAVA_CLASS_NAME), ImmutableList.of(sourceFile));
    boolean compiledOk = javacTask.call();
    assertTrue(compilerOut.toString() + diagnosticCollector.getDiagnostics(), compiledOk);
  }

  @SupportedAnnotationTypes("*")
  public static class TestProcessor extends AbstractProcessor {
    private boolean testsRan;
    private Elements elementUtil;
    private Types typeUtil;

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (!testsRan) {
        testsRan = true;
        elementUtil = processingEnv.getElementUtils();
        typeUtil = processingEnv.getTypeUtils();
        runTests();
      }
      return false;
    }

    private void runTests() {
      for (Method method : TestProcessor.class.getMethods()) {
        if (method.getName().startsWith("test")) {
          try {
            method.invoke(this);
          } catch (Exception e) {
            Throwable cause = (e instanceof InvocationTargetException) ? e.getCause() : e;
            StringWriter stringWriter = new StringWriter();
            cause.printStackTrace(new PrintWriter(stringWriter));
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR, stringWriter.toString());
          }
        }
      }
    }

    private TypeElement typeElementOf(String name) {
      return elementUtil.getTypeElement(name);
    }

    private TypeMirror typeMirrorOf(String name) {
      return typeElementOf(name).asType();
    }

    public void testPackageNameOfString() {
      assertEquals("java.lang", TypeSimplifier.packageNameOf(typeElementOf("java.lang.String")));
    }

    public void testPackageNameOfMapEntry() {
      assertEquals("java.util", TypeSimplifier.packageNameOf(typeElementOf("java.util.Map.Entry")));
    }

    public void testPackageNameOfDefaultPackage() {
      assertEquals("", TypeSimplifier.packageNameOf(typeElementOf(TEST_JAVA_CLASS_NAME)));
    }

    public void testImportsForNoTypes() {
      Set<TypeMirror> types = ImmutableSet.of();
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "foo.bar", types);
      assertEquals(ImmutableSet.of(), typeSimplifier.typesToImport());
    }

    public void testImportsForImplicitlyImportedTypes() {
      Set<TypeMirror> types = ImmutableSet.of(
          typeMirrorOf("java.lang.String"),
          typeMirrorOf("javax.management.MBeanServer"),  // Same package, so no import.
          typeUtil.getPrimitiveType(TypeKind.INT),
          typeUtil.getPrimitiveType(TypeKind.BOOLEAN)
      );
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "javax.management", types);
      assertEquals(ImmutableSet.of(), typeSimplifier.typesToImport());
    }

    public void testImportsForPlainTypes() {
      Set<TypeMirror> types = ImmutableSet.of(
          typeUtil.getPrimitiveType(TypeKind.INT),
          typeMirrorOf("java.lang.String"),
          typeMirrorOf("java.util.Map"),
          typeMirrorOf("java.util.Map.Entry"),
          typeMirrorOf("java.util.regex.Pattern"),
          typeMirrorOf("javax.management.MBeanServer"));
      List<String> expectedImports = ImmutableList.of(
          "java.util.Map",
          "java.util.Map.Entry",
          "java.util.regex.Pattern",
          "javax.management.MBeanServer"
      );
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "foo.bar", types);
      assertEquals(expectedImports, ImmutableList.copyOf(typeSimplifier.typesToImport()));
    }

    public void testImportsForComplicatedTypes() {
      TypeElement list = typeElementOf("java.util.List");
      TypeElement map = typeElementOf("java.util.Map");
      Set<TypeMirror> types = ImmutableSet.of(
          typeUtil.getPrimitiveType(TypeKind.INT),
          typeMirrorOf("java.util.regex.Pattern"),
          typeUtil.getDeclaredType(list,  // List<Timer>
              typeMirrorOf("java.util.Timer")),
          typeUtil.getDeclaredType(map,   // Map<? extends Timer, ? super BigInteger>
              typeUtil.getWildcardType(typeMirrorOf("java.util.Timer"), null),
              typeUtil.getWildcardType(null, typeMirrorOf("java.math.BigInteger"))));
      // Timer is referenced twice but should obviously only be imported once.
      List<String> expectedImports = ImmutableList.of(
          "java.math.BigInteger",
          "java.util.List",
          "java.util.Map",
          "java.util.Timer",
          "java.util.regex.Pattern"
      );
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "foo.bar", types);
      assertEquals(expectedImports, ImmutableList.copyOf(typeSimplifier.typesToImport()));
    }

    public void testImportsForArrayTypes() {
      TypeElement list = typeElementOf("java.util.List");
      TypeElement set = typeElementOf("java.util.Set");
      Set<TypeMirror> types = ImmutableSet.<TypeMirror>of(
          typeUtil.getArrayType(typeUtil.getPrimitiveType(TypeKind.INT)),
          typeUtil.getArrayType(typeMirrorOf("java.util.regex.Pattern")),
          typeUtil.getArrayType(          // Set<Matcher[]>[]
              typeUtil.getDeclaredType(set,
                  typeUtil.getArrayType(typeMirrorOf("java.util.regex.Matcher")))),
          typeUtil.getDeclaredType(list,  // List<Timer[]>
              typeUtil.getArrayType(typeMirrorOf("java.util.Timer"))));
      // Timer is referenced twice but should obviously only be imported once.
      List<String> expectedImports = ImmutableList.of(
          "java.util.List",
          "java.util.Set",
          "java.util.Timer",
          "java.util.regex.Matcher",
          "java.util.regex.Pattern"
      );
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "foo.bar", types);
      assertEquals(expectedImports, ImmutableList.copyOf(typeSimplifier.typesToImport()));
    }

    public void testImportsForDefaultPackage() {
      Set<TypeMirror> types = ImmutableSet.of(
          typeUtil.getPrimitiveType(TypeKind.INT),
          typeMirrorOf(TEST_JAVA_CLASS_NAME),
          typeMirrorOf("java.lang.String"),
          typeMirrorOf("java.util.Map"),
          typeMirrorOf("java.util.Map.Entry"),
          typeMirrorOf("java.util.regex.Pattern"),
          typeMirrorOf("javax.management.MBeanServer"));
      List<String> expectedImports = ImmutableList.of(
          "java.util.Map",
          "java.util.Map.Entry",
          "java.util.regex.Pattern",
          "javax.management.MBeanServer"
      );
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "", types);
      assertEquals(expectedImports, ImmutableList.copyOf(typeSimplifier.typesToImport()));
    }

    public void testImportsForAmbiguousNames() {
      Set<TypeMirror> types = ImmutableSet.of(
          typeUtil.getPrimitiveType(TypeKind.INT),
          typeMirrorOf("java.awt.List"),
          typeMirrorOf("java.lang.String"),
          typeMirrorOf("java.util.List"),
          typeMirrorOf("java.util.Map")
      );
      List<String> expectedImports = ImmutableList.of(
          "java.util.Map"
      );
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "foo.bar", types);
      assertEquals(expectedImports, ImmutableList.copyOf(typeSimplifier.typesToImport()));
    }

    public void testSimplifyJavaLangString() {
      TypeMirror string = typeMirrorOf("java.lang.String");
      Set<TypeMirror> types = ImmutableSet.of(string);
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "foo.bar", types);
      assertEquals("String", typeSimplifier.simplify(string));
    }

    public void testSimplifyJavaLangThreadState() {
      TypeMirror threadState = typeMirrorOf("java.lang.Thread.State");
      Set<TypeMirror> types = ImmutableSet.of(threadState);
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "foo.bar", types);
      assertEquals("Thread.State", typeSimplifier.simplify(threadState));
    }

    public void testSimplifyAmbiguousNames() {
      TypeMirror javaAwtList = typeMirrorOf("java.awt.List");
      TypeMirror javaUtilList = typeMirrorOf("java.util.List");
      Set<TypeMirror> types = ImmutableSet.of(javaAwtList, javaUtilList);
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "foo.bar", types);
      assertEquals(javaAwtList.toString(), typeSimplifier.simplify(javaAwtList));
      assertEquals(javaUtilList.toString(), typeSimplifier.simplify(javaUtilList));
    }

    public void testSimplifyJavaLangNamesake() {
      TypeMirror javaLangDouble = typeMirrorOf("java.lang.Double");
      TypeMirror awtDouble = typeMirrorOf("java.awt.geom.Arc2D.Double");
      Set<TypeMirror> types = ImmutableSet.of(javaLangDouble, awtDouble);
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "foo.bar", types);
      assertEquals(javaLangDouble.toString(), typeSimplifier.simplify(javaLangDouble));
      assertEquals(awtDouble.toString(), typeSimplifier.simplify(awtDouble));
    }

    public void testSimplifyComplicatedTypes() {
      TypeElement list = typeElementOf("java.util.List");
      TypeElement map = typeElementOf("java.util.Map");
      TypeMirror pattern = typeMirrorOf("java.util.regex.Pattern");
      TypeMirror timer = typeMirrorOf("java.util.Timer");
      TypeMirror bigInteger = typeMirrorOf("java.math.BigInteger");
      Set<TypeMirror> types = ImmutableSet.of(
          typeUtil.getPrimitiveType(TypeKind.INT),
          typeUtil.getArrayType(typeUtil.getPrimitiveType(TypeKind.BYTE)),
          pattern,
          typeUtil.getArrayType(pattern),
          typeUtil.getArrayType(typeUtil.getArrayType(pattern)),
          typeUtil.getDeclaredType(list, typeUtil.getWildcardType(null, null)),
          typeUtil.getDeclaredType(list, timer),
          typeUtil.getDeclaredType(map,
              typeUtil.getWildcardType(timer, null), typeUtil.getWildcardType(null, bigInteger)));
      List<String> expectedSimplifications = ImmutableList.of(
          "int",
          "byte[]",
          "Pattern",
          "Pattern[]",
          "Pattern[][]",
          "List<?>",
          "List<Timer>",
          "Map<? extends Timer, ? super BigInteger>"
      );
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "foo.bar", types);
      List<String> actualSimplifications = new ArrayList<String>();
      for (TypeMirror type : types) {
        actualSimplifications.add(typeSimplifier.simplify(type));
      }
      assertEquals(expectedSimplifications, actualSimplifications);
    }
  }
}
