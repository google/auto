/*
 * Copyright 2008 Google LLC
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
package com.google.auto.service.processor;

import static com.google.auto.service.processor.AutoServiceProcessor.MISSING_SERVICES_ERROR;
import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;

import com.google.common.io.Resources;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests the {@link AutoServiceProcessor}. */
@RunWith(JUnit4.class)
public class AutoServiceProcessorTest {
  private final Compiler compiler = Compiler.javac().withProcessors(new AutoServiceProcessor());

  @Test
  public void autoService() {
    Compilation compilation =
        compiler.compile(
            JavaFileObjects.forResource("test/SomeService.java"),
            JavaFileObjects.forResource("test/SomeServiceProvider1.java"),
            JavaFileObjects.forResource("test/SomeServiceProvider2.java"),
            JavaFileObjects.forResource("test/Enclosing.java"),
            JavaFileObjects.forResource("test/AnotherService.java"),
            JavaFileObjects.forResource("test/AnotherServiceProvider.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/test.SomeService")
        .hasContents(
            Resources.asByteSource(Resources.getResource("META-INF/services/test.SomeService")));
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/test.AnotherService")
        .hasContents(
            Resources.asByteSource(Resources.getResource("META-INF/services/test.AnotherService")));
  }

  @Test
  public void multiService() {
    Compilation compilation =
        compiler.compile(
            JavaFileObjects.forResource("test/SomeService.java"),
            JavaFileObjects.forResource("test/AnotherService.java"),
            JavaFileObjects.forResource("test/MultiServiceProvider.java"));
    assertThat(compilation).succeededWithoutWarnings();
    // We have @AutoService({SomeService.class, AnotherService.class}) class MultiServiceProvider.
    // So we expect META-INF/services/test.SomeService with contents that name MultiServiceProvider
    // and likewise META-INF/services/test.AnotherService.
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/test.SomeService")
        .contentsAsUtf8String()
        .isEqualTo("test.MultiServiceProvider\n");
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/test.AnotherService")
        .contentsAsUtf8String()
        .isEqualTo("test.MultiServiceProvider\n");
  }

  @Test
  public void badMultiService() {
    Compilation compilation = compiler.compile(JavaFileObjects.forResource("test/NoServices.java"));
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(MISSING_SERVICES_ERROR);
  }

  @Test
  public void doesNotImplement_failsByDefault() {
    Compilation compilation =
        compiler.compile(JavaFileObjects.forResource("test/DoesNotImplement.java"));
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("test.DoesNotImplement does not implement test.SomeService");
  }

  @Test
  public void doesNotImplement_succeedsWithVerifyFalse() {
    Compilation compilation =
        compiler
            .withOptions("-Averify=false")
            .compile(
                JavaFileObjects.forResource("test/DoesNotImplement.java"),
                JavaFileObjects.forResource("test/SomeService.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/test.SomeService")
        .contentsAsUtf8String()
        .isEqualTo("test.DoesNotImplement\n");
  }

  @Test
  public void doesNotImplement_suppressed() {
    Compilation compilation =
        compiler.compile(
            JavaFileObjects.forResource("test/DoesNotImplementSuppressed.java"),
            JavaFileObjects.forResource("test/SomeService.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/test.SomeService")
        .contentsAsUtf8String()
        .isEqualTo("test.DoesNotImplementSuppressed\n");
  }

  @Test
  public void generic() {
    Compilation compilation =
        compiler
            .withOptions("-Averify=false")
            .compile(
                JavaFileObjects.forResource("test/GenericService.java"),
                JavaFileObjects.forResource("test/GenericServiceProvider.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/test.GenericService")
        .contentsAsUtf8String()
        .isEqualTo("test.GenericServiceProvider\n");
  }

  @Test
  public void genericWithNoVerifyOption() {
    Compilation compilation =
        compiler.compile(
            JavaFileObjects.forResource("test/GenericService.java"),
            JavaFileObjects.forResource("test/GenericServiceProvider.java"));
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining(
            "Service provider test.GenericService is generic, so it can't be named exactly by"
                + " @AutoService. If this is OK, add @SuppressWarnings(\"rawtypes\").");
  }

  @Test
  public void genericWithExplicitVerify() {
    Compilation compilation =
        compiler
            .withOptions("-Averify=true")
            .compile(
                JavaFileObjects.forResource("test/GenericService.java"),
                JavaFileObjects.forResource("test/GenericServiceProvider.java"));
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining(
            "Service provider test.GenericService is generic, so it can't be named exactly by"
                + " @AutoService. If this is OK, add @SuppressWarnings(\"rawtypes\").");
  }

  @Test
  public void genericWithVerifyOptionAndSuppressWarings() {
    Compilation compilation =
        compiler
            .withOptions("-Averify=true")
            .compile(
                JavaFileObjects.forResource("test/GenericService.java"),
                JavaFileObjects.forResource("test/GenericServiceProviderSuppressWarnings.java"));
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void nestedGenericWithVerifyOptionAndSuppressWarnings() {
    Compilation compilation =
        compiler
            .withOptions("-Averify=true")
            .compile(
                JavaFileObjects.forResource("test/GenericService.java"),
                JavaFileObjects.forResource("test/EnclosingGeneric.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/test.GenericService")
        .contentsAsUtf8String()
        .isEqualTo("test.EnclosingGeneric$GenericServiceProvider\n");
  }

  @Test
  public void missing() {
    AutoServiceProcessor processor = new AutoServiceProcessor();
    Compilation compilation =
        compiler
            .withOptions("-Averify=true")
            .compile(
                JavaFileObjects.forResource(
                    "test/GenericServiceProviderWithMissingServiceClass.java"));
    assertThat(compilation).failed();
    assertThat(processor.exceptionStacks()).isEmpty();
  }

  @Test
  public void autoServiceOnInterface() {
    AutoServiceProcessor processor = new AutoServiceProcessor();
    JavaFileObject autoServiceOnInterface =
        JavaFileObjects.forResource("test/AutoServiceOnInterface.java");
    Compilation compilation =
        Compiler.javac()
            .withProcessors(processor)
            .withOptions("-Averify=true")
            .compile(autoServiceOnInterface);
    assertThat(compilation)
        .hadErrorContaining("@AutoService cannot be applied to an abstract class or an interface")
        .inFile(autoServiceOnInterface)
        .onLineContaining("@AutoService");
    assertThat(processor.exceptionStacks()).isEmpty();
  }

  @Test
  public void autoServiceOnAbstractClass() {
    AutoServiceProcessor processor = new AutoServiceProcessor();
    JavaFileObject autoServiceOnAbstractClass =
        JavaFileObjects.forResource("test/AutoServiceOnAbstractClass.java");
    Compilation compilation =
        Compiler.javac()
            .withProcessors(processor)
            .withOptions("-Averify=true")
            .compile(autoServiceOnAbstractClass);
    assertThat(compilation)
        .hadErrorContaining("@AutoService cannot be applied to an abstract class or an interface")
        .inFile(autoServiceOnAbstractClass)
        .onLineContaining("@AutoService");
    assertThat(processor.exceptionStacks()).isEmpty();
  }
}
