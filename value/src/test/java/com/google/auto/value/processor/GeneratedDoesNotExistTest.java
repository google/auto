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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
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
  // The classes here are basically just rigmarole to ensure that
  // Types.getTypeElement("javax.annotation.Generated") returns null, and to check that something
  // called that. We want a Processor that forwards everything to AutoValueProcessor, except that
  // the init(ProcessingEnvironment) method should forward a ProcessingEnvironment that filters
  // out the Generated class. So that ProcessingEnvironment forwards everything to the real
  // ProcessingEnvironment, except the ProcessingEnvironment.getElementUtils() method. That method
  // returns an Elements object that forwards everything to the real Elements except
  // getTypeElement("javax.annotation.Generated").

  /**
   * InvocationHandler that forwards every method to an original object, except methods where
   * there is an implementation in this class with the same signature. So for example in the
   * subclass {@link ElementsHandler} there is a method
   * {@link ElementsHandler#getTypeElement(CharSequence)}, which means that a call of
   * {@link Elements#getTypeElement(CharSequence)} on the proxy with this invocation handler will
   * end up calling that method, but a call of any of the other methods of {@code Elements} will
   * end up calling the method on the original object.
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
    private final AtomicBoolean ignoredGenerated;

    ElementsHandler(Elements original, AtomicBoolean ignoredGenerated) {
      super(original);
      this.ignoredGenerated = ignoredGenerated;
    }

    public TypeElement getTypeElement(CharSequence name) {
      if (name.toString().equals(Generated.class.getName())) {
        ignoredGenerated.set(true);
        return null;
      } else {
        return original.getTypeElement(name);
      }
    }
  }

  private static class ProcessingEnvironmentHandler
      extends OverridableInvocationHandler<ProcessingEnvironment> {
    private final Elements noGeneratedElements;

    ProcessingEnvironmentHandler(
        ProcessingEnvironment original, AtomicBoolean ignoredGenerated) {
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
    private final AtomicBoolean ignoredGenerated;

    ProcessorHandler(Processor original, AtomicBoolean ignoredGenerated) {
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
    ProcessorHandler handler = new ProcessorHandler(autoValueProcessor, ignoredGenerated);
    Processor noGeneratedProcessor = partialProxy(Processor.class, handler);
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(noGeneratedProcessor)
        .compilesWithoutError()
        .and()
        .generatesSources(expectedOutput);
    assertThat(ignoredGenerated.get()).isTrue();
  }
}
