/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;

import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor6;

import static javax.lang.model.element.ElementKind.PACKAGE;

/**
 * Static utility methods pertaining to {@link Element} instances.
 *
 * @author Gregory Kick
 */
@Beta
public final class MoreElements {
  /**
   * An alternate implementation of {@link Elements#getPackageOf} that does not require an
   * {@link Elements} instance.
   *
   * @throws NullPointerException if {@code element} is {@code null}
   */
  public static PackageElement getPackage(Element element) {
    while (element.getKind() != PACKAGE) {
      element = element.getEnclosingElement();
    }
    return (PackageElement) element;
  }

  private static final ElementVisitor<PackageElement, Void> PACKAGE_ELEMENT_VISITOR =
      new SimpleElementVisitor6<PackageElement, Void>() {
        @Override protected PackageElement defaultAction(Element e, Void p) {
          throw new IllegalArgumentException();
        }

        @Override public PackageElement visitPackage(PackageElement e, Void p) {
          return e;
        }
      };

  /**
   * Returns the given {@link Element} instance as {@link PackageElement}.
   *
   * <p>This method is functionally equivalent to an {@code instanceof} check and a cast, but should
   * always be used over that idiom as instructed in the documentation for {@link Element}.
   *
   * @throws NullPointerException if {@code element} is {@code null}
   * @throws IllegalArgumentException if {@code element} isn't a {@link PackageElement}.
   */
  public static PackageElement asPackage(Element element) {
    return element.accept(PACKAGE_ELEMENT_VISITOR, null);
  }

  private static final ElementVisitor<TypeElement, Void> TYPE_ELEMENT_VISITOR =
      new SimpleElementVisitor6<TypeElement, Void>() {
        @Override protected TypeElement defaultAction(Element e, Void p) {
          throw new IllegalArgumentException();
        }

        @Override public TypeElement visitType(TypeElement e, Void p) {
          return e;
        }
      };

  /**
   * Returns true if the given {@link Element} instance is a {@link TypeElement}.
   *
   * <p>This method is functionally equivalent to an {@code instanceof} check, but should
   * always be used over that idiom as instructed in the documentation for {@link Element}.
   *
   * @throws NullPointerException if {@code element} is {@code null}
   */
  public static boolean isType(Element element) {
    return element.getKind().isClass() || element.getKind().isInterface();
  }

  /**
   * Returns the given {@link Element} instance as {@link TypeElement}.
   *
   * <p>This method is functionally equivalent to an {@code instanceof} check and a cast, but should
   * always be used over that idiom as instructed in the documentation for {@link Element}.
   *
   * @throws NullPointerException if {@code element} is {@code null}
   * @throws IllegalArgumentException if {@code element} isn't a {@link TypeElement}.
   */
  public static TypeElement asType(Element element) {
    return element.accept(TYPE_ELEMENT_VISITOR, null);
  }

  private static final ElementVisitor<VariableElement, Void> VARIABLE_ELEMENT_VISITOR =
      new SimpleElementVisitor6<VariableElement, Void>() {
        @Override protected VariableElement defaultAction(Element e, Void p) {
          throw new IllegalArgumentException();
        }

        @Override public VariableElement visitVariable(VariableElement e, Void p) {
          return e;
        }
      };

  /**
   * Returns the given {@link Element} instance as {@link VariableElement}.
   *
   * <p>This method is functionally equivalent to an {@code instanceof} check and a cast, but should
   * always be used over that idiom as instructed in the documentation for {@link Element}.
   *
   * @throws NullPointerException if {@code element} is {@code null}
   * @throws IllegalArgumentException if {@code element} isn't a {@link VariableElement}.
   */
  public static VariableElement asVariable(Element element) {
    return element.accept(VARIABLE_ELEMENT_VISITOR, null);
  }

  private static final ElementVisitor<ExecutableElement, Void> EXECUTABLE_ELEMENT_VISITOR =
      new SimpleElementVisitor6<ExecutableElement, Void>() {
        @Override protected ExecutableElement defaultAction(Element e, Void p) {
          throw new IllegalArgumentException();
        }

        @Override public ExecutableElement visitExecutable(ExecutableElement e, Void p) {
          return e;
        }
      };

  /**
   * Returns the given {@link Element} instance as {@link ExecutableElement}.
   *
   * <p>This method is functionally equivalent to an {@code instanceof} check and a cast, but should
   * always be used over that idiom as instructed in the documentation for {@link Element}.
   *
   * @throws NullPointerException if {@code element} is {@code null}
   * @throws IllegalArgumentException if {@code element} isn't a {@link ExecutableElement}.
   */
  public static ExecutableElement asExecutable(Element element) {
    return element.accept(EXECUTABLE_ELEMENT_VISITOR, null);
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose
   * {@linkplain AnnotationMirror#getAnnotationType() annotation type} has the same canonical name
   * as that of {@code annotationClass}. This method is a safer alternative to calling
   * {@link Element#getAnnotation} and checking for {@code null} as it avoids any interaction with
   * annotation proxies.
   */
  public static boolean isAnnotationPresent(Element element,
      Class<? extends Annotation> annotationClass) {
    return getAnnotationMirror(element, annotationClass).isPresent();
  }

  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationClass} on
   * {@code element}, or {@link Optional#absent()} if no such annotation exists. This method is a
   * safer alternative to calling {@link Element#getAnnotation} as it avoids any interaction with
   * annotation proxies.
   */
  public static Optional<AnnotationMirror> getAnnotationMirror(Element element,
      Class<? extends Annotation> annotationClass) {
    String annotationClassName = annotationClass.getCanonicalName();
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      TypeElement annotationTypeElement = asType(annotationMirror.getAnnotationType().asElement());
      if (annotationTypeElement.getQualifiedName().contentEquals(annotationClassName)) {
        return Optional.of(annotationMirror);
      }
    }
    return Optional.absent();
  }

  /**
   * Returns a {@link Predicate} that can be used to filter elements by {@link Modifier}.
   * The predicate returns {@code true} if the input {@link Element} has all of the given
   * {@code modifiers}, perhaps in addition to others.
   *
   * <p>Here is an example how one could get a List of static methods of a class:
   * <pre>{@code
   * FluentIterable.from(ElementFilter.methodsIn(clazzElement.getEnclosedElements()))
   *     .filter(MoreElements.hasModifiers(Modifier.STATIC).toList();
   * }</pre>
   */
  public static Predicate<Element> hasModifiers(Modifier... modifiers) {
    return hasModifiers(ImmutableSet.copyOf(modifiers));
  }

  /**
   * Returns a {@link Predicate} that can be used to filter elements by {@link Modifier}.
   * The predicate returns {@code true} if the input {@link Element} has all of the given
   * {@code modifiers}, perhaps in addition to others.
   *
   * <p>Here is an example how one could get a List of methods with certain modifiers of a class:
   * <pre>{@code
   * Set<Modifier> modifiers = ...;
   * FluentIterable.from(ElementFilter.methodsIn(clazzElement.getEnclosedElements()))
   *     .filter(MoreElements.hasModifiers(modifiers).toList();}
   * </pre>
   */
  public static Predicate<Element> hasModifiers(final Set<Modifier> modifiers) {
    return new Predicate<Element>() {
      @Override
      public boolean apply(Element input) {
        return input.getModifiers().containsAll(modifiers);
      }
    };
  }

  /**
   * Returns the set of all non-private methods from {@code type}, including methods that it
   * inherits from its ancestors. Inherited methods that are overridden are not included in the
   * result. So if {@code type} defines {@code public String toString()}, the returned set will
   * contain that method, but not the {@code toString()} method defined by {@code Object}.
   *
   * @param type the type whose own and inherited methods are to be returned
   * @param elementUtils an {@link Elements} object, typically returned by
   *     {@link javax.annotation.processing.AbstractProcessor#processingEnv processingEnv}<!--
   *     -->.{@link javax.annotation.processing.ProcessingEnvironment.getElementUtils()
   *     getElementUtils()}
   */
  public static ImmutableSet<ExecutableElement> getLocalAndInheritedMethods(
      TypeElement type, Elements elementUtils) {
    SetMultimap<String, ExecutableElement> methodMap = LinkedHashMultimap.create();
    getLocalAndInheritedMethods(getPackage(type), type, methodMap);
    // Find methods that are overridden. We do this using `Elements.overrides`, which means
    // that it is inherently a quadratic operation, since we have to compare every method against
    // every other method. We reduce the performance impact by (a) grouping methods by name, since
    // a method cannot override another method with a different name, and (b) making sure that
    // methods in ancestor types precede those in descendant types, which means we only have to
    // check a method against the ones that follow it in that order.
    Set<ExecutableElement> overridden = new LinkedHashSet<ExecutableElement>();
    for (String methodName : methodMap.keySet()) {
      List<ExecutableElement> methodList = ImmutableList.copyOf(methodMap.get(methodName));
      for (int i = 0; i < methodList.size(); i++) {
        ExecutableElement methodI = methodList.get(i);
        for (int j = i + 1; j < methodList.size(); j++) {
          ExecutableElement methodJ = methodList.get(j);
          if (elementUtils.overrides(methodJ, methodI, type)) {
            overridden.add(methodI);
          }
        }
      }
    }
    Set<ExecutableElement> methods = new LinkedHashSet<ExecutableElement>(methodMap.values());
    methods.removeAll(overridden);
    return ImmutableSet.copyOf(methods);
  }

  // Add to `methods` the instance methods from `type` that are visible to code in the
  // package `pkg`. This means all the instance methods from `type` itself and all instance methods
  // it inherits from its ancestors, except private methods and package-private methods in other
  // packages. This method does not take overriding into account, so it will add both an ancestor
  // method and a descendant method that overrides it.
  // `methods` is a multimap from a method name to all of the methods with that name, including
  // methods that override or overload one another. Within those methods, those in ancestor types
  // always precede those in descendant types.
  private static void getLocalAndInheritedMethods(
      PackageElement pkg, TypeElement type, SetMultimap<String, ExecutableElement> methods) {
    for (TypeMirror superInterface : type.getInterfaces()) {
      getLocalAndInheritedMethods(pkg, MoreTypes.asTypeElement(superInterface), methods);
    }
    if (type.getSuperclass().getKind() != TypeKind.NONE) {
      // Visit the superclass after superinterfaces so we will always see the implementation of a
      // method after any interfaces that declared it.
      getLocalAndInheritedMethods(pkg, MoreTypes.asTypeElement(type.getSuperclass()), methods);
    }
    for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
      if (!method.getModifiers().contains(Modifier.STATIC)
          && methodVisibleFromPackage(method, pkg)) {
        methods.put(method.getSimpleName().toString(), method);
      }
    }
  }

  private static boolean methodVisibleFromPackage(ExecutableElement method, PackageElement pkg) {
    // We use Visibility.ofElement rather than .effectiveVisibilityOfElement because it doesn't
    // really matter whether the containing class is visible. If you inherit a public method
    // then you have a public method, regardless of whether you inherit it from a public class.
    Visibility visibility = Visibility.ofElement(method);
    switch (visibility) {
      case PRIVATE:
        return false;
      case DEFAULT:
        return getPackage(method).equals(pkg);
      default:
        return true;
    }
  }

  private MoreElements() {}
}
