package com.google.auto.value.processor;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

import com.google.common.reflect.Reflection;
import com.google.testing.compile.JavaFileObjects;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Generated;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;

/**
 * Tests that {@link AutoValueProcessor} works even if run in a context where the
 * {@code @Generated} annotation does not exist.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class GeneratedDoesNotExistTest {
  private static class ElementsInvocationHandler implements InvocationHandler {
    private final Elements realElements;
    private final AtomicBoolean ignoredGenerated;

    ElementsInvocationHandler(Elements realElements, AtomicBoolean ignoredGenerated) {
      this.realElements = realElements;
      this.ignoredGenerated = ignoredGenerated;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getName().equals("getTypeElement")) {
        assertThat(args).hasLength(1);
        return getTypeElement((CharSequence) args[0]);
      } else {
        return method.invoke(realElements, args);
      }
    }

    private TypeElement getTypeElement(CharSequence name) {
      if (name.toString().equals(Generated.class.getName())) {
        ignoredGenerated.set(true);
        return null;
      } else {
        return realElements.getTypeElement(name);
      }
    }
  }

  private static class ProcessingEnvironmentInvocationHandler implements InvocationHandler {
    private final ProcessingEnvironment realProcessingEnvironment;
    private final Elements elements;

    ProcessingEnvironmentInvocationHandler(
        ProcessingEnvironment realProcessingEnvironment, AtomicBoolean ignoredGenerated) {
      this.realProcessingEnvironment = realProcessingEnvironment;
      InvocationHandler elementsInvocationHandler = new ElementsInvocationHandler(
          realProcessingEnvironment.getElementUtils(), ignoredGenerated);
      this.elements = Reflection.newProxy(Elements.class, elementsInvocationHandler);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getName().equals("getElementUtils")) {
        // Remember that the stupid InvocationHandler API passes null instead of an empty array.
        assertThat(args).isNull();
        return elements;
      } else {
        return method.invoke(realProcessingEnvironment, args);
      }
    }
  }

  private static class NoGeneratedProcessor extends AbstractProcessor {
    private final Processor realProcessor;
    private final AtomicBoolean ignoredGenerated;

    NoGeneratedProcessor(Processor realProcessor, AtomicBoolean ignoredGenerated) {
      this.realProcessor = realProcessor;
      this.ignoredGenerated = ignoredGenerated;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      return realProcessor.process(annotations, roundEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return realProcessor.getSupportedAnnotationTypes();
    }

    @Override
    public Set<String> getSupportedOptions() {
      return realProcessor.getSupportedOptions();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return realProcessor.getSupportedSourceVersion();
    }

    @Override
    public void init(ProcessingEnvironment realProcessingEnvironment) {
      InvocationHandler processingEnvironmentInvocationHandler =
          new ProcessingEnvironmentInvocationHandler(realProcessingEnvironment, ignoredGenerated);
      ProcessingEnvironment processingEnvironment = Reflection.newProxy(
          ProcessingEnvironment.class, processingEnvironmentInvocationHandler);
      realProcessor.init(processingEnvironment);
    }
  }

  @Test
  public void test() {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
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
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
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
        "    int h = 1;",
        "    return h;",
        "  }",
        "}"
    );
    AtomicBoolean ignoredGenerated = new AtomicBoolean();
    Processor autoValueProcessor = new AutoValueProcessor();
    Processor noGeneratedProcessor = new NoGeneratedProcessor(autoValueProcessor, ignoredGenerated);
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(noGeneratedProcessor)
        .compilesWithoutError()
        .and()
        .generatesSources(expectedOutput);
    assertThat(ignoredGenerated.get()).isTrue();
  }
}
