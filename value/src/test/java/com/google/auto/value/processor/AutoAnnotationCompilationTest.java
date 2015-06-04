/*
 * Copyright (C) 2014 The Guava Authors
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class AutoAnnotationCompilationTest extends TestCase {
  public void testSimple() {
    JavaFileObject myAnnotationJavaFile = JavaFileObjects.forSourceLines(
        "com.example.annotations.MyAnnotation",
        "package com.example.annotations;",
        "",
        "import com.example.enums.MyEnum;",
        "",
        "public @interface MyAnnotation {",
        "  MyEnum value();",
        "}"
    );
    JavaFileObject myEnumJavaFile = JavaFileObjects.forSourceLines(
        "com.example.enums.MyEnum",
        "package com.example.enums;",
        "",
        "public enum MyEnum {",
        "  ONE",
        "}"
    );
    JavaFileObject annotationFactoryJavaFile = JavaFileObjects.forSourceLines(
        "com.example.factories.AnnotationFactory",
        "package com.example.factories;",
        "",
        "import com.google.auto.value.AutoAnnotation;",
        "import com.example.annotations.MyAnnotation;",
        "import com.example.enums.MyEnum;",
        "",
        "public class AnnotationFactory {",
        "  @AutoAnnotation",
        "  public static MyAnnotation newMyAnnotation(MyEnum value) {",
        "    return new AutoAnnotation_AnnotationFactory_newMyAnnotation(value);",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "com.example.factories.AutoAnnotation_AnnotationFactory_newMyAnnotation",
        "package com.example.factories;",
        "",
        "import com.example.annotations.MyAnnotation;",
        "import com.example.enums.MyEnum;",
        "import javax.annotation.Generated",
        "",
        "@Generated(\"" + AutoAnnotationProcessor.class.getName() + "\")",
        "final class AutoAnnotation_AnnotationFactory_newMyAnnotation implements MyAnnotation {",
        "  private final MyEnum value;",
        "",
        "  AutoAnnotation_AnnotationFactory_newMyAnnotation(MyEnum value) {",
        "    if (value == null) {",
        "      throw new NullPointerException(\"Null value\");",
        "    }",
        "    this.value = value;",
        "  }",
        "",
        "  @Override public Class<? extends MyAnnotation> annotationType() {",
        "    return MyAnnotation.class;",
        "  }",
        "",
        "  @Override public MyEnum value() {",
        "    return value;",
        "  }",
        "",
        "  @Override public String toString() {",
        "    StringBuilder sb = new StringBuilder(\"@com.example.annotations.MyAnnotation(\");",
        "    sb.append(value);",
        "    return sb.append(')').toString();",
        "  }",
        "",
        "  @Override public boolean equals(Object o) {",
        "    if (o == this) {",
        "      return true;",
        "    }",
        "    if (o instanceof MyAnnotation) {",
        "      MyAnnotation that = (MyAnnotation) o;",
        "      return (value.equals(that.value()));",
        "    }",
        "    return false;",
        "  }",
        "",
        "  @Override public int hashCode() {",
        "    return ((127 * " + "value".hashCode() + ") ^ (value.hashCode()));",
        "  }",
        "}"
    );
    assert_().about(javaSources())
        .that(ImmutableList.of(annotationFactoryJavaFile, myAnnotationJavaFile, myEnumJavaFile))
        .processedWith(new AutoAnnotationProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  public void testGwtSimple() {
    JavaFileObject myAnnotationJavaFile = JavaFileObjects.forSourceLines(
        "com.example.annotations.MyAnnotation",
        "package com.example.annotations;",
        "",
        "import com.google.common.annotations.GwtCompatible;",
        "",
        "@GwtCompatible",
        "public @interface MyAnnotation {",
        "  int[] value();",
        "}"
    );
    JavaFileObject gwtCompatibleJavaFile = JavaFileObjects.forSourceLines(
        "com.google.common.annotations.GwtCompatible",
        "package com.google.common.annotations;",
        "",
        "public @interface GwtCompatible {}"
    );
    JavaFileObject annotationFactoryJavaFile = JavaFileObjects.forSourceLines(
        "com.example.factories.AnnotationFactory",
        "package com.example.factories;",
        "",
        "import com.google.auto.value.AutoAnnotation;",
        "import com.example.annotations.MyAnnotation;",
        "",
        "public class AnnotationFactory {",
        "  @AutoAnnotation",
        "  public static MyAnnotation newMyAnnotation(int[] value) {",
        "    return new AutoAnnotation_AnnotationFactory_newMyAnnotation(value);",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "com.example.factories.AutoAnnotation_AnnotationFactory_newMyAnnotation",
        "package com.example.factories;",
        "",
        "import com.example.annotations.MyAnnotation;",
        "import java.util.Arrays;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"" + AutoAnnotationProcessor.class.getName() + "\")",
        "final class AutoAnnotation_AnnotationFactory_newMyAnnotation implements MyAnnotation {",
        "  private final int[] value;",
        "",
        "  AutoAnnotation_AnnotationFactory_newMyAnnotation(int[] value) {",
        "    if (value == null) {",
        "      throw new NullPointerException(\"Null value\");",
        "    }",
        "    this.value = Arrays.copyOf(value, value.length);",
        "  }",
        "",
        "  @Override public Class<? extends MyAnnotation> annotationType() {",
        "    return MyAnnotation.class;",
        "  }",
        "",
        "  @Override public int[] value() {",
        "    return Arrays.copyOf(value, value.length);",
        "  }",
        "",
        "  @Override public String toString() {",
        "    StringBuilder sb = new StringBuilder(\"@com.example.annotations.MyAnnotation(\");",
        "    sb.append(Arrays.toString(value));",
        "    return sb.append(')').toString();",
        "  }",
        "",
        "  @Override public boolean equals(Object o) {",
        "    if (o == this) {",
        "      return true;",
        "    }",
        "    if (o instanceof MyAnnotation) {",
        "      MyAnnotation that = (MyAnnotation) o;",
        "      return (Arrays.equals(value,",
        "          (that instanceof AutoAnnotation_AnnotationFactory_newMyAnnotation)",
        "              ? ((AutoAnnotation_AnnotationFactory_newMyAnnotation) that).value",
        "              : that.value()));",
        "    }",
        "    return false;",
        "  }",
        "",
        "  @Override public int hashCode() {",
        "    return ((127 * " + "value".hashCode() + ") ^ (Arrays.hashCode(value)));",
        "  }",
        "}"
    );
    assert_().about(javaSources())
        .that(ImmutableList.of(
            annotationFactoryJavaFile, myAnnotationJavaFile, gwtCompatibleJavaFile))
        .processedWith(new AutoAnnotationProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  public void testCollectionsForArrays() {
    JavaFileObject myAnnotationJavaFile = JavaFileObjects.forSourceLines(
        "com.example.annotations.MyAnnotation",
        "package com.example.annotations;",
        "",
        "import com.example.enums.MyEnum;",
        "",
        "public @interface MyAnnotation {",
        "  int[] value();",
        "  MyEnum[] enums() default {};",
        "}"
    );
    JavaFileObject myEnumJavaFile = JavaFileObjects.forSourceLines(
        "com.example.enums.MyEnum",
        "package com.example.enums;",
        "",
        "public enum MyEnum {",
        "  ONE",
        "}"
    );
    JavaFileObject annotationFactoryJavaFile = JavaFileObjects.forSourceLines(
        "com.example.factories.AnnotationFactory",
        "package com.example.factories;",
        "",
        "import com.google.auto.value.AutoAnnotation;",
        "import com.example.annotations.MyAnnotation;",
        "import com.example.enums.MyEnum;",
        "",
        "import java.util.List;",
        "import java.util.Set;",
        "",
        "public class AnnotationFactory {",
        "  @AutoAnnotation",
        "  public static MyAnnotation newMyAnnotation(",
        "      List<Integer> value, Set<MyEnum> enums) {",
        "    return new AutoAnnotation_AnnotationFactory_newMyAnnotation(value, enums);",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "com.example.factories.AutoAnnotation_AnnotationFactory_newMyAnnotation",
        "package com.example.factories;",
        "",
        "import com.example.annotations.MyAnnotation;",
        "import com.example.enums.MyEnum;",
        "import java.util.Arrays;",
        "import java.util.Collection;",
        "import java.util.List;",
        "import java.util.Set;",
        "import javax.annotation.Generated",
        "",
        "@Generated(\"" + AutoAnnotationProcessor.class.getName() + "\")",
        "final class AutoAnnotation_AnnotationFactory_newMyAnnotation implements MyAnnotation {",
        "  private final int[] value;",
        "  private final MyEnum[] enums;",
        "",
        "  AutoAnnotation_AnnotationFactory_newMyAnnotation(",
        "      List<Integer> value,",
        "      Set<MyEnum> enums) {",
        "    if (value == null) {",
        "      throw new NullPointerException(\"Null value\");",
        "    }",
        "    this.value = intArrayFromCollection(value);",
        "    if (enums == null) {",
        "      throw new NullPointerException(\"Null enums\");",
        "    }",
        "    this.enums = enums.toArray(new MyEnum[enums.size()];",
        "  }",
        "",
        "  @Override public Class<? extends MyAnnotation> annotationType() {",
        "    return MyAnnotation.class;",
        "  }",
        "",
        "  @Override public int[] value() {",
        "    return value.clone();",
        "  }",
        "",
        "  @Override public MyEnum[] enums() {",
        "    return enums.clone();",
        "  }",
        "",
        "  @Override public String toString() {",
        "    StringBuilder sb = new StringBuilder(\"@com.example.annotations.MyAnnotation(\");",
        "    sb.append(\"value=\");",
        "    sb.append(Arrays.toString(value));",
        "    sb.append(\", \");",
        "    sb.append(\"enums=\");",
        "    sb.append(Arrays.toString(enums));",
        "    return sb.append(')').toString();",
        "  }",
        "",
        "  @Override public boolean equals(Object o) {",
        "    if (o == this) {",
        "      return true;",
        "    }",
        "    if (o instanceof MyAnnotation) {",
        "      MyAnnotation that = (MyAnnotation) o;",
        "      return (Arrays.equals(value,",
        "          (that instanceof AutoAnnotation_AnnotationFactory_newMyAnnotation)",
        "              ? ((AutoAnnotation_AnnotationFactory_newMyAnnotation) that).value",
        "              : that.value()))",
        "          && (Arrays.equals(enums,",
        "          (that instanceof AutoAnnotation_AnnotationFactory_newMyAnnotation)",
        "              ? ((AutoAnnotation_AnnotationFactory_newMyAnnotation) that).enums",
        "              : that.enums()))",
        "    }",
        "    return false;",
        "  }",
        "",
        "  @Override public int hashCode() {",
        "    return ",
        "        ((127 * " + "value".hashCode() + ") ^ (Arrays.hashCode(value))) +",
        "        ((127 * " + "enums".hashCode() + ") ^ (Arrays.hashCode(enums)));",
        "  }",
        "",
        "  private static int[] intArrayFromCollection(Collection<Integer> c) {",
        "    int[] a = new int[c.size()];",
        "    int i = 0;",
        "    for (int x : c) {",
        "      a[i++] = x;",
        "    }",
        "    return a;",
        "  }",
        "}"
    );
    assert_().about(javaSources())
        .that(ImmutableList.of(annotationFactoryJavaFile, myEnumJavaFile, myAnnotationJavaFile))
        .processedWith(new AutoAnnotationProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  public void testMissingClass() throws IOException {
    File tempDir = File.createTempFile("AutoAnnotationCompilationTest", "");
    assertTrue(tempDir.delete());
    assertTrue(tempDir.mkdir());
    try {
      doTestMissingClass(tempDir);
    } finally {
      removeDirectory(tempDir);
    }
  }

  private void doTestMissingClass(File tempDir) {
    // Test that referring to an undefined annotation does not trigger @AutoAnnotation processing.
    // The class Erroneous references an undefined annotation @NotAutoAnnotation. If we didn't have
    // any special treatment of undefined types then we could run into a compiler bug where
    // AutoAnnotationProcessor would think that a method annotated with @NotAutoAnnotation was in
    // fact annotated with @AutoAnnotation. As it is, we do get an error about @NotAutoAnnotation
    // being undefined, and we do not get an error complaining that this supposed @AutoAnnotation
    // method is not static. We do need to have @AutoAnnotation appear somewhere so that the
    // processor will run.
    JavaFileObject erroneousJavaFileObject = JavaFileObjects.forSourceLines(
        "com.example.annotations.Erroneous",
        "package com.example.annotations;",
        "",
        "import com.google.auto.value.AutoAnnotation;",
        "",
        "public class Erroneous {",
        "  @interface Empty {}",
        "  @AutoAnnotation static Empty newEmpty() {}",
        "  @NotAutoAnnotation Empty notNewEmpty() {}",
        "}"
    );
    JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnosticCollector =
        new DiagnosticCollector<JavaFileObject>();
    JavaCompiler.CompilationTask compilationTask = javaCompiler.getTask(
        (Writer) null,
        (JavaFileManager) null,
        diagnosticCollector,
        ImmutableList.of("-d", tempDir.toString()),
        (Iterable<String>) null,
        ImmutableList.of(erroneousJavaFileObject));
    compilationTask.setProcessors(ImmutableList.of(new AutoAnnotationProcessor()));
    boolean result = compilationTask.call();
    assertThat(result).isFalse();
    List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
    assertThat(diagnostics).isNotEmpty();
    assertThat(diagnostics.get(0).getMessage(null)).contains("NotAutoAnnotation");
    assertThat(diagnostics.get(0).getMessage(null)).doesNotContain("static");
  }

  private static void removeDirectory(File dir) {
    for (File file : dir.listFiles()) {
      if (file.isDirectory()) {
        removeDirectory(file);
      } else {
        assertTrue(file.delete());
      }
    }
    assertTrue(dir.delete());
  }
}
