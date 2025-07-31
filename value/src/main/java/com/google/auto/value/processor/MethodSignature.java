/*
 * Copyright 2024 Google LLC
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

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import java.util.stream.IntStream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Types;

/**
 * Represents the signature of a method: the types of its parameters and of its return value. The
 * types in question are represented using {@link AnnotatedTypeMirror}, which is a {@link
 * TypeMirror} and associated type annotations.
 */
final class MethodSignature {
  private final ExecutableType originalMethod;
  private final ExecutableType rewrittenMethod;

  private MethodSignature(ExecutableType originalMethod, ExecutableType rewrittenMethod) {
    this.originalMethod = originalMethod;
    this.rewrittenMethod = rewrittenMethod;
  }

  ImmutableList<AnnotatedTypeMirror> parameterTypes() {
    return IntStream.range(0, originalMethod.getParameterTypes().size())
        .mapToObj(
            i ->
                new AnnotatedTypeMirror(
                    originalMethod.getParameterTypes().get(i),
                    rewrittenMethod.getParameterTypes().get(i)))
        .collect(toImmutableList());
  }

  AnnotatedTypeMirror returnType() {
    return new AnnotatedTypeMirror(originalMethod.getReturnType(), rewrittenMethod.getReturnType());
  }

  static MethodSignature asMemberOf(Types typeUtils, DeclaredType in, ExecutableElement method) {
    return new MethodSignature(
        asExecutable(method.asType()), asExecutable(typeUtils.asMemberOf(in, method)));
  }

  static MethodSignature asMemberOf(Types typeUtils, TypeElement in, ExecutableElement method) {
    return asMemberOf(typeUtils, asDeclared(in.asType()), method);
  }
}
