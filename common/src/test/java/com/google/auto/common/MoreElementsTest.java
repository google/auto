/*
 * Copyright (C) 2014 Google, Inc.
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
import static com.google.common.truth.Truth.assertWithMessage;
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

  private PackageElement javaLangPackageElement;
  private TypeElement objectElement;
  private TypeElement stringElement;

  @Before
  public void initializeTestElements() {
    Elements elements = compilation.getElements();
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
    assertThat(MoreElements.asPackage(javaLangPackageElement))
        .isEqualTo(javaLangPackageElement);
  }

  @Test
  public void asPackage_illegalArgument() {
    try {
      MoreElements.asPackage(stringElement);
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Test public void asTypeElement() {
    Element typeElement =
        compilation.getElements().getTypeElement(String.class.getCanonicalName());
    assertTrue(MoreElements.isType(typeElement));
    assertThat(MoreElements.asType(typeElement)).isEqualTo(typeElement);
  }

  @Test public void asTypeElement_notATypeElement() {
    TypeElement typeElement =
        compilation.getElements().getTypeElement(String.class.getCanonicalName());
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
  public void asType() {
    assertThat(MoreElements.asType(stringElement)).isEqualTo(stringElement);
  }

  @Test
  public void asType_illegalArgument() {
    assertFalse(MoreElements.isType(javaLangPackageElement));
    try {
      MoreElements.asType(javaLangPackageElement);
      fail();
    } catch (IllegalArgumentException expected) {}
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
    } catch (IllegalArgumentException expected) {}
  }

  @Test
  public void asExecutable() {
    for (Element methodElement : ElementFilter.methodsIn(stringElement.getEnclosedElements())) {
      assertThat(MoreElements.asExecutable(methodElement)).isEqualTo(methodElement);
    }
    for (Element methodElement
        : ElementFilter.constructorsIn(stringElement.getEnclosedElements())) {
      assertThat(MoreElements.asExecutable(methodElement)).isEqualTo(methodElement);
    }
  }

  @Test
  public void asExecutable_illegalArgument() {
    try {
      MoreElements.asExecutable(javaLangPackageElement);
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Retention(RetentionPolicy.RUNTIME)
  private @interface InnerAnnotation {}

  @Documented
  @InnerAnnotation
  private @interface AnnotatedAnnotation {}

  @Test
  public void isAnnotationPresent() {
    TypeElement annotatedAnnotationElement =
        compilation.getElements().getTypeElement(AnnotatedAnnotation.class.getCanonicalName());
    assertThat(MoreElements.isAnnotationPresent(annotatedAnnotationElement, Documented.class))
        .isTrue();
    assertThat(MoreElements.isAnnotationPresent(annotatedAnnotationElement, InnerAnnotation.class))
        .isTrue();
    assertThat(MoreElements.isAnnotationPresent(annotatedAnnotationElement, SuppressWarnings.class))
        .isFalse();
  }

  @Test
  public void getAnnotationMirror() {
    TypeElement element =
        compilation.getElements().getTypeElement(AnnotatedAnnotation.class.getCanonicalName());

    Optional<AnnotationMirror> documented =
        MoreElements.getAnnotationMirror(element, Documented.class);
    Optional<AnnotationMirror> innerAnnotation =
        MoreElements.getAnnotationMirror(element, InnerAnnotation.class);
    Optional<AnnotationMirror> suppressWarnings =
        MoreElements.getAnnotationMirror(element, SuppressWarnings.class);

    expect.that(documented).isPresent();
    expect.that(innerAnnotation).isPresent();
    expect.that(suppressWarnings).isAbsent();

    Element annotationElement = documented.get().getAnnotationType().asElement();
    expect.that(MoreElements.isType(annotationElement)).isTrue();
    expect.that(MoreElements.asType(annotationElement).getQualifiedName().toString())
        .isEqualTo(Documented.class.getCanonicalName());

    annotationElement = innerAnnotation.get().getAnnotationType().asElement();
    expect.that(MoreElements.isType(annotationElement)).isTrue();
    expect.that(MoreElements.asType(annotationElement).getQualifiedName().toString())
        .isEqualTo(InnerAnnotation.class.getCanonicalName());
  }

  private abstract static class ParentClass {
    abstract String foo();
    private void ignored() {}
  }

  private interface ParentInterface {
    abstract int bar();
    abstract int bar(long x);
  }

  private abstract static class Child extends ParentClass implements ParentInterface {
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
    Elements elements = compilation.getElements();
    Types types = compilation.getTypes();
    TypeMirror intMirror = types.getPrimitiveType(TypeKind.INT);
    TypeMirror longMirror = types.getPrimitiveType(TypeKind.LONG);
    TypeElement childType = elements.getTypeElement(Child.class.getCanonicalName());
    @SuppressWarnings("deprecation")
    Set<ExecutableElement> childTypeMethods =
        MoreElements.getLocalAndInheritedMethods(childType, elements);
    Set<ExecutableElement> objectMethods = visibleMethodsFromObject();
    assertThat(childTypeMethods).containsAllIn(objectMethods);
    Set<ExecutableElement> nonObjectMethods = Sets.difference(childTypeMethods, objectMethods);
    assertThat(nonObjectMethods).containsExactly(
        getMethod(ParentClass.class, "foo"),
        getMethod(ParentInterface.class, "bar", longMirror),
        getMethod(Child.class, "bar"),
        getMethod(Child.class, "baz"),
        getMethod(Child.class, "buh", intMirror),
        getMethod(Child.class, "buh", intMirror, intMirror));
  }

  @Test
  public void getLocalAndInheritedMethods() {
    Elements elements = compilation.getElements();
    Types types = compilation.getTypes();
    TypeMirror intMirror = types.getPrimitiveType(TypeKind.INT);
    TypeMirror longMirror = types.getPrimitiveType(TypeKind.LONG);
    TypeElement childType = elements.getTypeElement(Child.class.getCanonicalName());
    @SuppressWarnings("deprecation")
    Set<ExecutableElement> childTypeMethods =
        MoreElements.getLocalAndInheritedMethods(childType, types, elements);
    Set<ExecutableElement> objectMethods = visibleMethodsFromObject();
    assertThat(childTypeMethods).containsAllIn(objectMethods);
    Set<ExecutableElement> nonObjectMethods = Sets.difference(childTypeMethods, objectMethods);
    assertThat(nonObjectMethods).containsExactly(
        getMethod(ParentClass.class, "foo"),
        getMethod(ParentInterface.class, "bar", longMirror),
        getMethod(Child.class, "bar"),
        getMethod(Child.class, "baz"),
        getMethod(Child.class, "buh", intMirror),
        getMethod(Child.class, "buh", intMirror, intMirror));
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
    Elements elementUtils = compilation.getElements();
    TypeElement main = elementUtils.getTypeElement(Main.ParentComponent.class.getCanonicalName());
    Set<ExecutableElement> methods = MoreElements.getLocalAndInheritedMethods(
        main, compilation.getTypes(), elementUtils);
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
    assertThat(methods).containsAllOf(
        getMethod(Object.class, "clone"),
        getMethod(Object.class, "finalize"),
        getMethod(Object.class, "wait"),
        getMethod(Object.class, "wait", longMirror),
        getMethod(Object.class, "wait", longMirror, intMirror));
    return methods;
  }

  private ExecutableElement getMethod(Class<?> c, String methodName, TypeMirror... parameterTypes) {
    TypeElement type = compilation.getElements().getTypeElement(c.getCanonicalName());
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
    return found;
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
    Elements elements = compilation.getElements();

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
