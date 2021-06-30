/*
 * Copyright 2012 Google LLC
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
package com.google.auto.value;

import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.testing.EqualsTester;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.AssumptionViolatedException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for constructs new in Java 8, such as type annotations.
 *
 * @author Till Brychcy
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class AutoValueJava8Test {
  private static boolean javacHandlesTypeAnnotationsCorrectly;

  // This is appalling. Some versions of javac do not correctly report annotations on type uses in
  // certain cases, for example on type variables or arrays. Since some of the tests here are for
  // exactly that, we compile a test program with a test annotation processor to see whether we
  // might be in the presence of such a javac, and if so we skip the tests that would fail because
  // of the bug. This isn't completely sound because we can't be entirely sure that the javac that
  // Compiler.javac() finds is the same as the javac that was used to build this test (and therefore
  // run AutoValueProcessor), but it's better than just ignoring the tests outright.
  @BeforeClass
  public static void setUpClass() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "Test",
            "import java.lang.annotation.ElementType;",
            "import java.lang.annotation.Retention;",
            "import java.lang.annotation.RetentionPolicy;",
            "import java.lang.annotation.Target;",
            "public abstract class Test<T> {",
            "  @Retention(RetentionPolicy.RUNTIME)",
            "  @Target(ElementType.TYPE_USE)",
            "  public @interface Nullable {}",
            "",
            "  public abstract @Nullable T t();",
            "}");
    Compilation compilation =
        Compiler.javac().withProcessors(new BugTestProcessor()).compile(javaFileObject);
    if (compilation.errors().isEmpty()) {
      javacHandlesTypeAnnotationsCorrectly = true;
    } else {
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation).hadErrorContaining(JAVAC_HAS_BUG_ERROR);
    }
  }

  private static final String JAVAC_HAS_BUG_ERROR = "javac has the type-annotation bug";

  @SupportedAnnotationTypes("*")
  @SupportedSourceVersion(SourceVersion.RELEASE_8)
  private static class BugTestProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (roundEnv.processingOver()) {
        test();
      }
      return false;
    }

    private void test() {
      TypeElement test = processingEnv.getElementUtils().getTypeElement("Test");
      List<ExecutableElement> methods = ElementFilter.methodsIn(test.getEnclosedElements());
      ExecutableElement t = Iterables.getOnlyElement(methods);
      assertThat(t.getSimpleName().toString()).isEqualTo("t");
      List<? extends AnnotationMirror> typeAnnotations = t.getReturnType().getAnnotationMirrors();
      if (typeAnnotations.isEmpty()) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, JAVAC_HAS_BUG_ERROR);
        return;
      }
      AnnotationMirror typeAnnotation = Iterables.getOnlyElement(typeAnnotations);
      assertThat(typeAnnotation.getAnnotationType().toString()).contains("Nullable");
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE_USE)
  public @interface Nullable {}

  @AutoValue
  abstract static class NullableProperties {
    abstract @Nullable String nullableString();

    abstract int randomInt();

    static NullableProperties create(@Nullable String nullableString, int randomInt) {
      return new AutoValue_AutoValueJava8Test_NullableProperties(nullableString, randomInt);
    }
  }

  @Test
  public void testNullablePropertiesCanBeNull() {
    NullableProperties instance = NullableProperties.create(null, 23);
    assertThat(instance.nullableString()).isNull();
    assertThat(instance.randomInt()).isEqualTo(23);
    assertThat(instance.toString())
        .isEqualTo("NullableProperties{nullableString=null, randomInt=23}");
  }

  @Test
  public void testEqualsParameterIsAnnotated() throws NoSuchMethodException {
    // Sadly we can't rely on JDK 8 to handle type annotations correctly.
    // Some versions do, some don't. So skip the test unless we are on at least JDK 9.
    double javaVersion = Double.parseDouble(JAVA_SPECIFICATION_VERSION.value());
    assume().that(javaVersion).isAtLeast(9.0);
    Method equals =
        NullableProperties.create(null, 23).getClass().getMethod("equals", Object.class);
    AnnotatedType[] parameterTypes = equals.getAnnotatedParameterTypes();
    assertThat(parameterTypes).hasLength(1);
    assertThat(parameterTypes[0].getAnnotation(Nullable.class)).isNotNull();
  }

  @AutoAnnotation
  static Nullable nullable() {
    return new AutoAnnotation_AutoValueJava8Test_nullable();
  }

  @Test
  public void testNullablePropertyImplementationIsNullable() throws NoSuchMethodException {
    Method method =
        AutoValue_AutoValueJava8Test_NullableProperties.class.getDeclaredMethod("nullableString");
    assertThat(method.getAnnotatedReturnType().getAnnotations()).asList().contains(nullable());
  }

  @Test
  public void testNullablePropertyConstructorParameterIsNullable() throws NoSuchMethodException {
    Constructor<?> constructor =
        AutoValue_AutoValueJava8Test_NullableProperties.class.getDeclaredConstructor(
            String.class, int.class);
    try {
      assertThat(constructor.getAnnotatedParameterTypes()[0].getAnnotations())
          .asList()
          .contains(nullable());
    } catch (AssertionError e) {
      if (javacHandlesTypeAnnotationsCorrectly) {
        throw e;
      }
    }
  }

  @AutoValue
  abstract static class NullablePropertiesNotCopied {
    @AutoValue.CopyAnnotations(exclude = Nullable.class)
    abstract @Nullable String nullableString();

    abstract int randomInt();

    NullablePropertiesNotCopied create(String notNullableAfterAll, int randomInt) {
      return new AutoValue_AutoValueJava8Test_NullablePropertiesNotCopied(
          notNullableAfterAll, randomInt);
    }
  }

  @Test
  public void testExcludedNullablePropertyImplementation() throws NoSuchMethodException {
    Method method =
        AutoValue_AutoValueJava8Test_NullablePropertiesNotCopied.class.getDeclaredMethod(
            "nullableString");
    assertThat(method.getAnnotatedReturnType().getAnnotations())
        .asList()
        .doesNotContain(nullable());
  }

  @Test
  public void testExcludedNullablePropertyConstructorParameter() throws NoSuchMethodException {
    Constructor<?> constructor =
        AutoValue_AutoValueJava8Test_NullablePropertiesNotCopied.class.getDeclaredConstructor(
            String.class, int.class);
    try {
      assertThat(constructor.getAnnotatedParameterTypes()[0].getAnnotations())
          .asList()
          .doesNotContain(nullable());
    } catch (AssertionError e) {
      if (javacHandlesTypeAnnotationsCorrectly) {
        throw e;
      }
    }
  }

  @AutoValue
  abstract static class NullableNonNullable {
    abstract @Nullable String nullableString();

    abstract @Nullable String otherNullableString();

    abstract String nonNullableString();

    static NullableNonNullable create(
        String nullableString, String otherNullableString, String nonNullableString) {
      return new AutoValue_AutoValueJava8Test_NullableNonNullable(
          nullableString, otherNullableString, nonNullableString);
    }
  }

  @Test
  public void testEqualsWithNullable() throws Exception {
    NullableNonNullable everythingNull =
        NullableNonNullable.create(null, null, "nonNullableString");
    NullableNonNullable somethingNull =
        NullableNonNullable.create(null, "otherNullableString", "nonNullableString");
    NullableNonNullable nothingNull =
        NullableNonNullable.create("nullableString", "otherNullableString", "nonNullableString");
    NullableNonNullable nothingNullAgain =
        NullableNonNullable.create("nullableString", "otherNullableString", "nonNullableString");
    new EqualsTester()
        .addEqualityGroup(everythingNull)
        .addEqualityGroup(somethingNull)
        .addEqualityGroup(nothingNull, nothingNullAgain)
        .testEquals();
  }

  public static class Nested {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE_USE)
  public @interface OtherTypeAnnotation {}

  @AutoAnnotation
  public static OtherTypeAnnotation otherTypeAnnotation() {
    return new AutoAnnotation_AutoValueJava8Test_otherTypeAnnotation();
  }

  @AutoValue
  abstract static class NestedNullableProperties {
    abstract @Nullable @OtherTypeAnnotation Nested nullableThing();

    abstract int randomInt();

    static Builder builder() {
      return new AutoValue_AutoValueJava8Test_NestedNullableProperties.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setNullableThing(@Nullable @OtherTypeAnnotation Nested thing);

      abstract Builder setRandomInt(int x);

      abstract NestedNullableProperties build();
    }
  }

  @Test
  public void testNestedNullablePropertiesCanBeNull() {
    NestedNullableProperties instance = NestedNullableProperties.builder().setRandomInt(23).build();
    assertThat(instance.nullableThing()).isNull();
    assertThat(instance.randomInt()).isEqualTo(23);
    assertThat(instance.toString())
        .isEqualTo("NestedNullableProperties{nullableThing=null, randomInt=23}");
  }

  @Test
  public void testNestedNullablePropertiesAreCopied() throws Exception {
    try {
      Method generatedGetter =
          AutoValue_AutoValueJava8Test_NestedNullableProperties.class.getDeclaredMethod(
              "nullableThing");
      Annotation[] getterAnnotations = generatedGetter.getAnnotatedReturnType().getAnnotations();
      assertThat(getterAnnotations).asList().containsAtLeast(nullable(), otherTypeAnnotation());

      Method generatedSetter =
          AutoValue_AutoValueJava8Test_NestedNullableProperties.Builder.class.getDeclaredMethod(
              "setNullableThing", Nested.class);
      Annotation[] setterAnnotations =
          generatedSetter.getAnnotatedParameterTypes()[0].getAnnotations();
      assertThat(setterAnnotations).asList().containsAtLeast(nullable(), otherTypeAnnotation());
    } catch (AssertionError e) {
      if (javacHandlesTypeAnnotationsCorrectly) {
        throw e;
      }
    }
  }

  @AutoValue
  @SuppressWarnings("AutoValueImmutableFields")
  abstract static class PrimitiveArrays {
    @SuppressWarnings("mutable")
    abstract boolean[] booleans();

    @SuppressWarnings("mutable")
    abstract int @Nullable [] ints();

    static PrimitiveArrays create(boolean[] booleans, int[] ints) {
      // Real code would likely clone these parameters, but here we want to check that the
      // generated constructor rejects a null value for booleans.
      return new AutoValue_AutoValueJava8Test_PrimitiveArrays(booleans, ints);
    }
  }

  @Test
  public void testPrimitiveArrays() {
    PrimitiveArrays object0 = PrimitiveArrays.create(new boolean[0], new int[0]);
    boolean[] booleans = {false, true, true, false};
    int[] ints = {6, 28, 496, 8128, 33550336};
    PrimitiveArrays object1 = PrimitiveArrays.create(booleans.clone(), ints.clone());
    PrimitiveArrays object2 = PrimitiveArrays.create(booleans.clone(), ints.clone());
    new EqualsTester().addEqualityGroup(object1, object2).addEqualityGroup(object0).testEquals();
    // EqualsTester also exercises hashCode(). We clone the arrays above to ensure that using the
    // default Object.hashCode() will fail.

    String expectedString =
        "PrimitiveArrays{booleans="
            + Arrays.toString(booleans)
            + ", "
            + "ints="
            + Arrays.toString(ints)
            + "}";
    assertThat(object1.toString()).isEqualTo(expectedString);

    assertThat(object1.ints()).isSameInstanceAs(object1.ints());
  }

  @Test
  public void testNullablePrimitiveArrays() {
    assumeTrue(javacHandlesTypeAnnotationsCorrectly);
    PrimitiveArrays object0 = PrimitiveArrays.create(new boolean[0], null);
    boolean[] booleans = {false, true, true, false};
    PrimitiveArrays object1 = PrimitiveArrays.create(booleans.clone(), null);
    PrimitiveArrays object2 = PrimitiveArrays.create(booleans.clone(), null);
    new EqualsTester().addEqualityGroup(object1, object2).addEqualityGroup(object0).testEquals();

    String expectedString =
        "PrimitiveArrays{booleans=" + Arrays.toString(booleans) + ", " + "ints=null}";
    assertThat(object1.toString()).isEqualTo(expectedString);

    assertThat(object1.booleans()).isSameInstanceAs(object1.booleans());
    assertThat(object1.booleans()).isEqualTo(booleans);
    object1.booleans()[0] ^= true;
    assertThat(object1.booleans()).isNotEqualTo(booleans);
  }

  @Test
  public void testNotNullablePrimitiveArrays() {
    NullPointerException e =
        assertThrows(NullPointerException.class, () -> PrimitiveArrays.create(null, new int[0]));
    assertThat(e).hasMessageThat().contains("booleans");
  }

  @AutoValue
  public abstract static class NullablePropertyWithBuilder {
    public abstract String notNullable();

    public abstract @Nullable String nullable();

    public static Builder builder() {
      return new AutoValue_AutoValueJava8Test_NullablePropertyWithBuilder.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder notNullable(String s);

      Builder nullable(@Nullable String s);

      NullablePropertyWithBuilder build();
    }
  }

  @Test
  public void testOmitNullableWithBuilder() {
    NullablePropertyWithBuilder instance1 =
        NullablePropertyWithBuilder.builder().notNullable("hello").build();
    assertThat(instance1.notNullable()).isEqualTo("hello");
    assertThat(instance1.nullable()).isNull();

    NullablePropertyWithBuilder instance2 =
        NullablePropertyWithBuilder.builder().notNullable("hello").nullable(null).build();
    assertThat(instance2.notNullable()).isEqualTo("hello");
    assertThat(instance2.nullable()).isNull();
    assertThat(instance1).isEqualTo(instance2);

    NullablePropertyWithBuilder instance3 =
        NullablePropertyWithBuilder.builder().notNullable("hello").nullable("world").build();
    assertThat(instance3.notNullable()).isEqualTo("hello");
    assertThat(instance3.nullable()).isEqualTo("world");

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class, () -> NullablePropertyWithBuilder.builder().build());
    assertThat(e).hasMessageThat().contains("notNullable");
  }

  @AutoValue
  public abstract static class OptionalPropertyWithNullableBuilder {
    public abstract String notOptional();

    public abstract Optional<String> optional();

    public static Builder builder() {
      return new AutoValue_AutoValueJava8Test_OptionalPropertyWithNullableBuilder.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder notOptional(String s);

      Builder optional(@Nullable String s);

      OptionalPropertyWithNullableBuilder build();
    }
  }

  @Test
  public void testOmitOptionalWithNullableBuilder() {
    OptionalPropertyWithNullableBuilder instance1 =
        OptionalPropertyWithNullableBuilder.builder().notOptional("hello").build();
    assertThat(instance1.notOptional()).isEqualTo("hello");
    assertThat(instance1.optional()).isEmpty();

    OptionalPropertyWithNullableBuilder instance2 =
        OptionalPropertyWithNullableBuilder.builder().notOptional("hello").optional(null).build();
    assertThat(instance2.notOptional()).isEqualTo("hello");
    assertThat(instance2.optional()).isEmpty();
    assertThat(instance1).isEqualTo(instance2);

    OptionalPropertyWithNullableBuilder instance3 =
        OptionalPropertyWithNullableBuilder.builder()
            .notOptional("hello")
            .optional("world")
            .build();
    assertThat(instance3.notOptional()).isEqualTo("hello");
    assertThat(instance3.optional()).hasValue("world");

    assertThrows(
        IllegalStateException.class, () -> OptionalPropertyWithNullableBuilder.builder().build());
  }

  @AutoValue
  public abstract static class NullableOptionalPropertyWithNullableBuilder {
    public abstract @Nullable Optional<String> optional();

    public static Builder builder() {
      return new AutoValue_AutoValueJava8Test_NullableOptionalPropertyWithNullableBuilder.Builder();
    }

    @AutoValue.Builder
    public interface Builder {
      Builder optional(@Nullable String s);

      NullableOptionalPropertyWithNullableBuilder build();
    }
  }

  @Test
  public void testNullableOptional() {
    NullableOptionalPropertyWithNullableBuilder instance1 =
        NullableOptionalPropertyWithNullableBuilder.builder().build();
    assertThat(instance1.optional()).isNull();

    NullableOptionalPropertyWithNullableBuilder instance2 =
        NullableOptionalPropertyWithNullableBuilder.builder().optional(null).build();
    assertThat(instance2.optional()).isEmpty();

    NullableOptionalPropertyWithNullableBuilder instance3 =
        NullableOptionalPropertyWithNullableBuilder.builder().optional("haruspex").build();
    assertThat(instance3.optional()).hasValue("haruspex");
  }

  @AutoValue
  @SuppressWarnings("AutoValueImmutableFields")
  public abstract static class BuilderWithUnprefixedGetters<T extends Comparable<T>> {
    public abstract ImmutableList<T> list();

    public abstract @Nullable T t();

    @SuppressWarnings("mutable")
    public abstract int[] ints();

    public abstract int noGetter();

    public static <T extends Comparable<T>> Builder<T> builder() {
      return new AutoValue_AutoValueJava8Test_BuilderWithUnprefixedGetters.Builder<T>();
    }

    @AutoValue.Builder
    public interface Builder<T extends Comparable<T>> {
      Builder<T> setList(ImmutableList<T> list);

      Builder<T> setT(T t);

      Builder<T> setInts(int[] ints);

      Builder<T> setNoGetter(int x);

      ImmutableList<T> list();

      T t();

      int[] ints();

      BuilderWithUnprefixedGetters<T> build();
    }
  }

  @AutoValue
  abstract static class NoNullableRef {
    abstract String foo();

    static NoNullableRef of(String foo) {
      return new AutoValue_AutoValueJava8Test_NoNullableRef(foo);
    }
  }

  // Tests that we generate equals(@Nullable x) using JSpecify @Nullable if that annotation is
  // available and there is no other @Nullable type annotation mentioned in the @AutoValue class.
  // If there *are* other @Nullable type annotations, other test methods here will check that they
  // are used instead.
  @Test
  public void testDefaultToJSpecifyNullable() throws ReflectiveOperationException {
    Class<? extends Annotation> jspecifyNullable;
    try {
      // We write this using .concat in order to hide it from rewriting rules.
      jspecifyNullable =
          Class.forName("org".concat(".jspecify.nullness.Nullable")).asSubclass(Annotation.class);
    } catch (ClassNotFoundException e) {
      throw new AssumptionViolatedException("No JSpecify @Nullable available", e);
    }
    Class<? extends NoNullableRef> autoValueImpl = NoNullableRef.of("foo").getClass();
    Method equals = autoValueImpl.getDeclaredMethod("equals", Object.class);
    assertThat(equals.getAnnotatedParameterTypes()[0].isAnnotationPresent(jspecifyNullable))
        .isTrue();
  }

  @Test
  public void testBuilderWithUnprefixedGetter() {
    assumeTrue(javacHandlesTypeAnnotationsCorrectly);
    ImmutableList<String> names = ImmutableList.of("fred", "jim");
    int[] ints = {6, 28, 496, 8128, 33550336};
    int noGetter = -1;

    BuilderWithUnprefixedGetters.Builder<String> builder = BuilderWithUnprefixedGetters.builder();
    assertThat(builder.t()).isNull();
    IllegalStateException e1 = assertThrows(IllegalStateException.class, () -> builder.list());
    assertThat(e1).hasMessageThat().isEqualTo("Property \"list\" has not been set");
    IllegalStateException e2 = assertThrows(IllegalStateException.class, () -> builder.ints());
    assertThat(e2).hasMessageThat().isEqualTo("Property \"ints\" has not been set");

    builder.setList(names);
    assertThat(builder.list()).isSameInstanceAs(names);
    builder.setInts(ints);
    assertThat(builder.ints()).isEqualTo(ints);
    // The array is not cloned by the getter, so the client can modify it (but shouldn't).
    ints[0] = 0;
    assertThat(builder.ints()[0]).isEqualTo(0);
    ints[0] = 6;

    BuilderWithUnprefixedGetters<String> instance = builder.setNoGetter(noGetter).build();
    assertThat(instance.list()).isSameInstanceAs(names);
    assertThat(instance.t()).isNull();
    assertThat(instance.ints()).isEqualTo(ints);
    assertThat(instance.noGetter()).isEqualTo(noGetter);
  }

  @AutoValue
  @SuppressWarnings("AutoValueImmutableFields")
  public abstract static class BuilderWithPrefixedGetters<T extends Comparable<T>> {
    public abstract ImmutableList<T> getList();

    public abstract @Nullable T getT();

    @SuppressWarnings("mutable")
    public abstract int @Nullable [] getInts();

    public abstract int getNoGetter();

    public static <T extends Comparable<T>> Builder<T> builder() {
      return new AutoValue_AutoValueJava8Test_BuilderWithPrefixedGetters.Builder<T>();
    }

    @AutoValue.Builder
    public abstract static class Builder<T extends Comparable<T>> {
      public abstract Builder<T> setList(ImmutableList<T> list);

      public abstract Builder<T> setT(@Nullable T t);

      public abstract Builder<T> setInts(int[] ints);

      public abstract Builder<T> setNoGetter(int x);

      abstract ImmutableList<T> getList();

      abstract T getT();

      abstract int[] getInts();

      public abstract BuilderWithPrefixedGetters<T> build();
    }
  }

  @Test
  public void testBuilderWithPrefixedGetter() {
    assumeTrue(javacHandlesTypeAnnotationsCorrectly);
    ImmutableList<String> names = ImmutableList.of("fred", "jim");
    String name = "sheila";
    int noGetter = -1;

    BuilderWithPrefixedGetters.Builder<String> builder = BuilderWithPrefixedGetters.builder();
    assertThat(builder.getInts()).isNull();
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> builder.getList());
    assertThat(e).hasMessageThat().isEqualTo("Property \"list\" has not been set");

    builder.setList(names);
    assertThat(builder.getList()).isSameInstanceAs(names);
    builder.setT(name);
    assertThat(builder.getInts()).isNull();

    BuilderWithPrefixedGetters<String> instance = builder.setNoGetter(noGetter).build();
    assertThat(instance.getList()).isSameInstanceAs(names);
    assertThat(instance.getT()).isEqualTo(name);
    assertThat(instance.getInts()).isNull();
    assertThat(instance.getNoGetter()).isEqualTo(noGetter);
  }

  // This class tests the case where an annotation is both a method annotation and a type
  // annotation. If we weren't careful, we might emit it twice in the generated code.
  @AutoValue
  abstract static class FunkyNullable {
    @Target({ElementType.METHOD, ElementType.TYPE_USE})
    @interface Nullable {}

    abstract @Nullable String foo();

    abstract Optional<String> bar();

    static Builder builder() {
      return new AutoValue_AutoValueJava8Test_FunkyNullable.Builder();
    }

    @AutoValue.Builder
    interface Builder {
      Builder setFoo(@Nullable String foo);

      Builder setBar(@Nullable String bar);

      FunkyNullable build();
    }
  }

  @Test
  public void testFunkyNullable() {
    FunkyNullable explicitNull = FunkyNullable.builder().setFoo(null).setBar(null).build();
    FunkyNullable implicitNull = FunkyNullable.builder().build();
    assertThat(explicitNull).isEqualTo(implicitNull);
  }

  @AutoValue
  abstract static class EqualsNullable {
    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Nullable {}

    abstract String foo();

    static EqualsNullable create(String foo) {
      return new AutoValue_AutoValueJava8Test_EqualsNullable(foo);
    }

    @Override
    public abstract boolean equals(@Nullable Object x);

    @Override
    public abstract int hashCode();
  }

  /**
   * Tests that a type annotation on the parameter of {@code equals(Object)} is copied into the
   * implementation class.
   */
  @Test
  public void testEqualsNullable() throws ReflectiveOperationException {
    EqualsNullable x = EqualsNullable.create("foo");
    Class<? extends EqualsNullable> implClass = x.getClass();
    Method equals = implClass.getDeclaredMethod("equals", Object.class);
    AnnotatedType[] parameterTypes = equals.getAnnotatedParameterTypes();
    assertThat(parameterTypes[0].isAnnotationPresent(EqualsNullable.Nullable.class)).isTrue();
  }

  @AutoValue
  abstract static class AnnotatedTypeParameter<@Nullable T> {
    abstract @Nullable T thing();

    static <@Nullable T> AnnotatedTypeParameter<T> create(T thing) {
      return new AutoValue_AutoValueJava8Test_AnnotatedTypeParameter<T>(thing);
    }
  }

  /**
   * Tests that an annotation on a type parameter of an {@code @AutoValue} class is copied to the
   * implementation class.
   */
  @Test
  public void testTypeAnnotationCopiedToImplementation() {
    @Nullable String nullableString = "blibby";
    AnnotatedTypeParameter<@Nullable String> x = AnnotatedTypeParameter.create(nullableString);
    Class<?> c = x.getClass();
    assertThat(c.getTypeParameters()).hasLength(1);
    TypeVariable<?> typeParameter = c.getTypeParameters()[0];
    assertWithMessage(typeParameter.toString())
        .that(typeParameter.getAnnotations())
        .asList()
        .contains(nullable());
  }

  @AutoValue
  abstract static class AnnotatedTypeParameterWithBuilder<@Nullable T> {
    abstract @Nullable T thing();

    static <@Nullable T> Builder<T> builder() {
      return new AutoValue_AutoValueJava8Test_AnnotatedTypeParameterWithBuilder.Builder<T>();
    }

    @AutoValue.Builder
    abstract static class Builder<@Nullable T> {
      abstract Builder<T> setThing(T thing);

      abstract AnnotatedTypeParameterWithBuilder<T> build();
    }
  }

  /**
   * Tests that an annotation on a type parameter of an {@code @AutoValue} builder is copied to the
   * implementation class.
   */
  @Test
  public void testTypeAnnotationOnBuilderCopiedToImplementation() {
    AnnotatedTypeParameterWithBuilder.Builder<@Nullable String> builder =
        AnnotatedTypeParameterWithBuilder.builder();
    Class<?> c = builder.getClass();
    assertThat(c.getTypeParameters()).hasLength(1);
    TypeVariable<?> typeParameter = c.getTypeParameters()[0];
    assertWithMessage(typeParameter.toString())
        .that(typeParameter.getAnnotations())
        .asList()
        .contains(nullable());
  }

  // b/127701294
  @AutoValue
  abstract static class OptionalOptional {
    abstract Optional<Optional<String>> maybeJustMaybe();

    static Builder builder() {
      return new AutoValue_AutoValueJava8Test_OptionalOptional.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder maybeJustMaybe(Optional<String> maybe);

      abstract OptionalOptional build();
    }
  }

  @Test
  public void testOptionalOptional_empty() {
    OptionalOptional empty = OptionalOptional.builder().build();
    assertThat(empty.maybeJustMaybe()).isEmpty();
  }

  @Test
  public void testOptionalOptional_ofEmpty() {
    OptionalOptional ofEmpty = OptionalOptional.builder().maybeJustMaybe(Optional.empty()).build();
    assertThat(ofEmpty.maybeJustMaybe()).hasValue(Optional.empty());
  }

  @Test
  public void testOptionalOptional_ofSomething() {
    OptionalOptional ofSomething =
        OptionalOptional.builder().maybeJustMaybe(Optional.of("foo")).build();
    assertThat(ofSomething.maybeJustMaybe()).hasValue(Optional.of("foo"));
  }

  @AutoValue
  abstract static class OptionalExtends {
    abstract Optional<? extends Predicate<? super Integer>> predicate();

    static Builder builder() {
      return new AutoValue_AutoValueJava8Test_OptionalExtends.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setPredicate(Predicate<? super Integer> predicate);

      abstract OptionalExtends build();
    }
  }

  @Test
  public void testOptionalExtends() {
    Predicate<Number> predicate = n -> n.toString().equals("0");
    OptionalExtends t = OptionalExtends.builder().setPredicate(predicate).build();
    assertThat(t.predicate()).hasValue(predicate);
  }
}
