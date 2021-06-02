/*
 * Copyright 2017 Google LLC
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

import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static javax.lang.model.util.ElementFilter.fieldsIn;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Expect;
import com.google.testing.compile.JavaFileObjects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This test verifies the method {@link TypeEncoder#encodeWithAnnotations(TypeMirror).
 * It takes a list of "type spellings", like {@code @Nullable String}, and compiles a class
 * with one field for each spelling. So there might be a field {@code @Nullable String x2;}.
 * Then it examines each compiled field to extract its {@code TypeMirror}, and uses the
 * {@code TypeSimplifier} method to reconvert that into a string. It should get back the same
 * type spelling in each case.
 *
 * <p>I originally tried to write a less convoluted test using compile-testing. In my test,
 * each type to be tested was an actual type in the test class (the type of a field, or the
 * return type of a method). However, I found that if I examined these types by looking up a class
 * with {@link javax.lang.model.util.Elements#getTypeElement} and following through to the type
 * of interest, it never had any type annotations.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class SimplifyWithAnnotationsTest {
  @Rule public final Expect expect = Expect.create();

  /**
   * The types that we will compile and then recreate. They are referenced in a context where {@code
   * Set} is unambiguous but not {@code List}, which allows us to test the placement of annotations
   * in unqualified types like {@code Set<T>} and qualified types like {@code java.util.List<T>}.
   */
  private static final ImmutableList<String> TYPE_SPELLINGS =
      ImmutableList.of(
          "Object",
          "Set",
          "String",
          "Nullable",
          "@Nullable String",
          "String[]",
          "@Nullable String[]",
          "String @Nullable []",
          "String @Nullable [] @Nullable []",
          "java.awt.List",
          "java.util.List<String>",
          "Set<@Nullable String>",
          "@Nullable Set<String>",
          "int",
          "@Nullable int", // whatever that might mean
          "@Nullable int[]",
          "int @Nullable []",
          "T",
          "@Nullable T",
          "Set<@Nullable T>",
          "Set<? extends @Nullable T>",
          "Set<? extends @Nullable String>",
          "Set<? extends @Nullable String @Nullable []>",
          "java.util.@Nullable List<@Nullable T>",
          "java.util.@Nullable List<java.util.@Nullable List<T>>");

  private static final JavaFileObject NULLABLE_FILE_OBJECT =
      JavaFileObjects.forSourceLines(
          "pkg.Nullable",
          "package pkg;",
          "",
          "import java.lang.annotation.ElementType;",
          "import java.lang.annotation.Target;",
          "",
          "@Target(ElementType.TYPE_USE)",
          "public @interface Nullable {}");

  private static final JavaFileObject TEST_CLASS_FILE_OBJECT =
      JavaFileObjects.forSourceLines("pkg.TestClass", buildTestClass());

  private static ImmutableList<String> buildTestClass() {
    // Some older versions of javac don't handle type annotations at all well in annotation
    // processors. The `witness` method in the generated class is there to detect that, and
    // skip the test if it is the case.
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    builder.add(
        "package pkg;",
        "",
        "import java.util.Set;",
        "",
        "public abstract class TestClass<T> {",
        "  abstract @Nullable T witness();");
    int i = 0;
    for (String typeSpelling : TYPE_SPELLINGS) {
      builder.add(String.format("  %s x%d;\n", typeSpelling, i++));
    }
    builder.add("}");
    return builder.build();
  }

  @Test
  public void testSimplifyWithAnnotations() {
    // The real test happens inside the .compile(...), by virtue of TestProcessor.
    assertThat(
            javac()
                .withOptions("-proc:only")
                .withProcessors(new TestProcessor())
                .compile(NULLABLE_FILE_OBJECT, TEST_CLASS_FILE_OBJECT))
        .succeededWithoutWarnings();
  }

  @SupportedAnnotationTypes("*")
  private static class TestProcessor extends AbstractProcessor {
    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (roundEnv.processingOver()) {
        TypeElement testClass = processingEnv.getElementUtils().getTypeElement("pkg.TestClass");
        testTypeSpellings(testClass);
      }
      return false;
    }

    void testTypeSpellings(TypeElement testClass) {
      ExecutableElement witness =
          ElementFilter.methodsIn(testClass.getEnclosedElements()).stream()
              .filter(m -> m.getSimpleName().contentEquals("witness"))
              .collect(onlyElement());
      if (witness.getReturnType().getAnnotationMirrors().isEmpty()) {
        System.err.println("SKIPPING TEST BECAUSE OF BUGGY COMPILER");
        return;
      }
      ImmutableMap<String, TypeMirror> typeSpellingToType = typesFromTestClass(testClass);
      assertThat(typeSpellingToType).isNotEmpty();
      StringBuilder text = new StringBuilder();
      StringBuilder expected = new StringBuilder();
      // Build up a fake source text with the encodings for the types in it, and decode it to
      // ensure the type spellings are what we expect.
      typeSpellingToType.forEach(
          (typeSpelling, type) -> {
            text.append("{").append(TypeEncoder.encodeWithAnnotations(type)).append("}");
            expected.append("{").append(typeSpelling).append("}");
          });
      String decoded = TypeEncoder.decode(text.toString(), processingEnv, "pkg", null);
      assertThat(decoded).isEqualTo(expected.toString());
    }

    private static ImmutableMap<String, TypeMirror> typesFromTestClass(TypeElement type) {
      // Reads the types of the fields from the compiled TestClass and uses them to produce
      // a map from type spellings to types. This method depends on type.getEnclosedElements()
      // returning the fields in source order, which it is specified to do.
      ImmutableMap.Builder<String, TypeMirror> typeSpellingToTypeBuilder = ImmutableMap.builder();
      int i = 0;
      for (VariableElement field : fieldsIn(type.getEnclosedElements())) {
        String spelling = TYPE_SPELLINGS.get(i);
        typeSpellingToTypeBuilder.put(spelling, field.asType());
        i++;
      }
      return typeSpellingToTypeBuilder.build();
    }
  }
}
