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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubject.assertThat;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Functional tests for the {@link AutoFactoryProcessor}.
 */
@RunWith(JUnit4.class)
public class AutoFactoryProcessorTest {
  @Test public void simpleClass() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/SimpleClass.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource("expected/SimpleClassFactory.java"));
  }

  @Test
  public void nestedClasses() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/NestedClasses.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(
            JavaFileObjects.forResource("expected/NestedClasses_SimpleNestedClassFactory.java"),
            JavaFileObjects.forResource("expected/NestedClasses_CustomNamedFactory.java"));
  }

  @Test public void simpleClassNonFinal() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/SimpleClassNonFinal.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/SimpleClassNonFinalFactory.java"));
  }

  @Test public void publicClass() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/PublicClass.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource("expected/PublicClassFactory.java"));
  }

  @Test public void simpleClassCustomName() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/SimpleClassCustomName.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource("expected/CustomNamedFactory.java"));
  }

  @Test public void simpleClassMixedDeps() {
    assertAbout(javaSources())
        .that(ImmutableSet.of(
            JavaFileObjects.forResource("good/SimpleClassMixedDeps.java"),
            JavaFileObjects.forResource("support/AQualifier.java")))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/SimpleClassMixedDepsFactory.java"));
  }

  @Test public void simpleClassPassedDeps() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/SimpleClassPassedDeps.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/SimpleClassPassedDepsFactory.java"));
  }

  @Test public void simpleClassProvidedDeps() {
    assertAbout(javaSources())
        .that(ImmutableSet.of(
            JavaFileObjects.forResource("support/AQualifier.java"),
            JavaFileObjects.forResource("support/BQualifier.java"),
            JavaFileObjects.forResource("good/SimpleClassProvidedDeps.java")))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/SimpleClassProvidedDepsFactory.java"));
  }

  @Test
  public void simpleClassProvidedProviderDeps() {
    assertAbout(javaSources())
        .that(
            ImmutableSet.of(
                JavaFileObjects.forResource("support/AQualifier.java"),
                JavaFileObjects.forResource("support/BQualifier.java"),
                JavaFileObjects.forResource("good/SimpleClassProvidedProviderDeps.java")))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(
            JavaFileObjects.forResource("expected/SimpleClassProvidedProviderDepsFactory.java"));
  }

  @Test public void constructorAnnotated() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/ConstructorAnnotated.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/ConstructorAnnotatedFactory.java"));
  }

  @Test public void constructorAnnotatedNonFinal() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/ConstructorAnnotatedNonFinal.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/ConstructorAnnotatedNonFinalFactory.java"));
  }

  @Test public void simpleClassImplementingMarker() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/SimpleClassImplementingMarker.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/SimpleClassImplementingMarkerFactory.java"));
  }

  @Test public void simpleClassImplementingSimpleInterface() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/SimpleClassImplementingSimpleInterface.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource(
            "expected/SimpleClassImplementingSimpleInterfaceFactory.java"));
  }

  @Test public void mixedDepsImplementingInterfaces() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/MixedDepsImplementingInterfaces.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/MixedDepsImplementingInterfacesFactory.java"));
  }

  @Test public void failsWithMixedFinals() {
    JavaFileObject file = JavaFileObjects.forResource("bad/MixedFinals.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Cannot mix allowSubclasses=true and allowSubclasses=false in one factory.")
            .in(file).onLine(22).atColumn(3)
         .and().withErrorContaining(
            "Cannot mix allowSubclasses=true and allowSubclasses=false in one factory.")
            .in(file).onLine(23).atColumn(3);
  }

  @Test public void failsOnGenericClass() {
    JavaFileObject file = JavaFileObjects.forResource("bad/GenericClass.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining("AutoFactory does not support generic types")
            .in(file).onLine(21).atColumn(14);
  }

  @Test public void providedButNoAutoFactory() {
    JavaFileObject file = JavaFileObjects.forResource("bad/ProvidedButNoAutoFactory.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining(
            "@Provided may only be applied to constructors requesting an auto-factory")
                .in(file).onLine(21).atColumn(38);
  }

  @Test public void providedOnMethodParameter() {
    JavaFileObject file = JavaFileObjects.forResource("bad/ProvidedOnMethodParameter.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining(
            "@Provided may only be applied to constructor parameters")
                .in(file).onLine(21).atColumn(23);
  }

  @Test public void invalidCustomName() {
    JavaFileObject file = JavaFileObjects.forResource("bad/InvalidCustomName.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining("\"SillyFactory!\" is not a valid Java identifier")
            .in(file).onLine(20);
  }

  @Test public void factoryExtendingAbstractClass() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/FactoryExtendingAbstractClass.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/FactoryExtendingAbstractClassFactory.java"));
  }

  @Test public void factoryExtendingAbstractClass_withConstructorParams() {
    JavaFileObject file =
        JavaFileObjects.forResource("good/FactoryExtendingAbstractClassWithConstructorParams.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining(
            "tests.FactoryExtendingAbstractClassWithConstructorParams.AbstractFactory "
                + "is not a valid supertype for a factory. "
                + "Factory supertypes must have a no-arg constructor.")
                    .in(file).onLine(21);
  }

  @Test public void factoryExtendingAbstractClass_multipleConstructors() {
    JavaFileObject file = JavaFileObjects.forResource(
        "good/FactoryExtendingAbstractClassWithMultipleConstructors.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError();
  }

  @Test public void factoryExtendingInterface() {
    JavaFileObject file = JavaFileObjects.forResource("bad/InterfaceSupertype.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining("java.lang.Runnable is not a valid supertype for a factory. "
            + "Supertypes must be non-final classes.")
                .in(file).onLine(20);
  }

  @Test public void factoryExtendingEnum() {
    JavaFileObject file = JavaFileObjects.forResource("bad/EnumSupertype.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining(
            "java.util.concurrent.TimeUnit is not a valid supertype for a factory. "
                + "Supertypes must be non-final classes.")
                    .in(file).onLine(21);
  }

  @Test public void factoryExtendingFinalClass() {
    JavaFileObject file = JavaFileObjects.forResource("bad/FinalSupertype.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .failsToCompile()
        .withErrorContaining("java.lang.Boolean is not a valid supertype for a factory. "
            + "Supertypes must be non-final classes.")
                .in(file).onLine(20);
  }

  @Test public void factoryImplementingGenericInterfaceExtension() {
    JavaFileObject file =
        JavaFileObjects.forResource("good/FactoryImplementingGenericInterfaceExtension.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource(
            "expected/FactoryImplementingGenericInterfaceExtensionFactory.java"));
  }

  @Test public void multipleFactoriesImpementingInterface() {
    JavaFileObject file =
        JavaFileObjects.forResource("good/MultipleFactoriesImplementingInterface.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource(
                "expected/MultipleFactoriesImplementingInterface_ClassAFactory.java"),
            JavaFileObjects.forResource(
                "expected/MultipleFactoriesImplementingInterface_ClassBFactory.java"));
  }

  @Test public void classUsingQualifierWithArgs() {
    assertAbout(javaSources())
        .that(ImmutableSet.of(
            JavaFileObjects.forResource("support/QualifierWithArgs.java"),
            JavaFileObjects.forResource("good/ClassUsingQualifierWithArgs.java")))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/ClassUsingQualifierWithArgsFactory.java"));
  }

  @Test public void factoryImplementingInterfaceWhichRedeclaresCreateMethods() {
    JavaFileObject file =
        JavaFileObjects.forResource("good/FactoryImplementingCreateMethod.java");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
        JavaFileObjects.forResource(
            "expected/FactoryImplementingCreateMethod_ConcreteClassFactory.java"));
  }

  @Test public void nullableParams() {
    assertAbout(javaSources())
        .that(ImmutableSet.of(
            JavaFileObjects.forResource("good/SimpleClassNullableParameters.java"),
            JavaFileObjects.forResource("support/AQualifier.java"),
            JavaFileObjects.forResource("support/BQualifier.java")))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/SimpleClassNullableParametersFactory.java"));
  }

  @Test public void customNullableType() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/CustomNullable.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forResource("expected/CustomNullableFactory.java"));
  }

  @Test public void multipleProvidedParamsWithSameKey() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/MultipleProvidedParamsSameKey.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/MultipleProvidedParamsSameKeyFactory.java"));
  }

  @Test public void providerArgumentToCreateMethod() {
    assertAbout(javaSource())
        .that(JavaFileObjects.forResource("good/ProviderArgumentToCreateMethod.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource("expected/ProviderArgumentToCreateMethodFactory.java"));
  }

  @Test public void multipleFactoriesConflictingParameterNames() {
    assertThat(
            JavaFileObjects.forResource("good/MultipleFactoriesConflictingParameterNames.java"),
            JavaFileObjects.forResource("support/AQualifier.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and().generatesSources(
            JavaFileObjects.forResource(
                "expected/MultipleFactoriesConflictingParameterNamesFactory.java"));
  }

  @Test public void factoryVarargs() {
    assertThat(JavaFileObjects.forResource("good/SimpleClassVarargs.java"))
        .processedWith(new AutoFactoryProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(JavaFileObjects.forResource("expected/SimpleClassVarargsFactory.java"));
  }
}
