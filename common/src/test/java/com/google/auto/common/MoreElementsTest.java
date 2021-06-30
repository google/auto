/*
 * Copyright 2014 Google LLC
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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.truth.Expect;
import com.google.testing.compile.CompilationRule;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MoreElementsTest {
  @Rule public CompilationRule compilation = new CompilationRule();
  @Rule public Expect expect = Expect.create();

  private Elements elements;
  private PackageElement javaLangPackageElement;
  private TypeElement objectElement;
  private TypeElement stringElement;

  @Before
  public void initializeTestElements() {
    this.elements = compilation.getElements();
    this.javaLangPackageElement = elements.getPackageElement("java.lang");
    this.objectElement = elements.getTypeElement(Object.class.getCanonicalName());
    this.stringElement = elements.getTypeElement(String.class.getCanonicalName());
  }

  @Test
  public void getPackage() {
    assertThat(MoreElements.getPackage(stringElement)).isEqualTo(javaLangPackageElement);
    for (Element childElement : stringElement.getEnclosedElements()) {
      assertThat(MoreElements.getPackage(childElement)).isEqualTo(javaLangPackageElement);
    }
  }

  @Test
  public void asPackage() {
    assertThat(MoreElements.asPackage(javaLangPackageElement)).isEqualTo(javaLangPackageElement);
  }

  @Test
  public void asPackage_illegalArgument() {
    try {
      MoreElements.asPackage(stringElement);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void asTypeElement() {
    Element typeElement = elements.getTypeElement(String.class.getCanonicalName());
    assertTrue(MoreElements.isType(typeElement));
    assertThat(MoreElements.asType(typeElement)).isEqualTo(typeElement);
  }

  @Test
  public void asTypeElement_notATypeElement() {
    TypeElement typeElement = elements.getTypeElement(String.class.getCanonicalName());
    for (ExecutableElement e : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
      assertFalse(MoreElements.isType(e));
      try {
        MoreElements.asType(e);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }
  }

  @Test
  public void asTypeParameterElement() {
    Element typeParameterElement =
        getOnlyElement(
            compilation
                .getElements()
                .getTypeElement(List.class.getCanonicalName())
                .getTypeParameters());
    assertThat(MoreElements.asTypeParameter(typeParameterElement)).isEqualTo(typeParameterElement);
  }

  @Test
  public void asTypeParameterElement_illegalArgument() {
    try {
      MoreElements.asTypeParameter(javaLangPackageElement);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void asType() {
    assertThat(MoreElements.asType(stringElement)).isEqualTo(stringElement);
  }

  @Test
  public void asType_illegalArgument() {
    assertFalse(MoreElements.isType(javaLangPackageElement));
    try {
      MoreElements.asType(javaLangPackageElement);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void asVariable() {
    for (Element variableElement : ElementFilter.fieldsIn(stringElement.getEnclosedElements())) {
      assertThat(MoreElements.asVariable(variableElement)).isEqualTo(variableElement);
    }
  }

  @Test
  public void asVariable_illegalArgument() {
    try {
      MoreElements.asVariable(javaLangPackageElement);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void asExecutable() {
    for (Element methodElement : ElementFilter.methodsIn(stringElement.getEnclosedElements())) {
      assertThat(MoreElements.asExecutable(methodElement)).isEqualTo(methodElement);
    }
    for (Element methodElement :
        ElementFilter.constructorsIn(stringElement.getEnclosedElements())) {
      assertThat(MoreElements.asExecutable(methodElement)).isEqualTo(methodElement);
    }
  }

  @Test
  public void asExecutable_illegalArgument() {
    try {
      MoreElements.asExecutable(javaLangPackageElement);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  private @interface InnerAnnotation {}

  @Documented
  @InnerAnnotation
  private @interface AnnotatedAnnotation {}

  @Test
  public void isAnnotationPresent() {
    TypeElement annotatedAnnotationElement =
        elements.getTypeElement(AnnotatedAnnotation.class.getCanonicalName());

    // Test Class API
    isAnnotationPresentAsserts(
        MoreElements.isAnnotationPresent(annotatedAnnotationElement, Documented.class),
        MoreElements.isAnnotationPresent(annotatedAnnotationElement, InnerAnnotation.class),
        MoreElements.isAnnotationPresent(annotatedAnnotationElement, SuppressWarnings.class));

    // Test String API
    String documentedName = Documented.class.getCanonicalName();
    String innerAnnotationName = InnerAnnotation.class.getCanonicalName();
    String suppressWarningsName = SuppressWarnings.class.getCanonicalName();
    isAnnotationPresentAsserts(
        MoreElements.isAnnotationPresent(annotatedAnnotationElement, documentedName),
        MoreElements.isAnnotationPresent(annotatedAnnotationElement, innerAnnotationName),
        MoreElements.isAnnotationPresent(annotatedAnnotationElement, suppressWarningsName));

    // Test TypeElement API
    TypeElement documentedElement = elements.getTypeElement(documentedName);
    TypeElement innerAnnotationElement = elements.getTypeElement(innerAnnotationName);
    TypeElement suppressWarningsElement = elements.getTypeElement(suppressWarningsName);
    isAnnotationPresentAsserts(
        MoreElements.isAnnotationPresent(annotatedAnnotationElement, documentedElement),
        MoreElements.isAnnotationPresent(annotatedAnnotationElement, innerAnnotationElement),
        MoreElements.isAnnotationPresent(annotatedAnnotationElement, suppressWarningsElement));
  }

  private void isAnnotationPresentAsserts(
      boolean isDocumentedPresent,
      boolean isInnerAnnotationPresent,
      boolean isSuppressWarningsPresent) {
    assertThat(isDocumentedPresent).isTrue();
    assertThat(isInnerAnnotationPresent).isTrue();
    assertThat(isSuppressWarningsPresent).isFalse();
  }

  @Test
  public void getAnnotationMirror() {
    TypeElement element =
        elements.getTypeElement(AnnotatedAnnotation.class.getCanonicalName());

    // Test Class API
    getAnnotationMirrorAsserts(
        MoreElements.getAnnotationMirror(element, Documented.class),
        MoreElements.getAnnotationMirror(element, InnerAnnotation.class),
        MoreElements.getAnnotationMirror(element, SuppressWarnings.class));

    // Test String API
    String documentedName = Documented.class.getCanonicalName();
    String innerAnnotationName = InnerAnnotation.class.getCanonicalName();
    String suppressWarningsName = SuppressWarnings.class.getCanonicalName();
    getAnnotationMirrorAsserts(
        MoreElements.getAnnotationMirror(element, documentedName),
        MoreElements.getAnnotationMirror(element, innerAnnotationName),
        MoreElements.getAnnotationMirror(element, suppressWarningsName));

    // Test TypeElement API
    TypeElement documentedElement = elements.getTypeElement(documentedName);
    TypeElement innerAnnotationElement = elements.getTypeElement(innerAnnotationName);
    TypeElement suppressWarningsElement = elements.getTypeElement(suppressWarningsName);
    getAnnotationMirrorAsserts(
        MoreElements.getAnnotationMirror(element, documentedElement),
        MoreElements.getAnnotationMirror(element, innerAnnotationElement),
        MoreElements.getAnnotationMirror(element, suppressWarningsElement));
  }

  private void getAnnotationMirrorAsserts(
      Optional<AnnotationMirror> documented,
      Optional<AnnotationMirror> innerAnnotation,
      Optional<AnnotationMirror> suppressWarnings) {
    expect.that(documented).isPresent();
    expect.that(innerAnnotation).isPresent();
    expect.that(suppressWarnings).isAbsent();

    Element annotationElement = documented.get().getAnnotationType().asElement();
    expect.that(MoreElements.isType(annotationElement)).isTrue();
    expect
        .that(MoreElements.asType(annotationElement).getQualifiedName().toString())
        .isEqualTo(Documented.class.getCanonicalName());

    annotationElement = innerAnnotation.get().getAnnotationType().asElement();
    expect.that(MoreElements.isType(annotationElement)).isTrue();
    expect
        .that(MoreElements.asType(annotationElement).getQualifiedName().toString())
        .isEqualTo(InnerAnnotation.class.getCanonicalName());
  }

  private abstract static class ParentClass {
    static void staticMethod() {}

    abstract String foo();

    @SuppressWarnings("unused")
    private void privateMethod() {}
  }

  private interface ParentInterface {
    static void staticMethod() {}

    abstract int bar();

    abstract int bar(long x);
  }

  private abstract static class Child extends ParentClass implements ParentInterface {
    static void staticMethod() {}

    @Override
    public int bar() {
      return 0;
    }

    abstract void baz();

    void buh(int x) {}

    void buh(int x, int y) {}
  }

  @Test
  public void getLocalAndInheritedMethods_Old() {
    Types types = compilation.getTypes();
    TypeMirror intMirror = types.getPrimitiveType(TypeKind.INT);
    TypeMirror longMirror = types.getPrimitiveType(TypeKind.LONG);
    TypeElement childType = elements.getTypeElement(Child.class.getCanonicalName());
    @SuppressWarnings("deprecation")
    Set<ExecutableElement> childTypeMethods =
        MoreElements.getLocalAndInheritedMethods(childType, elements);
    Set<ExecutableElement> objectMethods = visibleMethodsFromObject();
    assertThat(childTypeMethods).containsAtLeastElementsIn(objectMethods);
    Set<ExecutableElement> nonObjectMethods = Sets.difference(childTypeMethods, objectMethods);
    assertThat(nonObjectMethods)
        .containsExactly(
            getMethod(ParentInterface.class, "bar", longMirror),
            getMethod(ParentClass.class, "foo"),
            getMethod(Child.class, "bar"),
            getMethod(Child.class, "baz"),
            getMethod(Child.class, "buh", intMirror),
            getMethod(Child.class, "buh", intMirror, intMirror))
        .inOrder();
    ;
  }

  @Test
  public void getLocalAndInheritedMethods() {
    Types types = compilation.getTypes();
    TypeMirror intMirror = types.getPrimitiveType(TypeKind.INT);
    TypeMirror longMirror = types.getPrimitiveType(TypeKind.LONG);
    TypeElement childType = elements.getTypeElement(Child.class.getCanonicalName());
    @SuppressWarnings("deprecation")
    Set<ExecutableElement> childTypeMethods =
        MoreElements.getLocalAndInheritedMethods(childType, types, elements);
    Set<ExecutableElement> objectMethods = visibleMethodsFromObject();
    assertThat(childTypeMethods).containsAtLeastElementsIn(objectMethods);
    Set<ExecutableElement> nonObjectMethods = Sets.difference(childTypeMethods, objectMethods);
    assertThat(nonObjectMethods)
        .containsExactly(
            getMethod(ParentInterface.class, "bar", longMirror),
            getMethod(ParentClass.class, "foo"),
            getMethod(Child.class, "bar"),
            getMethod(Child.class, "baz"),
            getMethod(Child.class, "buh", intMirror),
            getMethod(Child.class, "buh", intMirror, intMirror))
        .inOrder();
  }

  @Test
  public void getAllMethods() {
    Types types = compilation.getTypes();
    TypeMirror intMirror = types.getPrimitiveType(TypeKind.INT);
    TypeMirror longMirror = types.getPrimitiveType(TypeKind.LONG);
    TypeElement childType = elements.getTypeElement(Child.class.getCanonicalName());
    @SuppressWarnings("deprecation")
    Set<ExecutableElement> childTypeMethods =
        MoreElements.getAllMethods(childType, types, elements);
    Set<ExecutableElement> objectMethods = allMethodsFromObject();
    assertThat(childTypeMethods).containsAtLeastElementsIn(objectMethods);
    Set<ExecutableElement> nonObjectMethods = Sets.difference(childTypeMethods, objectMethods);
    assertThat(nonObjectMethods)
        .containsExactly(
            getMethod(ParentInterface.class, "staticMethod"),
            getMethod(ParentInterface.class, "bar", longMirror),
            getMethod(ParentClass.class, "staticMethod"),
            getMethod(ParentClass.class, "foo"),
            getMethod(ParentClass.class, "privateMethod"),
            getMethod(Child.class, "staticMethod"),
            getMethod(Child.class, "bar"),
            getMethod(Child.class, "baz"),
            getMethod(Child.class, "buh", intMirror),
            getMethod(Child.class, "buh", intMirror, intMirror))
        .inOrder();
  }

  static class Injectable {}

  public static class MenuManager {
    public interface ParentComponent extends MenuItemA.ParentComponent, MenuItemB.ParentComponent {}
  }

  public static class MenuItemA {
    public interface ParentComponent {
      Injectable injectable();
    }
  }

  public static class MenuItemB {
    public interface ParentComponent {
      Injectable injectable();
    }
  }

  public static class Main {
    public interface ParentComponent extends MenuManager.ParentComponent {}
  }

  // Example from https://github.com/williamlian/daggerbug
  @Test
  public void getLocalAndInheritedMethods_DaggerBug() {
    TypeElement main = elements.getTypeElement(Main.ParentComponent.class.getCanonicalName());
    Set<ExecutableElement> methods =
        MoreElements.getLocalAndInheritedMethods(main, compilation.getTypes(), elements);
    assertThat(methods).hasSize(1);
    ExecutableElement method = methods.iterator().next();
    assertThat(method.getSimpleName().toString()).isEqualTo("injectable");
    assertThat(method.getParameters()).isEmpty();
  }

  private Set<ExecutableElement> visibleMethodsFromObject() {
    Types types = compilation.getTypes();
    TypeMirror intMirror = types.getPrimitiveType(TypeKind.INT);
    TypeMirror longMirror = types.getPrimitiveType(TypeKind.LONG);
    Set<ExecutableElement> methods = new HashSet<ExecutableElement>();
    for (ExecutableElement method : ElementFilter.methodsIn(objectElement.getEnclosedElements())) {
      if (method.getModifiers().contains(Modifier.PUBLIC)
          || method.getModifiers().contains(Modifier.PROTECTED)) {
        methods.add(method);
      }
    }
    assertThat(methods)
        .containsAtLeast(
            getMethod(Object.class, "clone"),
            getMethod(Object.class, "finalize"),
            getMethod(Object.class, "wait"),
            getMethod(Object.class, "wait", longMirror),
            getMethod(Object.class, "wait", longMirror, intMirror));
    return methods;
  }

  private Set<ExecutableElement> allMethodsFromObject() {
    Types types = compilation.getTypes();
    TypeMirror intMirror = types.getPrimitiveType(TypeKind.INT);
    TypeMirror longMirror = types.getPrimitiveType(TypeKind.LONG);
    Set<ExecutableElement> methods = new HashSet<>();
    methods.addAll(ElementFilter.methodsIn(objectElement.getEnclosedElements()));
    assertThat(methods)
        .containsAtLeast(
            getMethod(Object.class, "clone"),
            getMethod(Object.class, "registerNatives"),
            getMethod(Object.class, "finalize"),
            getMethod(Object.class, "wait"),
            getMethod(Object.class, "wait", longMirror),
            getMethod(Object.class, "wait", longMirror, intMirror));
    return methods;
  }

  private ExecutableElement getMethod(Class<?> c, String methodName, TypeMirror... parameterTypes) {
    TypeElement type = elements.getTypeElement(c.getCanonicalName());
    Types types = compilation.getTypes();
    ExecutableElement found = null;
    for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
      if (method.getSimpleName().contentEquals(methodName)
          && method.getParameters().size() == parameterTypes.length) {
        boolean match = true;
        for (int i = 0; i < parameterTypes.length; i++) {
          TypeMirror expectedType = parameterTypes[i];
          TypeMirror actualType = method.getParameters().get(i).asType();
          match &= types.isSameType(expectedType, actualType);
        }
        if (match) {
          assertThat(found).isNull();
          found = method;
        }
      }
    }
    assertWithMessage(methodName + Arrays.toString(parameterTypes)).that(found).isNotNull();
    return requireNonNull(found);
  }

  private abstract static class AbstractAbstractList extends AbstractList<String> {}

  private static class ConcreteAbstractList<T> extends AbstractList<T> {
    @Override
    public int size() {
      return 0;
    }

    @Override
    public T get(int index) {
      throw new NoSuchElementException();
    }
  }

  private Set<String> abstractMethodNamesFrom(Set<ExecutableElement> methods) {
    ImmutableSet.Builder<String> abstractMethodNames = ImmutableSet.builder();
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(Modifier.ABSTRACT)) {
        abstractMethodNames.add(method.getSimpleName().toString());
      }
    }
    return abstractMethodNames.build();
  }

  // Test that getLocalAndInheritedMethods does the right thing with AbstractList. That class
  // inherits from Collection along two paths, via its parent AbstractCollection (which implements
  // Collection) and via its parent List (which extends Collection). Bugs in the past have meant
  // that the multiple paths might lead the code into thinking that all the abstract methods of
  // Collection were still abstract in the AbstractList subclasses here, even though most of them
  // are implemented in AbstractList.
  @Test
  public void getLocalAndInheritedMethods_AbstractList() {
    TypeElement abstractType =
        elements.getTypeElement(AbstractAbstractList.class.getCanonicalName());
    Set<ExecutableElement> abstractTypeMethods =
        MoreElements.getLocalAndInheritedMethods(abstractType, elements);
    assertThat(abstractMethodNamesFrom(abstractTypeMethods)).containsExactly("get", "size");

    TypeElement concreteType =
        elements.getTypeElement(ConcreteAbstractList.class.getCanonicalName());
    Set<ExecutableElement> concreteTypeMethods =
        MoreElements.getLocalAndInheritedMethods(concreteType, elements);
    assertThat(abstractMethodNamesFrom(concreteTypeMethods)).isEmpty();
  }
}
