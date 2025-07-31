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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Hacks needed to work around various bugs and incompatibilities in Eclipse's implementation of
 * annotation processing.
 *
 * @author Éamonn McManus
 */
class EclipseHack {
  private EclipseHack() {}

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
        ImmutableList<Name> argumentNames =
            arguments.stream()
                .map(t -> MoreTypes.asTypeVariable(t).asElement().getSimpleName())
                .collect(toImmutableList());
        TypeElement enclosingElement = MoreTypes.asTypeElement(declared);
        ImmutableList<Name> parameterNames =
            enclosingElement.getTypeParameters().stream()
                .map(Element::getSimpleName)
                .collect(toImmutableList());
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
}
