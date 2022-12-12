/*
 * Copyright 2021 Google LLC
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

import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static java.util.stream.Collectors.partitioningBy;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Expect;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NullablesTest {
  @Rule public Expect expect = Expect.create();

  @Target(ElementType.TYPE_USE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Nullable {}

  @Target(ElementType.TYPE_USE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Irrelevant {}

  // The class here has various methods that we will examine with
  // Nullables.nullableMentionedInMethods to ensure that we do indeed detect @Nullable annotations
  // in various contexts.
  // This test is a lot more complicated than it should be. Ideally we would just have this Methods
  // class be an actual class nested inside this test, and we would use CompilationRule to get
  // the corresponding TypeElement so we could check the various methods. Unfortunately, if we
  // do that then we get a TypeElement where all type annotations have disappeared because of
  // https://bugs.openjdk.java.net/browse/JDK-8225377. So instead we have to use Compiler to compile
  // the code here with a special annotation processor that will check the annotations of the
  // just-compiled class. Since the processor is running as part of the same compilation, we don't
  // lose the type annotations.

  private static final ImmutableList<String> METHOD_LINES =
      ImmutableList.of(
          // Methods in this class whose names begin with "no" do not mention @Nullable anywhere.",
          // All other methods do.",
          "package foo.bar;",
          "",
          "import " + Irrelevant.class.getCanonicalName() + ";",
          "import " + Nullable.class.getCanonicalName() + ";",
          "import java.util.List;",
          "",
          "abstract class Methods {",
          "  void noAnnotations() {}",
          "  abstract int noAnnotationsEither(int x);",
          "  abstract @Irrelevant String noRelevantAnnotations(@Irrelevant int x);",
          "  abstract @Nullable String nullableString();",
          "  abstract String @Nullable [] nullableArrayOfString();",
          "  abstract @Nullable String[] arrayOfNullableString();",
          "  abstract @Nullable String @Nullable [] nullableArrayOfNullableString();",
          "  abstract List<@Nullable String> listOfNullableString();",
          "  abstract List<? extends @Nullable Object> listOfExtendsNullable();",
          "  abstract List<? super @Nullable Number> listOfSuperNullable();",
          "  abstract <T extends @Nullable Object> T nullableTypeParamBound();",
          "  abstract <T> @Nullable T nullableTypeParamRef();",
          "  void nullableParam(@Nullable String x) {}",
          "  void nullableParamBound(List<? extends @Nullable String> x) {}",
          "}");

  @Test
  public void nullableMentionedInMethods() {
    // Sadly we can't rely on JDK 8 to handle type annotations correctly.
    // Some versions do, some don't. So skip the test unless we are on at least JDK 9.
    double javaVersion = Double.parseDouble(JAVA_SPECIFICATION_VERSION.value());
    assume().that(javaVersion).isAtLeast(9.0);
    NullableProcessor processor = new NullableProcessor(expect);
    Compilation compilation =
        Compiler.javac()
            .withProcessors(processor)
            .compile(JavaFileObjects.forSourceLines("foo.bar.Methods", METHOD_LINES));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(processor.ran).isTrue();
    // If any `expect` calls failed then the test will fail now because of the Expect rule.
  }

  @SupportedAnnotationTypes("*")
  private static class NullableProcessor extends AbstractProcessor {

    private final Expect expect;
    boolean ran;

    NullableProcessor(Expect expect) {
      this.expect = expect;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (roundEnv.processingOver()) {
        TypeElement methodsElement =
            processingEnv.getElementUtils().getTypeElement("foo.bar.Methods");
        expect.that(methodsElement).isNotNull();

        List<ExecutableElement> methods =
            ElementFilter.methodsIn(methodsElement.getEnclosedElements());
        Map<Boolean, List<ExecutableElement>> partitionedMethods =
            methods.stream()
                .collect(partitioningBy(p -> !p.getSimpleName().toString().startsWith("no")));
        List<ExecutableElement> nullableMethods = partitionedMethods.get(true);
        List<ExecutableElement> notNullableMethods = partitionedMethods.get(false);

        expect
            .that(Nullables.fromMethods(null, notNullableMethods).nullableTypeAnnotations())
            .isEmpty();

        TypeElement nullableElement =
            processingEnv.getElementUtils().getTypeElement(Nullable.class.getCanonicalName());
        expect.that(nullableElement).isNotNull();
        DeclaredType nullableType = MoreTypes.asDeclared(nullableElement.asType());

        for (ExecutableElement nullableMethod : nullableMethods) {
          // Make a list with all the methods that don't have @Nullable plus one method that does.
          ImmutableList<ExecutableElement> notNullablePlusNullable =
              ImmutableList.<ExecutableElement>builder()
                  .addAll(notNullableMethods)
                  .add(nullableMethod)
                  .build();
          expect
              .withMessage("method %s should have @Nullable", nullableMethod)
              .that(
                  Nullables.fromMethods(null, notNullablePlusNullable)
                      .nullableTypeAnnotations()
                      .stream()
                      .map(AnnotationMirror::getAnnotationType)
                      .collect(toImmutableList()))
              .containsExactly(nullableType);
        }
        ran = true;
      }
      return false;
    }
  }
}
