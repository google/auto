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

package com.google.auto.common;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link GeneratedAnnotations}Test */
@RunWith(JUnit4.class)
public class GeneratedAnnotationsTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final String JAVAX_ANNOTATION_PROCESSING_GENERATED =
      "javax.annotation.processing.Generated";
  private static final String JAVAX_ANNOTATION_GENERATED = "javax.annotation.Generated";

  /**
   * A toy annotation processor for testing. Matches on all annotations, and unconditionally
   * generates a source that uses {@code @Generated}.
   */
  @SupportedAnnotationTypes("*")
  public static class TestProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    private boolean first = true;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (first) {
        TypeSpec.Builder type = TypeSpec.classBuilder("G");
        GeneratedAnnotationSpecs.generatedAnnotationSpec(
                processingEnv.getElementUtils(),
                processingEnv.getSourceVersion(),
                TestProcessor.class)
            .ifPresent(type::addAnnotation);
        JavaFile javaFile = JavaFile.builder("", type.build()).build();
        try {
          javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
        first = false;
      }
      return false;
    }
  }

  /**
   * Run {@link TestProcessor} in a compilation with the given {@code options}, and prevent the
   * compilation from accessing classes with the qualified names in {@code maskFromClasspath}.
   */
  private String runProcessor(ImmutableList<String> options, String packageToMask)
      throws IOException {
    File tempDir = temporaryFolder.newFolder();
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager standardFileManager =
        compiler.getStandardFileManager(/* diagnostics= */ null, /* locale= */ null, UTF_8);
    standardFileManager.setLocation(StandardLocation.CLASS_OUTPUT, ImmutableList.of(tempDir));
    ForwardingJavaFileManager<StandardJavaFileManager> fileManager =
        new ForwardingJavaFileManager<StandardJavaFileManager>(standardFileManager) {
          @Override
          public Iterable<JavaFileObject> list(
              Location location, String packageName, Set<Kind> kinds, boolean recurse)
              throws IOException {
            if (packageToMask != null && packageName.equals(packageToMask)) {
              return ImmutableList.of();
            }
            return super.list(location, packageName, kinds, recurse);
          }
        };
    CompilationTask task =
        compiler.getTask(
            /* out= */ null,
            fileManager,
            /* diagnosticListener= */ null,
            options,
            /* classes= */ null,
            ImmutableList.of(
                new SimpleJavaFileObject(URI.create("test"), Kind.SOURCE) {
                  @Override
                  public CharSequence getCharContent(boolean ignoreEncodingErrors)
                      throws IOException {
                    return "class Test {}";
                  }
                }));
    task.setProcessors(ImmutableList.of(new TestProcessor()));
    assertThat(task.call()).isTrue();
    return new String(Files.readAllBytes(tempDir.toPath().resolve("G.java")), UTF_8);
  }

  @Test
  public void source8() throws Exception {
    String generated = runProcessor(ImmutableList.of("-source", "8", "-target", "8"), null);
    assertThat(generated).contains(JAVAX_ANNOTATION_GENERATED);
    assertThat(generated).doesNotContain(JAVAX_ANNOTATION_PROCESSING_GENERATED);
  }

  @Test
  public void source8_masked() throws Exception {
    String generated =
        runProcessor(ImmutableList.of("-source", "8", "-target", "8"), "javax.annotation");
    assertThat(generated).doesNotContain(JAVAX_ANNOTATION_GENERATED);
    assertThat(generated).doesNotContain(JAVAX_ANNOTATION_PROCESSING_GENERATED);
  }

  @Test
  public void source9() throws Exception {
    assumeTrue(isJdk9OrLater());
    String generated = runProcessor(ImmutableList.of("-source", "9", "-target", "9"), null);
    assertThat(generated).doesNotContain(JAVAX_ANNOTATION_GENERATED);
    assertThat(generated).contains(JAVAX_ANNOTATION_PROCESSING_GENERATED);
  }

  @Test
  public void source9_masked() throws Exception {
    assumeTrue(isJdk9OrLater());
    String generated =
        runProcessor(
            ImmutableList.of("-source", "9", "-target", "9"), "javax.annotation.processing");
    assertThat(generated).doesNotContain(JAVAX_ANNOTATION_GENERATED);
    assertThat(generated).doesNotContain(JAVAX_ANNOTATION_PROCESSING_GENERATED);
  }

  private static boolean isJdk9OrLater() {
    return SourceVersion.latestSupported().compareTo(SourceVersion.RELEASE_8) > 0;
  }
}
