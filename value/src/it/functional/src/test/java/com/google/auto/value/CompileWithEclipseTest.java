/*
 * Copyright 2014 Google LLC
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

import static com.google.common.base.StandardSystemProperty.JAVA_HOME;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.auto.value.processor.AutoAnnotationProcessor;
import com.google.auto.value.processor.AutoBuilderProcessor;
import com.google.auto.value.processor.AutoOneOfProcessor;
import com.google.auto.value.processor.AutoValueProcessor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.processing.Processor;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that we can compile our AutoValue tests using the Eclipse batch compiler. Since the tests
 * exercise many AutoValue subtleties, the ability to compile them all is a good indication of
 * Eclipse support.
 */
@RunWith(JUnit4.class)
public class CompileWithEclipseTest {
  private static final String SOURCE_ROOT = System.getProperty("basedir");

  @BeforeClass
  public static void setSourceRoot() {
    assertWithMessage("basedir property must be set - test must be run from Maven")
        .that(SOURCE_ROOT)
        .isNotNull();
  }

  public @Rule TemporaryFolder tmp = new TemporaryFolder();

  private static final ImmutableSet<String> IGNORED_TEST_FILES =
      ImmutableSet.of(
          "AutoValueNotEclipseTest.java",
          "CompileWithEclipseTest.java",
          "CustomFieldSerializerTest.java",
          "GradleIT.java",

          // AutoBuilder sometimes needs to generate a .class file for Kotlin that is used in the
          // rest of compilation, and Eclipse doesn't seem to handle that well. Presumably not many
          // Kotlin users use Eclipse since IntelliJ is obviously much more suitable.
          "AutoBuilderKotlinTest.java");

  private static final Predicate<File> JAVA_FILE =
      f -> f.getName().endsWith(".java") && !IGNORED_TEST_FILES.contains(f.getName());

  private static final ImmutableSet<String> JAVA8_TEST_FILES =
      ImmutableSet.of(
          "AutoBuilderTest.java",
          "AutoOneOfJava8Test.java",
          "AutoValueJava8Test.java",
          "EmptyExtension.java");
  private static final Predicate<File> JAVA8_TEST = f -> JAVA8_TEST_FILES.contains(f.getName());

  @Test
  public void compileWithEclipseJava7() throws Exception {
    compileWithEclipse("7", JAVA_FILE.and(JAVA8_TEST.negate()));
  }

  @Test
  public void compileWithEclipseJava8() throws Exception {
    compileWithEclipse("8", JAVA_FILE);
  }

  private void compileWithEclipse(String version, Predicate<File> predicate) throws IOException {
    File sourceRootFile = new File(SOURCE_ROOT);
    File javaDir = new File(sourceRootFile, "src/main/java");
    File javatestsDir = new File(sourceRootFile, "src/test/java");
    Set<File> sources =
        new ImmutableSet.Builder<File>()
            .addAll(filesUnderDirectory(javaDir, predicate))
            .addAll(filesUnderDirectory(javatestsDir, predicate))
            .build();
    assertThat(sources).isNotEmpty();
    JavaCompiler compiler = new EclipseCompiler();
    StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
    // This hack is only needed in a Google-internal Java 8 environment where symbolic links make it
    // hard for ecj to find the boot class path. Elsewhere it is unnecessary but harmless. Notably,
    // on Java 9+ there is no rt.jar. There, fileManager.getLocation(PLATFORM_CLASS_PATH) returns
    // null, because the relevant classes are in modules inside
    // fileManager.getLocation(SYSTEM_MODULES).
    File rtJar = new File(JAVA_HOME.value() + "/lib/rt.jar");
    if (rtJar.exists()) {
      List<File> bootClassPath =
          ImmutableList.<File>builder()
              .add(rtJar)
              .addAll(fileManager.getLocation(StandardLocation.PLATFORM_CLASS_PATH))
              .build();
      fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, bootClassPath);
    }
    Iterable<? extends JavaFileObject> sourceFileObjects =
        fileManager.getJavaFileObjectsFromFiles(sources);
    String outputDir = tmp.getRoot().toString();
    ImmutableList<String> options =
        ImmutableList.of(
            "-d",
            outputDir,
            "-s",
            outputDir,
            "-source",
            version,
            "-target",
            version,
            "-warn:-warningToken,-intfAnnotation");
    JavaCompiler.CompilationTask task =
        compiler.getTask(null, fileManager, null, options, null, sourceFileObjects);
    // Explicitly supply an empty list of extensions for AutoValueProcessor, because otherwise this
    // test will pick up a test one and get confused.
    AutoValueProcessor autoValueProcessor = new AutoValueProcessor(ImmutableList.of());
    ImmutableList<? extends Processor> processors =
        ImmutableList.of(
            autoValueProcessor,
            new AutoOneOfProcessor(),
            new AutoAnnotationProcessor(),
            new AutoBuilderProcessor());
    task.setProcessors(processors);
    assertWithMessage("Compilation should succeed").that(task.call()).isTrue();
  }

  private static ImmutableSet<File> filesUnderDirectory(File dir, Predicate<File> predicate)
      throws IOException {
    assertWithMessage(dir.toString()).that(dir.isDirectory()).isTrue();
    try (Stream<Path> paths = Files.walk(dir.toPath())) {
      return paths.map(Path::toFile).filter(predicate).collect(toImmutableSet());
    }
  }
}
