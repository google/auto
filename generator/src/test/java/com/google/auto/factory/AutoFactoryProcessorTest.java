/*
 * Copyright (C) 2013 Google, Inc.
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
package com.google.auto.factory;

import static com.google.auto.factory.gentest.JavaSourceSubjectFactory.JAVA_SOURCE;
import static com.google.common.base.Charsets.UTF_8;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import static org.junit.Assert.assertTrue;
import static org.truth0.Truth.ASSERT;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.common.io.Resources;

public class AutoFactoryProcessorTest {
  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private JavaCompiler compiler;
  private StandardJavaFileManager fileManager;

  private File inputSources;
  private File expectedSources;
  private File outputSources;

  @Before public void createCompiler() throws IOException {
    this.compiler = ToolProvider.getSystemJavaCompiler();
    this.fileManager = compiler.getStandardFileManager(null /* default diagnostic listener */,
        Locale.getDefault(), UTF_8);
    this.inputSources = folder.newFolder();
    this.expectedSources = folder.newFolder();
    this.outputSources = folder.newFolder();
    fileManager.setLocation(SOURCE_OUTPUT, ImmutableSet.of(outputSources));
  }

  private CompilationTask createCompilationTask(Set<File> sources) {
    return compiler.getTask(null, fileManager,
        null /* default diagnostic listener */,
        ImmutableList.of("-processor", "com.google.auto.factory.AutoFactoryProcessor"),
        null,
        fileManager.getJavaFileObjectsFromFiles(sources));
  }

  private File copyFromResource(String resourcePath, File destination)
      throws IOException {
    File sourceFile = new File(destination, resourcePath);
    Files.createParentDirs(sourceFile);
    Resources.asByteSource(Resources.getResource(resourcePath))
        .copyTo(Files.asByteSink(sourceFile));
    return sourceFile;
  }

  private void assertOutput(String path) throws IOException {
    File expectedOutput = copyFromResource(path, expectedSources);
    File actual = new File(outputSources, path);
    assertTrue("file does not exist. files: " + Arrays.toString(outputSources.listFiles()),
        actual.exists());
    try {
      ASSERT.about(JAVA_SOURCE).that(expectedOutput)
          .isEquivalentTo(actual);
    } catch (AssertionError e) {
      throw new ComparisonFailure("", Files.toString(expectedOutput, UTF_8),
          Files.toString(actual, UTF_8));
    } catch (RuntimeException e) {
      throw new ComparisonFailure("", Files.toString(expectedOutput, UTF_8),
          Files.toString(actual, UTF_8));
    }
  }

  @Test public void simpleClass() throws IOException {
    File sourceFile = copyFromResource("tests/SimpleClass.java", inputSources);
    CompilationTask task = createCompilationTask(ImmutableSet.of(sourceFile));
    assertTrue("compilation failed", task.call());
    assertOutput("tests/SimpleClassFactory.java");
  }

  @Test public void publicClass() throws IOException {
    File sourceFile = copyFromResource("tests/PublicClass.java", inputSources);
    CompilationTask task = createCompilationTask(ImmutableSet.of(sourceFile));
    assertTrue("compilation failed", task.call());
    assertOutput("tests/PublicClassFactory.java");
  }

  @Test public void simpleClassCustomName() throws IOException {
    File sourceFile = copyFromResource("tests/SimpleClassCustomName.java", inputSources);
    CompilationTask task = createCompilationTask(ImmutableSet.of(sourceFile));
    assertTrue("compilation failed", task.call());
    assertOutput("tests/CustomNamedFactory.java");
  }

  @Test public void simpleClassMixedDeps() throws IOException {
    File sourceFile = copyFromResource("tests/SimpleClassMixedDeps.java", inputSources);
    CompilationTask task = createCompilationTask(ImmutableSet.of(sourceFile));
    assertTrue("compilation failed", task.call());
    assertOutput("tests/SimpleClassMixedDepsFactory.java");
  }

  @Test public void simpleClassPassedDeps() throws IOException {
    File sourceFile = copyFromResource("tests/SimpleClassPassedDeps.java", inputSources);
    CompilationTask task = createCompilationTask(ImmutableSet.of(sourceFile));
    assertTrue("compilation failed", task.call());
    assertOutput("tests/SimpleClassPassedDepsFactory.java");
  }

  @Test public void simpleClassProvidedDeps() throws IOException {
    File sourceFile = copyFromResource("tests/SimpleClassProvidedDeps.java", inputSources);
    CompilationTask task = createCompilationTask(ImmutableSet.of(sourceFile));
    assertTrue("compilation failed", task.call());
    assertOutput("tests/SimpleClassProvidedDepsFactory.java");
  }

  @Test public void constructorAnnotated() throws IOException {
    File sourceFile = copyFromResource("tests/ConstructorAnnotated.java", inputSources);
    CompilationTask task = createCompilationTask(ImmutableSet.of(sourceFile));
    assertTrue("compilation failed", task.call());
    assertOutput("tests/ConstructorAnnotatedFactory.java");
  }

  @Ignore  // this test doesn't pass yet
  @Test public void simpleClassImplementingFactory() throws IOException {
    File sourceFile = copyFromResource("tests/SimpleClassImplementing.java", inputSources);
    CompilationTask task = createCompilationTask(ImmutableSet.of(sourceFile));
    assertTrue("compilation failed", task.call());
    assertOutput("tests/SimpleClassImplementingFactory.java");
  }
}
