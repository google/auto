/*
 * Copyright 2013 Google LLC
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

import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.Resources;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import javax.lang.model.SourceVersion;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Functional tests for the {@link AutoFactoryProcessor}. */
@RunWith(JUnit4.class)
public class AutoFactoryProcessorTest {
  private final Compiler javac = Compiler.javac().withProcessors(new AutoFactoryProcessor());

  @Test
  public void simpleClass() {
    Compilation compilation = javac.compile(JavaFileObjects.forResource("good/SimpleClass.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.SimpleClassFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/SimpleClassFactory.java"));
  }

  @Test
  public void simpleClassWithConstructorThrowsClause() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/SimpleClassThrows.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.SimpleClassThrowsFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/SimpleClassThrowsFactory.java"));
  }

  @Test
  public void nestedClasses() {
    Compilation compilation = javac.compile(JavaFileObjects.forResource("good/NestedClasses.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.NestedClasses_SimpleNestedClassFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/NestedClasses_SimpleNestedClassFactory.java"));
    assertThat(compilation)
        .generatedSourceFile("tests.NestedClassCustomNamedFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/NestedClassCustomNamedFactory.java"));
  }

  @Test
  public void simpleClassNonFinal() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/SimpleClassNonFinal.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.SimpleClassNonFinalFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/SimpleClassNonFinalFactory.java"));
  }

  @Test
  public void publicClass() {
    Compilation compilation = javac.compile(JavaFileObjects.forResource("good/PublicClass.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.PublicClassFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/PublicClassFactory.java"));
  }

  @Test
  public void simpleClassCustomName() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/SimpleClassCustomName.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.CustomNamedFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/CustomNamedFactory.java"));
  }

  @Test
  public void simpleClassMixedDeps() {
    Compilation compilation =
        javac.compile(
            JavaFileObjects.forResource("good/SimpleClassMixedDeps.java"),
            JavaFileObjects.forResource("support/AQualifier.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.SimpleClassMixedDepsFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/SimpleClassMixedDepsFactory.java"));
  }

  @Test
  public void simpleClassPassedDeps() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/SimpleClassPassedDeps.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.SimpleClassPassedDepsFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/SimpleClassPassedDepsFactory.java"));
  }

  @Test
  public void simpleClassProvidedDeps() {
    Compilation compilation =
        javac.compile(
            JavaFileObjects.forResource("support/AQualifier.java"),
            JavaFileObjects.forResource("support/BQualifier.java"),
            JavaFileObjects.forResource("good/SimpleClassProvidedDeps.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.SimpleClassProvidedDepsFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/SimpleClassProvidedDepsFactory.java"));
  }

  @Test
  public void simpleClassProvidedProviderDeps() {
    Compilation compilation =
        javac.compile(
            JavaFileObjects.forResource("support/AQualifier.java"),
            JavaFileObjects.forResource("support/BQualifier.java"),
            JavaFileObjects.forResource("good/SimpleClassProvidedProviderDeps.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.SimpleClassProvidedProviderDepsFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/SimpleClassProvidedProviderDepsFactory.java"));
  }

  @Test
  public void constructorAnnotated() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/ConstructorAnnotated.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.ConstructorAnnotatedFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/ConstructorAnnotatedFactory.java"));
  }

  @Test
  public void constructorWithThrowsClauseAnnotated() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/ConstructorAnnotatedThrows.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.ConstructorAnnotatedThrowsFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/ConstructorAnnotatedThrowsFactory.java"));
  }

  @Test
  public void constructorAnnotatedNonFinal() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/ConstructorAnnotatedNonFinal.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.ConstructorAnnotatedNonFinalFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/ConstructorAnnotatedNonFinalFactory.java"));
  }

  @Test
  public void simpleClassImplementingMarker() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/SimpleClassImplementingMarker.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.SimpleClassImplementingMarkerFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/SimpleClassImplementingMarkerFactory.java"));
  }

  @Test
  public void simpleClassImplementingSimpleInterface() {
    Compilation compilation =
        javac.compile(
            JavaFileObjects.forResource("good/SimpleClassImplementingSimpleInterface.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.SimpleClassImplementingSimpleInterfaceFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/SimpleClassImplementingSimpleInterfaceFactory.java"));
  }

  @Test
  public void mixedDepsImplementingInterfaces() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/MixedDepsImplementingInterfaces.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.MixedDepsImplementingInterfacesFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/MixedDepsImplementingInterfacesFactory.java"));
  }

  @Test
  public void failsWithMixedFinals() {
    JavaFileObject file = JavaFileObjects.forResource("bad/MixedFinals.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Cannot mix allowSubclasses=true and allowSubclasses=false in one factory.")
        .inFile(file)
        .onLine(24);
    assertThat(compilation)
        .hadErrorContaining(
            "Cannot mix allowSubclasses=true and allowSubclasses=false in one factory.")
        .inFile(file)
        .onLine(27);
  }

  @Test
  public void providedButNoAutoFactory() {
    JavaFileObject file = JavaFileObjects.forResource("bad/ProvidedButNoAutoFactory.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@Provided may only be applied to constructors requesting an auto-factory")
        .inFile(file)
        .onLineContaining("@Provided");
  }

  @Test
  public void providedOnMethodParameter() {
    JavaFileObject file = JavaFileObjects.forResource("bad/ProvidedOnMethodParameter.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Provided may only be applied to constructor parameters")
        .inFile(file)
        .onLineContaining("@Provided");
  }

  @Test
  public void invalidCustomName() {
    JavaFileObject file = JavaFileObjects.forResource("bad/InvalidCustomName.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("\"SillyFactory!\" is not a valid Java identifier")
        .inFile(file)
        .onLineContaining("SillyFactory!");
  }

  @Test
  public void factoryExtendingAbstractClass() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/FactoryExtendingAbstractClass.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.FactoryExtendingAbstractClassFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/FactoryExtendingAbstractClassFactory.java"));
  }

  @Test
  public void factoryWithConstructorThrowsClauseExtendingAbstractClass() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/FactoryExtendingAbstractClassThrows.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.FactoryExtendingAbstractClassThrowsFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/FactoryExtendingAbstractClassThrowsFactory.java"));
  }

  @Test
  public void factoryExtendingAbstractClass_withConstructorParams() {
    JavaFileObject file =
        JavaFileObjects.forResource("bad/FactoryExtendingAbstractClassWithConstructorParams.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "tests.FactoryExtendingAbstractClassWithConstructorParams.AbstractFactory is not a"
                + " valid supertype for a factory. Factory supertypes must have a no-arg"
                + " constructor.")
        .inFile(file)
        .onLineContaining("@AutoFactory");
  }

  @Test
  public void factoryExtendingAbstractClass_multipleConstructors() {
    JavaFileObject file =
        JavaFileObjects.forResource(
            "good/FactoryExtendingAbstractClassWithMultipleConstructors.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void factoryExtendingInterface() {
    JavaFileObject file = JavaFileObjects.forResource("bad/InterfaceSupertype.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "java.lang.Runnable is not a valid supertype for a factory. Supertypes must be"
                + " non-final classes.")
        .inFile(file)
        .onLineContaining("@AutoFactory");
  }

  @Test
  public void factoryExtendingEnum() {
    JavaFileObject file = JavaFileObjects.forResource("bad/EnumSupertype.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "java.util.concurrent.TimeUnit is not a valid supertype for a factory. Supertypes must"
                + " be non-final classes.")
        .inFile(file)
        .onLineContaining("@AutoFactory");
  }

  @Test
  public void factoryExtendingFinalClass() {
    JavaFileObject file = JavaFileObjects.forResource("bad/FinalSupertype.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "java.lang.Boolean is not a valid supertype for a factory. Supertypes must be"
                + " non-final classes.")
        .inFile(file)
        .onLineContaining("@AutoFactory");
  }

  @Test
  public void factoryImplementingGenericInterfaceExtension() {
    JavaFileObject file =
        JavaFileObjects.forResource("good/FactoryImplementingGenericInterfaceExtension.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.FactoryImplementingGenericInterfaceExtensionFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/FactoryImplementingGenericInterfaceExtensionFactory.java"));
  }

  @Test
  public void multipleFactoriesImpementingInterface() {
    JavaFileObject file =
        JavaFileObjects.forResource("good/MultipleFactoriesImplementingInterface.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.MultipleFactoriesImplementingInterface_ClassAFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/MultipleFactoriesImplementingInterface_ClassAFactory.java"));
    assertThat(compilation)
        .generatedSourceFile("tests.MultipleFactoriesImplementingInterface_ClassBFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/MultipleFactoriesImplementingInterface_ClassBFactory.java"));
  }

  @Test
  public void classUsingQualifierWithArgs() {
    Compilation compilation =
        javac.compile(
            JavaFileObjects.forResource("support/QualifierWithArgs.java"),
            JavaFileObjects.forResource("good/ClassUsingQualifierWithArgs.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.ClassUsingQualifierWithArgsFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/ClassUsingQualifierWithArgsFactory.java"));
  }

  @Test
  public void factoryImplementingInterfaceWhichRedeclaresCreateMethods() {
    JavaFileObject file = JavaFileObjects.forResource("good/FactoryImplementingCreateMethod.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.FactoryImplementingCreateMethod_ConcreteClassFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/FactoryImplementingCreateMethod_ConcreteClassFactory.java"));
  }

  @Test
  public void nullableParams() {
    Compilation compilation =
        javac.compile(
            JavaFileObjects.forResource("good/SimpleClassNullableParameters.java"),
            JavaFileObjects.forResource("support/AQualifier.java"),
            JavaFileObjects.forResource("support/BQualifier.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.SimpleClassNullableParametersFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/SimpleClassNullableParametersFactory.java"));
  }

  @Test
  public void customNullableType() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/CustomNullable.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.CustomNullableFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/CustomNullableFactory.java"));
  }

  @Test
  public void checkerFrameworkNullableType() {
    // TYPE_USE annotations are pretty much unusable with annotation processors on Java 8 because
    // of bugs that mean they only appear in the javax.lang.model API when the compiler feels like
    // it. Checking for a java.specification.version that does not start with "1." eliminates 8 and
    // any earlier version.
    assume().that(JAVA_SPECIFICATION_VERSION.value()).doesNotMatch("1\\..*");
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/CheckerFrameworkNullable.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.CheckerFrameworkNullableFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/CheckerFrameworkNullableFactory.java"));
  }

  @Test
  public void multipleProvidedParamsWithSameKey() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/MultipleProvidedParamsSameKey.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.MultipleProvidedParamsSameKeyFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/MultipleProvidedParamsSameKeyFactory.java"));
  }

  @Test
  public void providerArgumentToCreateMethod() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/ProviderArgumentToCreateMethod.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.ProviderArgumentToCreateMethodFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/ProviderArgumentToCreateMethodFactory.java"));
  }

  @Test
  public void multipleFactoriesConflictingParameterNames() {
    Compilation compilation =
        javac.compile(
            JavaFileObjects.forResource("good/MultipleFactoriesConflictingParameterNames.java"),
            JavaFileObjects.forResource("support/AQualifier.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.MultipleFactoriesConflictingParameterNamesFactory")
        .hasSourceEquivalentTo(
            loadExpectedFile("expected/MultipleFactoriesConflictingParameterNamesFactory.java"));
  }

  @Test
  public void factoryVarargs() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/SimpleClassVarargs.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.SimpleClassVarargsFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/SimpleClassVarargsFactory.java"));
  }

  @Test
  public void onlyPrimitives() {
    Compilation compilation =
        javac.compile(JavaFileObjects.forResource("good/OnlyPrimitives.java"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("tests.OnlyPrimitivesFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/OnlyPrimitivesFactory.java"));
  }

  @Test
  public void defaultPackage() {
    JavaFileObject file = JavaFileObjects.forResource("good/DefaultPackage.java");
    Compilation compilation = javac.compile(file);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("DefaultPackageFactory")
        .hasSourceEquivalentTo(loadExpectedFile("expected/DefaultPackageFactory.java"));
  }

  private JavaFileObject loadExpectedFile(String resourceName) {
    if (isJavaxAnnotationProcessingGeneratedAvailable()) {
      return JavaFileObjects.forResource(resourceName);
    }
    try {
      List<String> sourceLines = Resources.readLines(Resources.getResource(resourceName), UTF_8);
      replaceGeneratedImport(sourceLines);
      return JavaFileObjects.forSourceLines(
          resourceName.replace('/', '.').replace(".java", ""), sourceLines);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private boolean isJavaxAnnotationProcessingGeneratedAvailable() {
    return SourceVersion.latestSupported().compareTo(SourceVersion.RELEASE_8) > 0;
  }

  private static void replaceGeneratedImport(List<String> sourceLines) {
    int i = 0;
    int firstImport = Integer.MAX_VALUE;
    int lastImport = -1;
    for (String line : sourceLines) {
      if (line.startsWith("import ") && !line.startsWith("import static ")) {
        firstImport = min(firstImport, i);
        lastImport = max(lastImport, i);
      }
      i++;
    }
    if (lastImport >= 0) {
      List<String> importLines = sourceLines.subList(firstImport, lastImport + 1);
      importLines.replaceAll(
          line ->
              line.startsWith("import javax.annotation.processing.Generated;")
                  ? "import javax.annotation.Generated;"
                  : line);
      Collections.sort(importLines);
    }
  }
}
