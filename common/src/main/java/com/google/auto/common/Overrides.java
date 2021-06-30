/*
 * Copyright 2016 Google LLC
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

import static java.util.stream.Collectors.toList;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;
import org.checkerframework.checker.nullness.qual.Nullable;

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

    ExplicitOverrides(Types typeUtils) {
      this.typeUtils = typeUtils;
    }

    @Override
    public boolean overrides(
        ExecutableElement overrider, ExecutableElement overridden, TypeElement in) {
      if (!overrider.getSimpleName().equals(overridden.getSimpleName())) {
        // They must have the same name.
        return false;
      }
      // We should just be able to write overrider.equals(overridden) here, but that runs afoul
      // of a problem with Eclipse. If for example you look at the method Stream<E> stream() in
      // Collection<E>, as obtained by collectionTypeElement.getEnclosedElements(), it will not
      // compare equal to the method Stream<E> stream() as obtained by
      // elementUtils.getAllMembers(listTypeElement), even though List<E> inherits the method
      // from Collection<E>. The reason is that, in ecj, getAllMembers does type substitution,
      // so the return type of stream() is Stream<E'>, where E' is the E from List<E> rather than
      // the one from Collection<E>. Instead we compare the enclosing element, which will be
      // Collection<E> no matter how we got the method. If two methods are in the same type
      // then it's impossible for one to override the other, regardless of whether they are the
      // same method.
      if (overrider.getEnclosingElement().equals(overridden.getEnclosingElement())) {
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
      if (!isSubsignature(overrider, overridden, in)) {
        return false;
      }
      if (!MoreElements.methodVisibleFromPackage(overridden, MoreElements.getPackage(overrider))) {
        // If the overridden method is a package-private method in a different package then it
        // can't be overridden.
        return false;
      }
      if (!(overridden.getEnclosingElement() instanceof TypeElement)) {
        return false;
        // We don't know how this could happen but we avoid blowing up if it does.
      }
      TypeElement overriddenType = MoreElements.asType(overridden.getEnclosingElement());
      // We erase the types before checking subtypes, because the TypeMirror we get for List<E> is
      // not a subtype of the one we get for Collection<E> since the two E instances are not the
      // same. For the purposes of overriding, type parameters in the containing type should not
      // matter because if the code compiles at all then they must be consistent.
      if (!typeUtils.isSubtype(
          typeUtils.erasure(in.asType()), typeUtils.erasure(overriddenType.asType()))) {
        return false;
      }
      if (in.getKind().isClass()) {
        // Method mC in or inherited by class C (JLS 8.4.8.1)...
        if (overriddenType.getKind().isClass()) {
          // ...overrides from C another method mA declared in class A. The only condition we
          // haven't checked is that C does not inherit mA. Ideally we could just write this:
          //    return !elementUtils.getAllMembers(in).contains(overridden);
          // But that doesn't work in Eclipse. For example, getAllMembers(AbstractList)
          // contains List.isEmpty() where you might reasonably expect it to contain
          // AbstractCollection.isEmpty(). So we need to visit superclasses until we reach
          // one that declares the same method, and check that we haven't reached mA. We compare
          // the enclosing elements rather than the methods themselves for the reason described
          // at the start of the method.
          ExecutableElement inherited = methodFromSuperclasses(in, overridden);
          return inherited != null
              && !overridden.getEnclosingElement().equals(inherited.getEnclosingElement());
        } else if (overriddenType.getKind().isInterface()) {
          // ...overrides from C another method mI declared in interface I. We've already checked
          // the conditions (assuming that the only alternative to mI being abstract or default is
          // mI being static, which we eliminated above). However, it appears that the logic here
          // is necessary in order to be compatible with javac's `overrides` method. An inherited
          // abstract method does not override another method. (But, if it is not inherited,
          // it does, including if `in` inherits a concrete method of the same name from its
          // superclass.) Here again we can use getAllMembers with javac but not with ecj. javac
          // says that getAllMembers(AbstractList) contains both AbstractCollection.size() and
          // List.size(), but ecj doesn't have the latter. The spec is not particularly clear so
          // either seems justifiable. So we need to look up the interface path that goes from `in`
          // to `overriddenType` (or the several paths if there are several) and apply similar logic
          // to methodFromSuperclasses above.
          if (overrider.getModifiers().contains(Modifier.ABSTRACT)) {
            ExecutableElement inherited = methodFromSuperinterfaces(in, overridden);
            return inherited != null
                && !overridden.getEnclosingElement().equals(inherited.getEnclosingElement());
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

    private boolean isSubsignature(
        ExecutableElement overrider, ExecutableElement overridden, TypeElement in) {
      DeclaredType inType = MoreTypes.asDeclared(in.asType());
      try {
        ExecutableType overriderExecutable =
            MoreTypes.asExecutable(typeUtils.asMemberOf(inType, overrider));
        ExecutableType overriddenExecutable =
            MoreTypes.asExecutable(typeUtils.asMemberOf(inType, overridden));
        return typeUtils.isSubsignature(overriderExecutable, overriddenExecutable);
      } catch (IllegalArgumentException e) {
        // This might mean that at least one of the methods is not in fact declared in or inherited
        // by `in` (in which case we should indeed return false); or it might mean that we are
        // tickling an Eclipse bug such as https://bugs.eclipse.org/bugs/show_bug.cgi?id=499026
        // (in which case we fall back on explicit code to find the parameters).
        int nParams = overrider.getParameters().size();
        if (overridden.getParameters().size() != nParams) {
          return false;
        }
        List<TypeMirror> overriderParams = erasedParameterTypes(overrider, in);
        List<TypeMirror> overriddenParams = erasedParameterTypes(overridden, in);
        if (overriderParams == null || overriddenParams == null) {
          // This probably means that one or other of the methods is not in `in`.
          return false;
        }
        for (int i = 0; i < nParams; i++) {
          if (!typeUtils.isSameType(overriderParams.get(i), overriddenParams.get(i))) {
            // If the erasures of the parameters don't correspond, return false. We erase so we
            // don't get any confusion about different type variables not comparing equal.
            return false;
          }
        }
        return true;
      }
    }

    /**
     * Returns the list of erased parameter types of the given method as they appear in the given
     * type. For example, if the method is {@code add(E)} from {@code List<E>} and we ask how it
     * appears in {@code class NumberList implements List<Number>}, the answer will be
     * {@code Number}. That will also be the answer for {@code class NumberList<E extends Number>
     * implements List<E>}. The parameter types are erased since the purpose of this method is to
     * determine whether two methods are candidates for one to override the other.
     */
    @Nullable
    ImmutableList<TypeMirror> erasedParameterTypes(ExecutableElement method, TypeElement in) {
      if (method.getParameters().isEmpty()) {
        return ImmutableList.of();
      }
      return new TypeSubstVisitor().erasedParameterTypes(method, in);
    }

    /**
     * Visitor that replaces type variables with their values in the types it sees. If we know
     * that {@code E} is {@code String}, then we can return {@code String} for {@code E},
     * {@code List<String>} for {@code List<E>}, {@code String[]} for {@code E[]}, etc. We don't
     * have to cover all types here because (1) the type is going to end up being erased, and
     * (2) wildcards can't appear in direct supertypes. So for example it is illegal to write
     * {@code class MyList implements List<? extends Number>}. It's legal to write
     * {@code class MyList implements List<Set<? extends Number>>} but that doesn't matter
     * because the {@code E} of the {@code List} is going to be erased to raw {@code Set}.
     */
    private class TypeSubstVisitor extends SimpleTypeVisitor8<TypeMirror, Void> {
      /**
       * The bindings of type variables. We can put them all in one map because E in {@code List<E>}
       * is not the same as E in {@code Collection<E>}. As we ascend the type hierarchy we'll add
       * mappings for all the variables we see. We could equivalently create a new map for each type
       * we visit, but this is slightly simpler and probably about as performant.
       */
      private final Map<TypeParameterElement, TypeMirror> typeBindings = Maps.newLinkedHashMap();

      @Nullable
      ImmutableList<TypeMirror> erasedParameterTypes(ExecutableElement method, TypeElement in) {
        if (method.getEnclosingElement().equals(in)) {
          ImmutableList.Builder<TypeMirror> params = ImmutableList.builder();
          for (VariableElement param : method.getParameters()) {
            params.add(typeUtils.erasure(visit(param.asType())));
          }
          return params.build();
        }
        // Make a list of supertypes we are going to visit recursively: the superclass, if there
        // is one, plus the superinterfaces.
        List<TypeMirror> supers = Lists.newArrayList();
        if (in.getSuperclass().getKind() == TypeKind.DECLARED) {
          supers.add(in.getSuperclass());
        }
        supers.addAll(in.getInterfaces());
        for (TypeMirror supertype : supers) {
          DeclaredType declared = MoreTypes.asDeclared(supertype);
          TypeElement element = MoreElements.asType(declared.asElement());
          List<? extends TypeMirror> actuals = declared.getTypeArguments();
          List<? extends TypeParameterElement> formals = element.getTypeParameters();
          if (actuals.isEmpty()) {
            // Either the formal type arguments are also empty or `declared` is raw.
            actuals = formals.stream().map(t -> t.getBounds().get(0)).collect(toList());
          }
          Verify.verify(actuals.size() == formals.size());
          for (int i = 0; i < actuals.size(); i++) {
            typeBindings.put(formals.get(i), actuals.get(i));
          }
          ImmutableList<TypeMirror> params = erasedParameterTypes(method, element);
          if (params != null) {
            return params;
          }
        }
        return null;
      }

      @Override
      protected TypeMirror defaultAction(TypeMirror e, Void p) {
        return e;
      }

      @Override
      public TypeMirror visitTypeVariable(TypeVariable t, Void p) {
        Element element = typeUtils.asElement(t);
        if (element instanceof TypeParameterElement) {
          TypeParameterElement e = (TypeParameterElement) element;
          if (typeBindings.containsKey(e)) {
            return visit(typeBindings.get(e));
          }
        }
        // We erase the upper bound to avoid infinite recursion. We can get away with erasure for
        // the reasons described above.
        return visit(typeUtils.erasure(t.getUpperBound()));
      }

      @Override
      public TypeMirror visitDeclared(DeclaredType t, Void p) {
        if (t.getTypeArguments().isEmpty()) {
          return t;
        }
        List<TypeMirror> newArgs = Lists.newArrayList();
        for (TypeMirror arg : t.getTypeArguments()) {
          newArgs.add(visit(arg));
        }
        return typeUtils.getDeclaredType(asTypeElement(t), newArgs.toArray(new TypeMirror[0]));
      }

      @Override
      public TypeMirror visitArray(ArrayType t, Void p) {
        return typeUtils.getArrayType(visit(t.getComponentType()));
      }
    }

    /**
     * Returns the given method as it appears in the given type. This is the method itself,
     * or the nearest override in a superclass of the given type, or null if the method is not
     * found in the given type or any of its superclasses.
     */
    @Nullable ExecutableElement methodFromSuperclasses(TypeElement in, ExecutableElement method) {
      for (TypeElement t = in; t != null; t = superclass(t)) {
        ExecutableElement tMethod = methodInType(t, method);
        if (tMethod != null) {
          return tMethod;
        }
      }
      return null;
    }

    /**
     * Returns the given interface method as it appears in the given type. This is the method
     * itself, or the nearest override in a superinterface of the given type, or null if the method
     * is not found in the given type or any of its transitive superinterfaces.
     */
    @Nullable
    ExecutableElement methodFromSuperinterfaces(TypeElement in, ExecutableElement method) {
      TypeElement methodContainer = MoreElements.asType(method.getEnclosingElement());
      Preconditions.checkArgument(methodContainer.getKind().isInterface());
      TypeMirror methodContainerType = typeUtils.erasure(methodContainer.asType());
      ImmutableList<TypeElement> types = ImmutableList.of(in);
      // On the first pass through this loop, `types` is the type we're starting from,
      // which might be a class or an interface. On later passes it is a list of direct
      // superinterfaces we saw in the previous pass, but only the ones that were assignable
      // to the interface that `method` appears in.
      while (!types.isEmpty()) {
        ImmutableList.Builder<TypeElement> newTypes = ImmutableList.builder();
        for (TypeElement t : types) {
          TypeMirror candidateType = typeUtils.erasure(t.asType());
          if (typeUtils.isAssignable(candidateType, methodContainerType)) {
            ExecutableElement tMethod = methodInType(t, method);
            if (tMethod != null) {
              return tMethod;
            }
            newTypes.addAll(superinterfaces(t));
          }
          if (t.getKind().isClass()) {
            TypeElement sup = superclass(t);
            if (sup != null) {
              newTypes.add(sup);
            }
          }
        }
        types = newTypes.build();
      }
      return null;
    }

    /**
     * Returns the method from within the given type that has the same erased signature as the given
     * method, or null if there is no such method.
     */
    private @Nullable ExecutableElement methodInType(TypeElement type, ExecutableElement method) {
      int nParams = method.getParameters().size();
      List<TypeMirror> params = erasedParameterTypes(method, type);
      if (params == null) {
        return null;
      }
      methods:
      for (ExecutableElement tMethod : ElementFilter.methodsIn(type.getEnclosedElements())) {
        if (tMethod.getSimpleName().equals(method.getSimpleName())
            && tMethod.getParameters().size() == nParams) {
          for (int i = 0; i < nParams; i++) {
            TypeMirror tParamType = typeUtils.erasure(tMethod.getParameters().get(i).asType());
            if (!typeUtils.isSameType(params.get(i), tParamType)) {
              continue methods;
            }
          }
          return tMethod;
        }
      }
      return null;
    }

    private @Nullable TypeElement superclass(TypeElement type) {
      TypeMirror sup = type.getSuperclass();
      if (sup.getKind() == TypeKind.DECLARED) {
        return MoreElements.asType(typeUtils.asElement(sup));
      } else {
        return null;
      }
    }

    private ImmutableList<TypeElement> superinterfaces(TypeElement type) {
      ImmutableList.Builder<TypeElement> types = ImmutableList.builder();
      for (TypeMirror sup : type.getInterfaces()) {
        types.add(MoreElements.asType(typeUtils.asElement(sup)));
      }
      return types.build();
    }

    private TypeElement asTypeElement(TypeMirror typeMirror) {
      DeclaredType declaredType = MoreTypes.asDeclared(typeMirror);
      Element element = declaredType.asElement();
      return MoreElements.asType(element);
    }
  }
}
