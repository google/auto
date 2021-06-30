/*
 * Copyright 2018 Google LLC
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
import static java.util.Objects.requireNonNull;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.Reflection;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.checkerframework.checker.nullness.qual.Nullable;
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
  private String runProcessor(ImmutableList<String> options, @Nullable String packageToMask)
      throws IOException {
    File tempDir = temporaryFolder.newFolder();
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager standardFileManager =
        compiler.getStandardFileManager(/* diagnosticListener= */ null, /* locale= */ null, UTF_8);
    standardFileManager.setLocation(StandardLocation.CLASS_OUTPUT, ImmutableList.of(tempDir));
    StandardJavaFileManager proxyFileManager =
        Reflection.newProxy(
            StandardJavaFileManager.class,
            new FileManagerInvocationHandler(standardFileManager, packageToMask));
    CompilationTask task =
        compiler.getTask(
            /* out= */ null,
            proxyFileManager,
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

  /**
   * Used to produce a {@link StandardJavaFileManager} where a certain package appears to have
   * no classes. The results are exactly those from the proxied {@code StandardJavaFileManager}
   * except for the {@link StandardJavaFileManager#list list} method when its {@code packageName}
   * argument is the given package, in which case the result is an empty list.
   *
   * <p>We can't use {@link javax.tools.ForwardingJavaFileManager} because at least some JDK
   * versions require the file manager to be a {@code StandardJavaFileManager} when the
   * {@code --release} flag is given.
   */
  private static class FileManagerInvocationHandler implements InvocationHandler {
    private final StandardJavaFileManager fileManager;
    private final @Nullable String packageToMask;

    FileManagerInvocationHandler(
        StandardJavaFileManager fileManager, @Nullable String packageToMask) {
      this.fileManager = fileManager;
      this.packageToMask = packageToMask;
    }

    @Override
    public Object invoke(Object proxy, Method method, @Nullable Object @Nullable [] args)
        throws Throwable {
      if (method.getName().equals("list")) {
        String packageName = (String) requireNonNull(args)[1];
        if (Objects.equals(packageName, packageToMask)) {
          return ImmutableList.of();
        }
      }
      return method.invoke(fileManager, args);
    }
  }

  @Test
  public void source8() throws Exception {
    // Post-JDK8, we need to use --release 8 in order to be able to see the javax.annotation
    // package, which was deleted from the JDK in that release. On JDK8, there is no --release
    // option, so we have to use -source 8 -target 8.
    ImmutableList<String> options =
        isJdk9OrLater()
            ? ImmutableList.of("--release", "8")
            : ImmutableList.of("-source", "8", "-target", "8");
    String generated = runProcessor(options, null);
    assertThat(generated).contains(JAVAX_ANNOTATION_GENERATED);
    assertThat(generated).doesNotContain(JAVAX_ANNOTATION_PROCESSING_GENERATED);
  }

  @Test
  public void source8_masked() throws Exception {
    // It appears that the StandardJavaFileManager hack that removes a package does not work in
    // conjunction with --release. This is probably a bug. What we find is that
    // Elements.getTypeElement returns a value for javax.annotation.Generated even though
    // javax.annotation is being masked. It doesn't seem to go through the StandardJavaFileManager
    // interface to get it. So, we continue using the -source 8 -target 8 options. Those don't
    // actually get the JDK8 API when running post-JDK8, so we end up testing what we want.
    //
    // An alternative would be to delete this test method. JDK8 always has
    // javax.annotation.Generated so it isn't really meaningful to test it without.
    ImmutableList<String> options = ImmutableList.of("-source", "8", "-target", "8");
    String generated = runProcessor(options, "javax.annotation");
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
