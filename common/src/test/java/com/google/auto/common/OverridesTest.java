/*
 * Copyright 2016 Google LLC
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
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.common.base.Converter;
import com.google.common.base.Optional;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.io.Files;
import com.google.common.truth.Expect;
import com.google.testing.compile.CompilationRule;
import java.io.File;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.model.Statement;

/**
 * Tests that the {@link Overrides} class has behaviour consistent with javac. We test this in
 * two ways: once with {@link Overrides.ExplicitOverrides} using javac's own {@link Elements} and
 * {@link Types}, and once with it using the version of those objects from the Eclipse compiler
 * (ecj).
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(Parameterized.class)
public class OverridesTest {
  @Parameterized.Parameters(name = "{0}")
  public static ImmutableList<CompilerType> data() {
    return ImmutableList.of(CompilerType.JAVAC, CompilerType.ECJ);
  }

  @Rule public CompilationRule compilation = new CompilationRule();
  @Rule public EcjCompilationRule ecjCompilation = new EcjCompilationRule();
  @Rule public Expect expect = Expect.create();

  public enum CompilerType {
    JAVAC {
      @Override
      void initUtils(OverridesTest test) {
        test.typeUtils = test.compilation.getTypes();
        test.elementUtils = test.compilation.getElements();
      }
    },
    ECJ {
      @Override
      void initUtils(OverridesTest test) {
        test.typeUtils = test.ecjCompilation.types;
        test.elementUtils = test.ecjCompilation.elements;
      }
    };

    abstract void initUtils(OverridesTest test);
  }

  private final CompilerType compilerType;

  private Types typeUtils;
  private Elements elementUtils;
  private Elements javacElementUtils;
  private Overrides javacOverrides;
  private Overrides.ExplicitOverrides explicitOverrides;

  public OverridesTest(CompilerType compilerType) {
    this.compilerType = compilerType;
  }

  @Before
  public void initializeTestElements() {
    javacElementUtils = compilation.getElements();
    javacOverrides = new Overrides.NativeOverrides(javacElementUtils);
    compilerType.initUtils(this);
    explicitOverrides = new Overrides.ExplicitOverrides(typeUtils);
  }

  static class TypesForInheritance {
    interface One {
      void m();

      void m(String x);

      void n();
    }

    interface Two {
      void m();

      void m(int x);
    }

    static class Parent {
      public void m() {}
    }

    static class ChildOfParent extends Parent {}

    static class ChildOfOne implements One {
      @Override
      public void m() {}

      @Override
      public void m(String x) {}

      @Override
      public void n() {}
    }

    static class ChildOfOneAndTwo implements One, Two {
      @Override
      public void m() {}

      @Override
      public void m(String x) {}

      @Override
      public void m(int x) {}

      @Override
      public void n() {}
    }

    static class ChildOfParentAndOne extends Parent implements One {
      @Override
      public void m() {}

      @Override
      public void m(String x) {}

      @Override
      public void n() {}
    }

    static class ChildOfParentAndOneAndTwo extends Parent implements One, Two {
      @Override
      public void m(String x) {}

      @Override
      public void m(int x) {}

      @Override
      public void n() {}
    }

    abstract static class AbstractChildOfOne implements One {}

    abstract static class AbstractChildOfOneAndTwo implements One, Two {}

    abstract static class AbstractChildOfParentAndOneAndTwo extends Parent implements One, Two {}
  }

  static class MoreTypesForInheritance {
    interface Key {}

    interface BindingType {}

    interface ContributionType {}

    interface HasKey {
      Key key();
    }

    interface HasBindingType {
      BindingType bindingType();
    }

    interface HasContributionType {
      ContributionType contributionType();
    }

    abstract static class BindingDeclaration implements HasKey {
      abstract Optional<Element> bindingElement();

      abstract Optional<TypeElement> contributingModule();
    }

    abstract static class MultibindingDeclaration extends BindingDeclaration
        implements HasBindingType, HasContributionType {
      @Override
      public abstract Key key();

      @Override
      public abstract ContributionType contributionType();

      @Override
      public abstract BindingType bindingType();
    }
  }

  static class TypesForVisibility {
    public abstract static class PublicGrandparent {
      public abstract String foo();
    }

    private static class PrivateParent extends PublicGrandparent {
      @Override
      public String foo() {
        return "foo";
      }
    }

    static class Child extends PrivateParent {}
  }

  static class TypesForGenerics {
    interface GCollection<E> {
      boolean add(E x);
    }

    interface GList<E> extends GCollection<E> {
      @Override
      boolean add(E x);
    }

    static class StringList implements GList<String> {
      @Override
      public boolean add(String x) {
        return false;
      }
    }

    @SuppressWarnings("rawtypes")
    static class RawList implements GList {
      @Override
      public boolean add(Object x) {
        return false;
      }
    }
  }

  @SuppressWarnings("rawtypes")
  static class TypesForRaw {
    static class RawParent {
      void frob(List x) {}
    }

    static class RawChildOfRaw extends RawParent {
      @Override
      void frob(List x) {}
    }

    static class NonRawParent {
      void frob(List<String> x) {}
    }

    static class RawChildOfNonRaw extends NonRawParent {
      @Override
      void frob(List x) {}
    }
  }

  @Test
  public void overridesInheritance() {
    checkOverridesInContainedClasses(TypesForInheritance.class);
  }

  @Test
  public void overridesMoreInheritance() {
    checkOverridesInContainedClasses(MoreTypesForInheritance.class);
  }

  @Test
  public void overridesVisibility() {
    checkOverridesInContainedClasses(TypesForVisibility.class);
  }

  @Test
  public void overridesGenerics() {
    checkOverridesInContainedClasses(TypesForGenerics.class);
  }

  @Test
  public void overridesRaw() {
    checkOverridesInContainedClasses(TypesForRaw.class);
  }

  // Test a tricky diamond inheritance hierarchy:
  //               Collection
  //              /          \
  // AbstractCollection     List
  //              \          /
  //              AbstractList
  // This also tests that we do the right thing with generics, since naively the TypeMirror
  // that you get for List<E> will not appear to be a subtype of the one you get for Collection<E>
  // since the two Es are not the same.
  @Test
  public void overridesDiamond() {
    checkOverridesInSet(
        ImmutableSet.<Class<?>>of(
            Collection.class, List.class, AbstractCollection.class, AbstractList.class));
  }

  private void checkOverridesInContainedClasses(Class<?> container) {
    checkOverridesInSet(ImmutableSet.copyOf(container.getDeclaredClasses()));
  }

  private void checkOverridesInSet(ImmutableSet<Class<?>> testClasses) {
    assertThat(testClasses).isNotEmpty();
    ImmutableSet.Builder<TypeElement> testTypesBuilder = ImmutableSet.builder();
    for (Class<?> testClass : testClasses) {
      testTypesBuilder.add(getTypeElement(testClass));
    }
    ImmutableSet<TypeElement> testTypes = testTypesBuilder.build();
    ImmutableSet.Builder<ExecutableElement> testMethodsBuilder = ImmutableSet.builder();
    for (TypeElement testType : testTypes) {
      testMethodsBuilder.addAll(methodsIn(testType.getEnclosedElements()));
    }
    ImmutableSet<ExecutableElement> testMethods = testMethodsBuilder.build();
    for (TypeElement in : testTypes) {
      TypeElement javacIn = javacType(in);
      List<ExecutableElement> inMethods = methodsIn(elementUtils.getAllMembers(in));
      for (ExecutableElement overrider : inMethods) {
        ExecutableElement javacOverrider = javacMethod(overrider);
        for (ExecutableElement overridden : testMethods) {
          ExecutableElement javacOverridden = javacMethod(overridden);
          boolean javacSays = javacOverrides.overrides(javacOverrider, javacOverridden, javacIn);
          boolean weSay = explicitOverrides.overrides(overrider, overridden, in);
          if (javacSays != weSay) {
            expect
                .withMessage(
                    "%s.%s overrides %s.%s in %s: javac says %s, we say %s",
                    overrider.getEnclosingElement(),
                    overrider,
                    overridden.getEnclosingElement(),
                    overridden,
                    in,
                    javacSays,
                    weSay)
                .fail();
          }
        }
      }
    }
  }

  private TypeElement getTypeElement(Class<?> c) {
    return elementUtils.getTypeElement(c.getCanonicalName());
  }

  private ExecutableElement getMethod(TypeElement in, String name, TypeKind... parameterTypeKinds) {
    ExecutableElement found = null;
    methods:
    for (ExecutableElement method : methodsIn(in.getEnclosedElements())) {
      if (method.getSimpleName().contentEquals(name)
          && method.getParameters().size() == parameterTypeKinds.length) {
        for (int i = 0; i < parameterTypeKinds.length; i++) {
          if (method.getParameters().get(i).asType().getKind() != parameterTypeKinds[i]) {
            continue methods;
          }
        }
        assertThat(found).isNull();
        found = method;
      }
    }
    assertThat(found).isNotNull();
    return requireNonNull(found);
  }

  // These skeletal parallels to the real collection classes ensure that the test is independent
  // of the details of those classes, for example whether List<E> redeclares add(E) even though
  // it also inherits it from Collection<E>.

  private interface XCollection<E> {
    boolean add(E e);
  }

  private interface XList<E> extends XCollection<E> {}

  private abstract static class XAbstractCollection<E> implements XCollection<E> {
    @Override
    public boolean add(E e) {
      return false;
    }
  }

  private abstract static class XAbstractList<E> extends XAbstractCollection<E>
      implements XList<E> {
    @Override
    public boolean add(E e) {
      return true;
    }
  }

  private abstract static class XStringList extends XAbstractList<String> {}

  private abstract static class XAbstractStringList implements XList<String> {}

  private abstract static class XNumberList<E extends Number> extends XAbstractList<E> {}

  // Parameter of add(E) in StringList is String.
  // That means that we successfully recorded E[AbstractList] = String and E[List] = E[AbstractList]
  // and String made it all the way through.
  @Test
  public void methodParameters_StringList() {
    TypeElement xAbstractList = getTypeElement(XAbstractList.class);
    TypeElement xStringList = getTypeElement(XStringList.class);
    TypeElement string = getTypeElement(String.class);

    ExecutableElement add = getMethod(xAbstractList, "add", TypeKind.TYPEVAR);
    List<TypeMirror> params = explicitOverrides.erasedParameterTypes(add, xStringList);
    List<TypeMirror> expectedParams = ImmutableList.of(string.asType());
    assertTypeListsEqual(params, expectedParams);
  }

  // Parameter of add(E) in AbstractStringList is String.
  // That means that we successfully recorded E[List] = String and E[Collection] = E[List].
  @Test
  public void methodParameters_AbstractStringList() {
    TypeElement xCollection = getTypeElement(XCollection.class);
    TypeElement xAbstractStringList = getTypeElement(XAbstractStringList.class);
    TypeElement string = getTypeElement(String.class);

    ExecutableElement add = getMethod(xCollection, "add", TypeKind.TYPEVAR);

    List<TypeMirror> params = explicitOverrides.erasedParameterTypes(add, xAbstractStringList);
    List<TypeMirror> expectedParams = ImmutableList.of(string.asType());
    assertTypeListsEqual(params, expectedParams);
  }

  // Parameter of add(E) in NumberList is Number.
  // That means that we successfully recorded E[AbstractList] = Number and on from
  // there, with Number being used because it is the erasure of <E extends Number>.
  @Test
  public void methodParams_NumberList() {
    TypeElement xCollection = getTypeElement(XCollection.class);
    TypeElement xNumberList = getTypeElement(XNumberList.class);
    TypeElement number = getTypeElement(Number.class);

    ExecutableElement add = getMethod(xCollection, "add", TypeKind.TYPEVAR);

    List<TypeMirror> params = explicitOverrides.erasedParameterTypes(add, xNumberList);
    List<TypeMirror> expectedParams = ImmutableList.of(number.asType());
    assertTypeListsEqual(params, expectedParams);
  }

  // This is derived from a class that provoked a StackOverflowError in an earlier version.
  private abstract static class StringToRangeConverter<T extends Comparable<T>>
      extends Converter<String, Range<T>> {
    @Override
    protected String doBackward(Range<T> b) {
      return "";
    }
  }

  @Test
  public void methodParams_RecursiveBound() {
    TypeElement stringToRangeConverter = getTypeElement(StringToRangeConverter.class);
    TypeElement range = getTypeElement(Range.class);
    ExecutableElement valueConverter =
        getMethod(stringToRangeConverter, "doBackward", TypeKind.DECLARED);
    List<TypeMirror> params =
        explicitOverrides.erasedParameterTypes(valueConverter, stringToRangeConverter);
    List<TypeMirror> expectedParams =
        ImmutableList.<TypeMirror>of(typeUtils.erasure(range.asType()));
    assertTypeListsEqual(params, expectedParams);
  }

  @Test
  public void methodFromSuperclasses() {
    TypeElement xAbstractCollection = getTypeElement(XAbstractCollection.class);
    TypeElement xAbstractList = getTypeElement(XAbstractList.class);
    TypeElement xAbstractStringList = getTypeElement(XAbstractStringList.class);
    TypeElement xStringList = getTypeElement(XStringList.class);

    ExecutableElement add = getMethod(xAbstractCollection, "add", TypeKind.TYPEVAR);

    ExecutableElement addInAbstractStringList =
        explicitOverrides.methodFromSuperclasses(xAbstractStringList, add);
    assertThat(addInAbstractStringList).isNull();

    ExecutableElement addInStringList = explicitOverrides.methodFromSuperclasses(xStringList, add);
    assertThat(requireNonNull(addInStringList).getEnclosingElement()).isEqualTo(xAbstractList);
  }

  @Test
  public void methodFromSuperinterfaces() {
    TypeElement xCollection = getTypeElement(XCollection.class);
    TypeElement xAbstractList = getTypeElement(XAbstractList.class);
    TypeElement xAbstractStringList = getTypeElement(XAbstractStringList.class);
    TypeElement xNumberList = getTypeElement(XNumberList.class);
    TypeElement xList = getTypeElement(XList.class);

    ExecutableElement add = getMethod(xCollection, "add", TypeKind.TYPEVAR);

    ExecutableElement addInAbstractStringList =
        explicitOverrides.methodFromSuperinterfaces(xAbstractStringList, add);
    assertThat(requireNonNull(addInAbstractStringList).getEnclosingElement())
        .isEqualTo(xCollection);

    ExecutableElement addInNumberList =
        explicitOverrides.methodFromSuperinterfaces(xNumberList, add);
    assertThat(requireNonNull(addInNumberList).getEnclosingElement()).isEqualTo(xAbstractList);

    ExecutableElement addInList = explicitOverrides.methodFromSuperinterfaces(xList, add);
    assertThat(requireNonNull(addInList).getEnclosingElement()).isEqualTo(xCollection);
  }

  private void assertTypeListsEqual(@Nullable List<TypeMirror> actual, List<TypeMirror> expected) {
   requireNonNull(actual);
   assertThat(actual).hasSize(expected.size());
   for (int i = 0; i < actual.size(); i++) {
      assertThat(typeUtils.isSameType(actual.get(i), expected.get(i))).isTrue();
    }
  }

  // TODO(emcmanus): replace this with something from compile-testing when that's available.
  /**
   * An equivalent to {@link CompilationRule} that uses ecj (the Eclipse compiler) instead of javac.
   * If the parameterized test is not selecting ecj then this rule has no effect.
   */
  public class EcjCompilationRule implements TestRule {
    Elements elements;
    Types types;

    @Override
    public Statement apply(Statement base, Description description) {
      if (!compilerType.equals(CompilerType.ECJ)) {
        return base;
      }
      return new EcjCompilationStatement(base);
    }
  }

  private class EcjCompilationStatement extends Statement {
    private final Statement statement;

    EcjCompilationStatement(Statement base) {
      this.statement = base;
    }

    @Override
    public void evaluate() throws Throwable {
      File tmpDir = File.createTempFile("OverridesTest", "dir");
      tmpDir.delete();
      tmpDir.mkdir();
      File dummySourceFile = new File(tmpDir, "Dummy.java");
      try {
        Files.asCharSink(dummySourceFile, UTF_8).write("class Dummy {}");
        evaluate(dummySourceFile);
      } finally {
        dummySourceFile.delete();
        tmpDir.delete();
      }
    }

    private void evaluate(File dummySourceFile) throws Throwable {
      JavaCompiler compiler = new EclipseCompiler();
      StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8);
      // This hack is only needed in a Google-internal Java 8 environment where symbolic links make
      // it hard for ecj to find the boot class path. Elsewhere it is unnecessary but harmless.
      File rtJar = new File(StandardSystemProperty.JAVA_HOME.value() + "/lib/rt.jar");
      if (rtJar.exists()) {
        List<File> bootClassPath =
            ImmutableList.<File>builder()
                .add(rtJar)
                .addAll(fileManager.getLocation(StandardLocation.PLATFORM_CLASS_PATH))
                .build();
        fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, bootClassPath);
      }
      Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjects(dummySourceFile);
      JavaCompiler.CompilationTask task =
          compiler.getTask(null, fileManager, null, null, null, sources);
      EcjTestProcessor processor = new EcjTestProcessor(statement);
      task.setProcessors(ImmutableList.of(processor));
      assertThat(task.call()).isTrue();
      processor.maybeThrow();
    }
  }

  @SupportedAnnotationTypes("*")
  private class EcjTestProcessor extends AbstractProcessor {
    private final Statement statement;
    private Throwable thrown;

    EcjTestProcessor(Statement statement) {
      this.statement = statement;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (roundEnv.processingOver()) {
        ecjCompilation.elements = processingEnv.getElementUtils();
        ecjCompilation.types = processingEnv.getTypeUtils();
        try {
          statement.evaluate();
        } catch (Throwable t) {
          thrown = t;
        }
      }
      return false;
    }

    void maybeThrow() throws Throwable {
      if (thrown != null) {
        throw thrown;
      }
    }
  }

  private TypeElement javacType(TypeElement type) {
    return javacElementUtils.getTypeElement(type.getQualifiedName().toString());
  }

  private ExecutableElement javacMethod(ExecutableElement method) {
    if (elementUtils == javacElementUtils) {
      return method;
    }
    TypeElement containingType = MoreElements.asType(method.getEnclosingElement());
    TypeElement javacContainingType = javacType(containingType);
    List<ExecutableElement> candidates = new ArrayList<ExecutableElement>();
    methods:
    for (ExecutableElement javacMethod : methodsIn(javacContainingType.getEnclosedElements())) {
      if (javacMethod.getSimpleName().contentEquals(method.getSimpleName())
          && javacMethod.getParameters().size() == method.getParameters().size()) {
        for (int i = 0; i < method.getParameters().size(); i++) {
          VariableElement parameter = method.getParameters().get(i);
          VariableElement javacParameter = javacMethod.getParameters().get(i);
          if (!erasedToString(parameter.asType()).equals(erasedToString(javacParameter.asType()))) {
            continue methods;
          }
        }
        candidates.add(javacMethod);
      }
    }
    if (candidates.size() == 1) {
      return candidates.get(0);
    } else {
      throw new IllegalStateException(
          "Expected one javac method matching " + method + " but found " + candidates);
    }
  }

  private static String erasedToString(TypeMirror type) {
    return ERASED_STRING_TYPE_VISITOR.visit(type);
  }

  private static final TypeVisitor<String, Void> ERASED_STRING_TYPE_VISITOR =
      new SimpleTypeVisitor6<String, Void>() {
        @Override
        protected String defaultAction(TypeMirror e, Void p) {
          return e.toString();
        }

        @Override
        public String visitArray(ArrayType t, Void p) {
          return visit(t.getComponentType()) + "[]";
        }

        @Override
        public String visitDeclared(DeclaredType t, Void p) {
          return MoreElements.asType(t.asElement()).getQualifiedName().toString();
        }

        @Override
        public String visitTypeVariable(TypeVariable t, Void p) {
          return visit(t.getUpperBound());
        }
      };
}
