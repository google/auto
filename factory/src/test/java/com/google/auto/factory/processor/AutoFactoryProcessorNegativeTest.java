/*
 * Copyright 2013 Google LLC
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
package com.google.auto.factory.processor;

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.File;
import java.net.URL;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Testing compilation errors from {@link AutoFactoryProcessor}. */
@RunWith(JUnit4.class)
public class AutoFactoryProcessorNegativeTest {
  private final Compiler javac = Compiler.javac().withProcessors(new AutoFactoryProcessor());

  @Test
  public void failsWithMixedFinals() {
    JavaFileObject file = JavaFileObjects.forResource("bad/MixedFinals.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Cannot mix allowSubclasses=true and allowSubclasses=false in one factory.")
        .inFile(file)
        .onLine(24);
    assertThat(compilation)
        .hadErrorContaining(
            "Cannot mix allowSubclasses=true and allowSubclasses=false in one factory.")
        .inFile(file)
        .onLine(27);
  }

  @Test
  public void providedButNoAutoFactory() {
    JavaFileObject file = JavaFileObjects.forResource("bad/ProvidedButNoAutoFactory.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@Provided may only be applied to constructors requesting an auto-factory")
        .inFile(file)
        .onLineContaining("@Provided");
  }

  @Test
  public void providedOnMethodParameter() {
    JavaFileObject file = JavaFileObjects.forResource("bad/ProvidedOnMethodParameter.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Provided may only be applied to constructor parameters")
        .inFile(file)
        .onLineContaining("@Provided");
  }

  @Test
  public void invalidCustomName() {
    JavaFileObject file = JavaFileObjects.forResource("bad/InvalidCustomName.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("\"SillyFactory!\" is not a valid Java identifier")
        .inFile(file)
        .onLineContaining("SillyFactory!");
  }

  @Test
  public void factoryExtendingAbstractClass_withConstructorParams() {
    JavaFileObject file =
        JavaFileObjects.forResource("bad/FactoryExtendingAbstractClassWithConstructorParams.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "tests.FactoryExtendingAbstractClassWithConstructorParams.AbstractFactory is not a"
                + " valid supertype for a factory. Factory supertypes must have a no-arg"
                + " constructor.")
        .inFile(file)
        .onLineContaining("@AutoFactory");
  }

  @Test
  public void factoryExtendingInterface() {
    JavaFileObject file = JavaFileObjects.forResource("bad/InterfaceSupertype.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "java.lang.Runnable is not a valid supertype for a factory. Supertypes must be"
                + " non-final classes.")
        .inFile(file)
        .onLineContaining("@AutoFactory");
  }

  @Test
  public void factoryExtendingEnum() {
    JavaFileObject file = JavaFileObjects.forResource("bad/EnumSupertype.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "java.util.concurrent.TimeUnit is not a valid supertype for a factory. Supertypes must"
                + " be non-final classes.")
        .inFile(file)
        .onLineContaining("@AutoFactory");
  }

  @Test
  public void factoryExtendingFinalClass() {
    JavaFileObject file = JavaFileObjects.forResource("bad/FinalSupertype.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "java.lang.Boolean is not a valid supertype for a factory. Supertypes must be"
                + " non-final classes.")
        .inFile(file)
        .onLineContaining("@AutoFactory");
  }

  /**
   * We don't currently allow you to have more than one {@code @AnnotationsToApply} annotation for
   * any given AutoFactory class.
   */
  @Test
  public void annotationsToApplyMultiple() {
    JavaFileObject file = JavaFileObjects.forResource("bad/AnnotationsToApplyMultiple.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Multiple @AnnotationsToApply annotations are not supported");
  }

  /**
   * We also don't allow you to have the same annotation appear more than once inside a given
   * {@code @AnnotationsToApply}, even with the same values.
   */
  @Test
  public void annotationsToApplyRepeated() {
    JavaFileObject file = JavaFileObjects.forResource("bad/AnnotationsToApplyRepeated.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("More than one @java.lang.SuppressWarnings");
  }

  @Test
  public void annotationsToApplyNotAnnotations() {
    JavaFileObject file = JavaFileObjects.forResource("bad/AnnotationsToApplyNotAnnotations.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Members of an @AnnotationsToApply annotation must themselves be annotations;"
                + " whatIsThis has type int")
        .inFile(file)
        .onLineContaining("whatIsThis");
    assertThat(compilation)
        .hadErrorContaining(
            "Members of an @AnnotationsToApply annotation must themselves be annotations;"
                + " andWhatIsThis has type com.google.errorprone.annotations.Immutable[]")
        .inFile(file)
        .onLineContaining("andWhatIsThis");
  }

  @Test
  public void noInjectApi() throws Exception {
    URL autoFactoryUrl =
        Class.forName("com.google.auto.factory.AutoFactory")
            .getProtectionDomain()
            .getCodeSource()
            .getLocation();
    assertThat(autoFactoryUrl.getProtocol()).isEqualTo("file");
    File autoFactoryFile = new File(autoFactoryUrl.getPath());
    Compiler compiler =
        Compiler.javac()
            .withProcessors(new AutoFactoryProcessor())
            .withClasspath(ImmutableList.of(autoFactoryFile));
    JavaFileObject file = JavaFileObjects.forResource("good/SimpleClass.java");
    Compilation compilation = compiler.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Class path for AutoFactory class must include"
                + " jakarta.inject.{Inject,Provider,Qualifier} or"
                + " javax.inject.{Inject,Provider,Qualifier}");
    assertThat(compilation).hadErrorCount(1);
  }

  /**
   * AutoFactoryProcessor shouldn't complain about the absence of {@code javax.inject} if there are
   * no {@code @AutoFactory} classes being compiled. Its {@code init} will be called and will see
   * the problem, but will say nothing.
   */
  @Test
  public void noInjectApiButNoAutoFactoryEither() {
    Compiler compiler =
        Compiler.javac()
            .withProcessors(new AutoFactoryProcessor())
            .withClasspath(ImmutableList.of());
    JavaFileObject file =
        JavaFileObjects.forSourceString("test.Foo", "package test; public class Foo {}");
    Compilation compilation = compiler.compile(file);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation).generatedFile(StandardLocation.CLASS_OUTPUT, "test", "Foo.class");
  }
}
