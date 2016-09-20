/*
 * Copyright (C) 2016 Google, Inc.
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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Expect;
import com.google.testing.compile.CompilationRule;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class OverridesTest {
  @Rule public CompilationRule compilation = new CompilationRule();
  @Rule public Expect expect = Expect.create();

  private Types typeUtils;
  private Elements elementUtils;
  private Overrides nativeOverrides;
  private Overrides explicitOverrides;

  @Before
  public void initializeTestElements() {
    elementUtils = compilation.getElements();
    typeUtils = compilation.getTypes();
    nativeOverrides = new Overrides.NativeOverrides(elementUtils);
    explicitOverrides = new Overrides.ExplicitOverrides(typeUtils, elementUtils);
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
      @Override public void m() {}
      @Override public void m(String x) {}
      @Override public void n() {}
    }

    static class ChildOfOneAndTwo implements One, Two {
      @Override public void m() {}
      @Override public void m(String x) {}
      @Override public void m(int x) {}
      @Override public void n() {}
    }

    static class ChildOfParentAndOne extends Parent implements One {
      @Override public void m() {}
      @Override public void m(String x) {}
      @Override public void n() {}
    }

    static class ChildOfParentAndOneAndTwo extends Parent implements One, Two {
      @Override public void m(String x) {}
      @Override public void m(int x) {}
      @Override public void n() {}
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

    abstract static class MultibindingDeclaration
        extends BindingDeclaration implements HasBindingType, HasContributionType {
      @Override public abstract Key key();
      @Override public abstract ContributionType contributionType();
      @Override public abstract BindingType bindingType();
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
    interface XCollection<E> {
      boolean add(E x);
    }

    interface XList<E> extends XCollection<E> {
      @Override public boolean add(E x);
    }

    static class StringList implements XList<String> {
      @Override public boolean add(String x) {
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
      @Override void frob(List x) {}
    }

    static class NonRawParent {
      void frob(List<String> x) {}
    }

    static class RawChildOfNonRaw extends NonRawParent {
      @Override void frob(List x) {}
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
    checkOverridesInSet(ImmutableSet.<Class<?>>of(
        Collection.class, List.class, AbstractCollection.class, AbstractList.class));
  }

  private void checkOverridesInContainedClasses(Class<?> container) {
    checkOverridesInSet(ImmutableSet.copyOf(container.getDeclaredClasses()));
  }

  private void checkOverridesInSet(ImmutableSet<Class<?>> testClasses) {
    assertThat(testClasses).isNotEmpty();
    ImmutableSet.Builder<TypeElement> testTypesBuilder = ImmutableSet.builder();
    for (Class<?> testClass : testClasses) {
      testTypesBuilder.add(elementUtils.getTypeElement(testClass.getCanonicalName()));
    }
    ImmutableSet<TypeElement> testTypes = testTypesBuilder.build();
    ImmutableSet.Builder<ExecutableElement> testMethodsBuilder = ImmutableSet.builder();
    for (TypeElement testType : testTypes) {
      testMethodsBuilder.addAll(ElementFilter.methodsIn(testType.getEnclosedElements()));
    }
    ImmutableSet<ExecutableElement> testMethods = testMethodsBuilder.build();
    for (TypeElement in : testTypes) {
      List<ExecutableElement> inMethods = ElementFilter.methodsIn(elementUtils.getAllMembers(in));
      for (ExecutableElement overrider : inMethods) {
        for (ExecutableElement overridden : testMethods) {
          boolean expected = nativeOverrides.overrides(overrider, overridden, in);
          boolean actual = explicitOverrides.overrides(overrider, overridden, in);
          expect
              .withFailureMessage(
                  "%s.%s overrides %s.%s in %s",
                  overrider.getEnclosingElement(), overrider,
                  overridden.getEnclosingElement(), overridden,
                  in)
              .that(actual).isEqualTo(expected);
        }
      }
    }
  }
}
