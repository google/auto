/*
 * Copyright (C) 2019 Google, Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Methods for handling type variables.
 */
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
   * We want to be able to check that the parameter type of {@code setFoo} is the same as the
   * return type of {@code getFoo}. But in fact it isn't, because the {@code T} of {@code Foo<T>}
   * is not the same as the {@code T} of {@code Foo.Builder<T>}. So we create a parallel
   * {@code Foo<T>} where the {@code T} <i>is</i> the one from {@code Foo.Builder<T>}. That way the
   * types do correspond. This method then returns the return types of the given methods as they
   * appear in that parallel class, meaning the type given for {@code getFoo()} is the {@code T} of
   * {@code Foo.Builder<T>}.
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

    if (sourceTypeParameters.isEmpty()) {
      return methods.stream()
          .collect(ImmutableMap.toImmutableMap(m -> m, ExecutableElement::getReturnType));
    }
    EclipseHack eclipseHack = new EclipseHack(elementUtils, typeUtils);
    TypeMirror[] targetTypeParameterMirrors = new TypeMirror[targetTypeParameters.size()];
    for (int i = 0; i < targetTypeParameters.size(); i++) {
      targetTypeParameterMirrors[i] = targetTypeParameters.get(i).asType();
    }
    DeclaredType parallelSource = typeUtils.getDeclaredType(sourceType, targetTypeParameterMirrors);
    return methods.stream()
        .collect(
            ImmutableMap.toImmutableMap(
                m -> m, m -> eclipseHack.methodReturnType(m, parallelSource)));
  }
}
