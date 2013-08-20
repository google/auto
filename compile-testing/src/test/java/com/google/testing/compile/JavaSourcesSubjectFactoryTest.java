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
package com.google.testing.compile;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.truth0.FailureStrategy;
import org.truth0.TestVerb;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

/**
 * Tests {@link JavaSourcesSubjectFactory} (and {@link JavaSourceSubjectFactory}).
 *
 * @author Gregory Kick
 */
@RunWith(JUnit4.class)
public class JavaSourcesSubjectFactoryTest {
  /** We need a {@link TestVerb} that throws anything <i>except</i> {@link AssertionError}. */
  private static final TestVerb VERIFY = new TestVerb(new FailureStrategy() {
    @Override
    public void fail(String message) {
      throw new VerificationException(message);
    }
  });

  @Test
  public void compilesWithoutError() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource(Resources.getResource("HelloWorld.java")))
        .hasNoErrors();
  }

  @Test
  public void compilesWithoutError_throws() {
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld-broken.java"))
          .hasNoErrors();
      fail();
    } catch (VerificationException expected) {
      // TODO(gak): verify the message
    }
  }

  @Test
  public void failsToCompile_throws() {
    JavaFileObject fileObject = JavaFileObjects.forResource("HelloWorld.java");
    try {
      VERIFY.about(javaSource())
          .that(fileObject)
          .hasError("some error").in(fileObject);
      fail();
    } catch (VerificationException expected) {
      // TODO(gak): verify the message
    }
  }

  @Test
  public void failsToCompile() {
    JavaFileObject fileObject = JavaFileObjects.forResource("HelloWorld-broken.java");
    ASSERT.about(javaSource())
        .that(fileObject)
        .hasError("not a statement")
        .and().hasError("not a statement").in(fileObject)
        .and().hasError("not a statement").in(fileObject).onLine(23)
        .and().hasError("not a statement").in(fileObject).onLine(23).atColumn(5);
  }

  @Test
  public void generates() throws IOException {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("HelloWorld.java"))
        .processedWith(new GeneratingProcessor())
        .hasNoErrors()
        .and().generatesSources(JavaFileObjects.forSourceString(
            GeneratingProcessor.GENERATED_CLASS_NAME,
            GeneratingProcessor.GENERATED_SOURCE));
  }

  private static final class GeneratingProcessor extends AbstractProcessor {
    static final String GENERATED_CLASS_NAME = "Blah";
    static final String GENERATED_SOURCE = "final class Blah {}";

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
      try {
        JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(GENERATED_CLASS_NAME);
        Writer writer = sourceFile.openWriter();
        writer.write(GENERATED_SOURCE);
        writer.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      return false;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }
  }

  private static final class VerificationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    VerificationException(String message) {
      super(message);
    }
  }
}
