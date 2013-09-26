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
package com.google.auto.factory.processor;

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
        .that(JavaFileObjects.forResource("good/SimpleClass.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource("expected/SimpleClassFactory.java"));
  }

  @Test public void publicClass() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("good/PublicClass.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource("expected/PublicClassFactory.java"));
  }

  @Test public void simpleClassCustomName() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("good/SimpleClassCustomName.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource("expected/CustomNamedFactory.java"));
  }

  @Test public void simpleClassMixedDeps() {
    ASSERT.about(javaSources())
        .that(ImmutableSet.of(
            JavaFileObjects.forResource("good/SimpleClassMixedDeps.java"),
            JavaFileObjects.forResource("aux/AQualifier.java")))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/SimpleClassMixedDepsFactory.java"));
  }

  @Test public void simpleClassPassedDeps() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("good/SimpleClassPassedDeps.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/SimpleClassPassedDepsFactory.java"));
  }

  @Test public void simpleClassProvidedDeps() {
    ASSERT.about(javaSources())
        .that(ImmutableSet.of(
            JavaFileObjects.forResource("aux/AQualifier.java"),
            JavaFileObjects.forResource("aux/BQualifier.java"),
            JavaFileObjects.forResource("good/SimpleClassProvidedDeps.java")))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/SimpleClassProvidedDepsFactory.java"));
  }

  @Test public void constructorAnnotated() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("good/ConstructorAnnotated.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/ConstructorAnnotatedFactory.java"));
  }

  @Test public void simpleClassImplementingMarker() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("good/SimpleClassImplementingMarker.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/SimpleClassImplementingMarkerFactory.java"));
  }

  @Test public void simpleClassImplementingSimpleInterface() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("good/SimpleClassImplementingSimpleInterface.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource(
            "expected/SimpleClassImplementingSimpleInterfaceFactory.java"));
  }

  @Test public void mixedDepsImplementingInterfaces() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("good/MixedDepsImplementingInterfaces.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/MixedDepsImplementingInterfacesFactory.java"));
  }

  @Test public void failsOnGenericClass() {
    JavaFileObject file = JavaFileObjects.forResource("bad/GenericClass.java");
    ASSERT.about(javaSource())
            .that(file)
            .processedWith(new AutoFactoryProcessor())
            .failsToCompile()
            .withErrorContaining("AutoFactory does not support generic types")
                .in(file).onLine(21).atColumn(14);
  }

  @Test public void providedButNoAutoFactory() {
    JavaFileObject file = JavaFileObjects.forResource("bad/ProvidedButNoAutoFactory.java");
    ASSERT.about(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining(
            "@Provided may only be applied to constructors requesting an auto-factory")
                .in(file).onLine(21).atColumn(38);
  }

  @Test public void providedOnMethodParameter() {
    JavaFileObject file = JavaFileObjects.forResource("bad/ProvidedOnMethodParameter.java");
    ASSERT.about(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining(
            "@Provided may only be applied to constructor parameters")
                .in(file).onLine(21).atColumn(23);
  }

  @Test public void invalidCustomName() {
    JavaFileObject file = JavaFileObjects.forResource("bad/InvalidCustomName.java");
    ASSERT.about(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining("\"SillyFactory!\" is not a valid Java identifier")
            .in(file).onLine(20);
  }

  @Test public void factoryExtendingAbstractClass() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("good/FactoryExtendingAbstractClass.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/FactoryExtendingAbstractClassFactory.java"));
  }

  @Test public void factoryExtendingInterface() {
    JavaFileObject file = JavaFileObjects.forResource("bad/InterfaceSupertype.java");
    ASSERT.about(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining("java.lang.Runnable is not a valid supertype for a factory. "
            + "Supertypes must be non-final classes.")
                .in(file).onLine(20);
  }

  @Test public void factoryExtendingEnum() {
    JavaFileObject file = JavaFileObjects.forResource("bad/EnumSupertype.java");
    ASSERT.about(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining(
            "java.util.concurrent.TimeUnit is not a valid supertype for a factory. "
                + "Supertypes must be non-final classes.")
                    .in(file).onLine(22);
  }

  @Test public void factoryExtendingFinalClass() {
    JavaFileObject file = JavaFileObjects.forResource("bad/FinalSupertype.java");
    ASSERT.about(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining("java.lang.Boolean is not a valid supertype for a factory. "
            + "Supertypes must be non-final classes.")
                .in(file).onLine(20);
  }
}
