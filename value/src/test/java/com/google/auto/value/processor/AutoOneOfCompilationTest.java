/*
 * Copyright (C) 2018 Google, Inc.
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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.common.truth.Expect;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author emcmanus@google.com (Éamonn McManus) */
@RunWith(JUnit4.class)
public class AutoOneOfCompilationTest {
  @Rule public final Expect expect = Expect.create();

  @Test
  public void success() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.TaskResult",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoOneOf;",
            "import java.io.Serializable;",
            "",
            "@AutoOneOf(TaskResult.Kind.class)",
            "public abstract class TaskResult<V, T extends Throwable> {",
            "  public enum Kind {VALUE, EXCEPTION}",
            "  public abstract Kind getKind();",
            "",
            "  public abstract V value();",
            "  public abstract Throwable exception();",
            "",
            "  public static <V> TaskResult<V, ?> value(V value) {",
            "    return AutoOneOf_TaskResult.value(value);",
            "  }",
            "",
            "  public static <T extends Throwable> TaskResult<?, T> exception(T exception) {",
            "    return AutoOneOf_TaskResult.exception(exception);",
            "  }",
            "}");
    JavaFileObject expectedOutput =
        JavaFileObjects.forSourceLines(
            "foo.bar.AutoOneOf_TaskResult",
            "package foo.bar;",
            "",
            GeneratedImport.importGeneratedAnnotationType(),
            "",
            "@Generated(\"com.google.auto.value.processor.AutoOneOfProcessor\")",
            "final class AutoOneOf_TaskResult {",
            "  private AutoOneOf_TaskResult() {} // There are no instances of this type.",
            "",
            "  static <V, T extends Throwable> TaskResult<V, T> value(V value) {",
            "    value.getClass();",
            "    return new Impl_value<V, T>(value);",
            "  }",
            "",
            "  static <V, T extends Throwable> TaskResult<V, T> exception(Throwable exception) {",
            "    exception.getClass();",
            "    return new Impl_exception<V, T>(exception);",
            "  }",
            "",
            "  // Parent class that each implementation will inherit from.",
            "  private abstract static class Parent_<V, T extends Throwable> "
                + "extends TaskResult<V, T> {",
            "    @Override",
            "    public V value() {",
            "      throw new UnsupportedOperationException(getKind().toString());",
            "    }",
            "",
            "    @Override",
            "    public Throwable exception() {",
            "      throw new UnsupportedOperationException(getKind().toString());",
            "    }",
            "  }",
            "",
            "  // Implementation when the contained property is \"value\".",
            "  private static final class Impl_value<V, T extends Throwable> "
                + "extends Parent_<V, T> {",
            "    private final V value;",
            "",
            "    Impl_value(V value) {",
            "      this.value = value;",
            "    }",
            "",
            "    @Override",
            "    public TaskResult.Kind getKind() {",
            "      return TaskResult.Kind.VALUE;",
            "    }",
            "",
            "    @Override",
            "    public V value() {",
            "      return value;",
            "    }",
            "",
            "    @Override",
            "    public String toString() {",
            "      return \"TaskResult{value=\" + this.value + \"}\";",
            "    }",
            "",
            "    @Override",
            "    public boolean equals(Object x) {",
            "      if (x instanceof TaskResult) {",
            "        TaskResult<?, ?> that = (TaskResult<?, ?>) x;",
            "        return this.getKind() == that.getKind()",
            "            && this.value.equals(that.value());",
            "      } else {",
            "        return false;",
            "      }",
            "    }",
            "",
            "    @Override",
            "    public int hashCode() {",
            "      return value.hashCode();",
            "    }",
            "  }",
            "",
            "  // Implementation when the contained property is \"exception\".",
            "  private static final class Impl_exception<V, T extends Throwable> "
                + "extends Parent_<V, T> {",
            "    private final Throwable exception;",
            "",
            "    Impl_exception(Throwable exception) {",
            "      this.exception = exception;",
            "    }",
            "",
            "    @Override",
            "    public TaskResult.Kind getKind() {",
            "      return TaskResult.Kind.EXCEPTION;",
            "    }",
            "",
            "    @Override",
            "    public Throwable exception() {",
            "      return exception;",
            "    }",
            "",
            "    @Override",
            "    public String toString() {",
            "      return \"TaskResult{exception=\" + this.exception + \"}\";",
            "    }",
            "",
            "    @Override",
            "    public boolean equals(Object x) {",
            "      if (x instanceof TaskResult) {",
            "        TaskResult<?, ?> that = (TaskResult<?, ?>) x;",
            "        return this.getKind() == that.getKind()",
            "            && this.exception.equals(that.exception());",
            "      } else {",
            "        return false;",
            "      }",
            "    }",
            "",
            "    @Override",
            "    public int hashCode() {",
            "      return exception.hashCode();",
            "    }",
            "  }");
    Compilation compilation =
        javac()
            .withProcessors(new AutoOneOfProcessor())
            .withOptions("-Xlint:-processing", "-implicit:none")
            .compile(javaFileObject);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoOneOf_TaskResult")
        .hasSourceEquivalentTo(expectedOutput);
  }

  @Test
  public void noKindGetter() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Pet",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoOneOf;",
            "",
            "@AutoOneOf(Pet.Kind.class)",
            "public abstract class Pet {",
            "  public enum Kind {DOG, CAT}",
            "  public abstract String dog();",
            "  public abstract String cat();",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoOneOfProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "foo.bar.Pet must have a no-arg abstract method returning foo.bar.Pet.Kind")
        .inFile(javaFileObject)
        .onLineContaining("class Pet");
  }

  @Test
  public void kindGetterHasParam() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Pet",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoOneOf;",
            "",
            "@AutoOneOf(Pet.Kind.class)",
            "public abstract class Pet {",
            "  public enum Kind {DOG, CAT}",
            "  public abstract Kind getKind(String wut);",
            "  public abstract String dog();",
            "  public abstract String cat();",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoOneOfProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "foo.bar.Pet must have a no-arg abstract method returning foo.bar.Pet.Kind")
        .inFile(javaFileObject)
        .onLineContaining("class Pet");
  }

  @Test
  public void twoKindGetters() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Pet",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoOneOf;",
            "",
            "@AutoOneOf(Pet.Kind.class)",
            "public abstract class Pet {",
            "  public enum Kind {DOG, CAT}",
            "  public abstract Kind getKind();",
            "  public abstract Kind alsoGetKind();",
            "  public abstract String dog();",
            "  public abstract String cat();",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoOneOfProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("More than one abstract method returns foo.bar.Pet.Kind")
        .inFile(javaFileObject)
        .onLineContaining("getKind");
    assertThat(compilation)
        .hadErrorContaining("More than one abstract method returns foo.bar.Pet.Kind")
        .inFile(javaFileObject)
        .onLineContaining("alsoGetKind");
  }

  @Test
  public void enumMissingCase() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Pet",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoOneOf;",
            "",
            "@AutoOneOf(Pet.Kind.class)",
            "public abstract class Pet {",
            "  public enum Kind {DOG}",
            "  public abstract Kind getKind();",
            "  public abstract String dog();",
            "  public abstract String cat();",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoOneOfProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining("Enum has no constant with name corresponding to property 'cat'")
        .inFile(javaFileObject)
        .onLineContaining("enum Kind");
  }

  @Test
  public void enumExtraCase() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Pet",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoOneOf;",
            "",
            "@AutoOneOf(Pet.Kind.class)",
            "public abstract class Pet {",
            "  public enum Kind {",
            "    DOG,",
            "    CAT,",
            "    GERBIL,",
            "  }",
            "  public abstract Kind getKind();",
            "  public abstract String dog();",
            "  public abstract String cat();",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoOneOfProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadErrorContaining(
            "Name of enum constant 'GERBIL' does not correspond to any property name")
        .inFile(javaFileObject)
        .onLineContaining("GERBIL");
  }

  @Test
  public void abstractVoidMethod() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Pet",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoOneOf;",
            "",
            "@AutoOneOf(Pet.Kind.class)",
            "public abstract class Pet {",
            "  public enum Kind {",
            "    DOG,",
            "    CAT,",
            "  }",
            "  public abstract Kind getKind();",
            "  public abstract String dog();",
            "  public abstract String cat();",
            "  public abstract void frob();",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoOneOfProcessor()).compile(javaFileObject);
    assertThat(compilation)
        .hadWarningContaining(
            "Abstract methods in @AutoOneOf classes must be non-void with no parameters")
        .inFile(javaFileObject)
        .onLineContaining("frob");
  }
}
