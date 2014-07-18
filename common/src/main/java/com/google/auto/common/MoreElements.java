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
import java.lang.annotation.Annotation;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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
    String annotationClassName = annotationClass.getCanonicalName();
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      TypeElement annotationTypeElement = asType(annotationMirror.getAnnotationType().asElement());
      if (annotationTypeElement.getQualifiedName().contentEquals(annotationClassName)) {
        return true;
      }
    }
    return false;
  }

  private MoreElements() {}
}
