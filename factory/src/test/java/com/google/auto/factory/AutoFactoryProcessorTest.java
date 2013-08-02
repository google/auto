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

import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSourcesProcessedWith;
import static org.truth0.Truth.ASSERT;

import java.io.IOException;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.JavaFileObjects;

/**
 * Functional tests for the {@link AutoFactoryProcessor}.
 */
public class AutoFactoryProcessorTest {
  @Test public void simpleClass() throws IOException {
    ASSERT.about(javaSourcesProcessedWith(new AutoFactoryProcessor()))
        .that(ImmutableSet.of(JavaFileObjects.forResource("tests/SimpleClass.java")))
        .generatesSources(JavaFileObjects.forResource("tests/SimpleClassFactory.java"));
  }

  @Test public void publicClass() throws IOException {
    ASSERT.about(javaSourcesProcessedWith(new AutoFactoryProcessor()))
        .that(ImmutableSet.of(JavaFileObjects.forResource("tests/PublicClass.java")))
        .generatesSources(JavaFileObjects.forResource("tests/PublicClassFactory.java"));
  }

  @Test public void simpleClassCustomName() throws IOException {
    ASSERT.about(javaSourcesProcessedWith(new AutoFactoryProcessor()))
        .that(ImmutableSet.of(JavaFileObjects.forResource("tests/SimpleClassCustomName.java")))
        .generatesSources(JavaFileObjects.forResource("tests/CustomNamedFactory.java"));
  }

  @Test public void simpleClassMixedDeps() throws IOException {
    ASSERT.about(javaSourcesProcessedWith(new AutoFactoryProcessor()))
        .that(ImmutableSet.of(
            JavaFileObjects.forResource("tests/SimpleClassMixedDeps.java"),
            JavaFileObjects.forResource("tests/AQualifier.java")))
        .generatesSources(JavaFileObjects.forResource("tests/SimpleClassMixedDepsFactory.java"));
  }

  @Test public void simpleClassPassedDeps() throws IOException {
    ASSERT.about(javaSourcesProcessedWith(new AutoFactoryProcessor()))
        .that(ImmutableSet.of(JavaFileObjects.forResource("tests/SimpleClassPassedDeps.java")))
        .generatesSources(JavaFileObjects.forResource("tests/SimpleClassPassedDepsFactory.java"));
  }

  @Test public void simpleClassProvidedDeps() throws IOException {
    ASSERT.about(javaSourcesProcessedWith(new AutoFactoryProcessor()))
        .that(ImmutableSet.of(
            JavaFileObjects.forResource("tests/AQualifier.java"),
            JavaFileObjects.forResource("tests/BQualifier.java"),
            JavaFileObjects.forResource("tests/SimpleClassProvidedDeps.java")))
        .generatesSources(JavaFileObjects.forResource("tests/SimpleClassProvidedDepsFactory.java"));
  }

  @Test public void constructorAnnotated() throws IOException {
    ASSERT.about(javaSourcesProcessedWith(new AutoFactoryProcessor()))
        .that(ImmutableSet.of(JavaFileObjects.forResource("tests/ConstructorAnnotated.java")))
        .generatesSources(JavaFileObjects.forResource("tests/ConstructorAnnotatedFactory.java"));
  }

  @Test public void simpleClassImplementingMarker() throws IOException {
    ASSERT.about(javaSourcesProcessedWith(new AutoFactoryProcessor()))
        .that(ImmutableSet.of(JavaFileObjects.forResource(
            "tests/SimpleClassImplementingMarker.java")))
        .generatesSources(JavaFileObjects.forResource(
            "tests/SimpleClassImplementingMarkerFactory.java"));
  }

  @Test public void simpleClassImplementingSimpleInterface() throws IOException {
    ASSERT.about(javaSourcesProcessedWith(new AutoFactoryProcessor()))
        .that(ImmutableSet.of(JavaFileObjects.forResource(
            "tests/SimpleClassImplementingSimpleInterface.java")))
        .generatesSources(JavaFileObjects.forResource(
            "tests/SimpleClassImplementingSimpleInterfaceFactory.java"));
  }

  @Test public void mixedDepsImplementingInterfaces() throws IOException {
    ASSERT.about(javaSourcesProcessedWith(new AutoFactoryProcessor()))
        .that(ImmutableSet.of(JavaFileObjects.forResource(
            "tests/MixedDepsImplementingInterfaces.java")))
        .generatesSources(JavaFileObjects.forResource(
            "tests/MixedDepsImplementingInterfacesFactory.java"));
  }
}
