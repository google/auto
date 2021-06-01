/*
 * Copyright 2019 Google LLC
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

import static com.google.auto.common.MoreStreams.toImmutableMap;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;

/** Methods for handling type variables. */
final class TypeVariables {
  private TypeVariables() {}

  /**
   * Returns a map from methods to return types, where the return types are not necessarily the
   * original return types of the methods. Consider this example:
   *
   * <pre>
   * &#64;AutoValue class {@code Foo<T>} {
   *   abstract T getFoo();
   *
   *   &#64;AutoValue.Builder
   *   abstract class {@code Builder<T>} {
   *     abstract Builder setFoo(T t);
   *     abstract {@code Foo<T>} build();
   *   }
   * }
   * </pre>
   *
   * We want to be able to check that the parameter type of {@code setFoo} is the same as the return
   * type of {@code getFoo}. But in fact it isn't, because the {@code T} of {@code Foo<T>} is not
   * the same as the {@code T} of {@code Foo.Builder<T>}. So we create a parallel {@code Foo<T>}
   * where the {@code T} <i>is</i> the one from {@code Foo.Builder<T>}. That way the types do
   * correspond. This method then returns the return types of the given methods as they appear in
   * that parallel class, meaning the type given for {@code getFoo()} is the {@code T} of {@code
   * Foo.Builder<T>}.
   *
   * <p>We do the rewrite this way around (applying the type parameter from {@code Foo.Builder} to
   * {@code Foo}) because if we hit one of the historical Eclipse bugs with {@link Types#asMemberOf}
   * then {@link EclipseHack#methodReturnType} can use fallback logic, which only works for methods
   * with no arguments.
   *
   * @param methods the methods whose return types are to be rewritten.
   * @param sourceType the class containing those methods ({@code Foo} in the example).
   * @param targetType the class to translate the methods into ({@code Foo.Builder<T>}) in the
   *     example.
   */
  static ImmutableMap<ExecutableElement, TypeMirror> rewriteReturnTypes(
      Elements elementUtils,
      Types typeUtils,
      Collection<ExecutableElement> methods,
      TypeElement sourceType,
      TypeElement targetType) {
    List<? extends TypeParameterElement> sourceTypeParameters = sourceType.getTypeParameters();
    List<? extends TypeParameterElement> targetTypeParameters = targetType.getTypeParameters();
    Preconditions.checkArgument(
        sourceTypeParameters.toString().equals(targetTypeParameters.toString()),
        "%s != %s",
        sourceTypeParameters,
        targetTypeParameters);
    // What we're doing is only valid if the type parameters are "the same". The check here even
    // requires the names to be the same. The logic would still work without that, but we impose
    // that requirement elsewhere and it means we can check in this simple way.
    EclipseHack eclipseHack = new EclipseHack(elementUtils, typeUtils);
    TypeMirror[] targetTypeParameterMirrors = new TypeMirror[targetTypeParameters.size()];
    for (int i = 0; i < targetTypeParameters.size(); i++) {
      targetTypeParameterMirrors[i] = targetTypeParameters.get(i).asType();
    }
    DeclaredType parallelSource = typeUtils.getDeclaredType(sourceType, targetTypeParameterMirrors);
    return methods.stream()
        .collect(toImmutableMap(m -> m, m -> eclipseHack.methodReturnType(m, parallelSource)));
  }

  /**
   * Tests whether a given parameter can be given to a static method like {@code
   * ImmutableMap.copyOf} to produce a value that can be assigned to the given target type.
   *
   * <p>For example, suppose we have this method in {@code ImmutableMap}:<br>
   * {@code static <K, V> ImmutableMap<K, V> copyOf(Map<? extends K, ? extends V>)}<br>
   * and we want to know if we can do this:
   *
   * <pre>
   * {@code ImmutableMap<String, Integer> actualParameter = ...;}
   * {@code ImmutableMap<String, Number> target = ImmutableMap.copyOf(actualParameter);}
   * </pre>
   *
   * We will infer {@code K=String}, {@code V=Number} based on the target type, and then rewrite the
   * formal parameter type from<br>
   * {@code Map<? extends K, ? extends V>} to<br>
   * {@code Map<? extends String, ? extends Number>}. Then we can check whether {@code
   * actualParameter} is assignable to that.
   *
   * <p>The logic makes some simplifying assumptions, which are met for the {@code copyOf} and
   * {@code of} methods that we use this for. The method must be static, it must have exactly one
   * parameter, and it must have type parameters without bounds that are the same as the type
   * parameters of its return type. We can see that these assumptions are met for the {@code
   * ImmutableMap.copyOf} example above.
   */
  static boolean canAssignStaticMethodResult(
      ExecutableElement method,
      TypeMirror actualParameterType,
      TypeMirror targetType,
      Types typeUtils) {
    if (!targetType.getKind().equals(TypeKind.DECLARED)
        || !method.getModifiers().contains(Modifier.STATIC)
        || method.getParameters().size() != 1) {
      return false;
    }
    List<? extends TypeParameterElement> typeParameters = method.getTypeParameters();
    List<? extends TypeMirror> targetTypeArguments =
        MoreTypes.asDeclared(targetType).getTypeArguments();
    if (typeParameters.size() != targetTypeArguments.size()) {
      return false;
    }
    Map<Equivalence.Wrapper<TypeVariable>, TypeMirror> typeVariables = new LinkedHashMap<>();
    for (int i = 0; i < typeParameters.size(); i++) {
      TypeVariable v = MoreTypes.asTypeVariable(typeParameters.get(i).asType());
      typeVariables.put(MoreTypes.equivalence().wrap(v), targetTypeArguments.get(i));
    }
    Function<TypeVariable, TypeMirror> substitute =
        v -> typeVariables.get(MoreTypes.equivalence().wrap(v));
    TypeMirror formalParameterType = method.getParameters().get(0).asType();
    SubstitutionVisitor substitutionVisitor = new SubstitutionVisitor(substitute, typeUtils);
    TypeMirror substitutedParameterType = substitutionVisitor.visit(formalParameterType, null);
    if (substitutedParameterType.getKind().equals(TypeKind.WILDCARD)) {
      // If the target type is Optional<? extends Foo> then <T> T Optional.of(T) will give us
      // ? extends Foo here, and typeUtils.isAssignable will return false. But we can in fact
      // give a Foo as an argument, so we just replace ? extends Foo with Foo.
      WildcardType wildcard = MoreTypes.asWildcard(substitutedParameterType);
      if (wildcard.getExtendsBound() != null) {
        substitutedParameterType = wildcard.getExtendsBound();
      }
    }
    return typeUtils.isAssignable(actualParameterType, substitutedParameterType);
  }

  static TypeMirror substituteTypeVariables(
      TypeMirror input, Function<TypeVariable, TypeMirror> substitute, Types typeUtils) {
    SubstitutionVisitor substitutionVisitor = new SubstitutionVisitor(substitute, typeUtils);
    return substitutionVisitor.visit(input, null);
  }

  /**
   * Rewrites types such that references to type variables in the given map are replaced by the
   * values of those variables.
   */
  private static class SubstitutionVisitor extends SimpleTypeVisitor8<TypeMirror, Void> {
    private final Function<TypeVariable, TypeMirror> substitute;
    private final Types typeUtils;

    SubstitutionVisitor(Function<TypeVariable, TypeMirror> substitute, Types typeUtils) {
      this.substitute = substitute;
      this.typeUtils = typeUtils;
    }

    @Override
    protected TypeMirror defaultAction(TypeMirror t, Void p) {
      return t;
    }

    @Override
    public TypeMirror visitTypeVariable(TypeVariable t, Void p) {
      TypeMirror substituted = substitute.apply(t);
      return (substituted == null) ? t : substituted;
    }

    @Override
    public TypeMirror visitDeclared(DeclaredType t, Void p) {
      List<? extends TypeMirror> typeArguments = t.getTypeArguments();
      TypeMirror[] substitutedTypeArguments = new TypeMirror[typeArguments.size()];
      for (int i = 0; i < typeArguments.size(); i++) {
        substitutedTypeArguments[i] = visit(typeArguments.get(i));
      }
      return typeUtils.getDeclaredType(
          MoreElements.asType(t.asElement()), substitutedTypeArguments);
    }

    @Override
    public TypeMirror visitWildcard(WildcardType t, Void p) {
      TypeMirror ext = visitOrNull(t.getExtendsBound());
      if (ext != null && ext.getKind().equals(TypeKind.WILDCARD)) {
        // An example of where this happens is if we have this method in ImmutableSet:
        //    static <E> ImmutableSet<E> copyOf(Collection<? extends E>)
        // and we want to know if we can do this (where T is parameter of the enclosing class):
        //    ImmutableSet<T> actualParameter = ...
        //    ImmutableSet<? extends T> target = ImmutableSet.copyOf(actualParameter);
        // We will infer E=<? extends T> and rewrite the formal parameter type to
        // Collection<? extends ? extends T>, which we must simplify to Collection<? extends T>.
        return ext;
      }
      return typeUtils.getWildcardType(ext, visitOrNull(t.getSuperBound()));
    }

    @Override
    public TypeMirror visitArray(ArrayType t, Void p) {
      TypeMirror comp = visit(t.getComponentType());
      if (comp.getKind().equals(TypeKind.WILDCARD)) {
        // An example of where this happens is if we have this method in ImmutableSet:
        //    static <E> ImmutableSet<E> copyOf(E[])
        // and we want to know if we can do this:
        //    String[] actualParameter = ...
        //    ImmutableSet<? extends T> target = ImmutableSet.copyOf(actualParameter);
        // We will infer E=<? extends T> and rewrite the formal parameter type to
        // <? extends T>[], which we must simplify to T[].
        // TODO: what if getExtendsBound() returns null?
        comp = MoreTypes.asWildcard(comp).getExtendsBound();
      }
      return typeUtils.getArrayType(comp);
    }

    // We'd like to override visitIntersectionType here for completeness, but Types has no method
    // to fabricate a new IntersectionType. Anyway, currently IntersectionType can only occur as
    // the result of TypeVariable.getUpperBound(), but the TypeMirror we're starting from is not
    // that, and if we encounter a TypeVariable during our visit we don't visit its upper bound.

    private TypeMirror visitOrNull(TypeMirror t) {
      if (t == null) {
        return null;
      } else {
        return visit(t);
      }
    }
  }
}
