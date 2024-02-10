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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.util.Objects;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * A {@link TypeMirror} and associated annotations.
 *
 * <p>The reason this class is needed is that certain methods in the {@code javax.lang.model} API
 * return modified versions of types, for example {@link javax.lang.model.util.Types#asMemberOf}.
 * Historically, Java compilers were a bit inconsistent about whether those modified types preserve
 * annotations that were in the original type, but the recent consensus is that they should not.
 * Suppose for example if we have this:
 *
 * <pre>{@code
 * interface Parent<T> {
 *   @Nullable T thing();
 * }
 * abstract class Child implements Parent<String> {}
 * }</pre>
 *
 * <p>If we use {@code asMemberOf} to determine what the return type of {@code Child.thing()} is, we
 * will discover it is {@code String}. But we really wanted {@code @Nullable String}. To fix that,
 * we combine the annotations from {@code Parent.thing()} with the resolved type from {@code
 * Child.thing()}.
 *
 * <p>This is only a partial workaround. We aren't able to splice the {@code @Nullable} from {@code
 * List<@Nullable T>} into a type like {@code List<String>} that might be returned from {@code
 * asMemberOf}. Probably a more complete solution would be to adapt the type substitution logic in
 * {@link TypeVariables} so that it can be used instead of {@code Types.asMemberOf} and so that it
 * reattaches annotations on type parameters to the corresponding type arguments, like the {@code
 * List<@Nullable String>} in the example.
 *
 * <p>https://bugs.openjdk.org/browse/JDK-8174126 would potentially provide the basis for a cleaner
 * solution, via a new {@code Types.withAnnotations(type, annotations)} method.
 *
 * <p>This class deliberately does not implement {@link TypeMirror}. Making "mutant" {@code
 * TypeMirror} instances is a bit dangerous, because if you give such a thing to one of the {@code
 * javax.lang.model} APIs then you will almost certainly get a {@code ClassCastException}. Those
 * APIs only expect objects that they themselves produced.
 */
final class AnnotatedTypeMirror {
  private final TypeMirror originalType;
  private final TypeMirror rewrittenType;

  AnnotatedTypeMirror(TypeMirror originalType, TypeMirror rewrittenType) {
    this.originalType = originalType;
    this.rewrittenType = rewrittenType;
  }

  AnnotatedTypeMirror(TypeMirror type) {
    this(type, type);
  }

  ImmutableList<AnnotationMirror> annotations() {
    return ImmutableList.copyOf(originalType.getAnnotationMirrors());
  }

  TypeMirror getType() {
    return rewrittenType;
  }

  TypeKind getKind() {
    return rewrittenType.getKind();
  }

  @Override
  public String toString() {
    String annotations = Joiner.on(' ').join(originalType.getAnnotationMirrors());
    return annotations.isEmpty() ? rewrittenType.toString() : annotations + " " + rewrittenType;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof AnnotatedTypeMirror) {
      AnnotatedTypeMirror that = (AnnotatedTypeMirror) obj;
      // This is just for tests. If we wanted to have a genuinely useful `equals` method we would
      // probably want it to use something like `Types.isSameType`.
      return this.originalType == that.originalType && this.rewrittenType == that.rewrittenType;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(originalType, rewrittenType);
  }
}
