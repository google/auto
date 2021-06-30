/*
 * Copyright 2013 Google LLC
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

import static com.google.auto.common.MoreStreams.toImmutableSet;
import static javax.lang.model.element.ElementKind.PACKAGE;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.Overrides.ExplicitOverrides;
import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.lang.model.util.Types;

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

  private static final class PackageElementVisitor extends CastingElementVisitor<PackageElement> {
    private static final PackageElementVisitor INSTANCE = new PackageElementVisitor();

    PackageElementVisitor() {
      super("package element");
    }

    @Override
    public PackageElement visitPackage(PackageElement e, Void ignore) {
      return e;
    }
  }

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
    return element.accept(PackageElementVisitor.INSTANCE, null);
  }

  private static final class TypeElementVisitor extends CastingElementVisitor<TypeElement> {
    private static final TypeElementVisitor INSTANCE = new TypeElementVisitor();

    TypeElementVisitor() {
      super("type element");
    }

    @Override
    public TypeElement visitType(TypeElement e, Void ignore) {
      return e;
    }
  }

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
    return element.accept(TypeElementVisitor.INSTANCE, null);
  }

  /**
   * Returns the given {@link Element} instance as {@link TypeParameterElement}.
   *
   * <p>This method is functionally equivalent to an {@code instanceof} check and a cast, but should
   * always be used over that idiom as instructed in the documentation for {@link Element}.
   *
   * @throws NullPointerException if {@code element} is {@code null}
   * @throws IllegalArgumentException if {@code element} isn't a {@link TypeParameterElement}.
   */
  public static TypeParameterElement asTypeParameter(Element element) {
    return element.accept(TypeParameterElementVisitor.INSTANCE, null);
  }

  private static final class TypeParameterElementVisitor
      extends CastingElementVisitor<TypeParameterElement> {
    private static final TypeParameterElementVisitor INSTANCE = new TypeParameterElementVisitor();

    TypeParameterElementVisitor() {
      super("type parameter element");
    }

    @Override
    public TypeParameterElement visitTypeParameter(TypeParameterElement e, Void ignore) {
      return e;
    }
  }

  private static final class VariableElementVisitor extends CastingElementVisitor<VariableElement> {
    private static final VariableElementVisitor INSTANCE = new VariableElementVisitor();

    VariableElementVisitor() {
      super("variable element");
    }

    @Override
    public VariableElement visitVariable(VariableElement e, Void ignore) {
      return e;
    }
  }

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
    return element.accept(VariableElementVisitor.INSTANCE, null);
  }

  private static final class ExecutableElementVisitor
      extends CastingElementVisitor<ExecutableElement> {
    private static final ExecutableElementVisitor INSTANCE = new ExecutableElementVisitor();

    ExecutableElementVisitor() {
      super("executable element");
    }

    @Override
    public ExecutableElement visitExecutable(ExecutableElement e, Void label) {
      return e;
    }
  }

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
    return element.accept(ExecutableElementVisitor.INSTANCE, null);
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose {@linkplain
   * AnnotationMirror#getAnnotationType() annotation type} has the same canonical name as that of
   * {@code annotationClass}. This method is a safer alternative to calling {@link
   * Element#getAnnotation} and checking for {@code null} as it avoids any interaction with
   * annotation proxies.
   */
  public static boolean isAnnotationPresent(
      Element element, Class<? extends Annotation> annotationClass) {
    return getAnnotationMirror(element, annotationClass).isPresent();
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose {@linkplain
   * AnnotationMirror#getAnnotationType() annotation type} has the same fully qualified name as that
   * of {@code annotation}. This method is a safer alternative to calling {@link
   * Element#getAnnotation} and checking for {@code null} as it avoids any interaction with
   * annotation proxies.
   */
  public static boolean isAnnotationPresent(Element element, TypeElement annotation) {
    return getAnnotationMirror(element, annotation).isPresent();
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose {@linkplain
   * AnnotationMirror#getAnnotationType() annotation type} has {@code annotationName} as its
   * canonical name. This method is a safer alternative to calling {@link Element#getAnnotation} and
   * checking for {@code null} as it avoids any interaction with annotation proxies.
   */
  public static boolean isAnnotationPresent(Element element, String annotationName) {
    return getAnnotationMirror(element, annotationName).isPresent();
  }

  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationClass} on
   * {@code element}, or {@link Optional#absent()} if no such annotation exists. This method is a
   * safer alternative to calling {@link Element#getAnnotation} as it avoids any interaction with
   * annotation proxies.
   */
  public static Optional<AnnotationMirror> getAnnotationMirror(
      Element element, Class<? extends Annotation> annotationClass) {
    String name = annotationClass.getCanonicalName();
    if (name == null) {
      return Optional.absent();
    }
    return getAnnotationMirror(element, name);
  }

  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotation} on {@code
   * element}, or {@link Optional#absent()} if no such annotation exists. This method is a safer
   * alternative to calling {@link Element#getAnnotation} as it avoids any interaction with
   * annotation proxies.
   */
  public static Optional<AnnotationMirror> getAnnotationMirror(
      Element element, TypeElement annotation) {
    for (AnnotationMirror elementAnnotation : element.getAnnotationMirrors()) {
      if (elementAnnotation.getAnnotationType().asElement().equals(annotation)) {
        return Optional.of(elementAnnotation);
      }
    }
    return Optional.absent();
  }

  /**
   * Returns an {@link AnnotationMirror} for the annotation whose type's canonical name is on {@code
   * element}, or {@link Optional#absent()} if no such annotation exists. This method is a safer
   * alternative to calling {@link Element#getAnnotation} as it avoids any interaction with
   * annotation proxies.
   */
  public static Optional<AnnotationMirror> getAnnotationMirror(
      Element element, String annotationName) {
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      TypeElement annotationTypeElement = asType(annotationMirror.getAnnotationType().asElement());
      if (annotationTypeElement.getQualifiedName().contentEquals(annotationName)) {
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
  public static <T extends Element> Predicate<T> hasModifiers(Modifier... modifiers) {
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
  public static <T extends Element> Predicate<T> hasModifiers(final Set<Modifier> modifiers) {
    return new Predicate<T>() {
      @Override
      public boolean apply(T input) {
        return input.getModifiers().containsAll(modifiers);
      }
    };
  }

  /**
   * Returns the set of all non-private, non-static methods from {@code type}, including methods
   * that it inherits from its ancestors. Inherited methods that are overridden are not included in
   * the result. So if {@code type} defines {@code public String toString()}, the returned set will
   * contain that method, but not the {@code toString()} method defined by {@code Object}.
   *
   * <p>The returned set may contain more than one method with the same signature, if
   * {@code type} inherits those methods from different ancestors. For example, if it
   * inherits from unrelated interfaces {@code One} and {@code Two} which each define
   * {@code void foo();}, and if it does not itself override the {@code foo()} method,
   * then both {@code One.foo()} and {@code Two.foo()} will be in the returned set.
   *
   * <p>The order of the returned set is deterministic: within a class or interface, methods are in
   * the order they appear in the source code; methods in ancestors come before methods in
   * descendants; methods in interfaces come before methods in classes; and in a class or interface
   * that has more than one superinterface, the interfaces are in the order of their appearance in
   * {@code implements} or {@code extends}.
   *
   * @param type the type whose own and inherited methods are to be returned
   * @param elementUtils an {@link Elements} object, typically returned by
   *     {@link javax.annotation.processing.AbstractProcessor#processingEnv processingEnv}<!--
   *     -->.{@link javax.annotation.processing.ProcessingEnvironment#getElementUtils
   *     getElementUtils()}
   *
   * @deprecated The method {@link #getLocalAndInheritedMethods(TypeElement, Types, Elements)}
   *     has better consistency between Java compilers.
   */
  @Deprecated
  public static ImmutableSet<ExecutableElement> getLocalAndInheritedMethods(
      TypeElement type, Elements elementUtils) {
    Overrides overrides = new Overrides.NativeOverrides(elementUtils);
    return getLocalAndInheritedMethods(type, overrides);
  }

  /**
   * Returns the set of all non-private, non-static methods from {@code type}, including methods
   * that it inherits from its ancestors. Inherited methods that are overridden are not included in
   * the result. So if {@code type} defines {@code public String toString()}, the returned set will
   * contain that method, but not the {@code toString()} method defined by {@code Object}.
   *
   * <p>The returned set may contain more than one method with the same signature, if
   * {@code type} inherits those methods from different ancestors. For example, if it
   * inherits from unrelated interfaces {@code One} and {@code Two} which each define
   * {@code void foo();}, and if it does not itself override the {@code foo()} method,
   * then both {@code One.foo()} and {@code Two.foo()} will be in the returned set.
   *
   * <p>The order of the returned set is deterministic: within a class or interface, methods are in
   * the order they appear in the source code; methods in ancestors come before methods in
   * descendants; methods in interfaces come before methods in classes; and in a class or interface
   * that has more than one superinterface, the interfaces are in the order of their appearance in
   * {@code implements} or {@code extends}.
   *
   * @param type the type whose own and inherited methods are to be returned
   * @param typeUtils a {@link Types} object, typically returned by
   *     {@link javax.annotation.processing.AbstractProcessor#processingEnv processingEnv}<!--
   *     -->.{@link javax.annotation.processing.ProcessingEnvironment#getTypeUtils
   *     getTypeUtils()}
   * @param elementUtils an {@link Elements} object, typically returned by
   *     {@link javax.annotation.processing.AbstractProcessor#processingEnv processingEnv}<!--
   *     -->.{@link javax.annotation.processing.ProcessingEnvironment#getElementUtils
   *     getElementUtils()}
   */
  public static ImmutableSet<ExecutableElement> getLocalAndInheritedMethods(
      TypeElement type, Types typeUtils, Elements elementUtils) {
    return getLocalAndInheritedMethods(type, new ExplicitOverrides(typeUtils));
  }

  private static ImmutableSet<ExecutableElement> getLocalAndInheritedMethods(
      TypeElement type, Overrides overrides) {
    PackageElement pkg = getPackage(type);

    ImmutableSet.Builder<ExecutableElement> methods = ImmutableSet.builder();
    for (ExecutableElement method : getAllMethods(type, overrides)) {
      // Filter out all static and non-visible methods.
      if (!method.getModifiers().contains(STATIC) && methodVisibleFromPackage(method, pkg)) {
        methods.add(method);
      }
    }
    return methods.build();
  }

  /**
   * Tests whether one method, as a member of a given type, overrides another method.
   *
   * <p>This method does the same thing as {@link Elements#overrides(ExecutableElement,
   * ExecutableElement, TypeElement)}, but in a way that is more consistent between compilers, in
   * particular between javac and ecj (the Eclipse compiler).
   *
   * @param overrider the first method, possible overrider
   * @param overridden the second method, possibly being overridden
   * @param type the type of which the first method is a member
   * @return {@code true} if and only if the first method overrides the second
   */
  public static boolean overrides(
      ExecutableElement overrider,
      ExecutableElement overridden,
      TypeElement type,
      Types typeUtils) {
    return new ExplicitOverrides(typeUtils).overrides(overrider, overridden, type);
  }

  /**
   * Returns the set of all methods from {@code type}, including methods that it inherits
   * from its ancestors. Inherited methods that are overridden are not included in the
   * result. So if {@code type} defines {@code public String toString()}, the returned set
   * will contain that method, but not the {@code toString()} method defined by {@code Object}.
   *
   * <p>The returned set may contain more than one method with the same signature, if
   * {@code type} inherits those methods from different ancestors. For example, if it
   * inherits from unrelated interfaces {@code One} and {@code Two} which each define
   * {@code void foo();}, and if it does not itself override the {@code foo()} method,
   * then both {@code One.foo()} and {@code Two.foo()} will be in the returned set.
   *
   * <p>The order of the returned set is deterministic: within a class or interface, methods are in
   * the order they appear in the source code; methods in ancestors come before methods in
   * descendants; methods in interfaces come before methods in classes; and in a class or interface
   * that has more than one superinterface, the interfaces are in the order of their appearance in
   * {@code implements} or {@code extends}.
   *
   * @param type the type whose own and inherited methods are to be returned
   * @param typeUtils a {@link Types} object, typically returned by
   *     {@link javax.annotation.processing.AbstractProcessor#processingEnv processingEnv}<!--
   *     -->.{@link javax.annotation.processing.ProcessingEnvironment#getTypeUtils
   *     getTypeUtils()}
   * @param elementUtils an {@link Elements} object, typically returned by
   *     {@link javax.annotation.processing.AbstractProcessor#processingEnv processingEnv}<!--
   *     -->.{@link javax.annotation.processing.ProcessingEnvironment#getElementUtils
   *     getElementUtils()}
   */
  public static ImmutableSet<ExecutableElement> getAllMethods(
      TypeElement type, Types typeUtils, Elements elementUtils) {
    return getAllMethods(type, new ExplicitOverrides(typeUtils));
  }

  private static ImmutableSet<ExecutableElement> getAllMethods(
      TypeElement type, Overrides overrides) {
    SetMultimap<String, ExecutableElement> methodMap = LinkedHashMultimap.create();
    getAllMethods(type, methodMap);
    // Find methods that are overridden. We do this using `Elements.overrides`, which means
    // that it is inherently a quadratic operation, since we have to compare every method against
    // every other method. We reduce the performance impact by (a) grouping methods by name, since
    // a method cannot override another method with a different name, and (b) making sure that
    // methods in ancestor types precede those in descendant types, which means we only have to
    // check a method against the ones that follow it in that order.
    Set<ExecutableElement> overridden = new LinkedHashSet<ExecutableElement>();
    for (Collection<ExecutableElement> methods : methodMap.asMap().values()) {
      List<ExecutableElement> methodList = ImmutableList.copyOf(methods);
      for (int i = 0; i < methodList.size(); i++) {
        ExecutableElement methodI = methodList.get(i);
        for (int j = i + 1; j < methodList.size(); j++) {
          ExecutableElement methodJ = methodList.get(j);
          if (overrides.overrides(methodJ, methodI, type)) {
            overridden.add(methodI);
            break;
          }
        }
      }
    }
    return methodMap.values().stream()
        .filter(m -> !overridden.contains(m))
        .collect(toImmutableSet());
  }

  // Add to `methods` the static and instance methods from `type`. This means all methods from
  // `type` itself and all methods it inherits from its ancestors. This method does not take
  // overriding into account, so it will add both an ancestor method and a descendant method that
  // overrides it. `methods` is a multimap from a method name to all of the methods with that name,
  // including methods that override or overload one another. Within those methods, those in
  // ancestor types always precede those in descendant types.
  private static void getAllMethods(
      TypeElement type, SetMultimap<String, ExecutableElement> methods) {
    for (TypeMirror superInterface : type.getInterfaces()) {
      getAllMethods(MoreTypes.asTypeElement(superInterface), methods);
    }
    if (type.getSuperclass().getKind() != TypeKind.NONE) {
      // Visit the superclass after superinterfaces so we will always see the implementation of a
      // method after any interfaces that declared it.
      getAllMethods(MoreTypes.asTypeElement(type.getSuperclass()), methods);
    }
    for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
      methods.put(method.getSimpleName().toString(), method);
    }
  }

  static boolean methodVisibleFromPackage(ExecutableElement method, PackageElement pkg) {
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

  private abstract static class CastingElementVisitor<T> extends SimpleElementVisitor8<T, Void> {
    private final String label;

    CastingElementVisitor(String label) {
      this.label = label;
    }

    @Override
    protected final T defaultAction(Element e, Void ignore) {
      throw new IllegalArgumentException(e + " does not represent a " + label);
    }
  }

  private MoreElements() {}
}
