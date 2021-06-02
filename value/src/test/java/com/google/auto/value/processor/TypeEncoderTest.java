/*
 * Copyright 2017 Google LLC
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
package com.google.auto.value.processor;

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static java.util.stream.Collectors.joining;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.processor.MissingTypes.MissingTypeException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationRule;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link TypeEncoder}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class TypeEncoderTest {
  @Rule public final CompilationRule compilationRule = new CompilationRule();

  private Types typeUtils;
  private Elements elementUtils;

  @Before
  public void setUp() {
    typeUtils = compilationRule.getTypes();
    elementUtils = compilationRule.getElements();
  }

  /**
   * Assert that the fake program returned by fakeProgramForTypes has the given list of imports and
   * the given list of spellings. Here, "spellings" means the way each type is referenced in the
   * decoded program, for example {@code Timer} if {@code java.util.Timer} can be imported, or
   * {@code java.util.Timer} if not.
   *
   * <p>We construct a fake program that references each of the given types in turn.
   * TypeEncoder.decode doesn't have any real notion of Java syntax, so our program just consists of
   * START and END markers around the {@code `import`} tag, followed by each type in braces, as
   * encoded by TypeEncoder.encode. Once decoded, the program should consist of the appropriate
   * imports (inside START...END) and each type in braces, spelled appropriately.
   *
   * @param fakePackage the package that TypeEncoder should consider the fake program to be in.
   *     Classes in the same package don't usually need to be imported.
   */
  private void assertTypeImportsAndSpellings(
      Set<TypeMirror> types, String fakePackage, List<String> imports, List<String> spellings) {
    String fakeProgram =
        "START\n`import`\nEND\n"
            + types.stream().map(TypeEncoder::encode).collect(joining("}\n{", "{", "}"));
    String decoded =
        TypeEncoder.decode(
            fakeProgram, elementUtils, typeUtils, fakePackage, baseWithoutContainedTypes());
    String expected =
        "START\n"
            + imports.stream().map(s -> "import " + s + ";\n").collect(joining())
            + "\nEND\n"
            + spellings.stream().collect(joining("}\n{", "{", "}"));
    assertThat(decoded).isEqualTo(expected);
  }

  private static class MultipleBounds<K extends List<V> & Comparable<K>, V> {}

  @Test
  public void testImportsForNoTypes() {
    assertTypeImportsAndSpellings(
        typeMirrorSet(), "foo.bar", ImmutableList.of(), ImmutableList.of());
  }

  @Test
  public void testImportsForImplicitlyImportedTypes() {
    Set<TypeMirror> types =
        typeMirrorSet(
            typeMirrorOf(java.lang.String.class),
            typeMirrorOf(javax.management.MBeanServer.class), // Same package, so no import.
            typeUtils.getPrimitiveType(TypeKind.INT),
            typeUtils.getPrimitiveType(TypeKind.BOOLEAN));
    assertTypeImportsAndSpellings(
        types,
        "javax.management",
        ImmutableList.of(),
        ImmutableList.of("String", "MBeanServer", "int", "boolean"));
  }

  @Test
  public void testImportsForPlainTypes() {
    Set<TypeMirror> types =
        typeMirrorSet(
            typeUtils.getPrimitiveType(TypeKind.INT),
            typeMirrorOf(java.lang.String.class),
            typeMirrorOf(java.net.Proxy.class),
            typeMirrorOf(java.net.Proxy.Type.class),
            typeMirrorOf(java.util.regex.Pattern.class),
            typeMirrorOf(javax.management.MBeanServer.class));
    assertTypeImportsAndSpellings(
        types,
        "foo.bar",
        ImmutableList.of(
            "java.net.Proxy", "java.util.regex.Pattern", "javax.management.MBeanServer"),
        ImmutableList.of("int", "String", "Proxy", "Proxy.Type", "Pattern", "MBeanServer"));
  }

  @Test
  public void testImportsForComplicatedTypes() {
    TypeElement list = typeElementOf(java.util.List.class);
    TypeElement map = typeElementOf(java.util.Map.class);
    Set<TypeMirror> types =
        typeMirrorSet(
            typeUtils.getPrimitiveType(TypeKind.INT),
            typeMirrorOf(java.util.regex.Pattern.class),
            typeUtils.getDeclaredType(
                list, // List<Timer>
                typeMirrorOf(java.util.Timer.class)),
            typeUtils.getDeclaredType(
                map, // Map<? extends Timer, ? super BigInteger>
                typeUtils.getWildcardType(typeMirrorOf(java.util.Timer.class), null),
                typeUtils.getWildcardType(null, typeMirrorOf(java.math.BigInteger.class))));
    // Timer is referenced twice but should obviously only be imported once.
    assertTypeImportsAndSpellings(
        types,
        "foo.bar",
        ImmutableList.of(
            "java.math.BigInteger",
            "java.util.List",
            "java.util.Map",
            "java.util.Timer",
            "java.util.regex.Pattern"),
        ImmutableList.of(
            "int", "Pattern", "List<Timer>", "Map<? extends Timer, ? super BigInteger>"));
  }

  @Test
  public void testImportsForArrayTypes() {
    TypeElement list = typeElementOf(java.util.List.class);
    TypeElement set = typeElementOf(java.util.Set.class);
    Set<TypeMirror> types =
        typeMirrorSet(
            typeUtils.getArrayType(typeUtils.getPrimitiveType(TypeKind.INT)),
            typeUtils.getArrayType(typeMirrorOf(java.util.regex.Pattern.class)),
            typeUtils.getArrayType( // Set<Matcher[]>[]
                typeUtils.getDeclaredType(
                    set, typeUtils.getArrayType(typeMirrorOf(java.util.regex.Matcher.class)))),
            typeUtils.getDeclaredType(
                list, // List<Timer[]>
                typeUtils.getArrayType(typeMirrorOf(java.util.Timer.class))));
    // Timer is referenced twice but should obviously only be imported once.
    assertTypeImportsAndSpellings(
        types,
        "foo.bar",
        ImmutableList.of(
            "java.util.List",
            "java.util.Set",
            "java.util.Timer",
            "java.util.regex.Matcher",
            "java.util.regex.Pattern"),
        ImmutableList.of("int[]", "Pattern[]", "Set<Matcher[]>[]", "List<Timer[]>"));
  }

  @Test
  public void testImportNestedType() {
    Set<TypeMirror> types = typeMirrorSet(typeMirrorOf(java.net.Proxy.Type.class));
    assertTypeImportsAndSpellings(
        types, "foo.bar", ImmutableList.of("java.net.Proxy"), ImmutableList.of("Proxy.Type"));
  }

  @Test
  public void testImportsForAmbiguousNames() {
    TypeMirror wildcard = typeUtils.getWildcardType(null, null);
    Set<TypeMirror> types =
        typeMirrorSet(
            typeUtils.getPrimitiveType(TypeKind.INT),
            typeMirrorOf(java.awt.List.class),
            typeMirrorOf(java.lang.String.class),
            typeUtils.getDeclaredType( // List<?>
                typeElementOf(java.util.List.class), wildcard),
            typeUtils.getDeclaredType( // Map<?, ?>
                typeElementOf(java.util.Map.class), wildcard, wildcard));
    assertTypeImportsAndSpellings(
        types,
        "foo.bar",
        ImmutableList.of("java.util.Map"),
        ImmutableList.of("int", "java.awt.List", "String", "java.util.List<?>", "Map<?, ?>"));
  }

  @Test
  public void testSimplifyJavaLangString() {
    Set<TypeMirror> types = typeMirrorSet(typeMirrorOf(java.lang.String.class));
    assertTypeImportsAndSpellings(types, "foo.bar", ImmutableList.of(), ImmutableList.of("String"));
  }

  @Test
  public void testSimplifyJavaLangThreadState() {
    Set<TypeMirror> types = typeMirrorSet(typeMirrorOf(java.lang.Thread.State.class));
    assertTypeImportsAndSpellings(
        types, "foo.bar", ImmutableList.of(), ImmutableList.of("Thread.State"));
  }

  @Test
  public void testSimplifyJavaLangNamesake() {
    TypeMirror javaLangType = typeMirrorOf(java.lang.RuntimePermission.class);
    TypeMirror notJavaLangType =
        typeMirrorOf(com.google.auto.value.processor.testclasses.RuntimePermission.class);
    Set<TypeMirror> types = typeMirrorSet(javaLangType, notJavaLangType);
    assertTypeImportsAndSpellings(
        types,
        "foo.bar",
        ImmutableList.of(),
        ImmutableList.of(javaLangType.toString(), notJavaLangType.toString()));
  }

  @Test
  public void testSimplifyComplicatedTypes() {
    // This test constructs a set of types and feeds them to TypeEncoder. Then it verifies that
    // the resultant rewrites of those types are what we would expect.
    TypeElement list = typeElementOf(java.util.List.class);
    TypeElement map = typeElementOf(java.util.Map.class);
    TypeMirror string = typeMirrorOf(java.lang.String.class);
    TypeMirror integer = typeMirrorOf(java.lang.Integer.class);
    TypeMirror pattern = typeMirrorOf(java.util.regex.Pattern.class);
    TypeMirror timer = typeMirrorOf(java.util.Timer.class);
    TypeMirror bigInteger = typeMirrorOf(java.math.BigInteger.class);
    ImmutableMap<TypeMirror, String> typeMap =
        ImmutableMap.<TypeMirror, String>builder()
            .put(typeUtils.getPrimitiveType(TypeKind.INT), "int")
            .put(typeUtils.getArrayType(typeUtils.getPrimitiveType(TypeKind.BYTE)), "byte[]")
            .put(pattern, "Pattern")
            .put(typeUtils.getArrayType(pattern), "Pattern[]")
            .put(typeUtils.getArrayType(typeUtils.getArrayType(pattern)), "Pattern[][]")
            .put(typeUtils.getDeclaredType(list, typeUtils.getWildcardType(null, null)), "List<?>")
            .put(typeUtils.getDeclaredType(list, timer), "List<Timer>")
            .put(typeUtils.getDeclaredType(map, string, integer), "Map<String, Integer>")
            .put(
                typeUtils.getDeclaredType(
                    map,
                    typeUtils.getWildcardType(timer, null),
                    typeUtils.getWildcardType(null, bigInteger)),
                "Map<? extends Timer, ? super BigInteger>")
            .build();
    assertTypeImportsAndSpellings(
        typeMap.keySet(),
        "foo.bar",
        ImmutableList.of(
            "java.math.BigInteger",
            "java.util.List",
            "java.util.Map",
            "java.util.Timer",
            "java.util.regex.Pattern"),
        ImmutableList.copyOf(typeMap.values()));
  }

  @Test
  public void testSimplifyMultipleBounds() {
    TypeElement multipleBoundsElement = typeElementOf(MultipleBounds.class);
    TypeMirror multipleBoundsMirror = multipleBoundsElement.asType();
    String text = "`import`\n";
    text += "{" + TypeEncoder.encode(multipleBoundsMirror) + "}";
    text += "{" + TypeEncoder.typeParametersString(multipleBoundsElement.getTypeParameters()) + "}";
    String myPackage = getClass().getPackage().getName();
    String decoded =
        TypeEncoder.decode(text, elementUtils, typeUtils, myPackage, baseWithoutContainedTypes());
    String expected =
        "import java.util.List;\n\n"
            + "{TypeEncoderTest.MultipleBounds<K, V>}"
            + "{<K extends List<V> & Comparable<K>, V>}";
    assertThat(decoded).isEqualTo(expected);
  }

  @SuppressWarnings("ClassCanBeStatic")
  static class Outer<T extends Number> {
    class InnerWithoutTypeParam {}

    class Middle<U> {
      class InnerWithTypeParam<V> {}
    }
  }

  @Test
  public void testOuterParameterizedInnerNot() {
    TypeElement outerElement = typeElementOf(Outer.class);
    DeclaredType doubleMirror = typeMirrorOf(Double.class);
    DeclaredType outerOfDoubleMirror = typeUtils.getDeclaredType(outerElement, doubleMirror);
    TypeElement innerWithoutTypeParamElement = typeElementOf(Outer.InnerWithoutTypeParam.class);
    DeclaredType parameterizedInnerWithoutTypeParam =
        typeUtils.getDeclaredType(outerOfDoubleMirror, innerWithoutTypeParamElement);
    String encoded = TypeEncoder.encode(parameterizedInnerWithoutTypeParam);
    String myPackage = getClass().getPackage().getName();
    String decoded =
        TypeEncoder.decode(
            encoded, elementUtils, typeUtils, myPackage, baseWithoutContainedTypes());
    String expected = "TypeEncoderTest.Outer<Double>.InnerWithoutTypeParam";
    assertThat(decoded).isEqualTo(expected);
  }

  @Test
  public void testOuterParameterizedInnerAlso() {
    TypeElement outerElement = typeElementOf(Outer.class);
    DeclaredType doubleMirror = typeMirrorOf(Double.class);
    DeclaredType outerOfDoubleMirror = typeUtils.getDeclaredType(outerElement, doubleMirror);
    TypeElement middleElement = typeElementOf(Outer.Middle.class);
    DeclaredType stringMirror = typeMirrorOf(String.class);
    DeclaredType middleOfStringMirror =
        typeUtils.getDeclaredType(outerOfDoubleMirror, middleElement, stringMirror);
    TypeElement innerWithTypeParamElement = typeElementOf(Outer.Middle.InnerWithTypeParam.class);
    DeclaredType integerMirror = typeMirrorOf(Integer.class);
    DeclaredType parameterizedInnerWithTypeParam =
        typeUtils.getDeclaredType(middleOfStringMirror, innerWithTypeParamElement, integerMirror);
    String encoded = TypeEncoder.encode(parameterizedInnerWithTypeParam);
    String myPackage = getClass().getPackage().getName();
    String decoded =
        TypeEncoder.decode(
            encoded, elementUtils, typeUtils, myPackage, baseWithoutContainedTypes());
    String expected = "TypeEncoderTest.Outer<Double>.Middle<String>.InnerWithTypeParam<Integer>";
    assertThat(decoded).isEqualTo(expected);
  }

  private static Set<TypeMirror> typeMirrorSet(TypeMirror... typeMirrors) {
    Set<TypeMirror> set = new TypeMirrorSet();
    for (TypeMirror typeMirror : typeMirrors) {
      assertThat(set.add(typeMirror)).isTrue();
    }
    return set;
  }

  private TypeElement typeElementOf(Class<?> c) {
    return elementUtils.getTypeElement(c.getCanonicalName());
  }

  private DeclaredType typeMirrorOf(Class<?> c) {
    return MoreTypes.asDeclared(typeElementOf(c).asType());
  }

  /**
   * Returns a "base type" for TypeSimplifier that does not contain any nested types. The point
   * being that every {@code TypeSimplifier} has a base type that the class being generated is going
   * to extend, and if that class has nested types they will be in scope, and therefore a possible
   * source of ambiguity.
   */
  private TypeMirror baseWithoutContainedTypes() {
    return typeMirrorOf(Object.class);
  }

  // This test checks that we correctly throw MissingTypeException if there is an ErrorType anywhere
  // inside a type we are asked to simplify. There's no way to get an ErrorType from typeUtils or
  // elementUtils, so we need to fire up the compiler with an erroneous source file and use an
  // annotation processor to capture the resulting ErrorType. Then we can run tests within that
  // annotation processor, and propagate any failures out of this test.
  @Test
  public void testErrorTypes() {
    JavaFileObject source =
        JavaFileObjects.forSourceString(
            "ExtendsUndefinedType", "class ExtendsUndefinedType extends UndefinedParent {}");
    Compilation compilation = javac().withProcessors(new ErrorTestProcessor()).compile(source);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("UndefinedParent");
    assertThat(compilation).hadErrorCount(1);
  }

  @SupportedAnnotationTypes("*")
  private static class ErrorTestProcessor extends AbstractProcessor {
    Types typeUtils;
    Elements elementUtils;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (roundEnv.processingOver()) {
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        test();
      }
      return false;
    }

    private void test() {
      TypeElement extendsUndefinedType = elementUtils.getTypeElement("ExtendsUndefinedType");
      ErrorType errorType = (ErrorType) extendsUndefinedType.getSuperclass();
      TypeElement list = elementUtils.getTypeElement("java.util.List");
      TypeMirror listOfError = typeUtils.getDeclaredType(list, errorType);
      TypeMirror queryExtendsError = typeUtils.getWildcardType(errorType, null);
      TypeMirror listOfQueryExtendsError = typeUtils.getDeclaredType(list, queryExtendsError);
      TypeMirror querySuperError = typeUtils.getWildcardType(null, errorType);
      TypeMirror listOfQuerySuperError = typeUtils.getDeclaredType(list, querySuperError);
      TypeMirror arrayOfError = typeUtils.getArrayType(errorType);
      testErrorType(errorType);
      testErrorType(listOfError);
      testErrorType(listOfQueryExtendsError);
      testErrorType(listOfQuerySuperError);
      testErrorType(arrayOfError);
    }

    @SuppressWarnings("MissingFail") // error message gets converted into assertion failure
    private void testErrorType(TypeMirror typeWithError) {
      try {
        TypeEncoder.encode(typeWithError);
        processingEnv
            .getMessager()
            .printMessage(Diagnostic.Kind.ERROR, "Expected exception for type: " + typeWithError);
      } catch (MissingTypeException expected) {
      }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }
  }
}
