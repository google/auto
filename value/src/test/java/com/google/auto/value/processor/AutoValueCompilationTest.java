/*
 * Copyright 2018 Google LLC
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
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.CompilationSubject.compilations;
import static com.google.testing.compile.Compiler.javac;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Expect;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author emcmanus@google.com (Éamonn McManus) */
@RunWith(JUnit4.class)
public class AutoValueCompilationTest {
  @Rule public final Expect expect = Expect.create();

  @Test
  public void simpleSuccess() {
    // Positive test case that ensures we generate the expected code for at least one case.
    // Most AutoValue code-generation tests are functional, meaning that we check that the generated
    // code does the right thing rather than checking what it looks like, but this test checks that
    // we are not generating correct but weird code.
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  public abstract long buh();",
            "",
            "  public static Baz create(long buh) {",
            "    return new AutoValue_Baz(buh);",
            "  }",
            "}");
    JavaFileObject expectedOutput =
        JavaFileObjects.forSourceLines(
            "foo.bar.AutoValue_Baz",
            "package foo.bar;",
            "",
            GeneratedImport.importGeneratedAnnotationType(),
            "",
            "@Generated(\"" + AutoValueProcessor.class.getName() + "\")",
            "final class AutoValue_Baz extends Baz {",
            "  private final long buh;",
            "",
            "  AutoValue_Baz(long buh) {",
            "    this.buh = buh;",
            "  }",
            "",
            "  @Override public long buh() {",
            "    return buh;",
            "  }",
            "",
            "  @Override public String toString() {",
            "    return \"Baz{\"",
            "        + \"buh=\" + buh",
            "        + \"}\";",
            "  }",
            "",
            "  @Override public boolean equals(Object o) {",
            "    if (o == this) {",
            "      return true;",
            "    }",
            "    if (o instanceof Baz) {",
            "      Baz that = (Baz) o;",
            "      return this.buh == that.buh();",
            "    }",
            "    return false;",
            "  }",
            "",
            "  @Override public int hashCode() {",
            "    int h$ = 1;",
            "    h$ *= 1000003;",
            "    h$ ^= (int) ((buh >>> 32) ^ buh);",
            "    return h$;",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions("-A" + Nullables.NULLABLE_OPTION + "=")
            .compile(javaFileObject);
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Baz")
        .hasSourceEquivalentTo(expectedOutput);
  }

  @Test
  public void importTwoWays() {
    // Test that referring to the same class in two different ways does not confuse the import logic
    // into thinking it is two different classes and that therefore it can't import. The code here
    // is nonsensical but successfully reproduces a real problem, which is that a TypeMirror that is
    // extracted using Elements.getTypeElement(name).asType() does not compare equal to one that is
    // extracted from ExecutableElement.getReturnType(), even though Types.isSameType considers them
    // equal. So unless we are careful, the java.util.Arrays that we import explicitly to use its
    // methods will appear different from the java.util.Arrays that is the return type of the
    // arrays() method here.
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "import java.util.Arrays;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  @SuppressWarnings(\"mutable\")",
            "  public abstract int[] ints();",
            "  public abstract Arrays arrays();",
            "",
            "  public static Baz create(int[] ints, Arrays arrays) {",
            "    return new AutoValue_Baz(ints, arrays);",
            "  }",
            "}");
    JavaFileObject expectedOutput =
        JavaFileObjects.forSourceLines(
            "foo.bar.AutoValue_Baz",
            "package foo.bar;",
            "",
            "import java.util.Arrays;",
            GeneratedImport.importGeneratedAnnotationType(),
            "",
            "@Generated(\"" + AutoValueProcessor.class.getName() + "\")",
            "final class AutoValue_Baz extends Baz {",
            "  private final int[] ints;",
            "  private final Arrays arrays;",
            "",
            "  AutoValue_Baz(int[] ints, Arrays arrays) {",
            "    if (ints == null) {",
            "      throw new NullPointerException(\"Null ints\");",
            "    }",
            "    this.ints = ints;",
            "    if (arrays == null) {",
            "      throw new NullPointerException(\"Null arrays\");",
            "    }",
            "    this.arrays = arrays;",
            "  }",
            "",
            "  @SuppressWarnings(\"mutable\")",
            "  @Override public int[] ints() {",
            "    return ints;",
            "  }",
            "",
            "  @Override public Arrays arrays() {",
            "    return arrays;",
            "  }",
            "",
            "  @Override public String toString() {",
            "    return \"Baz{\"",
            "        + \"ints=\" + Arrays.toString(ints) + \", \"",
            "        + \"arrays=\" + arrays",
            "        + \"}\";",
            "  }",
            "",
            "  @Override public boolean equals(Object o) {",
            "    if (o == this) {",
            "      return true;",
            "    }",
            "    if (o instanceof Baz) {",
            "      Baz that = (Baz) o;",
            "      return Arrays.equals(this.ints, (that instanceof AutoValue_Baz) "
                + "? ((AutoValue_Baz) that).ints : that.ints())",
            "          && this.arrays.equals(that.arrays());",
            "    }",
            "    return false;",
            "  }",
            "",
            "  @Override public int hashCode() {",
            "    int h$ = 1;",
            "    h$ *= 1000003;",
            "    h$ ^= Arrays.hashCode(ints);",
            "    h$ *= 1000003;",
            "    h$ ^= arrays.hashCode();",
            "    return h$;",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions("-A" + Nullables.NULLABLE_OPTION + "=")
            .compile(javaFileObject);
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Baz")
        .hasSourceEquivalentTo(expectedOutput);
  }

  @Test
  public void testNoWarningsFromGenerics() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
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
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(javaFileObject);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void testNestedParameterizedTypesWithTypeAnnotations() {
    JavaFileObject annotFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Annot",
            "package foo.bar;",
            "",
            "import java.lang.annotation.ElementType;",
            "import java.lang.annotation.Target;",
            "",
            "@Target(ElementType.TYPE_USE)",
            "public @interface Annot {",
            "  int value();",
            "}");
    JavaFileObject outerFileObject =
        JavaFileObjects.forSourceLines(
            "foo.baz.OuterWithTypeParam",
            "package foo.baz;",
            "",
            "public class OuterWithTypeParam<T extends Number> {",
            "  public class InnerWithTypeParam<U> {}",
            "}");
    JavaFileObject nestyFileObject =
        JavaFileObjects.forSourceLines(
            "com.example.Nesty",
            "package com.example;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import foo.bar.Annot;",
            "import foo.baz.OuterWithTypeParam;",
            "",
            "@AutoValue",
            "abstract class Nesty {",
            "  abstract @Annot(1) OuterWithTypeParam<@Annot(2) Double>",
            "      .@Annot(3) InnerWithTypeParam<@Annot(4) String> inner();",
            "",
            "  static Nesty of(",
            "      @Annot(1) OuterWithTypeParam<@Annot(2) Double>",
            "          .@Annot(3) InnerWithTypeParam<@Annot(4) String> inner) {",
            "    return new AutoValue_Nesty(inner);",
            "  }",
            "}");
    JavaFileObject expectedOutput =
        JavaFileObjects.forSourceLines(
            "com.example.AutoValue_Nesty",
            "package com.example;",
            "",
            "import foo.bar.Annot;",
            "import foo.baz.OuterWithTypeParam;",
            GeneratedImport.importGeneratedAnnotationType(),
            "",
            "@Generated(\"com.google.auto.value.processor.AutoValueProcessor\")",
            "final class AutoValue_Nesty extends Nesty {",
            "  private final @Annot(1) OuterWithTypeParam<@Annot(2) Double>"
                + ".@Annot(3) InnerWithTypeParam<@Annot(4) String> inner;",
            "",
            "  AutoValue_Nesty(",
            "      @Annot(1) OuterWithTypeParam<@Annot(2) Double>"
                + ".@Annot(3) InnerWithTypeParam<@Annot(4) String> inner) {",
            "    if (inner == null) {",
            "      throw new NullPointerException(\"Null inner\");",
            "    }",
            "    this.inner = inner;",
            "  }",
            "",
            "  @Override",
            "  @Annot(1) OuterWithTypeParam<@Annot(2) Double>"
                + ".@Annot(3) InnerWithTypeParam<@Annot(4) String> inner() {",
            "    return inner;",
            "  }",
            "",
            "  @Override",
            "  public String toString() {",
            "    return \"Nesty{\"",
            "        + \"inner=\" + inner",
            "        + \"}\";",
            "  }",
            "",
            "  @Override",
            "  public boolean equals(Object o) {",
            "    if (o == this) {",
            "      return true;",
            "    }",
            "    if (o instanceof Nesty) {",
            "      Nesty that = (Nesty) o;",
            "      return this.inner.equals(that.inner());",
            "    }",
            "    return false;",
            "  }",
            "",
            "  @Override",
            "  public int hashCode() {",
            "    int h$ = 1;",
            "    h$ *= 1000003;",
            "    h$ ^= inner.hashCode();",
            "    return h$;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions(
                "-Xlint:-processing", "-implicit:none", "-A" + Nullables.NULLABLE_OPTION + "=")
            .compile(annotFileObject, outerFileObject, nestyFileObject);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("com.example.AutoValue_Nesty")
        .hasSourceEquivalentTo(expectedOutput);
  }

  // Tests that type annotations are correctly copied from the bounds of type parameters in the
  // @AutoValue class to the bounds of the corresponding parameters in the generated class. For
  // example, if we have `@AutoValue abstract class Foo<T extends @NullableType Object>`, then the
  // generated class should be `class AutoValue_Foo<T extends @NullableType Object> extends Foo<T>`.
  // Some buggy versions of javac do not report type annotations correctly in this context.
  // AutoValue can't copy them if it can't see them, so we make a special annotation processor to
  // detect if we are in the presence of this bug and if so we don't fail.
  @Test
  public void testTypeParametersWithAnnotationsOnBounds() {
    @SupportedAnnotationTypes("*")
    class CompilerBugProcessor extends AbstractProcessor {
      boolean checkedAnnotationsOnTypeBounds;
      boolean reportsAnnotationsOnTypeBounds;

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
          TypeElement test = processingEnv.getElementUtils().getTypeElement("com.example.Test");
          TypeParameterElement t = test.getTypeParameters().get(0);
          this.checkedAnnotationsOnTypeBounds = true;
          this.reportsAnnotationsOnTypeBounds =
              !t.getBounds().get(0).getAnnotationMirrors().isEmpty();
        }
        return false;
      }
    }
    CompilerBugProcessor compilerBugProcessor = new CompilerBugProcessor();
    JavaFileObject nullableTypeFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.NullableType",
            "package foo.bar;",
            "",
            "import java.lang.annotation.ElementType;",
            "import java.lang.annotation.Target;",
            "",
            "@Target(ElementType.TYPE_USE)",
            "public @interface NullableType {}");
    JavaFileObject autoValueFileObject =
        JavaFileObjects.forSourceLines(
            "com.example.Test",
            "package com.example;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import foo.bar.NullableType;",
            "",
            "@AutoValue",
            "abstract class Test<T extends @NullableType Object & @NullableType Cloneable> {}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), compilerBugProcessor)
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(nullableTypeFileObject, autoValueFileObject);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilerBugProcessor.checkedAnnotationsOnTypeBounds).isTrue();
    if (compilerBugProcessor.reportsAnnotationsOnTypeBounds) {
      assertThat(compilation)
          .generatedSourceFile("com.example.AutoValue_Test")
          .contentsAsUtf8String()
          .contains(
              "class AutoValue_Test<T extends @NullableType Object & @NullableType Cloneable>"
                  + " extends Test<T> {");
    }
  }

  // In the following few tests, see AutoValueProcessor.validateMethods for why unrecognized
  // abstract methods provoke only a warning rather than an error. Compilation will fail anyway
  // because the generated class is not abstract and does not implement the unrecognized methods.

  @Test
  public void testAbstractVoid() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "import com.google.auto.value.AutoValue;",
            "@AutoValue",
            "public abstract class Baz {",
            "  public abstract void foo();",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadWarningContaining(
            "Abstract method is neither a property getter nor a Builder converter")
        .inFile(javaFileObject)
        .onLineContaining("void foo()");
  }

  @Test
  public void testAbstractWithParams() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "import com.google.auto.value.AutoValue;",
            "@AutoValue",
            "public abstract class Baz {",
            "  public abstract int foo(int bar);",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadWarningContaining(
            "Abstract method is neither a property getter nor a Builder converter")
        .inFile(javaFileObject)
        .onLineContaining("int foo(int bar)");
  }

  @Test
  public void testPrimitiveArrayWarning() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "import com.google.auto.value.AutoValue;",
            "@AutoValue",
            "public abstract class Baz {",
            "  public abstract byte[] bytes();",
            "  public static Baz create(byte[] bytes) {",
            "    return new AutoValue_Baz(bytes);",
            "  }",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining(
            "An @AutoValue property that is a primitive array returns the original array")
        .inFile(javaFileObject)
        .onLineContaining("byte[] bytes()");
  }

  @Test
  public void testPrimitiveArrayWarningFromParent() {
    // If the array-valued property is defined by an ancestor then we shouldn't try to attach
    // the warning to the method that defined it, but rather to the @AutoValue class itself.
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
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
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContainingMatch(
            "An @AutoValue property that is a primitive array returns the original array"
                + ".*foo\\.bar\\.Baz\\.bytes")
        .inFile(javaFileObject)
        .onLineContaining("BazChild extends Baz");
  }

  @Test
  public void testPrimitiveArrayWarningSuppressed() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
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
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(javaFileObject);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void autoValueMustBeStatic() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "public class Baz {",
            "  @AutoValue",
            "  public abstract class NotStatic {",
            "    public abstract String buh();",
            "    public NotStatic create(String buh) {",
            "      return new AutoValue_Baz_NotStatic(buh);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("Nested @AutoValue class must be static")
        .inFile(javaFileObject)
        .onLineContaining("abstract class NotStatic");
  }

  @Test
  public void autoValueMustBeNotBePrivate() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "public class Baz {",
            "  @AutoValue",
            "  private abstract static class Private {",
            "    public abstract String buh();",
            "    public Private create(String buh) {",
            "      return new AutoValue_Baz_Private(buh);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("@AutoValue class must not be private")
        .inFile(javaFileObject)
        .onLineContaining("class Private");
  }

  @Test
  public void autoValueMustBeNotBeNestedInPrivate() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "public class Baz {",
            "  private static class Private {",
            "    @AutoValue",
            "    abstract static class Nested {",
            "      public abstract String buh();",
            "      public Nested create(String buh) {",
            "        return new AutoValue_Baz_Private_Nested(buh);",
            "      }",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("@AutoValue class must not be nested in a private class")
        .inFile(javaFileObject)
        .onLineContaining("class Nested");
  }

  @Test
  public void noMultidimensionalPrimitiveArrays() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  public abstract int[][] ints();",
            "",
            "  public static Baz create(int[][] ints) {",
            "    return new AutoValue_Baz(ints);",
            "  }",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "@AutoValue class cannot define an array-valued property "
                + "unless it is a primitive array")
        .inFile(javaFileObject)
        .onLineContaining("int[][] ints()");
  }

  @Test
  public void noObjectArrays() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  public abstract String[] strings();",
            "",
            "  public static Baz create(String[] strings) {",
            "    return new AutoValue_Baz(strings);",
            "  }",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "@AutoValue class cannot define an array-valued property "
                + "unless it is a primitive array")
        .inFile(javaFileObject)
        .onLineContaining("String[] strings()");
  }

  @Test
  public void annotationOnInterface() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public interface Baz {}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("AutoValue only applies to classes")
        .inFile(javaFileObject)
        .onLineContaining("interface Baz");
  }

  @Test
  public void annotationOnEnum() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public enum Baz {}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("AutoValue only applies to classes")
        .inFile(javaFileObject)
        .onLineContaining("enum Baz");
  }

  @Test
  public void extendAutoValue() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Outer",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "public class Outer {",
            "  @AutoValue",
            "  static abstract class Parent {",
            "    static Parent create(int randomProperty) {",
            "      return new AutoValue_Outer_Parent(randomProperty);",
            "    }",
            "",
            "    abstract int randomProperty();",
            "  }",
            "",
            "  @AutoValue",
            "  static abstract class Child extends Parent {",
            "    static Child create(int randomProperty) {",
            "      return new AutoValue_Outer_Child(randomProperty);",
            "    }",
            "",
            "    abstract int randomProperty();",
            "  }",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("may not extend")
        .inFile(javaFileObject)
        .onLineContaining("Child extends Parent");
  }

  @Test
  public void bogusSerialVersionUID() {
    String[] mistakes = {
      "final long serialVersionUID = 1234L", // not static
      "static long serialVersionUID = 1234L", // not final
      "static final Long serialVersionUID = 1234L", // not long
      "static final long serialVersionUID = (Long) 1234L", // not a compile-time constant
    };
    for (String mistake : mistakes) {
      JavaFileObject javaFileObject =
          JavaFileObjects.forSourceLines(
              "foo.bar.Baz",
              "package foo.bar;",
              "",
              "import com.google.auto.value.AutoValue;",
              "",
              "@AutoValue",
              "public abstract class Baz implements java.io.Serializable {",
              "  " + mistake + ";",
              "",
              "  public abstract int foo();",
              "}");
      Compilation compilation =
          javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
      expect
          .about(compilations())
          .that(compilation)
          .hadErrorContaining("serialVersionUID must be a static final long compile-time constant")
          .inFile(javaFileObject)
          .onLineContaining(mistake);
    }
  }

  @Test
  public void nonExistentSuperclass() {
    // The main purpose of this test is to check that AutoValueProcessor doesn't crash the
    // compiler in this case.
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Existent extends NonExistent {",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("NonExistent")
        .inFile(javaFileObject)
        .onLineContaining("NonExistent");
  }

  @Test
  public void cannotImplementAnnotation() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.RetentionImpl",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import java.lang.annotation.Retention;",
            "import java.lang.annotation.RetentionPolicy;",
            "",
            "@AutoValue",
            "public abstract class RetentionImpl implements Retention {",
            "  public static Retention create(RetentionPolicy policy) {",
            "    return new AutoValue_RetentionImpl(policy);",
            "  }",
            "",
            "  @Override public Class<? extends Retention> annotationType() {",
            "    return Retention.class;",
            "  }",
            "",
            "  @Override public boolean equals(Object o) {",
            "    return (o instanceof Retention && value().equals((Retention) o).value());",
            "  }",
            "",
            "  @Override public int hashCode() {",
            "    return (\"value\".hashCode() * 127) ^ value().hashCode();",
            "  }",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("may not be used to implement an annotation interface")
        .inFile(javaFileObject)
        .onLineContaining("RetentionImpl implements Retention");
  }

  @Test
  public void missingPropertyType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  public abstract MissingType missingType();",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("MissingType")
        .inFile(javaFileObject)
        .onLineContaining("MissingType");
  }

  @Test
  public void missingGenericPropertyType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  public abstract MissingType<?> missingType();",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("MissingType")
        .inFile(javaFileObject)
        .onLineContaining("MissingType");
  }

  @Test
  public void missingComplexGenericPropertyType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  public abstract Map<Set<?>, MissingType<?>> missingType();",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("MissingType")
        .inFile(javaFileObject)
        .onLineContaining("MissingType");
  }

  @Test
  public void missingSuperclassGenericParameter() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz<T extends MissingType<?>> {",
            "  public abstract int foo();",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("MissingType")
        .inFile(javaFileObject)
        .onLineContaining("MissingType");
  }

  @Test
  public void nullablePrimitive() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  @interface Nullable {}",
            "  public abstract @Nullable int foo();",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("Primitive types cannot be @Nullable")
        .inFile(javaFileObject)
        .onLineContaining("@Nullable int");
  }

  @Test
  public void correctBuilder() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.base.Optional;",
            "import com.google.common.collect.ImmutableList;",
            "",
            "import java.util.ArrayList;",
            "import java.util.List;",
            "import javax.annotation.Nullable;",
            "",
            "@AutoValue",
            "public abstract class Baz<T extends Number> {",
            "  public abstract int anInt();",
            "  @SuppressWarnings(\"mutable\")",
            "  public abstract byte[] aByteArray();",
            "  @SuppressWarnings(\"mutable\")",
            "  @Nullable public abstract int[] aNullableIntArray();",
            "  public abstract List<T> aList();",
            "  public abstract ImmutableList<T> anImmutableList();",
            "  public abstract Optional<String> anOptionalString();",
            "  public abstract NestedAutoValue<T> aNestedAutoValue();",
            "",
            "  public abstract Builder<T> toBuilder();",
            "",
            "  @AutoValue.Builder",
            "  public abstract static class Builder<T extends Number> {",
            "    public abstract Builder<T> anInt(int x);",
            "    public abstract Builder<T> aByteArray(byte[] x);",
            "    public abstract Builder<T> aNullableIntArray(@Nullable int[] x);",
            "    public abstract Builder<T> aList(List<T> x);",
            "    public abstract Builder<T> anImmutableList(List<T> x);",
            "    public abstract ImmutableList.Builder<T> anImmutableListBuilder();",
            "    public abstract Builder<T> anOptionalString(Optional<String> s);",
            "    public abstract Builder<T> anOptionalString(String s);",
            "    public abstract NestedAutoValue.Builder<T> aNestedAutoValueBuilder();",
            "",
            "    public Builder<T> aList(ArrayList<T> x) {",
            // ArrayList should not be imported in the generated class.
            "      return aList((List<T>) x);",
            "    }",
            "",
            "    public abstract Optional<Integer> anInt();",
            "    public abstract List<T> aList();",
            "    public abstract ImmutableList<T> anImmutableList();",
            "",
            "    public abstract Baz<T> build();",
            "  }",
            "",
            "  public static <T extends Number> Builder<T> builder() {",
            "    return AutoValue_Baz.builder();",
            "  }",
            "}");
    JavaFileObject nestedJavaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.NestedAutoValue",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class NestedAutoValue<T extends Number> {",
            "  public abstract T t();",
            "",
            "  public abstract Builder<T> toBuilder();",
            "",
            "  @AutoValue.Builder",
            "  public abstract static class Builder<T extends Number> {",
            "    public abstract Builder<T> t(T t);",
            "    public abstract NestedAutoValue<T> build();",
            "  }",
            "",
            "  public static <T extends Number> Builder<T> builder() {",
            "    return AutoValue_NestedAutoValue.builder();",
            "  }",
            "}");
    JavaFileObject expectedOutput =
        JavaFileObjects.forSourceLines(
            "foo.bar.AutoValue_Baz",
            "package foo.bar;",
            "",
            "import com.google.common.base.Optional;",
            "import com.google.common.collect.ImmutableList;",
            "import java.util.Arrays;",
            "import java.util.List;",
            sorted(
                GeneratedImport.importGeneratedAnnotationType(),
                "import javax.annotation.Nullable;"),
            "",
            "@Generated(\"" + AutoValueProcessor.class.getName() + "\")",
            "final class AutoValue_Baz<T extends Number> extends Baz<T> {",
            "  private final int anInt;",
            "  private final byte[] aByteArray;",
            "  private final int[] aNullableIntArray;",
            "  private final List<T> aList;",
            "  private final ImmutableList<T> anImmutableList;",
            "  private final Optional<String> anOptionalString;",
            "  private final NestedAutoValue<T> aNestedAutoValue;",
            "",
            "  private AutoValue_Baz(",
            "      int anInt,",
            "      byte[] aByteArray,",
            "      @Nullable int[] aNullableIntArray,",
            "      List<T> aList,",
            "      ImmutableList<T> anImmutableList,",
            "      Optional<String> anOptionalString,",
            "      NestedAutoValue<T> aNestedAutoValue) {",
            "    this.anInt = anInt;",
            "    this.aByteArray = aByteArray;",
            "    this.aNullableIntArray = aNullableIntArray;",
            "    this.aList = aList;",
            "    this.anImmutableList = anImmutableList;",
            "    this.anOptionalString = anOptionalString;",
            "    this.aNestedAutoValue = aNestedAutoValue;",
            "  }",
            "",
            "  @Override public int anInt() {",
            "    return anInt;",
            "  }",
            "",
            "  @SuppressWarnings(\"mutable\")",
            "  @Override public byte[] aByteArray() {",
            "    return aByteArray;",
            "  }",
            "",
            "  @SuppressWarnings(\"mutable\")",
            "  @Nullable",
            "  @Override public int[] aNullableIntArray() {",
            "    return aNullableIntArray;",
            "  }",
            "",
            "  @Override public List<T> aList() {",
            "    return aList;",
            "  }",
            "",
            "  @Override public ImmutableList<T> anImmutableList() {",
            "    return anImmutableList;",
            "  }",
            "",
            "  @Override public Optional<String> anOptionalString() {",
            "    return anOptionalString;",
            "  }",
            "",
            "  @Override public NestedAutoValue<T> aNestedAutoValue() {",
            "    return aNestedAutoValue;",
            "  }",
            "",
            "  @Override public String toString() {",
            "    return \"Baz{\"",
            "        + \"anInt=\" + anInt + \", \"",
            "        + \"aByteArray=\" + Arrays.toString(aByteArray) + \", \"",
            "        + \"aNullableIntArray=\" + Arrays.toString(aNullableIntArray) + \", \"",
            "        + \"aList=\" + aList + \", \"",
            "        + \"anImmutableList=\" + anImmutableList + \", \"",
            "        + \"anOptionalString=\" + anOptionalString + \", \"",
            "        + \"aNestedAutoValue=\" + aNestedAutoValue",
            "        + \"}\";",
            "  }",
            "",
            "  @Override public boolean equals(Object o) {",
            "    if (o == this) {",
            "      return true;",
            "    }",
            "    if (o instanceof Baz) {",
            "      Baz<?> that = (Baz<?>) o;",
            "      return this.anInt == that.anInt()",
            "          && Arrays.equals(this.aByteArray, "
                + "(that instanceof AutoValue_Baz) "
                + "? ((AutoValue_Baz<?>) that).aByteArray : that.aByteArray())",
            "          && Arrays.equals(this.aNullableIntArray, "
                + "(that instanceof AutoValue_Baz) "
                + "? ((AutoValue_Baz<?>) that).aNullableIntArray : that.aNullableIntArray())",
            "          && this.aList.equals(that.aList())",
            "          && this.anImmutableList.equals(that.anImmutableList())",
            "          && this.anOptionalString.equals(that.anOptionalString())",
            "          && this.aNestedAutoValue.equals(that.aNestedAutoValue());",
            "    }",
            "    return false;",
            "  }",
            "",
            "  @Override public int hashCode() {",
            "    int h$ = 1;",
            "    h$ *= 1000003;",
            "    h$ ^= anInt;",
            "    h$ *= 1000003;",
            "    h$ ^= Arrays.hashCode(aByteArray);",
            "    h$ *= 1000003;",
            "    h$ ^= Arrays.hashCode(aNullableIntArray);",
            "    h$ *= 1000003;",
            "    h$ ^= aList.hashCode();",
            "    h$ *= 1000003;",
            "    h$ ^= anImmutableList.hashCode();",
            "    h$ *= 1000003;",
            "    h$ ^= anOptionalString.hashCode();",
            "    h$ *= 1000003;",
            "    h$ ^= aNestedAutoValue.hashCode();",
            "    return h$;",
            "  }",
            "",
            "  @Override public Baz.Builder<T> toBuilder() {",
            "    return new Builder<T>(this);",
            "  }",
            "",
            "  static final class Builder<T extends Number> extends Baz.Builder<T> {",
            "    private Integer anInt;",
            "    private byte[] aByteArray;",
            "    private int[] aNullableIntArray;",
            "    private List<T> aList;",
            "    private ImmutableList.Builder<T> anImmutableListBuilder$;",
            "    private ImmutableList<T> anImmutableList;",
            "    private Optional<String> anOptionalString = Optional.absent();",
            "    private NestedAutoValue.Builder<T> aNestedAutoValueBuilder$;",
            "    private NestedAutoValue<T> aNestedAutoValue;",
            "",
            "    Builder() {",
            "    }",
            "",
            "    private Builder(Baz<T> source) {",
            "      this.anInt = source.anInt();",
            "      this.aByteArray = source.aByteArray();",
            "      this.aNullableIntArray = source.aNullableIntArray();",
            "      this.aList = source.aList();",
            "      this.anImmutableList = source.anImmutableList();",
            "      this.anOptionalString = source.anOptionalString();",
            "      this.aNestedAutoValue = source.aNestedAutoValue();",
            "    }",
            "",
            "    @Override",
            "    public Baz.Builder<T> anInt(int anInt) {",
            "      this.anInt = anInt;",
            "      return this;",
            "    }",
            "",
            "    @Override",
            "    public Optional<Integer> anInt() {",
            "      if (anInt == null) {",
            "        return Optional.absent();",
            "      } else {",
            "        return Optional.of(anInt);",
            "      }",
            "    }",
            "",
            "    @Override",
            "    public Baz.Builder<T> aByteArray(byte[] aByteArray) {",
            "      if (aByteArray == null) {",
            "        throw new NullPointerException(\"Null aByteArray\");",
            "      }",
            "      this.aByteArray = aByteArray;",
            "      return this;",
            "    }",
            "",
            "    @Override",
            "    public Baz.Builder<T> aNullableIntArray(@Nullable int[] aNullableIntArray) {",
            "      this.aNullableIntArray = aNullableIntArray;",
            "      return this;",
            "    }",
            "",
            "    @Override",
            "    public Baz.Builder<T> aList(List<T> aList) {",
            "      if (aList == null) {",
            "        throw new NullPointerException(\"Null aList\");",
            "      }",
            "      this.aList = aList;",
            "      return this;",
            "    }",
            "",
            "    @Override",
            "    public List<T> aList() {",
            "      if (aList == null) {",
            "        throw new IllegalStateException(\"Property \\\"aList\\\" has not been set\");",
            "      }",
            "      return aList;",
            "    }",
            "",
            "    @Override",
            "    public Baz.Builder<T> anImmutableList(List<T> anImmutableList) {",
            "      if (anImmutableListBuilder$ != null) {",
            "        throw new IllegalStateException("
                + "\"Cannot set anImmutableList after calling anImmutableListBuilder()\");",
            "      }",
            "      this.anImmutableList = ImmutableList.copyOf(anImmutableList);",
            "      return this;",
            "    }",
            "",
            "    @Override",
            "    public ImmutableList.Builder<T> anImmutableListBuilder() {",
            "      if (anImmutableListBuilder$ == null) {",
            "        if (anImmutableList == null) {",
            "          anImmutableListBuilder$ = ImmutableList.builder();",
            "        } else {",
            "          anImmutableListBuilder$ = ImmutableList.builder();",
            "          anImmutableListBuilder$.addAll(anImmutableList);",
            "          anImmutableList = null;",
            "        }",
            "      }",
            "      return anImmutableListBuilder$;",
            "    }",
            "",
            "    @Override",
            "    public ImmutableList<T> anImmutableList() {",
            "      if (anImmutableListBuilder$ != null) {",
            "        return anImmutableListBuilder$.build();",
            "      }",
            "      if (anImmutableList == null) {",
            "        anImmutableList = ImmutableList.of();",
            "      }",
            "      return anImmutableList;",
            "    }",
            "",
            "    @Override",
            "    public Baz.Builder<T> anOptionalString(Optional<String> anOptionalString) {",
            "      if (anOptionalString == null) {",
            "        throw new NullPointerException(\"Null anOptionalString\");",
            "      }",
            "      this.anOptionalString = anOptionalString;",
            "      return this;",
            "    }",
            "",
            "    @Override",
            "    public Baz.Builder<T> anOptionalString(String anOptionalString) {",
            "      this.anOptionalString = Optional.of(anOptionalString);",
            "      return this;",
            "    }",
            "",
            "    @Override",
            "    public NestedAutoValue.Builder<T> aNestedAutoValueBuilder() {",
            "      if (aNestedAutoValueBuilder$ == null) {",
            "        if (aNestedAutoValue == null) {",
            "          aNestedAutoValueBuilder$ = NestedAutoValue.builder();",
            "        } else {",
            "          aNestedAutoValueBuilder$ = aNestedAutoValue.toBuilder();",
            "          aNestedAutoValue = null;",
            "        }",
            "      }",
            "      return aNestedAutoValueBuilder$;",
            "    }",
            "",
            "    @Override",
            "    public Baz<T> build() {",
            "      if (anImmutableListBuilder$ != null) {",
            "        this.anImmutableList = anImmutableListBuilder$.build();",
            "      } else if (this.anImmutableList == null) {",
            "        this.anImmutableList = ImmutableList.of();",
            "      }",
            "      if (aNestedAutoValueBuilder$ != null) {",
            "        this.aNestedAutoValue = aNestedAutoValueBuilder$.build();",
            "      } else if (this.aNestedAutoValue == null) {",
            "        NestedAutoValue.Builder<T> aNestedAutoValue$builder = "
                + "NestedAutoValue.builder();",
            "        this.aNestedAutoValue = aNestedAutoValue$builder.build();",
            "      }",
            "      if (this.anInt == null",
            "          || this.aByteArray == null",
            "          || this.aList == null) {",
            "        StringBuilder missing = new StringBuilder();",
            "        if (this.anInt == null) {",
            "          missing.append(\" anInt\");",
            "        }",
            "        if (this.aByteArray == null) {",
            "          missing.append(\" aByteArray\");",
            "        }",
            "        if (this.aList == null) {",
            "          missing.append(\" aList\");",
            "        }",
            "        throw new IllegalStateException(\"Missing required properties:\" + missing);",
            "      }",
            "      return new AutoValue_Baz<T>(",
            "          this.anInt,",
            "          this.aByteArray,",
            "          this.aNullableIntArray,",
            "          this.aList,",
            "          this.anImmutableList,",
            "          this.anOptionalString,",
            "          this.aNestedAutoValue);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions(
                "-Xlint:-processing", "-implicit:none", "-A" + Nullables.NULLABLE_OPTION + "=")
            .compile(javaFileObject, nestedJavaFileObject);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Baz")
        .hasSourceEquivalentTo(expectedOutput);
  }

  @Test
  public void autoValueBuilderOnTopLevelClass() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Builder",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue.Builder",
            "public interface Builder {",
            "  Builder foo(int x);",
            "  Object build();",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("can only be applied to a class or interface inside")
        .inFile(javaFileObject)
        .onLineContaining("public interface Builder");
  }

  @Test
  public void autoValueBuilderNotInsideAutoValue() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "public abstract class Baz {",
            "  abstract int foo();",
            "",
            "  static Builder builder() {",
            "    return new AutoValue_Baz.Builder();",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder foo(int x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("can only be applied to a class or interface inside")
        .inFile(javaFileObject)
        .onLineContaining("public interface Builder");
  }

  @Test
  public void autoValueBuilderNotStatic() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Example",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "class Example {",
            "  @AutoValue",
            "  abstract static class Baz {",
            "    abstract int foo();",
            "",
            "    static Builder builder() {",
            "      return new AutoValue_Example_Baz.Builder();",
            "    }",
            "",
            "    @AutoValue.Builder",
            "    abstract class Builder {",
            "      abstract Builder foo(int x);",
            "      abstract Baz build();",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("@AutoValue.Builder cannot be applied to a non-static class")
        .inFile(javaFileObject)
        .onLineContaining("abstract class Builder");
  }

  @Test
  public void autoValueBuilderOnEnum() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract int foo();",
            "",
            "  static Builder builder() {",
            "    return null;",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public enum Builder {}",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("can only apply to a class or an interface")
        .inFile(javaFileObject)
        .onLineContaining("public enum Builder");
  }

  @Test
  public void autoValueBuilderDuplicate() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  @AutoValue.Builder",
            "  public interface Builder1 {",
            "    Baz build();",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder2 {",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("already has a Builder: foo.bar.Baz.Builder1")
        .inFile(javaFileObject)
        .onLineContaining("public interface Builder2");
  }

  @Test
  public void autoValueBuilderMissingSetter() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract int blim();",
            "  abstract String blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder blam(String x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("with this signature: foo.bar.Baz.Builder blim(int)")
        .inFile(javaFileObject)
        .onLineContaining("public interface Builder");
  }

  @Test
  public void autoValueBuilderMissingSetterUsingSetPrefix() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract int blim();",
            "  abstract String blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder setBlam(String x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("with this signature: foo.bar.Baz.Builder setBlim(int)")
        .inFile(javaFileObject)
        .onLineContaining("public interface Builder");
  }

  @Test
  public void autoValueBuilderWrongTypeSetter() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract int blim();",
            "  abstract String blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder blim(String x);",
            "    Builder blam(String x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Parameter type java.lang.String of setter method should be int "
                + "to match property method foo.bar.Baz.blim()")
        .inFile(javaFileObject)
        .onLineContaining("Builder blim(String x)");
  }

  @Test
  public void autoValueBuilderWrongTypeSetterWithCopyOf() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableList;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String blim();",
            "  abstract ImmutableList<String> blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder blim(String x);",
            "    Builder blam(String x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Parameter type java.lang.String of setter method should be"
                + " com.google.common.collect.ImmutableList<java.lang.String> to match property"
                + " method foo.bar.Baz.blam(), or it should be a type that can be passed to"
                + " ImmutableList.copyOf")
        .inFile(javaFileObject)
        .onLineContaining("Builder blam(String x)");
  }

  @Test
  public void autoValueBuilderWrongTypeSetterWithCopyOfGenericallyWrong() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableList;",
            "import java.util.Collection;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String blim();",
            "  abstract ImmutableList<String> blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder blim(String x);",
            "    Builder blam(Collection<Integer> x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Parameter type java.util.Collection<java.lang.Integer> of setter method should be"
                + " com.google.common.collect.ImmutableList<java.lang.String> to match property"
                + " method foo.bar.Baz.blam(), or it should be a type that can be passed to"
                + " ImmutableList.copyOf to produce"
                + " com.google.common.collect.ImmutableList<java.lang.String>")
        .inFile(javaFileObject)
        .onLineContaining("Builder blam(Collection<Integer> x)");
  }

  @Test
  public void autoValueBuilderWrongTypeSetterWithGetPrefix() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract int getBlim();",
            "  abstract String getBlam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder blim(String x);",
            "    Builder blam(String x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Parameter type java.lang.String of setter method should be int "
                + "to match property method foo.bar.Baz.getBlim()")
        .inFile(javaFileObject)
        .onLineContaining("Builder blim(String x)");
  }

  @Test
  public void autoValueBuilderNullableSetterForNonNullable() {
    JavaFileObject nullableFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Nullable",
            "package foo.bar;",
            "",
            "import java.lang.annotation.ElementType;",
            "import java.lang.annotation.Target;",
            "",
            "@Target(ElementType.TYPE_USE)",
            "public @interface Nullable {}");
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String notNull();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder setNotNull(@Nullable String x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject, nullableFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Parameter of setter method is @Nullable but property method"
                + " foo.bar.Baz.notNull() is not")
        .inFile(javaFileObject)
        .onLineContaining("setNotNull");
  }

  // Check that we get a helpful error message if some of your properties look like getters but
  // others don't.
  @Test
  public void autoValueBuilderBeansConfusion() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Item",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Item {",
            "  abstract String getTitle();",
            "  abstract boolean hasThumbnail();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder setTitle(String title);",
            "    Builder setHasThumbnail(boolean t);",
            "    Item build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Method setTitle does not correspond to a property method of foo.bar.Item")
        .inFile(javaFileObject)
        .onLineContaining("Builder setTitle(String title)");
    assertThat(compilation)
        .hadNoteContaining("hasThumbnail")
        .inFile(javaFileObject)
        .onLineContaining("Builder setTitle(String title)");
  }

  @Test
  public void autoValueBuilderExtraSetter() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder blim(int x);",
            "    Builder blam(String x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("Method blim does not correspond to a property method of foo.bar.Baz")
        .inFile(javaFileObject)
        .onLineContaining("Builder blim(int x)");
  }

  @Test
  public void autoValueBuilderSetPrefixAndNoSetPrefix() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract int blim();",
            "  abstract String blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder blim(int x);",
            "    Builder setBlam(String x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("If any setter methods use the setFoo convention then all must")
        .inFile(javaFileObject)
        .onLineContaining("Builder blim(int x)");
  }

  @Test
  public void autoValueBuilderSetterReturnType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract int blim();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    void blim(int x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("Setter methods must return foo.bar.Baz.Builder")
        .inFile(javaFileObject)
        .onLineContaining("void blim(int x)");
  }

  @Test
  public void autoValueBuilderWrongTypeGetter() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz<T, U> {",
            "  abstract T blim();",
            "  abstract U blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T, U> {",
            "    Builder<T, U> blim(T x);",
            "    Builder<T, U> blam(U x);",
            "    T blim();",
            "    T blam();",
            "    Baz<T, U> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContainingMatch(
            "Method matches a property of foo\\.bar\\.Baz<T, ?U> but has return type T instead of"
                + " U")
        .inFile(javaFileObject)
        .onLineContaining("T blam()");
    // The <T, ?U> is because we're depending on TypeMirror.toString(), and the JDK actually spells
    // this as <T,U> with no space. While it's not completely sound to expect a given string from
    // TypeMirror.toString(), in practice it's hard to imagine that it would be anything other
    // than "foo.bar.Baz<T,U>" or "foo.bar.Baz<T, U>" given the specification.
  }

  @Test
  public void autoValueBuilderPropertyBuilderInvalidType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz<T, U> {",
            "  abstract String blim();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T, U> {",
            "    StringBuilder blimBuilder();",
            "    Baz<T, U> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Method looks like a property builder, but it returns java.lang.StringBuilder which "
                + "does not have a non-static build() method")
        .inFile(javaFileObject)
        .onLineContaining("StringBuilder blimBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderNullable() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableList;",
            "",
            "@AutoValue",
            "public abstract class Baz<T, U> {",
            "  @interface Nullable {}",
            "  abstract @Nullable ImmutableList<String> strings();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T, U> {",
            "    ImmutableList.Builder<String> stringsBuilder();",
            "    Baz<T, U> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("Property strings is @Nullable so it cannot have a property builder")
        .inFile(javaFileObject)
        .onLineContaining("stringsBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderNullableType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableList;",
            "import java.lang.annotation.ElementType;",
            "import java.lang.annotation.Target;",
            "",
            "@AutoValue",
            "public abstract class Baz<T, U> {",
            "  @Target(ElementType.TYPE_USE)",
            "  @interface Nullable {}",
            "  abstract @Nullable ImmutableList<String> strings();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T, U> {",
            "    ImmutableList.Builder<String> stringsBuilder();",
            "    Baz<T, U> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("Property strings is @Nullable so it cannot have a property builder")
        .inFile(javaFileObject)
        .onLineContaining("stringsBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderWrongCollectionType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableList;",
            "import com.google.common.collect.ImmutableSet;",
            "",
            "@AutoValue",
            "public abstract class Baz<T, U> {",
            "  abstract ImmutableList<T> blim();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T, U> {",
            "    ImmutableSet.Builder<T> blimBuilder();",
            "    Baz<T, U> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Property builder for blim has type com.google.common.collect.ImmutableSet.Builder "
                + "whose build() method returns com.google.common.collect.ImmutableSet<T> "
                + "instead of com.google.common.collect.ImmutableList<T>")
        .inFile(javaFileObject)
        .onLineContaining("ImmutableSet.Builder<T> blimBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderWeirdBuilderType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableSet;",
            "",
            "@AutoValue",
            "public abstract class Baz<T, U> {",
            "  abstract Integer blim();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T, U> {",
            "    int blimBuilder();",
            "    Baz<T, U> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Method looks like a property builder, but its return type is not a class or interface")
        .inFile(javaFileObject)
        .onLineContaining("int blimBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderWeirdBuiltType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableSet;",
            "",
            "@AutoValue",
            "public abstract class Baz<T, U> {",
            "  abstract int blim();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T, U> {",
            "    Integer blimBuilder();",
            "    Baz<T, U> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Method looks like a property builder, but the type of property blim is not a class "
                + "or interface")
        .inFile(javaFileObject)
        .onLineContaining("Integer blimBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderHasNoBuild() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableSet;",
            "",
            "@AutoValue",
            "public abstract class Baz<T, U> {",
            "  abstract String blim();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T, U> {",
            "    StringBuilder blimBuilder();",
            "    Baz<T, U> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Method looks like a property builder, but it returns java.lang.StringBuilder which "
                + "does not have a non-static build() method")
        .inFile(javaFileObject)
        .onLineContaining("StringBuilder blimBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderHasStaticBuild() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableSet;",
            "",
            "@AutoValue",
            "public abstract class Baz<T, U> {",
            "  abstract String blim();",
            "",
            "  public static class StringFactory {",
            "    public static String build() {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T, U> {",
            "    StringFactory blimBuilder();",
            "    Baz<T, U> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Method looks like a property builder, but it returns foo.bar.Baz.StringFactory which "
                + "does not have a non-static build() method")
        .inFile(javaFileObject)
        .onLineContaining("StringFactory blimBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderReturnsWrongType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.List;",
            "",
            "@AutoValue",
            "public abstract class Baz<E> {",
            "  abstract List<E> blim();",
            "",
            "  public static class ListFactory<E> {",
            "    public List<? extends E> build() {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<E> {",
            "    ListFactory<E> blimBuilder();",
            "    Baz<E> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Property builder for blim has type foo.bar.Baz.ListFactory whose build() method "
                + "returns java.util.List<? extends E> instead of java.util.List<E>")
        .inFile(javaFileObject)
        .onLineContaining("ListFactory<E> blimBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderCantConstruct() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableSet;",
            "",
            "@AutoValue",
            "public abstract class Baz<E> {",
            "  abstract String blim();",
            "",
            "  public static class StringFactory {",
            "    private StringFactory() {}",
            "",
            "    public String build() {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<E> {",
            "    StringFactory blimBuilder();",
            "    Baz<E> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Method looks like a property builder, but its type foo.bar.Baz.StringFactory "
                + "does not have a public constructor and java.lang.String does not have a static "
                + "builder() or newBuilder() method that returns foo.bar.Baz.StringFactory")
        .inFile(javaFileObject)
        .onLineContaining("StringFactory blimBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderCantReconstruct() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz<E> {",
            "  abstract String blim();",
            "  abstract Builder<E> toBuilder();",
            "",
            "  public static class StringFactory {",
            "    public String build() {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<E> {",
            "    StringFactory blimBuilder();",
            "    Baz<E> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Property builder method returns foo.bar.Baz.StringFactory but there is no way to make"
                + " that type from java.lang.String: java.lang.String does not have a non-static"
                + " toBuilder() method that returns foo.bar.Baz.StringFactory, and"
                + " foo.bar.Baz.StringFactory does not have a method addAll or putAll that accepts"
                + " an argument of type java.lang.String")
        .inFile(javaFileObject)
        .onLineContaining("StringFactory blimBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderWrongTypeAddAll() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Iterator;",
            "",
            "@AutoValue",
            "public abstract class Baz<T> {",
            "  abstract ImmutableSet<String> strings();",
            "  abstract Builder<T> toBuilder();",
            "",
            "  public static class ImmutableSetBuilder<E> {",
            "    public void addAll(Iterator<? extends E> elements) {}",
            "",
            "    public ImmutableSet<E> build() {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T> {",
            "    ImmutableSetBuilder<String> stringsBuilder();",
            "    Baz<T> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Property builder method returns foo.bar.Baz.ImmutableSetBuilder<java.lang.String> but"
                + " there is no way to make that type from"
                + " com.google.common.collect.ImmutableSet<java.lang.String>:"
                + " com.google.common.collect.ImmutableSet<java.lang.String> does not have a"
                + " non-static toBuilder() method that returns"
                + " foo.bar.Baz.ImmutableSetBuilder<java.lang.String>, and"
                + " foo.bar.Baz.ImmutableSetBuilder<java.lang.String> does not have a method"
                + " addAll or putAll that accepts an argument of type"
                + " com.google.common.collect.ImmutableSet<java.lang.String>")
        .inFile(javaFileObject)
        .onLineContaining("ImmutableSetBuilder<String> stringsBuilder();");
  }

  @Test
  public void autoValueBuilderPropertyBuilderCantSet() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableSet;",
            "",
            "@AutoValue",
            "public abstract class Baz<E> {",
            "  abstract String blim();",
            "",
            "  public static class StringFactory {",
            "    public String build() {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<E> {",
            "    Builder<E> setBlim(String s);",
            "    StringFactory blimBuilder();",
            "    Baz<E> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Property builder method returns foo.bar.Baz.StringFactory but there is no way to make "
                + "that type from java.lang.String: java.lang.String does not have a non-static "
                + "toBuilder() method that returns foo.bar.Baz.StringFactory")
        .inFile(javaFileObject)
        .onLineContaining("StringFactory blimBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderWrongTypeToBuilder() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableSet;",
            "",
            "@AutoValue",
            "public abstract class Baz<E> {",
            "  abstract Buh blim();",
            "  abstract Builder<E> toBuilder();",
            "",
            "  public static class Buh {",
            "    StringBuilder toBuilder() {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  public static class BuhBuilder {",
            "    public Buh build() {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<E> {",
            "    BuhBuilder blimBuilder();",
            "    Baz<E> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Property builder method returns foo.bar.Baz.BuhBuilder but there is no way to make "
                + "that type from foo.bar.Baz.Buh: foo.bar.Baz.Buh does not have a non-static "
                + "toBuilder() method that returns foo.bar.Baz.BuhBuilder")
        .inFile(javaFileObject)
        .onLineContaining("BuhBuilder blimBuilder()");
  }

  @Test
  public void autoValueBuilderPropertyBuilderWrongElementType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableSet;",
            "",
            "@AutoValue",
            "public abstract class Baz<T, U> {",
            "  abstract ImmutableSet<T> blim();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T, U> {",
            "    ImmutableSet.Builder<U> blimBuilder();",
            "    Baz<T, U> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Property builder for blim has type com.google.common.collect.ImmutableSet.Builder "
                + "whose build() method returns com.google.common.collect.ImmutableSet<U> "
                + "instead of com.google.common.collect.ImmutableSet<T>")
        .inFile(javaFileObject)
        .onLineContaining("ImmutableSet.Builder<U> blimBuilder()");
  }

  @Test
  public void autoValueBuilderAlienMethod0() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder blam(String x);",
            "    Builder whut();",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Method without arguments should be a build method returning foo.bar.Baz, or a getter"
                + " method with the same name and type as a property method of foo.bar.Baz, or"
                + " fooBuilder() where foo() or getFoo() is a property method of foo.bar.Baz")
        .inFile(javaFileObject)
        .onLineContaining("Builder whut()");
  }

  @Test
  public void autoValueBuilderAlienMethod1() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    void whut(String x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("Method whut does not correspond to a property method of foo.bar.Baz")
        .inFile(javaFileObject)
        .onLineContaining("void whut(String x)");
  }

  @Test
  public void autoValueBuilderAlienMethod2() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder blam(String x, String y);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("Builder methods must have 0 or 1 parameters")
        .inFile(javaFileObject)
        .onLineContaining("Builder blam(String x, String y)");
  }

  @Test
  public void autoValueBuilderMissingBuildMethod() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz<T> {",
            "  abstract T blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T> {",
            "    Builder<T> blam(T x);",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Builder must have a single no-argument method, typically called build(), that returns"
                + " foo.bar.Baz<T>")
        .inFile(javaFileObject)
        .onLineContaining("public interface Builder<T>");
  }

  @Test
  public void autoValueBuilderDuplicateBuildMethods() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder blam(String x);",
            "    Baz build();",
            "    Baz create();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Builder must have a single no-argument method, typically called build(), that returns"
                + " foo.bar.Baz")
        .inFile(javaFileObject)
        .onLineContaining("Baz build()");
    assertThat(compilation)
        .hadErrorContaining(
            "Builder must have a single no-argument method, typically called build(), that returns"
                + " foo.bar.Baz")
        .inFile(javaFileObject)
        .onLineContaining("Baz create()");
  }

  @Test
  public void autoValueBuilderWrongTypeBuildMethod() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder blam(String x);",
            "    String build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Method without arguments should be a build method returning foo.bar.Baz")
        .inFile(javaFileObject)
        .onLineContaining("String build()");
  }

  @Test
  public void autoValueBuilderTypeParametersDontMatch1() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz<T> {",
            "  abstract String blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder {",
            "    Builder blam(String x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Type parameters of foo.bar.Baz.Builder must have same names and "
                + "bounds as type parameters of foo.bar.Baz")
        .inFile(javaFileObject)
        .onLineContaining("public interface Builder");
  }

  @Test
  public void autoValueBuilderTypeParametersDontMatch2() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz<T> {",
            "  abstract T blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<E> {",
            "    Builder<E> blam(E x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Type parameters of foo.bar.Baz.Builder must have same names and "
                + "bounds as type parameters of foo.bar.Baz")
        .inFile(javaFileObject)
        .onLineContaining("public interface Builder<E>");
  }

  @Test
  public void autoValueBuilderTypeParametersDontMatch3() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz<T extends Number & Comparable<T>> {",
            "  abstract T blam();",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<T extends Number> {",
            "    Builder<T> blam(T x);",
            "    Baz build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Type parameters of foo.bar.Baz.Builder must have same names and "
                + "bounds as type parameters of foo.bar.Baz")
        .inFile(javaFileObject)
        .onLineContaining("public interface Builder<T extends Number>");
  }

  @Test
  public void autoValueBuilderToBuilderWrongTypeParameters() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "abstract class Baz<K extends Comparable<K>, V> {",
            "  abstract K key();",
            "  abstract V value();",
            "  abstract Builder<V, K> toBuilder1();",
            "",
            "  @AutoValue.Builder",
            "  interface Builder<K extends Comparable<K>, V> {",
            "    Builder<K, V> key(K key);",
            "    Builder<K, V> value(V value);",
            "    Baz<K, V> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("Builder converter method should return foo.bar.Baz.Builder<K, V>")
        .inFile(javaFileObject)
        .onLineContaining("abstract Builder<V, K> toBuilder1()");
  }

  @Test
  public void autoValueBuilderToBuilderDuplicate() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "abstract class Baz<K extends Comparable<K>, V> {",
            "  abstract K key();",
            "  abstract V value();",
            "  abstract Builder<K, V> toBuilder1();",
            "  abstract Builder<K, V> toBuilder2();",
            "",
            "  @AutoValue.Builder",
            "  interface Builder<K extends Comparable<K>, V> {",
            "    Builder<K, V> key(K key);",
            "    Builder<K, V> value(V value);",
            "    Baz<K, V> build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("There can be at most one builder converter method")
        .inFile(javaFileObject)
        .onLineContaining("abstract Builder<K, V> toBuilder1()");
  }

  @Test
  public void getFooIsFoo() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract int getFoo();",
            "  abstract boolean isFoo();",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new AutoValueBuilderProcessor())
            .compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("More than one @AutoValue property called foo")
        .inFile(javaFileObject)
        .onLineContaining("getFoo");
    assertThat(compilation)
        .hadErrorContaining("More than one @AutoValue property called foo")
        .inFile(javaFileObject)
        .onLineContaining("isFoo");
  }

  @Retention(RetentionPolicy.SOURCE)
  public @interface Foo {}

  /* Processor that generates an empty class BarFoo every time it sees a class Bar annotated with
   * @Foo.
   */
  public static class FooProcessor extends AbstractProcessor {
    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of(Foo.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Foo.class);
      for (TypeElement type : ElementFilter.typesIn(elements)) {
        try {
          generateFoo(type);
        } catch (IOException e) {
          throw new AssertionError(e);
        }
      }
      return false;
    }

    private void generateFoo(TypeElement type) throws IOException {
      String pkg = TypeSimplifier.packageNameOf(type);
      String className = type.getSimpleName().toString();
      String generatedClassName = className + "Foo";
      JavaFileObject source =
          processingEnv.getFiler().createSourceFile(pkg + "." + generatedClassName, type);
      PrintWriter writer = new PrintWriter(source.openWriter());
      writer.println("package " + pkg + ";");
      writer.println("public class " + generatedClassName + " {}");
      writer.close();
    }
  }

  @Test
  public void referencingGeneratedClass() {
    // Test that ensures that a type that does not exist can be the type of an @AutoValue property
    // as long as it later does come into existence. The BarFoo type referenced here does not exist
    // when the AutoValueProcessor runs on the first round, but the FooProcessor then generates it.
    // That generation provokes a further round of annotation processing and AutoValueProcessor
    // should succeed then.
    JavaFileObject bazFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  public abstract BarFoo barFoo();",
            "",
            "  public static Baz create(BarFoo barFoo) {",
            "    return new AutoValue_Baz(barFoo);",
            "  }",
            "}");
    JavaFileObject barFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Bar",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@" + Foo.class.getCanonicalName(),
            "public abstract class Bar {",
            "  public abstract BarFoo barFoo();",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(), new FooProcessor())
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(bazFileObject, barFileObject);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void annotationReferencesUndefined() {
    // Test that we don't throw an exception if asked to compile @SuppressWarnings(UNDEFINED)
    // where UNDEFINED is an undefined symbol.
    JavaFileObject bazFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  @SuppressWarnings(UNDEFINED)",
            "  public abstract int[] buh();",
            "}");
    Compilation compilation1 =
        javac()
            .withOptions("-Xlint:-processing")
            .withProcessors(new AutoValueProcessor())
            .compile(bazFileObject);
    assertThat(compilation1).hadErrorCount(1);
    assertThat(compilation1)
        .hadErrorContaining("UNDEFINED")
        .inFile(bazFileObject)
        .onLineContaining("UNDEFINED");
    assertThat(compilation1).hadWarningCount(1);
    assertThat(compilation1)
        .hadWarningContaining("mutable")
        .inFile(bazFileObject)
        .onLineContaining("public abstract int[] buh()");

    // Same test, except we do successfully suppress the warning despite the UNDEFINED.
    bazFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  @SuppressWarnings({UNDEFINED, \"mutable\"})",
            "  public abstract int[] buh();",
            "}");
    Compilation compilation2 =
        javac()
            .withOptions("-Xlint:-processing")
            .withProcessors(new AutoValueProcessor())
            .compile(bazFileObject);
    assertThat(compilation2).hadErrorCount(1);
    assertThat(compilation2)
        .hadErrorContaining("UNDEFINED")
        .inFile(bazFileObject)
        .onLineContaining("UNDEFINED");
    assertThat(compilation2).hadWarningCount(0);
  }

  @Test
  public void packagePrivateAnnotationFromOtherPackage() {
    JavaFileObject bazFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz extends otherpackage.Parent {",
            "}");
    JavaFileObject parentFileObject =
        JavaFileObjects.forSourceLines(
            "otherpackage.Parent",
            "package otherpackage;",
            "",
            "public abstract class Parent {",
            "  @PackageAnnotation",
            "  public abstract String foo();",
            "",
            "  @interface PackageAnnotation {}",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(bazFileObject, parentFileObject);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation).generatedSourceFile("foo.bar.AutoValue_Baz");
  }

  @Test
  public void visibleProtectedAnnotationFromOtherPackage() {
    JavaFileObject bazFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz extends otherpackage.Parent {}");
    JavaFileObject parentFileObject =
        JavaFileObjects.forSourceLines(
            "otherpackage.Parent",
            "package otherpackage;",
            "",
            "public abstract class Parent {",
            "  @ProtectedAnnotation",
            "  public abstract String foo();",
            "",
            "  protected @interface ProtectedAnnotation {}",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(bazFileObject, parentFileObject);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Baz")
        .contentsAsUtf8String()
        .containsMatch("(?s:@Parent.ProtectedAnnotation\\s*@Override\\s*public String foo\\(\\))");
  }

  @Test
  public void nonVisibleProtectedAnnotationFromOtherPackage() {
    JavaFileObject bazFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz extends otherpackage.Parent {",
            "}");
    JavaFileObject parentFileObject =
        JavaFileObjects.forSourceLines(
            "otherpackage.Parent",
            "package otherpackage;",
            "",
            "import otherpackage.Annotations.ProtectedAnnotation;",
            "",
            "public abstract class Parent {",
            "  @ProtectedAnnotation",
            "  public abstract String foo();",
            "}");
    JavaFileObject annotationsFileObject =
        JavaFileObjects.forSourceLines(
            "otherpackage.Annotations",
            "package otherpackage;",
            "",
            "public class Annotations {",
            "  protected @interface ProtectedAnnotation {}",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(bazFileObject, parentFileObject, annotationsFileObject);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Baz")
        .contentsAsUtf8String()
        .doesNotContain("ProtectedAnnotation");
  }

  @Test
  public void nonVisibleProtectedClassAnnotationFromOtherPackage() {
    JavaFileObject bazFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Outer",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "class Outer extends otherpackage.Parent {",
            "  @AutoValue",
            "  @AutoValue.CopyAnnotations",
            "  @ProtectedAnnotation",
            "  abstract static class Inner {",
            "    abstract String foo();",
            "  }",
            "}");
    JavaFileObject parentFileObject =
        JavaFileObjects.forSourceLines(
            "otherpackage.Parent",
            "package otherpackage;",
            "",
            "public abstract class Parent {",
            "  protected @interface ProtectedAnnotation {}",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(bazFileObject, parentFileObject);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Outer_Inner")
        .contentsAsUtf8String()
        .doesNotContain("ProtectedAnnotation");
  }

  @Test
  public void builderWithVarArgsDoesNotImportJavaUtilArrays() {
    // Repro from https://github.com/google/auto/issues/373.
    JavaFileObject testFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Test",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.common.collect.ImmutableList;",
            "",
            "@AutoValue",
            "public abstract class Test {",
            "  abstract ImmutableList<String> foo();",
            "",
            "  @AutoValue.Builder",
            "  abstract static class Builder {",
            "    abstract Builder foo(String... foos);",
            "    abstract Test build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(testFileObject);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Test")
        .contentsAsUtf8String()
        .doesNotContain("java.util.Arrays");
  }

  @Test
  public void staticBuilderMethodInBuilderClass() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "com.example.Foo",
            "package com.example;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Foo {",
            "  public abstract String bar();",
            "",
            "  @AutoValue.Builder",
            "  public abstract static class Builder {",
            "    public static Builder builder() {",
            "      return new AutoValue_Foo.Builder();",
            "    }",
            "",
            "    public abstract Builder setBar(String s);",
            "    public abstract Foo build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(javaFileObject);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining("Static builder() method should be in the containing class")
        .inFile(javaFileObject)
        .onLineContaining("builder()");
  }

  /**
   * Tests behaviour when the package containing an {@code @AutoValue} class also has classes with
   * the same name as classes in {@code java.lang}. If you call a class {@code Object} you are
   * asking for trouble, but you could innocently call a class {@code Compiler} without realizing
   * there is a {@code java.lang.Compiler}.
   *
   * <p>The case where the class in question is mentioned in the {@code @AutoValue} class is the
   * easy one, because then our logic can easily see that there is a clash and will use
   * fully-qualified names. This is the case of the {@code Compiler} class below. The case where the
   * class is <i>not</i> mentioned is harder. We have to realize that we can't elide the package
   * name in {@code java.lang.Object} because there is also a {@code foo.bar.Object} in scope, and
   * in fact it takes precedence.
   */
  @Test
  public void javaLangClash() {
    JavaFileObject object =
        JavaFileObjects.forSourceLines(
            "foo.bar.Object", //
            "package foo.bar;",
            "",
            "public class Object {}");
    JavaFileObject string =
        JavaFileObjects.forSourceLines(
            "foo.bar.String", //
            "package foo.bar;",
            "",
            "public class String {}");
    JavaFileObject integer =
        JavaFileObjects.forSourceLines(
            "foo.bar.Integer", //
            "package foo.bar;",
            "",
            "public class Integer {}");
    JavaFileObject thread =
        JavaFileObjects.forSourceLines(
            "foo.bar.Thread", //
            "package foo.bar;",
            "",
            "public class Thread {}");
    JavaFileObject override =
        JavaFileObjects.forSourceLines(
            "foo.bar.Override", //
            "package foo.bar;",
            "",
            "public class Override {}");
    JavaFileObject test =
        JavaFileObjects.forSourceLines(
            "foo.bar.Test",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Test {",
            "  public abstract java.lang.Integer integer();",
            "  public abstract java.lang.Thread.State state();",
            "  public static Builder builder() {",
            "    return new AutoValue_Test.Builder();",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public abstract static class Builder {",
            "    public abstract Builder setInteger(java.lang.Integer x);",
            "    public abstract Builder setState(java.lang.Thread.State x);",
            "    public abstract Test build();",
            "  }",
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor())
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(object, string, integer, thread, override, test);
    assertThat(compilation).succeededWithoutWarnings();
  }

  // This is a regression test for the problem described in
  // https://github.com/google/auto/issues/847#issuecomment-629857642.
  @Test
  public void generatedParentWithGeneratedGetterButSetterInBuilder() {
    JavaFileObject test =
        JavaFileObjects.forSourceLines(
            "foo.bar.Test",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import foo.baz.GeneratedParent;",
            "import foo.baz.GeneratedPropertyType;",
            "import java.util.Optional;",
            "",
            "@AutoValue",
            "public abstract class Test extends GeneratedParent {",
            "  public abstract String string();",
            "",
            "  public static Builder builder() {",
            "    return new AutoValue_Test.Builder();",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public abstract static class Builder extends GeneratedParent.Builder<Builder> {",
            "    public abstract Builder setString(String x);",
            "    public abstract Builder setGenerated(GeneratedPropertyType x);",
            "    public abstract Test build();",
            "  }",
            "}");
    AutoValueProcessor autoValueProcessor = new AutoValueProcessor();
    GeneratedParentProcessor generatedParentProcessor =
        new GeneratedParentProcessor(autoValueProcessor, expect);
    Compilation compilation =
        javac()
            .withProcessors(autoValueProcessor, generatedParentProcessor)
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(test);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Test")
        .contentsAsUtf8String()
        .contains("  public int integer() {");
  }

  @SupportedAnnotationTypes("*")
  private static class GeneratedParentProcessor extends AbstractProcessor {
    private static final String GENERATED_PARENT =
        String.join(
            "\n",
            "package foo.baz;",
            "",
            "public abstract class GeneratedParent {",
            "  public abstract int integer();",
            "  public abstract GeneratedPropertyType generated();",
            "",
            "  public abstract static class Builder<B extends Builder<B>> {",
            "    public abstract B setInteger(int x);",
            "  }",
            "}");
    private static final String GENERATED_PROPERTY_TYPE =
        String.join(
            "\n", //
            "package foo.baz;",
            "",
            "public class GeneratedPropertyType {}");
    private static final ImmutableMap<String, String> GENERATED_TYPES =
        ImmutableMap.of(
            "foo.baz.GeneratedParent", GENERATED_PARENT,
            "foo.baz.GeneratedPropertyType", GENERATED_PROPERTY_TYPE);

    private final AutoValueProcessor autoValueProcessor;
    private final Expect expect;

    GeneratedParentProcessor(AutoValueProcessor autoValueProcessor, Expect expect) {
      this.autoValueProcessor = autoValueProcessor;
      this.expect = expect;
    }

    private boolean generated;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (!generated) {
        generated = true;
        // Check that AutoValueProcessor has already run and deferred the foo.bar.Test type because
        // we haven't generated its parent yet.
        expect.that(autoValueProcessor.deferredTypeNames()).contains("foo.bar.Test");
        GENERATED_TYPES.forEach(
            (typeName, source) -> {
              try {
                JavaFileObject generated = processingEnv.getFiler().createSourceFile(typeName);
                try (Writer writer = generated.openWriter()) {
                  writer.write(source);
                }
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
      }
      return false;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }
  }

  // This is a regression test for the problem described in
  // https://github.com/google/auto/issues/1087.
  @Test
  public void kotlinMetadataAnnotationsAreImplicitlyExcludedFromCopying() {
    JavaFileObject metadata =
        JavaFileObjects.forSourceLines(
            "kotlin.Metadata", "package kotlin;", "", "public @interface Metadata {", "}");
    JavaFileObject test =
        JavaFileObjects.forSourceLines(
            "foo.bar.Test",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import kotlin.Metadata;",
            "",
            "@AutoValue.CopyAnnotations",
            "@Metadata",
            "@AutoValue",
            "public abstract class Test {",
            "  public abstract String string();",
            "}");
    AutoValueProcessor autoValueProcessor = new AutoValueProcessor();
    Compilation compilation =
        javac()
            .withProcessors(autoValueProcessor)
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(test, metadata);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Test")
        .contentsAsUtf8String()
        .doesNotContain("kotlin.Metadata");
  }

  private String sorted(String... imports) {
    return Arrays.stream(imports).sorted().collect(joining("\n"));
  }
}
