/*
 * Copyright 2018 Google LLC
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

import static java.util.stream.Collectors.joining;

import com.google.auto.common.AnnotationMirrors;
import com.google.common.base.Equivalence;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * A method on an {@code @AutoValue} or {@code AutoOneOf} class that has no specific attached
 * information, such as a {@code toBuilder()} method, or a {@code build()} method, where the name,
 * access, thrown types, and receiver type-use annotations are needed in context.
 *
 * <p>It implements JavaBean-style getters which means it can be referenced from templates, for
 * example {@code $method.access}. This template access means that the class and its getters must be
 * public.
 */
public final class SimpleMethod {
  private static final Splitter TYPE_PARAMETER_SPLITTER = Splitter.on(',').trimResults();
  private static final Equivalence<AnnotationMirror> ANNOTATION_EQUIVALENCE =
      AnnotationMirrors.equivalence();

  private final ExecutableElement method;
  private final String access;
  private final String name;
  private final String throwsString;

  SimpleMethod(ExecutableElement method) {
    this.method = method;
    this.access = access(method);
    this.name = method.getSimpleName().toString();
    this.throwsString = throwsString(method);
  }

  public String getAccess() {
    return access;
  }

  public String getName() {
    return name;
  }

  public String getThrows() {
    return throwsString;
  }

  /**
   * Returns the receiver parameter to declare on a generated override of this method, or {@code ""}
   * if it has no type-use annotations on its receiver.
   *
   * @param generatedType simple name of the class that will declare the override, such as {@code
   *     AutoValue_Animal} or {@code Builder}
   * @param actualTypeParameters type arguments of that class, such as {@code <T>}, or empty
   */
  public String receiver(String generatedType, String actualTypeParameters) {
    return receiverParameter(method, generatedType, actualTypeParameters);
  }

  /**
   * Returns an appropriate string to be used in code for the access specification of the given
   * method. This will be {@code public} or {@code protected} followed by a space, or the empty
   * string for default access.
   */
  static String access(ExecutableElement method) {
    Set<Modifier> mods = method.getModifiers();
    if (mods.contains(Modifier.PUBLIC)) {
      return "public ";
    } else if (mods.contains(Modifier.PROTECTED)) {
      return "protected ";
    } else {
      return "";
    }
  }

  private static String throwsString(ExecutableElement method) {
    if (method.getThrownTypes().isEmpty()) {
      return "";
    }
    return "throws "
        + method.getThrownTypes().stream().map(TypeEncoder::encode).collect(joining(", "));
  }

  /**
   * Returns the receiver parameter to declare on a generated override of {@code method}, or {@code
   * ""} if {@code method} has no type-use annotations on its receiver that should be copied.
   *
   * <p>{@code ExecutableElement.getParameters()} omits the receiver, so a type-use annotation
   * written on {@code this} is only visible on {@link ExecutableElement#getReceiverType()}. An
   * override must name the class that declares the override, not the abstract class, so {@code
   * generatedType} and {@code actualTypeParameters} spell that class.
   *
   * <p>JDK 8 returns null from {@code getReceiverType()} when the source has no receiver
   * (JDK-8027261). A receiver that does exist may be a {@code Type$AnnotatedType}. {@code
   * MoreTypes.asDeclared} visits that type and drops its annotations, so this method casts instead.
   * ECJ synthesizes a receiver for every instance method and repeats the enclosing type-parameter
   * declaration annotations on its type arguments. Those annotations are not copied. A use-site
   * annotation that is identical to the declaration cannot be distinguished from that, so it is
   * not copied either.
   */
  static String receiverParameter(
      ExecutableElement method, String generatedType, String actualTypeParameters) {
    TypeMirror receiverType = method.getReceiverType();
    if (receiverType == null || receiverType.getKind() != TypeKind.DECLARED) {
      return "";
    }
    // Not MoreTypes.asDeclared: that visitor strips JDK 8 type annotations.
    DeclaredType declaredReceiver = (DeclaredType) receiverType;
    String annotations = annotationPrefix(declaredReceiver.getAnnotationMirrors());
    String typeArguments =
        typeArgumentsString(
            receiverTypeArgumentAnnotations(method, declaredReceiver.getTypeArguments()),
            actualTypeParameters);
    if (annotations.isEmpty() && typeArguments.equals(actualTypeParameters)) {
      return "";
    }
    return annotations + generatedType + typeArguments + " this";
  }

  /**
   * Annotations to copy onto each receiver type argument. Declaration annotations of the
   * corresponding type parameter are removed when the counts match. Otherwise every type-argument
   * annotation is kept, because there is no declaration annotation to compare it with.
   */
  private static ImmutableList<ImmutableList<AnnotationMirror>> receiverTypeArgumentAnnotations(
      ExecutableElement method, List<? extends TypeMirror> typeArguments) {
    Element enclosing = method.getEnclosingElement();
    List<? extends TypeParameterElement> typeParameters =
        enclosing instanceof TypeElement
            ? ((TypeElement) enclosing).getTypeParameters()
            : ImmutableList.of();
    boolean filterDeclarations = typeParameters.size() == typeArguments.size();
    ImmutableList.Builder<ImmutableList<AnnotationMirror>> filtered = ImmutableList.builder();
    for (int i = 0; i < typeArguments.size(); i++) {
      List<? extends AnnotationMirror> declarationAnnotations =
          filterDeclarations ? typeParameters.get(i).getAnnotationMirrors() : ImmutableList.of();
      ImmutableList.Builder<AnnotationMirror> kept = ImmutableList.builder();
      for (AnnotationMirror annotation : typeArguments.get(i).getAnnotationMirrors()) {
        if (!equivalentToAny(annotation, declarationAnnotations)) {
          kept.add(annotation);
        }
      }
      filtered.add(kept.build());
    }
    return filtered.build();
  }

  private static boolean equivalentToAny(
      AnnotationMirror annotation, List<? extends AnnotationMirror> others) {
    for (AnnotationMirror other : others) {
      if (ANNOTATION_EQUIVALENCE.equivalent(annotation, other)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Type arguments of the generated receiver. Unannotated arguments keep {@code
   * actualTypeParameters}. When a receiver type argument still has annotations after declaration
   * annotations are removed, and it lines up with a generated type parameter, those annotations
   * are applied to that parameter name.
   */
  private static String typeArgumentsString(
      ImmutableList<ImmutableList<AnnotationMirror>> typeArgumentAnnotations,
      String actualTypeParameters) {
    if (!hasAnnotations(typeArgumentAnnotations)) {
      return actualTypeParameters;
    }
    ImmutableList<String> names = actualTypeParameterNames(actualTypeParameters);
    if (names.size() != typeArgumentAnnotations.size()) {
      return actualTypeParameters;
    }
    StringBuilder sb = new StringBuilder("<");
    for (int i = 0; i < names.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(annotationPrefix(typeArgumentAnnotations.get(i)));
      sb.append(names.get(i));
    }
    return sb.append('>').toString();
  }

  private static boolean hasAnnotations(
      ImmutableList<ImmutableList<AnnotationMirror>> typeArgumentAnnotations) {
    for (ImmutableList<AnnotationMirror> argumentAnnotations : typeArgumentAnnotations) {
      if (!argumentAnnotations.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static ImmutableList<String> actualTypeParameterNames(String actualTypeParameters) {
    if (actualTypeParameters.isEmpty()) {
      return ImmutableList.of();
    }
    String inside = actualTypeParameters.substring(1, actualTypeParameters.length() - 1);
    return ImmutableList.copyOf(TYPE_PARAMETER_SPLITTER.split(inside));
  }

  private static String annotationPrefix(List<? extends AnnotationMirror> annotations) {
    StringBuilder sb = new StringBuilder();
    for (AnnotationMirror annotation : annotations) {
      sb.append(AnnotationOutput.sourceFormForAnnotation(annotation)).append(' ');
    }
    return sb.toString();
  }
}
