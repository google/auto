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
package com.google.auto.value.processor;

import static java.util.stream.Collectors.toList;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Hacks needed to work around various bugs and incompatibilities in Eclipse's implementation of
 * annotation processing.
 *
 * @author Éamonn McManus
 */
class EclipseHack {
  private final Elements elementUtils;
  private final Types typeUtils;

  EclipseHack(ProcessingEnvironment processingEnv) {
    this(processingEnv.getElementUtils(), processingEnv.getTypeUtils());
  }

  EclipseHack(Elements elementUtils, Types typeUtils) {
    this.elementUtils = elementUtils;
    this.typeUtils = typeUtils;
  }

  /**
   * Returns the enclosing type of {@code type}, if {@code type} is an inner class. Otherwise
   * returns a {@code NoType}. This is what {@link DeclaredType#getEnclosingType()} is supposed to
   * do. However, some versions of Eclipse have a bug where, for example, asking for the enclosing
   * type of {@code PrimitiveIterator.OfInt} will return {@code PrimitiveIterator<T, T_CONS>} rather
   * than plain {@code PrimitiveIterator}, as if {@code OfInt} were an inner class rather than a
   * static one. This would lead us to write a reference to {@code OfInt} as {@code
   * PrimitiveIterator<T, T_CONS>.OfInt}, which would obviously break. We attempt to avert this by
   * detecting that:
   *
   * <ul>
   *   <li>there is an enclosing type that is a {@code DeclaredType}, which should mean that {@code
   *       type} is an inner class;
   *   <li>we are in the Eclipse compiler;
   *   <li>the type arguments of the purported enclosing type are all type variables with the same
   *       names as the corresponding type parameters.
   * </ul>
   *
   * <p>If all these conditions are met, we assume we're hitting the Eclipse bug, and we return no
   * enclosing type instead. That does mean that in the unlikely event where we really do have an
   * inner class of an instantiation of the outer class with type arguments that happen to be type
   * variables with the same names as the corresponding parameters, we will do the wrong thing on
   * Eclipse. But doing the wrong thing in that case is better than doing the wrong thing in the
   * usual case.
   */
  static TypeMirror getEnclosingType(DeclaredType type) {
    TypeMirror enclosing = type.getEnclosingType();
    if (!enclosing.getKind().equals(TypeKind.DECLARED)
        || !enclosing.getClass().getName().contains("eclipse")) {
      // If the class representing the enclosing type comes from the Eclipse compiler, it will be
      // something like org.eclipse.jdt.internal.compiler.apt.model.DeclaredTypeImpl. If we're not
      // in the Eclipse compiler then we don't expect to see "eclipse" in the name of this
      // implementation class.
      return enclosing;
    }
    DeclaredType declared = MoreTypes.asDeclared(enclosing);
    List<? extends TypeMirror> arguments = declared.getTypeArguments();
    if (!arguments.isEmpty()) {
      boolean allVariables = arguments.stream().allMatch(t -> t.getKind().equals(TypeKind.TYPEVAR));
      if (allVariables) {
        List<Name> argumentNames =
            arguments.stream()
                .map(t -> MoreTypes.asTypeVariable(t).asElement().getSimpleName())
                .collect(toList());
        TypeElement enclosingElement = MoreTypes.asTypeElement(declared);
        List<Name> parameterNames =
            enclosingElement.getTypeParameters().stream()
                .map(Element::getSimpleName)
                .collect(toList());
        if (argumentNames.equals(parameterNames)) {
          // We're going to return a NoType. We don't have a Types to hand so we can't call
          // Types.getNoType(). Instead, just keep going through outer types until we get to
          // the outside, which will be a NoType.
          while (enclosing.getKind().equals(TypeKind.DECLARED)) {
            enclosing = MoreTypes.asDeclared(enclosing).getEnclosingType();
          }
          return enclosing;
        }
      }
    }
    return declared;
  }

  TypeMirror methodReturnType(ExecutableElement method, DeclaredType in) {
    try {
      TypeMirror methodMirror = typeUtils.asMemberOf(in, method);
      return MoreTypes.asExecutable(methodMirror).getReturnType();
    } catch (IllegalArgumentException e) {
      return methodReturnTypes(ImmutableSet.of(method), in).get(method);
    }
  }

  /**
   * Returns a map containing the real return types of the given methods, knowing that they appear
   * in the given type. This means that if the given type is say {@code StringIterator implements
   * Iterator<String>} then we want the {@code next()} method to map to String, rather than the
   * {@code T} that it returns as inherited from {@code Iterator<T>}. This method is in EclipseHack
   * because if it weren't for <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=382590">this
   * Eclipse bug</a> it would be trivial. Unfortunately, versions of Eclipse up to at least 4.5 have
   * a bug where the {@link Types#asMemberOf} method throws IllegalArgumentException if given a
   * method that is inherited from an interface. Fortunately, Eclipse's implementation of {@link
   * Elements#getAllMembers} does the type substitution that {@code asMemberOf} would have done. But
   * javac's implementation doesn't. So we try the way that would work if Eclipse weren't buggy, and
   * only if we get IllegalArgumentException do we use {@code getAllMembers}.
   */
  ImmutableMap<ExecutableElement, TypeMirror> methodReturnTypes(
      Set<ExecutableElement> methods, DeclaredType in) {
    ImmutableMap.Builder<ExecutableElement, TypeMirror> map = ImmutableMap.builder();
    Map<Name, ExecutableElement> noArgMethods = null;
    for (ExecutableElement method : methods) {
      TypeMirror returnType = null;
      try {
        TypeMirror methodMirror = typeUtils.asMemberOf(in, method);
        returnType = MoreTypes.asExecutable(methodMirror).getReturnType();
      } catch (IllegalArgumentException e) {
        if (method.getParameters().isEmpty()) {
          if (noArgMethods == null) {
            noArgMethods = noArgMethodsIn(in);
          }
          returnType = noArgMethods.get(method.getSimpleName()).getReturnType();
        }
      }
      if (returnType == null) {
        returnType = method.getReturnType();
      }
      map.put(method, returnType);
    }
    return map.build();
  }

  /**
   * Constructs a map from name to method of the no-argument methods in the given type. We need this
   * because an ExecutableElement returned by {@link Elements#getAllMembers} will not compare equal
   * to the original ExecutableElement if {@code getAllMembers} substituted type parameters, as it
   * does in Eclipse.
   */
  private Map<Name, ExecutableElement> noArgMethodsIn(DeclaredType in) {
    TypeElement autoValueType = MoreElements.asType(typeUtils.asElement(in));
    List<ExecutableElement> allMethods =
        ElementFilter.methodsIn(elementUtils.getAllMembers(autoValueType));
    Map<Name, ExecutableElement> map = new LinkedHashMap<Name, ExecutableElement>();
    for (ExecutableElement method : allMethods) {
      if (method.getParameters().isEmpty()) {
        map.put(method.getSimpleName(), method);
      }
    }
    return map;
  }
}
