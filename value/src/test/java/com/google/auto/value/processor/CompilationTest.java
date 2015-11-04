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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
@RunWith(JUnit4.class)
public class CompilationTest {
  @Test
  public void simpleSuccess() {
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
        "    h ^= this.buh;",
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
        "  @SuppressWarnings(\"mutable\")",
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
        "  @SuppressWarnings(value = {\"mutable\"})",
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
        "    h ^= Arrays.hashCode(this.ints);",
        "    h *= 1000003;",
        "    h ^= this.arrays.hashCode();",
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

  @Test
  public void autoValueMustBeStatic() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("Nested @AutoValue class must be static")
        .in(javaFileObject).onLine(7);
  }

  @Test
  public void noMultidimensionalPrimitiveArrays() throws Exception {
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

  @Test
  public void noObjectArrays() throws Exception {
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

  @Test
  public void annotationOnInterface() throws Exception {
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

  @Test
  public void annotationOnEnum() throws Exception {
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

  @Test
  public void extendAutoValue() throws Exception {
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

  @Test
  public void bogusSerialVersionUID() throws Exception {
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

  @Test
  public void nonExistentSuperclass() throws Exception {
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

  @Test
  public void cannotImplementAnnotation() throws Exception {
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

  @Test
  public void missingPropertyType() throws Exception {
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

  @Test
  public void missingGenericPropertyType() throws Exception {
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

  @Test
  public void missingComplexGenericPropertyType() throws Exception {
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

  @Test
  public void missingSuperclassGenericParameter() throws Exception {
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

  @Test
  public void correctBuilder() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "import com.google.common.collect.ImmutableList;",
        "",
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
        "",
        "  public abstract Builder<T> toBuilder();",
        "",
        "  @AutoValue.Builder",
        "  public interface Builder<T extends Number> {",
        "    Builder<T> anInt(int x);",
        "    Builder<T> aByteArray(byte[] x);",
        "    Builder<T> aNullableIntArray(@Nullable int[] x);",
        "    Builder<T> aList(List<T> x);",
        "    Builder<T> anImmutableList(List<T> x);",
        "    ImmutableList.Builder<T> anImmutableListBuilder();",
        "",
        "    List<T> aList();",
        "    ImmutableList<T> anImmutableList();",
        "",
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
        "import com.google.common.collect.ImmutableList",
        "import java.util.Arrays;",
        "import java.util.List;",
        "import javax.annotation.Generated;",
        "import javax.annotation.Nullable;",
        "",
        "@Generated(\"" + AutoValueProcessor.class.getName() + "\")",
        "final class AutoValue_Baz<T extends Number> extends Baz<T> {",
        "  private final int anInt;",
        "  private final byte[] aByteArray;",
        "  private final int[] aNullableIntArray;",
        "  private final List<T> aList;",
        "  private final ImmutableList<T> anImmutableList;",
        "",
        "  private AutoValue_Baz(",
        "      int anInt,",
        "      byte[] aByteArray,",
        "      @Nullable int[] aNullableIntArray,",
        "      List<T> aList,",
        "      ImmutableList<T> anImmutableList) {",
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
        "    this.anImmutableList = anImmutableList;",
        "  }",
        "",
        "  @Override public int anInt() {",
        "    return anInt;",
        "  }",
        "",
        "  @SuppressWarnings(value = {\"mutable\"})",
        "  @Override public byte[] aByteArray() {",
        "    return aByteArray;",
        "  }",
        "",
        "  @SuppressWarnings(value = {\"mutable\"})",
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
        "  @Override public String toString() {",
        "    return \"Baz{\"",
        "        + \"anInt=\" + anInt + \", \"",
        "        + \"aByteArray=\" + Arrays.toString(aByteArray) + \", \"",
        "        + \"aNullableIntArray=\" + Arrays.toString(aNullableIntArray) + \", \"",
        "        + \"aList=\" + aList + \", \"",
        "        + \"anImmutableList=\" + anImmutableList",
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
        "          && (this.aList.equals(that.aList()))",
        "          && (this.anImmutableList.equals(that.anImmutableList()));",
        "    }",
        "    return false;",
        "  }",
        "",
        "  @Override public int hashCode() {",
        "    int h = 1;",
        "    h *= 1000003;",
        "    h ^= this.anInt;",
        "    h *= 1000003;",
        "    h ^= Arrays.hashCode(this.aByteArray);",
        "    h *= 1000003;",
        "    h ^= Arrays.hashCode(this.aNullableIntArray);",
        "    h *= 1000003;",
        "    h ^= this.aList.hashCode();",
        "    h *= 1000003;",
        "    h ^= this.anImmutableList.hashCode();",
        "    return h;",
        "  }",
        "",
        "  @Override public Baz.Builder<T> toBuilder() {",
        "    return new Builder<T>(this);",
        "  }",
        "",
        "  static final class Builder<T extends Number> implements Baz.Builder<T> {",
        "    private Integer anInt;",
        "    private byte[] aByteArray;",
        "    private int[] aNullableIntArray;",
        "    private List<T> aList;",
        "    private ImmutableList.Builder<T> anImmutableListBuilder$",
        "    private ImmutableList<T> anImmutableList;",
        "",
        "    Builder() {",
        "      this.anImmutableList = ImmutableList.of();",
        "    }",
        "",
        "    Builder(Baz<T> source) {",
        "      this.anInt = source.anInt();",
        "      this.aByteArray = source.aByteArray();",
        "      this.aNullableIntArray = source.aNullableIntArray();",
        "      this.aList = source.aList();",
        "      this.anImmutableList = source.anImmutableList();",
        "    }",
        "",
        "    @Override",
        "    public Baz.Builder<T> anInt(int anInt) {",
        "      this.anInt = anInt;",
        "      return this;",
        "    }",
        "",
        "    @Override",
        "    public Baz.Builder<T> aByteArray(byte[] aByteArray) {",
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
                     + "\"Cannot set anImmutableList after calling anImmutableListBuilder()\")",
        "      }",
        "      this.anImmutableList = ImmutableList.copyOf(anImmutableList);",
        "      return this;",
        "    }",
        "",
        "    @Override",
        "    public ImmutableList.Builder<T> anImmutableListBuilder() {",
        "      if (anImmutableListBuilder$ == null) {",
        "        anImmutableListBuilder$ = ImmutableList.builder();",
        "        anImmutableListBuilder$.addAll(anImmutableList);",
        "        anImmutableList = null;",
        "      }",
        "      return anImmutableListBuilder$;",
        "    }",
        "",
        "    @Override",
        "    public ImmutableList<T> anImmutableList() {",
        "      if (anImmutableListBuilder$ != null) {",
        "        return anImmutableListBuilder$.build();",
        "      }",
        "      return anImmutableList;",
        "    }",
        "",
        "    @Override",
        "    public Baz<T> build() {",
        "      if (anImmutableListBuilder$ != null) {",
        "        anImmutableList = anImmutableListBuilder$.build();",
        "      }",
        "      String missing = \"\";",
        "      if (anInt == null) {",
        "        missing += \" anInt\";",
        "      }",
        "      if (aByteArray == null) {",
        "        missing += \" aByteArray\";",
        "      }",
        "      if (aList == null) {",
        "        missing += \" aList\";",
        "      }",
        "      if (!missing.isEmpty()) {",
        "        throw new IllegalStateException(\"Missing required properties:\" + missing);",
        "      }",
        "      return new AutoValue_Baz<T>(",
        "          this.anInt,",
        "          this.aByteArray,",
        "          this.aNullableIntArray,",
        "          this.aList,",
        "          this.anImmutableList);",
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

  @Test
  public void autoValueBuilderOnTopLevelClass() throws Exception {
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

  @Test
  public void autoValueBuilderNotInsideAutoValue() throws Exception {
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

  @Test
  public void autoValueBuilderOnEnum() throws Exception {
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

  @Test
  public void autoValueBuilderDuplicate() {
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

  @Test
  public void autoValueBuilderMissingSetter() {
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

  @Test
  public void autoValueBuilderMissingSetterUsingSetPrefix() {
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

  @Test
  public void autoValueBuilderWrongTypeSetter() {
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
        .withErrorContaining(
            "Parameter type of setter method should be int to match getter foo.bar.Baz.blim")
        .in(javaFileObject).onLine(12);
  }

  @Test
  public void autoValueBuilderWrongTypeSetterWithCopyOf() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Parameter type of setter method should be "
                + "com.google.common.collect.ImmutableList<java.lang.String> to match getter "
                + "foo.bar.Baz.blam, or it should be a type that can be passed to "
                + "ImmutableList.copyOf")
        .in(javaFileObject).onLine(14);
  }

  @Test
  public void autoValueBuilderWrongTypeSetterWithCopyOfGenericallyWrong() {
    // This puts the finger on our insufficient error-detection logic for the case where the
    // parameter would be compatible with copyOf were it not for generics. Currently, this leads to
    // a compile error in the generated code. We don't want to suppose anything about the error
    // message the compiler might come up with. It might be something like this for example:
    //   incompatible types: inference variable E has incompatible bounds
    //        equality constraints: java.lang.String
    //        lower bounds: java.lang.Integer
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile();
  }

  @Test
  public void autoValueBuilderWrongTypeSetterWithGetPrefix() {
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
        .withErrorContaining(
            "Parameter type of setter method should be int to match getter foo.bar.Baz.getBlim")
        .in(javaFileObject).onLine(12);
  }

  @Test
  public void autoValueBuilderExtraSetter() {
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

  @Test
  public void autoValueBuilderSetPrefixAndNoSetPrefix() {
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

  @Test
  public void autoValueBuilderWrongTypeGetter() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Method matches a property of foo.bar.Baz but has return type T instead of U")
        .in(javaFileObject).onLine(15);
  }

  @Test
  public void autoValueBuilderPropertyBuilderInvalidType() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Method looks like a property builder, but its return type is not a builder for an "
                + "immutable type in com.google.common.collect")
        .in(javaFileObject).onLine(11);
  }

  @Test
  public void autoValueBuilderPropertyBuilderRawType() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "import com.google.common.collect.ImmutableList;",
        "",
        "@AutoValue",
        "public abstract class Baz<T, U> {",
        "  abstract ImmutableList blim();",
        "",
        "  @AutoValue.Builder",
        "  public interface Builder<T, U> {",
        "    ImmutableList.Builder blimBuilder();",
        "    Baz<T, U> build();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Property builder type cannot be raw (missing <...>)")
        .in(javaFileObject).onLine(12);
  }

  @Test
  public void autoValueBuilderPropertyBuilderWrongCollectionType() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Return type of property-builder method implies a property of type "
                + "com.google.common.collect.ImmutableSet<T>, but property blim has type "
                + "com.google.common.collect.ImmutableList<T>")
        .in(javaFileObject).onLine(13);
  }

  @Test
  public void autoValueBuilderPropertyBuilderWrongElementType() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Return type of property-builder method implies a property of type "
                + "com.google.common.collect.ImmutableSet<U>, but property blim has type "
                + "com.google.common.collect.ImmutableSet<T>")
        .in(javaFileObject).onLine(12);
  }

  @Test
  public void autoValueBuilderAlienMethod0() {
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
        "    Builder whut();",
        "    Baz build();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Method without arguments should be a build method returning foo.bar.Baz"
            + " or a getter method with the same name and type as a getter method of foo.bar.Baz")
        .in(javaFileObject).onLine(12);
  }

  @Test
  public void autoValueBuilderAlienMethod1() {
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
        "    void whut(String x);",
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

  @Test
  public void autoValueBuilderAlienMethod2() {
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
        .withErrorContaining("Builder methods must have 0 or 1 parameters")
        .in(javaFileObject).onLine(11);
  }

  @Test
  public void autoValueBuilderMissingBuildMethod() {
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
        "  public interface Builder<T> {",
        "    Builder<T> blam(T x);",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Builder must have a single no-argument method returning foo.bar.Baz<T>")
        .in(javaFileObject).onLine(10);
  }

  @Test
  public void autoValueBuilderDuplicateBuildMethods() {
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

  @Test
  public void autoValueBuilderWrongTypeBuildMethod() {
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
        .withErrorContaining(
            "Method without arguments should be a build method returning foo.bar.Baz")
        .in(javaFileObject).onLine(12);
  }

  @Test
  public void autoValueBuilderTypeParametersDontMatch1() {
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
        "    Baz build();",
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

  @Test
  public void autoValueBuilderTypeParametersDontMatch2() {
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
        "    Baz build();",
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

  @Test
  public void autoValueBuilderTypeParametersDontMatch3() {
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
        "    Baz build();",
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

  @Test
  public void autoValueBuilderToBuilderWrongTypeParameters() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("Builder converter method should return foo.bar.Baz.Builder<K, V>")
        .in(javaFileObject).onLine(9);
  }

  @Test
  public void autoValueBuilderToBuilderDuplicate() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor(), new AutoValueBuilderProcessor())
        .failsToCompile()
        .withErrorContaining("There can be at most one builder converter method")
        .in(javaFileObject).onLine(9);
  }

  @Test
  public void getFooIsFoo() throws Exception {
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

  @Test
  public void exceptionBecomesError() throws Exception {
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

  @Test
  public void referencingGeneratedClass() {
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
