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
package com.google.auto.factory.gentest;

import static com.google.auto.factory.gentest.JavaSourcesSubjectFactory.javaSources;
import static com.google.auto.factory.gentest.JavaSourcesSubjectFactory.javaSourcesProcessedWith;
import static org.truth0.Truth.ASSERT;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

/**
 * Tests {@link JavaSourcesSubjectFactory}.
 *
 * @author Gregory Kick
 */
@RunWith(JUnit4.class)
public class JavaSourceSubjectFactoryTest {
  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void compilesWithoutError() {
    ASSERT.about(javaSources())
        .that(Arrays.asList(JavaFileObjects.forResource(Resources.getResource("HelloWorld.java"))))
        .compiles();
  }

  @Test
  public void compilesWithoutError_throws() {
    try {
      ASSERT.about(javaSources())
          .that(Arrays.asList(JavaFileObjects.forResource("HelloWorld-broken.java")))
          .compiles();
      throw new RuntimeException();
    } catch (AssertionError expected) {
      // TODO(gak): verify the message
    }
  }

  @Test
  public void failsToCompile_throws() {
    try {
      ASSERT.about(javaSources())
          .that(Arrays.asList(JavaFileObjects.forResource(Resources.getResource("HelloWorld.java"))))
          .failsToCompile();
      throw new RuntimeException();
    } catch (AssertionError expected) {}
  }

  @Test
  public void failsToCompile() {
    ASSERT.about(javaSources())
        .that(Arrays.asList(JavaFileObjects.forResource("HelloWorld-broken.java")))
        .failsToCompile();
  }

  @Test
  public void generates() throws IOException {
    ASSERT.about(javaSourcesProcessedWith(new GeneratingProcessor()))
        .that(Arrays.asList(JavaFileObjects.forResource("HelloWorld.java")))
        .generatesSources(JavaFileObjects.forSourceString(GeneratingProcessor.GENERATED_CLASS_NAME,
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
}
