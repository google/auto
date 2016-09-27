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

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Determines if one method overrides another. This class defines two ways of doing that:
 * {@code NativeOverrides} uses the method
 * {@link Elements#overrides(ExecutableElement, ExecutableElement, TypeElement)} while
 * {@code ExplicitOverrides} reimplements that method in a way that is more consistent between
 * compilers, in particular between javac and ecj (the Eclipse compiler).
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
abstract class Overrides {
  abstract boolean overrides(
      ExecutableElement overrider, ExecutableElement overridden, TypeElement in);

  static class NativeOverrides extends Overrides {
    private final Elements elementUtils;

    NativeOverrides(Elements elementUtils) {
      this.elementUtils = elementUtils;
    }

    @Override
    boolean overrides(ExecutableElement overrider, ExecutableElement overridden, TypeElement in) {
      return elementUtils.overrides(overrider, overridden, in);
    }
  }

  static class ExplicitOverrides extends Overrides {
    private final Types typeUtils;
    private final Elements elementUtils;

    ExplicitOverrides(Types typeUtils, Elements elementUtils) {
      this.typeUtils = typeUtils;
      this.elementUtils = elementUtils;
    }

    @Override
    public boolean overrides(ExecutableElement overrider, ExecutableElement overridden,
        TypeElement in) {
      if (overrider.equals(overridden)) {
        return false;
      }
      if (!overrider.getSimpleName().equals(overridden.getSimpleName())) {
        // They must have the same name.
        return false;
      }
      if (overridden.getModifiers().contains(Modifier.STATIC)) {
        // Static methods can't be overridden (though they can be hidden by other static methods).
        return false;
      }
      Visibility overriddenVisibility = Visibility.ofElement(overridden);
      Visibility overriderVisibility = Visibility.ofElement(overrider);
      if (overriddenVisibility.equals(Visibility.PRIVATE)
          || overriderVisibility.compareTo(overriddenVisibility) < 0) {
        // Private methods can't be overridden, and methods can't be overridden by less-visible
        // methods. The latter condition is enforced by the compiler so in theory we might report
        // an "incorrect" result here for code that javac would not have allowed.
        return false;
      }
      DeclaredType inType = MoreTypes.asDeclared(in.asType());
      ExecutableType overriderExecutable;
      ExecutableType overriddenExecutable;
      try {
        overriderExecutable = MoreTypes.asExecutable(typeUtils.asMemberOf(inType, overrider));
        overriddenExecutable = MoreTypes.asExecutable(typeUtils.asMemberOf(inType, overridden));
      } catch (IllegalArgumentException e) {
        // This might mean that at least one of the methods is not in fact declared in or inherited
        // by `in` (in which case we should indeed return false); or it might mean that we are
        // tickling an Eclipse bug such as https://bugs.eclipse.org/bugs/show_bug.cgi?id=499026
        // (in which case we can't do any better than returning false).
        return false;
      }
      if (!typeUtils.isSubsignature(overriderExecutable, overriddenExecutable)) {
        return false;
      }
      if (!MoreElements.methodVisibleFromPackage(overridden, MoreElements.getPackage(overrider))) {
        // If the overridden method is a package-private method in a different package then it
        // can't be overridden.
        return false;
      }
      TypeElement overriddenType;
      if (!(overridden.getEnclosingElement() instanceof TypeElement)) {
        return false;
        // We don't know how this could happen but we avoid blowing up if it does.
      }
      overriddenType = MoreElements.asType(overridden.getEnclosingElement());
      // We erase the types before checking subtypes, because the TypeMirror we get for List<E> is
      // not a subtype of the one we get for Collection<E> since the two E instances are not the
      // same.  For the purposes of overriding, type parameters in the containing type should not
      // matter because if the code compiles at all then they must be consistent.
      if (!typeUtils.isSubtype(
          typeUtils.erasure(in.asType()), typeUtils.erasure(overriddenType.asType()))) {
        return false;
      }
      if (in.getKind().isClass()) {
        // Method mC in or inherited by class C (JLS 8.4.8.1)...
        if (overriddenType.getKind().isClass()) {
          // ...overrides from C another method mA declared in class A. The only condition we
          // haven't checked is that C does not inherit mA.
          return !elementUtils.getAllMembers(in).contains(overridden);
        } else if (overriddenType.getKind().isInterface()) {
          // ...overrides from C another method mI declared in interface I. We've already checked
          // the conditions (assuming that the only alternative to mI being abstract or default is
          // mI being static, which we eliminated above). However, it appears that the logic here
          // is necessary in order to be compatible with javac's `overrides` method. An inherited
          // abstract method does not override another method. (But, if it is not inherited,
          // it does, including if `in` inherits a concrete method of the same name from its
          // superclass.)
          if (overrider.getModifiers().contains(Modifier.ABSTRACT)) {
            return !elementUtils.getAllMembers(in).contains(overridden);
          } else {
            return true;
          }
        } else {
          // We don't know what this is so say no.
          return false;
        }
      } else {
        return in.getKind().isInterface();
        // Method mI in or inherited by interface I (JLS 9.4.1.1). We've already checked everything.
        // If this is not an interface then we don't know what it is so we say no.
      }
    }
  }
}
