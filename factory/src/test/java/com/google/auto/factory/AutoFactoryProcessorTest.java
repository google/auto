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

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static org.truth0.Truth.ASSERT;

import javax.tools.JavaFileObject;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.JavaFileObjects;

/**
 * Functional tests for the {@link AutoFactoryProcessor}.
 */
public class AutoFactoryProcessorTest {
  @Test public void simpleClass() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("tests/SimpleClass.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource("tests/SimpleClassFactory.java"));
  }

  @Test public void publicClass() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("tests/PublicClass.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource("tests/PublicClassFactory.java"));
  }

  @Test public void simpleClassCustomName() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("tests/SimpleClassCustomName.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource("tests/CustomNamedFactory.java"));
  }

  @Test public void simpleClassMixedDeps() {
    ASSERT.about(javaSources())
        .that(ImmutableSet.of(
            JavaFileObjects.forResource("tests/SimpleClassMixedDeps.java"),
            JavaFileObjects.forResource("tests/AQualifier.java")))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("tests/SimpleClassMixedDepsFactory.java"));
  }

  @Test public void simpleClassPassedDeps() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("tests/SimpleClassPassedDeps.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("tests/SimpleClassPassedDepsFactory.java"));
  }

  @Test public void simpleClassProvidedDeps() {
    ASSERT.about(javaSources())
        .that(ImmutableSet.of(
            JavaFileObjects.forResource("tests/AQualifier.java"),
            JavaFileObjects.forResource("tests/BQualifier.java"),
            JavaFileObjects.forResource("tests/SimpleClassProvidedDeps.java")))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("tests/SimpleClassProvidedDepsFactory.java"));
  }

  @Test public void constructorAnnotated() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("tests/ConstructorAnnotated.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("tests/ConstructorAnnotatedFactory.java"));
  }

  @Test public void simpleClassImplementingMarker() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("tests/SimpleClassImplementingMarker.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("tests/SimpleClassImplementingMarkerFactory.java"));
  }

  @Test public void simpleClassImplementingSimpleInterface() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("tests/SimpleClassImplementingSimpleInterface.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource(
            "tests/SimpleClassImplementingSimpleInterfaceFactory.java"));
  }

  @Test public void mixedDepsImplementingInterfaces() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("tests/MixedDepsImplementingInterfaces.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("tests/MixedDepsImplementingInterfacesFactory.java"));
  }

  @Test public void failsOnGenericClass() {
    JavaFileObject file = JavaFileObjects.forResource("tests/GenericClass.java");
    ASSERT.about(javaSource())
            .that(file)
            .processedWith(new AutoFactoryProcessor())
            .failsToCompile()
            .withErrorContaining("AutoFactory does not support generic types")
                .in(file).onLine(6).atColumn(14);
  }

  @Test public void providedButNoAutoFactory() {
    JavaFileObject file = JavaFileObjects.forResource("tests/ProvidedButNoAutoFactory.java");
    ASSERT.about(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining(
            "@Provided may only be applied to constructors requesting an auto-factory")
                .in(file).onLine(21).atColumn(38);
  }

  @Test public void providedOnMethodParameter() {
    JavaFileObject file = JavaFileObjects.forResource("tests/ProvidedOnMethodParameter.java");
    ASSERT.about(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining(
            "@Provided may only be applied to constructor parameters")
                .in(file).onLine(21).atColumn(23);
  }

  @Test public void invalidCustomName() {
    JavaFileObject file = JavaFileObjects.forResource("tests/InvalidCustomName.java");
    ASSERT.about(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining("\"SillyFactory!\" is not a valid Java identifier");
  }
}
