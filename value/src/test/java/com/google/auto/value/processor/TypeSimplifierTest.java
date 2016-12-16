/*
 * Copyright (C) 2012 Google Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link TypeSimplifier}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class TypeSimplifierTest {
  private static final ImmutableMap<String, String> CLASS_TO_SOURCE = ImmutableMap.of(
      "Test",
          "public class Test {}\n",
      "MultipleBounds",
          "import java.util.List;\n"
          + "public class MultipleBounds<K extends List<V> & Comparable<K>, V> {}\n",
      "Erasure",
          "import java.util.List;\n"
          + "import java.util.Map;\n"
          + "@SuppressWarnings(\"rawtypes\")"
          + "public class Erasure<T> {\n"
          + "  int intNo; boolean booleanNo; int[] intArrayNo; String stringNo;\n"
          + "  String[] stringArrayNo; List rawListNo; List<?> listOfQueryNo;\n"
          + "  List<? extends Object> listOfQueryExtendsObjectNo;\n"
          + "  Map<?, ?> mapQueryToQueryNo;\n"
          + "\n"
          + "  List<String> listOfStringYes; List<? extends String> listOfQueryExtendsStringYes;\n"
          + "  List<? super String> listOfQuerySuperStringYes; List<T> listOfTypeVarYes;\n"
          + "  List<? extends T> listOfQueryExtendsTypeVarYes;\n"
          + "  List<? super T> listOfQuerySuperTypeVarYes;\n"
          + "}\n",
      "Wildcards",
          "import java.util.Map;\n"
          + "public abstract class Wildcards {\n"
          + "  abstract <T extends V, U extends T, V> Map<? extends T, ? super U> one();\n"
          + "  abstract <T extends V, U extends T, V> Map<? extends T, ? super U> two();\n"
          + "}\n"
  );
  private static final ImmutableMap<String, String> ERROR_CLASS_TO_SOURCE = ImmutableMap.of(
      "ExtendsUndefinedType",
          "public class ExtendsUndefinedType extends MissingType {}\n"
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
  // TODO(emcmanus): Use compile-testing to make all this unnecessary.
  @Test
  public void testTypeSimplifier() throws Exception {
    doTestTypeSimplifierWithSources(new MainTestProcessor(), CLASS_TO_SOURCE);
  }

  @Test
  public void testTypeSimplifierErrorTypes() throws IOException {
    doTestTypeSimplifierWithSources(new ErrorTestProcessor(), ERROR_CLASS_TO_SOURCE);
  }

  private void doTestTypeSimplifierWithSources(
      AbstractTestProcessor testProcessor, ImmutableMap<String, String> classToSource)
      throws IOException {
    File tmpDir = Files.createTempDir();
    for (String className : classToSource.keySet()) {
      File java = new File(tmpDir, className + ".java");
      Files.write(classToSource.get(className), java, Charset.forName("UTF-8"));
    }
    try {
      doTestTypeSimplifier(testProcessor, tmpDir, classToSource);
    } finally {
      for (String className : classToSource.keySet()) {
        File java = new File(tmpDir, className + ".java");
        assertTrue(java.delete());
        new File(tmpDir, className + ".class").delete();
      }
      assertTrue(tmpDir.delete());
    }
  }

  private void doTestTypeSimplifier(
      AbstractTestProcessor testProcessor, File tmpDir, ImmutableMap<String, String> classToSource)
      throws IOException {
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnosticCollector =
        new DiagnosticCollector<JavaFileObject>();
    StandardJavaFileManager fileManager =
        javac.getStandardFileManager(diagnosticCollector, null, null);

    StringWriter compilerOut = new StringWriter();

    List<String> options = ImmutableList.of(
        "-sourcepath", tmpDir.getPath(),
        "-d", tmpDir.getPath(),
        "-Xlint");
    javac.getTask(compilerOut, fileManager, diagnosticCollector, options, null, null);
    // This doesn't compile anything but communicates the paths to the JavaFileManager.

    ImmutableList.Builder<JavaFileObject> javaFilesBuilder = ImmutableList.builder();
    for (String className : classToSource.keySet()) {
      JavaFileObject sourceFile = fileManager.getJavaFileForInput(
          StandardLocation.SOURCE_PATH, className, Kind.SOURCE);
      javaFilesBuilder.add(sourceFile);
    }

    // Compile the empty source file to trigger the annotation processor.
    // (Annotation processors are somewhat misnamed because they run even on classes with no
    // annotations.)
    JavaCompiler.CompilationTask javacTask = javac.getTask(
        compilerOut, fileManager, diagnosticCollector, options,
        classToSource.keySet(), javaFilesBuilder.build());
    javacTask.setProcessors(ImmutableList.of(testProcessor));
    javacTask.call();
    List<Diagnostic<? extends JavaFileObject>> diagnostics =
        new ArrayList<Diagnostic<? extends JavaFileObject>>(diagnosticCollector.getDiagnostics());

    // In the ErrorTestProcessor case, the code being compiled contains a deliberate reference to an
    // undefined type, so that we can capture an instance of ErrorType. (Synthesizing one ourselves
    // leads to ClassCastException inside javac.) So remove any errors for that from the output, and
    // only fail if there were other errors.
    for (Iterator<Diagnostic<? extends JavaFileObject>> it = diagnostics.iterator();
         it.hasNext(); ) {
      Diagnostic<? extends JavaFileObject> diagnostic = it.next();
      if (diagnostic.getSource() != null
          && diagnostic.getSource().getName().contains("ExtendsUndefinedType")) {
        it.remove();
      }
    }
    // In the ErrorTestProcessor case, compilerOut.toString() will include the error for
    // ExtendsUndefinedType, which can safely be ignored, as well as stack traces for any failing
    // assertion.
    assertEquals(compilerOut.toString() + diagnosticCollector.getDiagnostics(),
        ImmutableList.of(), diagnostics);
  }

  // A type which is deliberately ambiguous with Map.Entry. Used to perform an ambiguity test below.
  static final class Entry {}

  private abstract static class AbstractTestProcessor extends AbstractProcessor {
    private boolean testsRan;
    Types typeUtil;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("*");
    }

    @Override
    public final SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latest();
    }

    @Override
    public final boolean process(
        Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (!testsRan) {
        testsRan = true;
        typeUtil = processingEnv.getTypeUtils();
        runTests();
      }
      return false;
    }

    private void runTests() {
      for (Method method : getClass().getMethods()) {
        if (method.isAnnotationPresent(Test.class)) {
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

    TypeElement typeElementOf(String name) {
      return processingEnv.getElementUtils().getTypeElement(name);
    }

    TypeMirror typeMirrorOf(String name) {
      return typeElementOf(name).asType();
    }

    TypeMirror baseWithoutContainedTypes() {
      return typeMirrorOf("java.lang.Object");
    }

    TypeMirror baseDeclaresEntry() {
      return typeMirrorOf("java.util.Map");
    }
  }

  private static class MainTestProcessor extends AbstractTestProcessor {
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
    @Test
    public void testQuirkyTypeMirrors() {
      TypeMirror objectMirror = objectMirror();
      TypeMirror cloneReturnTypeMirror = cloneReturnTypeMirror();
      assertFalse(objectMirror.equals(cloneReturnTypeMirror));
      assertTrue(typeUtil.isSameType(objectMirror, cloneReturnTypeMirror));
    }

    @Test
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
      assertFalse(set.contains((Object) "foo"));
      assertFalse(set.remove(null));
      assertFalse(set.remove((Object) "foo"));

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

    @Test
    public void testTypeMirrorSetWildcardCapture() {
      // TODO(emcmanus): this test should really be in MoreTypesTest.
      // This test checks the assumption made by MoreTypes that you can find the
      // upper bounds of a TypeVariable tv like this:
      //   TypeParameterElement tpe = (TypeParameterElement) tv.asElement();
      //   List<? extends TypeMirror> bounds = tpe.getBounds();
      // There was some doubt as to whether this would be true in exotic cases involving
      // wildcard capture, but apparently it is.
      // The methods one and two here have identical signatures:
      //   abstract <T extends V, U extends T, V> Map<? extends T, ? super U> name();
      // Their return types should be considered equal by TypeMirrorSet. The capture of
      // each return type is different from the original return type, but the two captures
      // should compare equal to each other. We also add various other types like ? super U
      // to the set to ensure that the implied calls to the equals and hashCode visitors
      // don't cause a ClassCastException for TypeParameterElement.
      TypeElement wildcardsElement = typeElementOf("Wildcards");
      List<? extends ExecutableElement> methods =
          ElementFilter.methodsIn(wildcardsElement.getEnclosedElements());
      assertEquals(2, methods.size());
      ExecutableElement one = methods.get(0);
      ExecutableElement two = methods.get(1);
      assertEquals("one", one.getSimpleName().toString());
      assertEquals("two", two.getSimpleName().toString());
      TypeMirrorSet typeMirrorSet = new TypeMirrorSet();
      assertTrue(typeMirrorSet.add(one.getReturnType()));
      assertFalse(typeMirrorSet.add(two.getReturnType()));
      DeclaredType captureOne = (DeclaredType) typeUtil.capture(one.getReturnType());
      assertTrue(typeMirrorSet.add(captureOne));
      DeclaredType captureTwo = (DeclaredType) typeUtil.capture(two.getReturnType());
      assertFalse(typeMirrorSet.add(captureTwo));
      // Reminder: captureOne is Map<?#123 extends T, ?#456 super U>
      TypeVariable extendsT = (TypeVariable) captureOne.getTypeArguments().get(0);
      assertTrue(typeMirrorSet.add(extendsT));
      assertTrue(typeMirrorSet.add(extendsT.getLowerBound()));  // NoType
      for (TypeMirror bound : ((TypeParameterElement) extendsT.asElement()).getBounds()) {
        assertTrue(typeMirrorSet.add(bound));
      }
      TypeVariable superU = (TypeVariable) captureOne.getTypeArguments().get(1);
      assertTrue(typeMirrorSet.add(superU));
      assertTrue(typeMirrorSet.add(superU.getLowerBound()));
    }

    @Test
    public void testPackageNameOfString() {
      assertEquals("java.lang", TypeSimplifier.packageNameOf(typeElementOf("java.lang.String")));
    }

    @Test
    public void testPackageNameOfMapEntry() {
      assertEquals("java.util", TypeSimplifier.packageNameOf(typeElementOf("java.util.Map.Entry")));
    }

    @Test
    public void testPackageNameOfDefaultPackage() {
      String aClassName = CLASS_TO_SOURCE.keySet().iterator().next();
      assertEquals("", TypeSimplifier.packageNameOf(typeElementOf(aClassName)));
    }

    @Test
    public void testImportsForNoTypes() {
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", typeMirrorSet(), baseWithoutContainedTypes());
      assertEquals(ImmutableSet.of(), typeSimplifier.typesToImport());
    }

    @Test
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

    @Test
    public void testImportsForPlainTypes() {
      Set<TypeMirror> types = typeMirrorSet(
          typeUtil.getPrimitiveType(TypeKind.INT),
          typeMirrorOf("java.lang.String"),
          typeMirrorOf("java.net.Proxy"),
          typeMirrorOf("java.net.Proxy.Type"),
          typeMirrorOf("java.util.regex.Pattern"),
          typeMirrorOf("javax.management.MBeanServer"));
      List<String> expectedImports = ImmutableList.of(
          "java.net.Proxy",
          "java.util.regex.Pattern",
          "javax.management.MBeanServer"
      );
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals(expectedImports, typeSimplifier.typesToImport().asList());
      assertEquals("Proxy", typeSimplifier.simplify(typeMirrorOf("java.net.Proxy")));
      assertEquals("Proxy.Type", typeSimplifier.simplify(typeMirrorOf("java.net.Proxy.Type")));
    }

    @Test
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
      assertEquals(expectedImports, typeSimplifier.typesToImport().asList());
    }

    @Test
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
      assertEquals(expectedImports, typeSimplifier.typesToImport().asList());
    }

    @Test
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
          typeMirrorOf("java.net.Proxy"),
          typeMirrorOf("java.net.Proxy.Type"),
          typeMirrorOf("java.util.regex.Pattern"),
          typeMirrorOf("javax.management.MBeanServer")));
      List<String> expectedImports = ImmutableList.of(
          "java.net.Proxy",
          "java.util.List",
          "java.util.regex.Pattern",
          "javax.management.MBeanServer"
      );
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "", types, baseWithoutContainedTypes());
      assertEquals(expectedImports, typeSimplifier.typesToImport().asList());
      assertEquals("Proxy", typeSimplifier.simplify(typeMirrorOf("java.net.Proxy")));
      assertEquals("Proxy.Type", typeSimplifier.simplify(typeMirrorOf("java.net.Proxy.Type")));
    }

    @Test
    public void testImportNestedType() {
      Set<TypeMirror> types = typeMirrorSet(typeMirrorOf("java.net.Proxy.Type"));
      List<String> expectedImports = ImmutableList.of(
          "java.net.Proxy"
      );
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals(expectedImports, typeSimplifier.typesToImport().asList());
      assertEquals("Proxy.Type", typeSimplifier.simplify(typeMirrorOf("java.net.Proxy.Type")));
    }

    @Test
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
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals(expectedImports, typeSimplifier.typesToImport().asList());
    }

    @Test
    public void testSimplifyJavaLangString() {
      TypeMirror string = typeMirrorOf("java.lang.String");
      Set<TypeMirror> types = typeMirrorSet(string);
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals("String", typeSimplifier.simplify(string));
    }

    @Test
    public void testSimplifyJavaLangThreadState() {
      TypeMirror threadState = typeMirrorOf("java.lang.Thread.State");
      Set<TypeMirror> types = typeMirrorSet(threadState);
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals("Thread.State", typeSimplifier.simplify(threadState));
    }

    @Test
    public void testSimplifyAmbiguousNames() {
      TypeMirror javaAwtList = typeMirrorOf("java.awt.List");
      TypeMirror javaUtilList = typeMirrorOf("java.util.List");
      Set<TypeMirror> types = typeMirrorSet(javaAwtList, javaUtilList);
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals(javaAwtList.toString(), typeSimplifier.simplify(javaAwtList));
      assertEquals(javaUtilList.toString(), typeSimplifier.simplify(javaUtilList));
    }

    @Test
    public void testSimplifyJavaLangNamesake() {
      TypeMirror javaLangType = typeMirrorOf("java.lang.RuntimePermission");
      TypeMirror notJavaLangType = typeMirrorOf(
          com.google.auto.value.processor.testclasses.RuntimePermission.class.getName());
      Set<TypeMirror> types = typeMirrorSet(javaLangType, notJavaLangType);
      TypeSimplifier typeSimplifier =
          new TypeSimplifier(typeUtil, "foo.bar", types, baseWithoutContainedTypes());
      assertEquals(javaLangType.toString(), typeSimplifier.simplify(javaLangType));
      assertEquals(notJavaLangType.toString(), typeSimplifier.simplify(notJavaLangType));
    }

    @Test
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

    @Test
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

    // Test TypeSimplifier.isCastingUnchecked. We do this by examining the fields of the Erasure
    // class that is defined in CLASS_TO_SOURCE. A field whose name ends with Yes has a type where
    // isCastingUnchecked should return true, and one whose name ends with No has a type where
    // isCastingUnchecked should return false.
    @Test
    public void testIsCastingUnchecked() {
      TypeElement erasureClass = typeElementOf("Erasure");
      List<VariableElement> fields = ElementFilter.fieldsIn(erasureClass.getEnclosedElements());
      for (VariableElement field : fields) {
        String fieldName = field.getSimpleName().toString();
        boolean expectUnchecked;
        if (fieldName.endsWith("Yes")) {
          expectUnchecked = true;
        } else if (fieldName.endsWith("No")) {
          expectUnchecked = false;
        } else {
          throw new AssertionError("Fields in Erasure class must end with Yes or No: " + fieldName);
        }
        TypeMirror fieldType = field.asType();
        boolean actualUnchecked = TypeSimplifier.isCastingUnchecked(fieldType);
        assertEquals("Unchecked-cast status for " + fieldType, expectUnchecked, actualUnchecked);
      }
    }
  }

  private static class ErrorTestProcessor extends AbstractTestProcessor {
    public void testErrorTypes() {
      TypeElement extendsUndefinedType =
          processingEnv.getElementUtils().getTypeElement("ExtendsUndefinedType");
      ErrorType errorType = (ErrorType) extendsUndefinedType.getSuperclass();
      TypeMirror javaLangObject = typeMirrorOf("java.lang.Object");
      TypeElement list = typeElementOf("java.util.List");
      TypeMirror listOfError = typeUtil.getDeclaredType(list, errorType);
      TypeMirror queryExtendsError = typeUtil.getWildcardType(errorType, null);
      TypeMirror listOfQueryExtendsError = typeUtil.getDeclaredType(list, queryExtendsError);
      TypeMirror querySuperError = typeUtil.getWildcardType(null, errorType);
      TypeMirror listOfQuerySuperError = typeUtil.getDeclaredType(list, querySuperError);
      TypeMirror arrayOfError = typeUtil.getArrayType(errorType);
      TypeMirror[] typesWithErrors = {
          errorType, listOfError, listOfQueryExtendsError, listOfQuerySuperError, arrayOfError
      };
      for (TypeMirror typeWithError : typesWithErrors) {
        try {
          new TypeSimplifier(typeUtil, "foo.bar", ImmutableSet.of(typeWithError), javaLangObject);
          fail("Expected exception for type: " + typeWithError);
        } catch (MissingTypeException expected) {
        }
      }
    }
  }
}
