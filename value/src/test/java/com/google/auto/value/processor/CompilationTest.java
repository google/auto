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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.JavaFileObjects;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class CompilationTest extends TestCase {
  public void testCompilation() {
    // Positive test case that ensures we generate the expected code for at least one case.
    // Most AutoValue code-generation tests are functional, meaning that we check that the generated
    // code does the right thing rather than checking what it looks like, but this test is a sanity
    // check that we are not generating correct but weird code.
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract int buh();",
        "",
        "  public static Baz create(int buh) {",
        "    return new AutoValue_Baz(buh);",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
        "package foo.bar;",
        "",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"" + AutoValueProcessor.class.getName() + "\")",
        "final class AutoValue_Baz extends Baz {",
        "  private final int buh;",
        "",
        "  AutoValue_Baz(int buh) {",
        "    this.buh = buh;",
        "  }",
        "",
        "  @Override public int buh() {",
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
        "      return (this.buh == that.buh());",
        "    }",
        "    return false;",
        "  }",
        "",
        "  @Override public int hashCode() {",
        "    int h = 1;",
        "    h *= 1000003;",
        "    h ^= buh;",
        "    return h;",
        "  }",
        "}"
    );
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  public void testImports() {
    // Test that referring to the same class in two different ways does not confuse the import logic
    // into thinking it is two different classes and that therefore it can't import. The code here
    // is nonsensical but successfully reproduces a real problem, which is that a TypeMirror that is
    // extracted using Elements.getTypeElement(name).asType() does not compare equal to one that is
    // extracted from ExecutableElement.getReturnType(), even though Types.isSameType considers them
    // equal. So unless we are careful, the java.util.Arrays that we import explicitly to use its
    // methods will appear different from the java.util.Arrays that is the return type of the
    // arrays() method here.
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "import java.util.Arrays;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract int[] ints();",
        "  public abstract Arrays arrays();",
        "",
        "  public static Baz create(int[] ints, Arrays arrays) {",
        "    return new AutoValue_Baz(ints, arrays);",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
        "package foo.bar;",
        "",
        "import java.util.Arrays;",
        "import javax.annotation.Generated;",
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
        "  @Override public int[] ints() {",
        "    return ints.clone();",
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
        "      return (Arrays.equals(this.ints, (that instanceof AutoValue_Baz) "
                      + "? ((AutoValue_Baz) that).ints : that.ints()))",
        "          && (this.arrays.equals(that.arrays()));",
        "    }",
        "    return false;",
        "  }",
        "",
        "  @Override public int hashCode() {",
        "    int h = 1;",
        "    h *= 1000003;",
        "    h ^= Arrays.hashCode(ints);",
        "    h *= 1000003;",
        "    h ^= arrays.hashCode();",
        "    return h;",
        "  }",
        "}"
    );
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  public void testNoMultidimensionalPrimitiveArrays() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("AutoValue class cannot define an array-valued property "
            + "unless it is a primitive array")
        .in(javaFileObject).onLine(7);
  }

  public void testNoObjectArrays() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("AutoValue class cannot define an array-valued property "
            + "unless it is a primitive array")
        .in(javaFileObject).onLine(7);
  }

  public void testAnnotationOnInterface() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public interface Baz {}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("AutoValue only applies to classes")
        .in(javaFileObject).onLine(6);
  }

  public void testAnnotationOnEnum() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public enum Baz {}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("AutoValue only applies to classes")
        .in(javaFileObject).onLine(6);
  }

  public void testExtendAutoValue() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("may not extend")
        .in(javaFileObject).onLine(16);
  }

  public void testBogusSerialVersionUID() throws Exception {
    String[] mistakes = {
      "final long serialVersionUID = 1234L", // not static
      "static long serialVersionUID = 1234L", // not final
      "static final Long serialVersionUID = 1234L", // not long
      "static final long serialVersionUID = (Long) 1234L", // not a compile-time constant
    };
    for (String mistake : mistakes) {
      JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
      assertAbout(javaSource())
          .that(javaFileObject)
          .processedWith(new AutoValueProcessor())
          .failsToCompile()
          .withErrorContaining(
              "serialVersionUID must be a static final long compile-time constant")
          .in(javaFileObject).onLine(7);
    }
  }

  public void testNonExistentSuperclass() throws Exception {
    // The main purpose of this test is to check that AutoValueProcessor doesn't crash the
    // compiler in this case.
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Existent extends NonExistent {",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("NonExistent")
        .in(javaFileObject).onLine(6);
  }

  public void testCannotImplementAnnotation() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("may not be used to implement an annotation interface")
        .in(javaFileObject).onLine(8);
  }

  public void testMissingPropertyType() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract MissingType missingType();",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("MissingType")
        .in(javaFileObject).onLine(7);
  }

  public void testMissingGenericPropertyType() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract MissingType<?> missingType();",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("MissingType")
        .in(javaFileObject).onLine(7);
  }

  public void testMissingComplexGenericPropertyType() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("MissingType")
        .in(javaFileObject).onLine(10);
  }

  public void testMissingSuperclassGenericParameter() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz<T extends MissingType<?>> {",
        "  public abstract int foo();",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("MissingType")
        .in(javaFileObject).onLine(6);
  }

  public void testCorrectBuilder() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "import java.util.List;",
        "import javax.annotation.Nullable;",
        "",
        "@AutoValue",
        "public abstract class Baz<T extends Number> {",
        "  public abstract int anInt();",
        "  public abstract byte[] aByteArray();",
        "  @Nullable public abstract int[] aNullableIntArray();",
        "  public abstract List<T> aList();",
        "",
        "  @AutoValue.Validate",
        "  void validate() {",
        "    if (anInt() < 0) {",
        "      throw new IllegalStateException(\"Negative integer\");",
        "    }",
        "  }",
        "",
        "  @AutoValue.Builder",
        "  public interface Builder<T extends Number> {",
        "    Builder<T> anInt(int x);",
        "    Builder<T> aByteArray(byte[] x);",
        "    Builder<T> aNullableIntArray(@Nullable int[] x);",
        "    Builder<T> aList(List<T> x);",
        "    Baz<T> build();",
        "  }",
        "",
        "  public static <T extends Number> Builder<T> builder() {",
        "    return AutoValue_Baz.builder();",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
        "package foo.bar;",
        "",
        "import java.util.Arrays;",
        "import java.util.BitSet;",
        "import java.util.List;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"" + AutoValueProcessor.class.getName() + "\")",
        "final class AutoValue_Baz<T extends Number> extends Baz<T> {",
        "  private final int anInt;",
        "  private final byte[] aByteArray;",
        "  private final int[] aNullableIntArray;",
        "  private final List<T> aList;",
        "",
        "  AutoValue_Baz(int anInt, byte[] aByteArray, int[] aNullableIntArray, List<T> aList) {",
        "    this.anInt = anInt;",
        "    if (aByteArray == null) {",
        "      throw new NullPointerException(\"Null aByteArray\");",
        "    }",
        "    this.aByteArray = aByteArray;",
        "    this.aNullableIntArray = aNullableIntArray;",
        "    if (aList == null) {",
        "      throw new NullPointerException(\"Null aList\");",
        "    }",
        "    this.aList = aList;",
        "  }",
        "",
        "  @Override public int anInt() {",
        "    return anInt;",
        "  }",
        "",
        "  @Override public byte[] aByteArray() {",
        "    return aByteArray.clone();",
        "  }",
        "",
        "  @javax.annotation.Nullable",
        "  @Override public int[] aNullableIntArray() {",
        "    return aNullableIntArray == null ? null : aNullableIntArray.clone();",
        "  }",
        "",
        "  @Override public List<T> aList() {",
        "    return aList;",
        "  }",
        "",
        "  @Override public String toString() {",
        "    return \"Baz{\"",
        "        + \"anInt=\" + anInt + \", \"",
        "        + \"aByteArray=\" + Arrays.toString(aByteArray) + \", \"",
        "        + \"aNullableIntArray=\" + Arrays.toString(aNullableIntArray) + \", \"",
        "        + \"aList=\" + aList",
        "        + \"}\";",
        "  }",
        "",
        "  @Override public boolean equals(Object o) {",
        "    if (o == this) {",
        "      return true;",
        "    }",
        "    if (o instanceof Baz) {",
        "      Baz<?> that = (Baz<?>) o;",
        "      return (this.anInt == that.anInt())",
        "          && (Arrays.equals(this.aByteArray, "
                    + "(that instanceof AutoValue_Baz) "
                        + "? ((AutoValue_Baz) that).aByteArray : that.aByteArray()))",
        "          && (Arrays.equals(this.aNullableIntArray, "
                    + "(that instanceof AutoValue_Baz) "
                        + "? ((AutoValue_Baz) that).aNullableIntArray : that.aNullableIntArray()))",
        "          && (this.aList.equals(that.aList()));",
        "    }",
        "    return false;",
        "  }",
        "",
        "  @Override public int hashCode() {",
        "    int h = 1;",
        "    h *= 1000003;",
        "    h ^= anInt;",
        "    h *= 1000003;",
        "    h ^= Arrays.hashCode(aByteArray);",
        "    h *= 1000003;",
        "    h ^= Arrays.hashCode(aNullableIntArray);",
        "    h *= 1000003;",
        "    h ^= aList.hashCode();",
        "    return h;",
        "  }",
        "",
        "  static final class Builder<T extends Number> implements Baz.Builder<T> {",
        "    private final BitSet set$ = new BitSet(4);",
        "",
        "    private int anInt;",
        "    private byte[] aByteArray;",
        "    private int[] aNullableIntArray;",
        "    private List<T> aList;",
        "",
        "    @Override",
        "    public Baz.Builder<T> anInt(int anInt) {",
        "      this.anInt = anInt;",
        "      set$.set(0);",
        "      return this;",
        "    }",
        "",
        "    @Override",
        "    public Baz.Builder<T> aByteArray(byte[] aByteArray) {",
        "      this.aByteArray = aByteArray.clone();",
        "      set$.set(1);",
        "      return this;",
        "    }",
        "",
        "    @Override",
        "    public Baz.Builder<T> aNullableIntArray(int[] aNullableIntArray) {",
        "      this.aNullableIntArray = "
                + "(aNullableIntArray == null) ? null : aNullableIntArray.clone();",
        "      set$.set(2);",
        "      return this;",
        "    }",
        "",
        "    @Override",
        "    public Baz.Builder<T> aList(List<T> aList) {",
        "      this.aList = aList;",
        "      set$.set(3);",
        "      return this;",
        "    }",
        "",
        "    @Override",
        "    public Baz<T> build() {",
        "      if (set$.cardinality() < 4) {",
        "        String[] propertyNames = {",
        "          \"anInt\", \"aByteArray\", \"aNullableIntArray\", \"aList\",",
        "        };",
        "        StringBuilder missing = new StringBuilder();",
        "        for (int i = 0; i < 4; i++) {",
        "          if (!set$.get(i)) {",
        "            missing.append(' ').append(propertyNames[i]);",
        "          }",
        "        }",
        "        throw new IllegalStateException(\"Missing required properties:\" + missing);",
        "      }",
        "      Baz<T> result = new AutoValue_Baz<T>(",
        "          this.anInt, this.aByteArray, this.aNullableIntArray, this.aList);",
        "      result.validate();",
        "      return result;",
        "    }",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedOutput);
  }

  public void testAutoValueBuilderOnTopLevelClass() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("can only be applied to a class or interface inside")
        .in(javaFileObject).onLine(6);
  }

  public void testAutoValueBuilderNotInsideAutoValue() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("can only be applied to a class or interface inside")
        .in(javaFileObject).onLine(13);
  }

  public void testAutoValueBuilderOnEnum() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("can only apply to a class or an interface")
        .in(javaFileObject).onLine(14);
  }

  public void testAutoValueBuilderDuplicate() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("already has a Builder: foo.bar.Baz.Builder1")
        .in(javaFileObject).onLine(13);
  }

  public void testAutoValueBuilderMissingSetter() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("with this signature: foo.bar.Baz.Builder blim(int)")
        .in(javaFileObject).onLine(11);
  }

  public void testAutoValueBuilderMissingSetterUsingSetPrefix() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("with this signature: foo.bar.Baz.Builder setBlim(int)")
        .in(javaFileObject).onLine(11);
  }

  public void testAutoValueBuilderWrongTypeSetter() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("Parameter type should be int")
        .in(javaFileObject).onLine(12);
  }

  public void testAutoValueBuilderWrongTypeSetterWithGetPrefix() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("Parameter type should be int")
        .in(javaFileObject).onLine(12);
  }

  public void testAutoValueBuilderExtraSetter() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("Method does not correspond to a property of foo.bar.Baz")
        .in(javaFileObject).onLine(11);
  }

  public void testAutoValueBuilderSetPrefixAndNoSetPrefix() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("If any setter methods use the setFoo convention then all must")
        .in(javaFileObject).onLine(12);
  }

  public void testAutoValueBuilderAlienMethod() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Builder methods must either have no arguments and return foo.bar.Baz or have one"
                + " argument and return foo.bar.Baz.Builder")
        .in(javaFileObject).onLine(11);
  }

  public void testAutoValueBuilderMissingBuildMethod() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("Builder must have a single no-argument method returning foo.bar.Baz")
        .in(javaFileObject).onLine(10);
  }

  public void testAutoValueBuilderDuplicateBuildMethods() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("Builder must have a single no-argument method returning foo.bar.Baz")
        .in(javaFileObject).onLine(12)
        .and()
        .withErrorContaining("Builder must have a single no-argument method returning foo.bar.Baz")
        .in(javaFileObject).onLine(13);
  }

  public void testAutoValueBuilderWrongTypeBuildMethod() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("Builder must have a single no-argument method returning foo.bar.Baz")
        .in(javaFileObject).onLine(10);
  }

  public void testAutoValueBuilderTypeParametersDontMatch1() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
        "    String build();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("Type parameters of foo.bar.Baz.Builder must have same names and "
            + "bounds as type parameters of foo.bar.Baz")
        .in(javaFileObject).onLine(10);
  }

  public void testAutoValueBuilderTypeParametersDontMatch2() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
        "    String build();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("Type parameters of foo.bar.Baz.Builder must have same names and "
            + "bounds as type parameters of foo.bar.Baz")
        .in(javaFileObject).onLine(10);
  }

  public void testAutoValueBuilderTypeParametersDontMatch3() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
        "    String build();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("Type parameters of foo.bar.Baz.Builder must have same names and "
            + "bounds as type parameters of foo.bar.Baz")
        .in(javaFileObject).onLine(10);
  }

  public void testAutoValueValidateNotInAutoValue() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "public abstract class Baz {",
        "  abstract String blam();",
        "",
        "  @AutoValue.Validate",
        "  void validate() {}",
        "",
        "  public interface Builder {",
        "    Builder blam(String x);",
        "    Baz build();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining(
            "@AutoValue.Validate can only be applied to a method inside an @AutoValue class")
        .in(javaFileObject).onLine(9);
  }

  public void testAutoValueValidateWithoutBuilder() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  abstract String blam();",
        "",
        "  @AutoValue.Validate",
        "  void validate() {}",
        "",
        "  public interface Builder {",
        "    Builder blam(String x);",
        "    Baz build();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining(
            "@AutoValue.Validate is only meaningful if there is an @AutoValue.Builder")
        .in(javaFileObject).onLine(10);
  }

  public void testAutoValueBuilderValidateMethodStatic() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  abstract String blam();",
        "",
        "  @AutoValue.Validate",
        "  static void validate() {}",
        "",
        "  @AutoValue.Builder",
        "  public interface Builder {",
        "    Builder blam(String x);",
        "    Baz build();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("@AutoValue.Validate cannot apply to a static method")
        .in(javaFileObject).onLine(10);
  }

  public void testAutoValueBuilderValidateMethodNotVoid() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  abstract String blam();",
        "",
        "  @AutoValue.Validate",
        "  Baz validate() {",
        "    return this;",
        "  }",
        "",
        "  @AutoValue.Builder",
        "  public interface Builder {",
        "    Builder blam(String x);",
        "    Baz build();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("@AutoValue.Validate method must be void")
        .in(javaFileObject).onLine(10);
  }

  public void testAutoValueBuilderValidateMethodWithParameters() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  abstract String blam();",
        "",
        "  @AutoValue.Validate",
        "  void validate(boolean why) {}",
        "",
        "  @AutoValue.Builder",
        "  public interface Builder {",
        "    Builder blam(String x);",
        "    Baz build();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("@AutoValue.Validate method must not have parameters")
        .in(javaFileObject).onLine(10);
  }

  public void testAutoValueBuilderValidateMethodDuplicate() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  abstract String blam();",
        "",
        "  @AutoValue.Validate",
        "  void validate() {}",
        "",
        "  @AutoValue.Validate",
        "  void validateSomeMore() {}",
        "",
        "  @AutoValue.Builder",
        "  public interface Builder {",
        "    Builder blam(String x);",
        "    Baz build();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("There can only be one @AutoValue.Validate method")
        .in(javaFileObject).onLine(13);
  }

  public void testGetFooIsFoo() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("More than one @AutoValue property called foo")
        .in(javaFileObject).onLine(8);
  }

  private static class PoisonedAutoValueProcessor extends AutoValueProcessor {
    private final IllegalArgumentException filerException;

    PoisonedAutoValueProcessor(IllegalArgumentException filerException) {
      this.filerException = filerException;
    }

    private class ErrorInvocationHandler implements InvocationHandler {
      private final ProcessingEnvironment originalProcessingEnv;

      ErrorInvocationHandler(ProcessingEnvironment originalProcessingEnv) {
        this.originalProcessingEnv = originalProcessingEnv;
      }

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("getFiler")) {
          throw filerException;
        } else {
          return method.invoke(originalProcessingEnv, args);
        }
      }
    };

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      ProcessingEnvironment poisonedProcessingEnv = (ProcessingEnvironment) Proxy.newProxyInstance(
          getClass().getClassLoader(),
          new Class<?>[] {ProcessingEnvironment.class},
          new ErrorInvocationHandler(processingEnv));
      processingEnv = poisonedProcessingEnv;
      return super.process(annotations, roundEnv);
    }
  }

  public void testExceptionBecomesError() throws Exception {
    // Ensure that if the annotation processor code gets an unexpected exception, it is converted
    // into a compiler error rather than being propagated. Otherwise the output can be very
    // confusing to the user who stumbles into a bug that causes an exception, whether in
    // AutoValueProcessor or javac.
    // We inject an exception by subclassing AutoValueProcessor in order to poison its processingEnv
    // in a way that will cause an exception the first time it tries to get the Filer.
    IllegalArgumentException exception =
        new IllegalArgumentException("I don't understand the question, and I won't respond to it");
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract int foo();",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new PoisonedAutoValueProcessor(exception))
        .failsToCompile()
        .withErrorContaining(exception.toString())
        .in(javaFileObject).onLine(6);
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

  public void testReferencingGeneratedClass() {
    // Test that ensures that a type that does not exist can be the type of an @AutoValue property
    // as long as it later does come into existence. The BarFoo type referenced here does not exist
    // when the AutoValueProcessor runs on the first round, but the FooProcessor then generates it.
    // That generation provokes a further round of annotation processing and AutoValueProcessor
    // should succeed then.
    JavaFileObject bazFileObject = JavaFileObjects.forSourceLines(
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
    JavaFileObject barFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Bar",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@" + Foo.class.getCanonicalName(),
        "public abstract class Bar {",
        "  public abstract BarFoo barFoo();",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(bazFileObject, barFileObject))
        .processedWith(new AutoValueProcessor(), new FooProcessor())
        .compilesWithoutError();
  }
}
