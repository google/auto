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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
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
  private static final ImmutableMap<String, String> CLASS_TO_SOURCE = ImmutableMap.of(
      "Test",
          "public class Test {}\n",
      "MultipleBounds",
          "import java.util.List;\n"
          + "public class MultipleBounds<K extends List<V> & Comparable<K>, V> {}\n"
  );

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
    for (String className : CLASS_TO_SOURCE.keySet()) {
      File java = new File(tmpDir, className + ".java");
      Files.write(CLASS_TO_SOURCE.get(className), java, Charsets.UTF_8);
    }
    try {
      doTestTypeSimplifier(tmpDir);
    } finally {
      for (String className : CLASS_TO_SOURCE.keySet()) {
        File java = new File(tmpDir, className + ".java");
        assertTrue(java.delete());
        new File(tmpDir, className + ".class").delete();
      }
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

    ImmutableList.Builder<JavaFileObject> javaFilesBuilder = ImmutableList.builder();
    for (String className : CLASS_TO_SOURCE.keySet()) {
      JavaFileObject sourceFile = fileManager.getJavaFileForInput(
          StandardLocation.SOURCE_PATH, className, Kind.SOURCE);
      javaFilesBuilder.add(sourceFile);
    }

    // Compile the empty source file to trigger the annotation processor.
    // (Annotation processors are somewhat misnamed because they run even on classes with no
    // annotations.)
    JavaCompiler.CompilationTask javacTask = javac.getTask(
        compilerOut, fileManager, diagnosticCollector, options,
        CLASS_TO_SOURCE.keySet(), javaFilesBuilder.build());
    boolean compiledOk = javacTask.call();
    assertTrue(compilerOut.toString() + diagnosticCollector.getDiagnostics(), compiledOk);
  }

  // A type which is deliberately ambiguous with Map.Entry. Used to perform an ambiguity test below.
  static final class Entry {}

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

    private TypeMirror baseWithoutContainedTypes() {
      return typeMirrorOf("java.lang.Object");
    }

    private TypeMirror baseDeclaresEntry() {
      return typeMirrorOf("java.util.Map");
    }

    private Set<TypeMirror> typeMirrorSet(TypeMirror... typeMirrors) {
      Set<TypeMirror> set = new TypeMirrorSet();
      for (TypeMirror typeMirror : typeMirrors) {
        assertTrue(set.add(typeMirror));
      }
      return set;
    }

    private TypeMirror objectMirror() {
      return typeMirrorOf("java.lang.Object");
    }

    private TypeMirror cloneReturnTypeMirror() {
      TypeElement object = typeElementOf("java.lang.Object");
      ExecutableElement clone = null;
      for (Element element : object.getEnclosedElements()) {
        if (element.getSimpleName().contentEquals("clone")) {
          clone = (ExecutableElement) element;
          break;
        }
      }
      return clone.getReturnType();
    }

    /**
     * This test shows why we need to have TypeMirrorSet. The mirror of java.lang.Object obtained
     * from {@link Elements#getTypeElement Elements.getTypeElement("java.lang.Object")} does not
     * compare equal to the mirror of the return type of Object.clone(), even though that is also
     * java.lang.Object and {@link Types#isSameType} considers them the same.
     *
     * <p>There's no requirement that this test pass and if it starts failing or doesn't work in
     * another test environment then we can delete it. The specification of
     * {@link TypeMirror#equals} explicitly says that it cannot be used for type equality, so even
     * if this particular case stops being a problem (which means this test would fail), we would
     * need TypeMirrorSet for complete correctness.
     */
    public void testQuirkyTypeMirrors() {
      TypeMirror objectMirror = objectMirror();
      TypeMirror cloneReturnTypeMirror = cloneReturnTypeMirror();
      assertFalse(objectMirror.equals(cloneReturnTypeMirror));
      assertTrue(typeUtil.isSameType(objectMirror, cloneReturnTypeMirror));
    }

    public void testTypeMirrorSet() {
      TypeMirror objectMirror = objectMirror();
      TypeMirror otherObjectMirror = cloneReturnTypeMirror();
      Set<TypeMirror> set = new TypeMirrorSet();
      assertEquals(0, set.size());
      assertTrue(set.isEmpty());
      boolean added = set.add(objectMirror);
      assertTrue(added);
      assertEquals(1, set.size());

      Set<TypeMirror> otherSet = typeMirrorSet(otherObjectMirror);
      assertEquals(set, otherSet);
      assertEquals(otherSet, set);
      assertEquals(set.hashCode(), otherSet.hashCode());

      assertFalse(set.add(otherObjectMirror));
      assertTrue(set.contains(otherObjectMirror));

      assertFalse(set.contains(null));
      assertFalse(set.contains("foo"));
      assertFalse(set.remove(null));
      assertFalse(set.remove("foo"));

      TypeElement list = typeElementOf("java.util.List");
      TypeMirror listOfObjectMirror = typeUtil.getDeclaredType(list, objectMirror);
      TypeMirror listOfOtherObjectMirror = typeUtil.getDeclaredType(list, otherObjectMirror);
      assertFalse(listOfObjectMirror.equals(listOfOtherObjectMirror));
      assertTrue(typeUtil.isSameType(listOfObjectMirror, listOfOtherObjectMirror));
      added = set.add(listOfObjectMirror);
      assertTrue(added);
      assertEquals(2, set.size());
      assertFalse(set.add(listOfOtherObjectMirror));
      assertTrue(set.contains(listOfOtherObjectMirror));

      boolean removed = set.remove(listOfOtherObjectMirror);
      assertTrue(removed);
      assertFalse(set.contains(listOfObjectMirror));

      set.removeAll(otherSet);
      assertTrue(set.isEmpty());
    }

    public void testPackageNameOfString() {
      assertEquals("java.lang", TypeSimplifier.packageNameOf(typeElementOf("java.lang.String")));
    }

    public void testPackageNameOfMapEntry() {
      assertEquals("java.util", TypeSimplifier.packageNameOf(typeElementOf("java.util.Map.Entry")));
    }

    public void testPackageNameOfDefaultPackage() {
      String aClassName = CLASS_TO_SOURCE.keySet().iterator().next();
      assertEquals("", TypeSimplifier.packageNameOf(typeElementOf(aClassName)));
    }

    public void testImportsForNoTypes() {
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", typeMirrorSet(), baseWithoutContainedTypes());
      assertEquals(ImmutableSet.of(), typeSimplifier.typesToImport());
    }

    public void testImportsForImplicitlyImportedTypes() {
      Set<TypeMirror> types = typeMirrorSet(
          typeMirrorOf("java.lang.String"),
          typeMirrorOf("javax.management.MBeanServer"),  // Same package, so no import.
          typeUtil.getPrimitiveType(TypeKind.INT),
          typeUtil.getPrimitiveType(TypeKind.BOOLEAN)
      );
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "javax.management", types, baseWithoutContainedTypes());
      assertEquals(ImmutableSet.of(), typeSimplifier.typesToImport());
    }

    public void testImportsForPlainTypes() {
      Set<TypeMirror> types = typeMirrorSet(
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
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals(expectedImports, ImmutableList.copyOf(typeSimplifier.typesToImport()));
    }

    public void testImportsForComplicatedTypes() {
      TypeElement list = typeElementOf("java.util.List");
      TypeElement map = typeElementOf("java.util.Map");
      Set<TypeMirror> types = typeMirrorSet(
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
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals(expectedImports, ImmutableList.copyOf(typeSimplifier.typesToImport()));
    }

    public void testImportsForArrayTypes() {
      TypeElement list = typeElementOf("java.util.List");
      TypeElement set = typeElementOf("java.util.Set");
      Set<TypeMirror> types = typeMirrorSet(
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
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals(expectedImports, ImmutableList.copyOf(typeSimplifier.typesToImport()));
    }

    public void testImportsForDefaultPackage() {
      Set<TypeMirror> types = typeMirrorSet();
      for (String className : CLASS_TO_SOURCE.keySet()) {
        assertTrue(types.add(typeMirrorOf(className)));
        // These are all in the default package so they don't need to be imported.
        // But MultipleBounds references java.util.List so that will be imported.
      }
      types.addAll(typeMirrorSet(
          typeUtil.getPrimitiveType(TypeKind.INT),
          typeMirrorOf("java.lang.String"),
          typeMirrorOf("java.util.Map"),
          typeMirrorOf("java.util.Map.Entry"),
          typeMirrorOf("java.util.regex.Pattern"),
          typeMirrorOf("javax.management.MBeanServer")));
      List<String> expectedImports = ImmutableList.of(
          "java.util.List",
          "java.util.Map",
          "java.util.Map.Entry",
          "java.util.regex.Pattern",
          "javax.management.MBeanServer"
      );
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "", types, baseWithoutContainedTypes());
      assertEquals(expectedImports, ImmutableList.copyOf(typeSimplifier.typesToImport()));
    }

    public void testImportsForAmbiguousNames() {
      Set<TypeMirror> types = typeMirrorSet(
          typeUtil.getPrimitiveType(TypeKind.INT),
          typeMirrorOf("java.awt.List"),
          typeMirrorOf("java.lang.String"),
          typeMirrorOf("java.util.List"),
          typeMirrorOf("java.util.Map")
      );
      List<String> expectedImports = ImmutableList.of(
          "java.util.Map"
      );
      TypeSimplifier typeSimplifier
          = new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals(expectedImports, ImmutableList.copyOf(typeSimplifier.typesToImport()));
    }

    public void testSimplifyJavaLangString() {
      TypeMirror string = typeMirrorOf("java.lang.String");
      Set<TypeMirror> types = typeMirrorSet(string);
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals("String", typeSimplifier.simplify(string));
    }

    public void testSimplifyJavaLangThreadState() {
      TypeMirror threadState = typeMirrorOf("java.lang.Thread.State");
      Set<TypeMirror> types = typeMirrorSet(threadState);
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals("Thread.State", typeSimplifier.simplify(threadState));
    }

    public void testSimplifyAmbiguousNames() {
      TypeMirror javaAwtList = typeMirrorOf("java.awt.List");
      TypeMirror javaUtilList = typeMirrorOf("java.util.List");
      Set<TypeMirror> types = typeMirrorSet(javaAwtList, javaUtilList);
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals(javaAwtList.toString(), typeSimplifier.simplify(javaAwtList));
      assertEquals(javaUtilList.toString(), typeSimplifier.simplify(javaUtilList));
    }

    public void testSimplifyAmbiguityFromWithinClass() {
      TypeMirror otherEntry = typeMirrorOf(TypeSimplifierTest.class.getCanonicalName() + ".Entry");
      Set<TypeMirror> types = typeMirrorSet(otherEntry);
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseDeclaresEntry());
      assertEquals(otherEntry.toString(), typeSimplifier.simplify(otherEntry));
    }

    public void testSimplifyJavaLangNamesake() {
      TypeMirror javaLangDouble = typeMirrorOf("java.lang.Double");
      TypeMirror awtDouble = typeMirrorOf("java.awt.geom.Arc2D.Double");
      Set<TypeMirror> types = typeMirrorSet(javaLangDouble, awtDouble);
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals(javaLangDouble.toString(), typeSimplifier.simplify(javaLangDouble));
      assertEquals(awtDouble.toString(), typeSimplifier.simplify(awtDouble));
    }

    public void testSimplifyComplicatedTypes() {
      TypeElement list = typeElementOf("java.util.List");
      TypeElement map = typeElementOf("java.util.Map");
      TypeMirror string = typeMirrorOf("java.lang.String");
      TypeMirror integer = typeMirrorOf("java.lang.Integer");
      TypeMirror pattern = typeMirrorOf("java.util.regex.Pattern");
      TypeMirror timer = typeMirrorOf("java.util.Timer");
      TypeMirror bigInteger = typeMirrorOf("java.math.BigInteger");
      Set<TypeMirror> types = typeMirrorSet(
          typeUtil.getPrimitiveType(TypeKind.INT),
          typeUtil.getArrayType(typeUtil.getPrimitiveType(TypeKind.BYTE)),
          pattern,
          typeUtil.getArrayType(pattern),
          typeUtil.getArrayType(typeUtil.getArrayType(pattern)),
          typeUtil.getDeclaredType(list, typeUtil.getWildcardType(null, null)),
          typeUtil.getDeclaredType(list, timer),
          typeUtil.getDeclaredType(map, string, integer),
          typeUtil.getDeclaredType(map,
              typeUtil.getWildcardType(timer, null), typeUtil.getWildcardType(null, bigInteger)));
      Set<String> expectedSimplifications = ImmutableSet.of(
          "int",
          "byte[]",
          "Pattern",
          "Pattern[]",
          "Pattern[][]",
          "List<?>",
          "List<Timer>",
          "Map<String, Integer>",
          "Map<? extends Timer, ? super BigInteger>"
      );
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      Set<String> actualSimplifications = new HashSet<String>();
      for (TypeMirror type : types) {
        actualSimplifications.add(typeSimplifier.simplify(type));
      }
      assertEquals(expectedSimplifications, actualSimplifications);
    }

    public void testSimplifyMultipleBounds() {
      TypeElement multipleBoundsElement = typeElementOf("MultipleBounds");
      TypeMirror multipleBoundsMirror = multipleBoundsElement.asType();
      TypeSimplifier typeSimplifier = new TypeSimplifier(typeUtil, "",
          typeMirrorSet(multipleBoundsMirror), baseWithoutContainedTypes());
      assertEquals(ImmutableSet.of("java.util.List"), typeSimplifier.typesToImport());
      assertEquals("MultipleBounds<K, V>", typeSimplifier.simplify(multipleBoundsMirror));
      assertEquals("<K extends List<V> & Comparable<K>, V>",
          typeSimplifier.formalTypeParametersString(multipleBoundsElement));
    }
  }
}
