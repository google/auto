/*
 * Copyright (C) 2013 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
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
  private final ProcessingEnvironment processingEnv;

  EclipseHack(ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
  }

  TypeMirror methodReturnType(ExecutableElement method, DeclaredType in) {
    Types typeUtils = processingEnv.getTypeUtils();
    try {
      TypeMirror methodMirror = typeUtils.asMemberOf(in, method);
      return MoreTypes.asExecutable(methodMirror).getReturnType();
    } catch (IllegalArgumentException e) {
      return methodReturnTypes(ImmutableSet.of(method), in).get(method);
    }
  }

  /**
   * Returns a map containing the real return types of the given methods, knowing that they appear
   * in the given type. This means that if the given type is say
   * {@code StringIterator implements Iterator<String>} then we want the {@code next()} method
   * to map to String, rather than the {@code T} that it returns as inherited from
   * {@code Iterator<T>}. This method is in EclipseHack because if it weren't for
   * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=382590">this Eclipse bug</a> it would
   * be trivial. Unfortunately, versions of Eclipse up to at least 4.5 have a bug where the
   * {@link Types#asMemberOf} method throws IllegalArgumentException if given a method that is
   * inherited from an interface. Fortunately, Eclipse's implementation of
   * {@link Elements#getAllMembers} does the type substitution that {@code asMemberOf} would have
   * done. But javac's implementation doesn't. So we try the way that would work if Eclipse weren't
   * buggy, and only if we get IllegalArgumentException do we use {@code getAllMembers}.
   */
  ImmutableMap<ExecutableElement, TypeMirror> methodReturnTypes(
      Set<ExecutableElement> methods, DeclaredType in) {
    Types typeUtils = processingEnv.getTypeUtils();
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
   * Constructs a map from name to method of the no-argument methods in the given type. We need
   * this because an ExecutableElement returned by {@link Elements#getAllMembers} will not compare
   * equal to the original ExecutableElement if {@code getAllMembers} substituted type parameters,
   * as it does in Eclipse.
   */
  private Map<Name, ExecutableElement> noArgMethodsIn(DeclaredType in) {
    Types typeUtils = processingEnv.getTypeUtils();
    Elements elementUtils = processingEnv.getElementUtils();
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
