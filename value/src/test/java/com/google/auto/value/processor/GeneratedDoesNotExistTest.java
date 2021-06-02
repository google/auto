/*
 * Copyright 2015 Google LLC
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
import static com.google.testing.compile.Compiler.javac;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.Reflection;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests that {@link AutoValueProcessor} works even if run in a context where the {@code @Generated}
 * annotation does not exist.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(Parameterized.class)
public class GeneratedDoesNotExistTest {
  private static final ImmutableList<String> STANDARD_OPTIONS =
      ImmutableList.of("-A" + Nullables.NULLABLE_OPTION + "=");

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    ImmutableList.Builder<Object[]> params = ImmutableList.builder();
    if (SourceVersion.latestSupported().compareTo(SourceVersion.RELEASE_8) > 0) {
      // use default options when running on JDK > 8
      // TODO(b/72513371): use --release 8 once compile-testing supports that
      params.add(
          new Object[] {
            STANDARD_OPTIONS, "javax.annotation.processing.Generated",
          });
    }
    params.add(
        new Object[] {
          ImmutableList.<String>builder()
              .addAll(STANDARD_OPTIONS)
              .add("-source", "8", "-target", "8")
              .build(),
          "javax.annotation.Generated",
        });
    return params.build();
  }

  private final ImmutableList<String> javacOptions;
  private final String expectedAnnotation;

  public GeneratedDoesNotExistTest(ImmutableList<String> javacOptions, String expectedAnnotation) {
    this.javacOptions = javacOptions;
    this.expectedAnnotation = expectedAnnotation;
  }

  // The classes here are basically just rigmarole to ensure that
  // Types.getTypeElement("javax.annotation.Generated") returns null, and to check that something
  // called that. We want a Processor that forwards everything to AutoValueProcessor, except that
  // the init(ProcessingEnvironment) method should forward a ProcessingEnvironment that filters
  // out the Generated class. So that ProcessingEnvironment forwards everything to the real
  // ProcessingEnvironment, except the ProcessingEnvironment.getElementUtils() method. That method
  // returns an Elements object that forwards everything to the real Elements except
  // getTypeElement("javax.annotation.Generated") and
  // getTypeElement("javax.annotation.processing.Generated").

  private static final ImmutableSet<String> GENERATED_ANNOTATIONS =
      ImmutableSet.of("javax.annotation.Generated", "javax.annotation.processing.Generated");

  /**
   * InvocationHandler that forwards every method to an original object, except methods where there
   * is an implementation in this class with the same signature. So for example in the subclass
   * {@link ElementsHandler} there is a method {@link ElementsHandler#getTypeElement(CharSequence)},
   * which means that a call of {@link Elements#getTypeElement(CharSequence)} on the proxy with this
   * invocation handler will end up calling that method, but a call of any of the other methods of
   * {@code Elements} will end up calling the method on the original object.
   */
  private abstract static class OverridableInvocationHandler<T> implements InvocationHandler {
    final T original;

    OverridableInvocationHandler(T original) {
      this.original = original;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      try {
        Method override = getClass().getMethod(method.getName(), method.getParameterTypes());
        if (override.getDeclaringClass() == getClass()) {
          return override.invoke(this, args);
        }
      } catch (NoSuchMethodException ignored) {
        // OK: we don't have an override for this method, so just invoke the original method.
      }
      return method.invoke(original, args);
    }
  }

  private static <T> T partialProxy(Class<T> type, OverridableInvocationHandler<T> handler) {
    return Reflection.newProxy(type, handler);
  }

  private static class ElementsHandler extends OverridableInvocationHandler<Elements> {

    private final Set<String> ignoredGenerated;

    ElementsHandler(Elements original, Set<String> ignoredGenerated) {
      super(original);
      this.ignoredGenerated = ignoredGenerated;
    }

    public TypeElement getTypeElement(CharSequence name) {
      if (GENERATED_ANNOTATIONS.contains(name.toString())) {
        ignoredGenerated.add(name.toString());
        return null;
      } else {
        return original.getTypeElement(name);
      }
    }
  }

  private static class ProcessingEnvironmentHandler
      extends OverridableInvocationHandler<ProcessingEnvironment> {
    private final Elements noGeneratedElements;

    ProcessingEnvironmentHandler(ProcessingEnvironment original, Set<String> ignoredGenerated) {
      super(original);
      ElementsHandler elementsHandler =
          new ElementsHandler(original.getElementUtils(), ignoredGenerated);
      this.noGeneratedElements = partialProxy(Elements.class, elementsHandler);
    }

    public Elements getElementUtils() {
      return noGeneratedElements;
    }
  }

  private static class ProcessorHandler extends OverridableInvocationHandler<Processor> {
    private final Set<String> ignoredGenerated;

    ProcessorHandler(Processor original, Set<String> ignoredGenerated) {
      super(original);
      this.ignoredGenerated = ignoredGenerated;
    }

    public void init(ProcessingEnvironment processingEnv) {
      ProcessingEnvironmentHandler processingEnvironmentHandler =
          new ProcessingEnvironmentHandler(processingEnv, ignoredGenerated);
      ProcessingEnvironment noGeneratedProcessingEnvironment =
          partialProxy(ProcessingEnvironment.class, processingEnvironmentHandler);
      original.init(noGeneratedProcessingEnvironment);
    }
  }

  @Test
  public void test() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  public static Baz create() {",
            "    return new AutoValue_Baz();",
            "  }",
            "}");
    JavaFileObject expectedOutput =
        JavaFileObjects.forSourceLines(
            "foo.bar.AutoValue_Baz",
            "package foo.bar;",
            "",
            "final class AutoValue_Baz extends Baz {",
            "  AutoValue_Baz() {",
            "  }",
            "",
            "  @Override public String toString() {",
            "    return \"Baz{\"",
            "        + \"}\";",
            "  }",
            "",
            "  @Override public boolean equals(Object o) {",
            "    if (o == this) {",
            "      return true;",
            "    }",
            "    if (o instanceof Baz) {",
            "      return true;",
            "    }",
            "    return false;",
            "  }",
            "",
            "  @Override public int hashCode() {",
            "    int h$ = 1;",
            "    return h$;",
            "  }",
            "}");
    Set<String> ignoredGenerated = ConcurrentHashMap.newKeySet();
    Processor autoValueProcessor = new AutoValueProcessor();
    ProcessorHandler handler = new ProcessorHandler(autoValueProcessor, ignoredGenerated);
    Processor noGeneratedProcessor = partialProxy(Processor.class, handler);
    Compilation compilation =
        javac()
            .withOptions(javacOptions)
            .withProcessors(noGeneratedProcessor)
            .compile(javaFileObject);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Baz")
        .hasSourceEquivalentTo(expectedOutput);
    assertThat(ignoredGenerated).containsExactly(expectedAnnotation);
  }
}
