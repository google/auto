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
package com.google.auto.value.processor;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * A wrapper for properties of Optional-like classes. This can be com.google.common.base.Optional,
 * or any of Optional, OptionalDouble, OptionalInt, OptionalLong in java.util.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class Optionalish {
  private static final ImmutableSet<String> OPTIONAL_CLASS_NAMES =
      ImmutableSet.of(
          "com.".concat("google.common.base.Optional"), // subterfuge to foil shading
          "java.util.Optional",
          "java.util.OptionalDouble",
          "java.util.OptionalInt",
          "java.util.OptionalLong");

  private final DeclaredType optionalType;
  private final String className;

  private Optionalish(DeclaredType optionalType) {
    this.optionalType = optionalType;
    this.className = MoreElements.asType(optionalType.asElement()).getQualifiedName().toString();
  }

  /**
   * Returns an instance wrapping the given TypeMirror, or null if it is not any kind of Optional.
   *
   * @param type the TypeMirror for the original optional type, for example {@code
   *     Optional<String>}.
   */
  static Optionalish createIfOptional(TypeMirror type) {
    if (isOptional(type)) {
      return new Optionalish(MoreTypes.asDeclared(type));
    } else {
      return null;
    }
  }

  static boolean isOptional(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }
    DeclaredType declaredType = MoreTypes.asDeclared(type);
    TypeElement typeElement = MoreElements.asType(declaredType.asElement());
    return OPTIONAL_CLASS_NAMES.contains(typeElement.getQualifiedName().toString())
        && typeElement.getTypeParameters().size() == declaredType.getTypeArguments().size();
  }

  /**
   * Returns a string representing the raw type of this Optional. This will typically be just {@code
   * "Optional"}, but it might be {@code "OptionalInt"} or {@code "java.util.Optional"} for example.
   */
  public String getRawType() {
    return TypeEncoder.encodeRaw(optionalType);
  }

  /**
   * Returns a string representing the method call to obtain the empty version of this Optional.
   * This will be something like {@code "Optional.empty()"} or possibly {@code
   * "java.util.Optional.empty()"}. It does not have a final semicolon.
   *
   * <p>This method is public so that it can be referenced as {@code p.optional.empty} from
   * templates.
   */
  public String getEmpty() {
    String empty = className.startsWith("java.util.") ? ".empty()" : ".absent()";
    return TypeEncoder.encodeRaw(optionalType) + empty;
  }

  TypeMirror getContainedType(Types typeUtils) {
    List<? extends TypeMirror> typeArguments = optionalType.getTypeArguments();
    switch (typeArguments.size()) {
      case 1:
        return typeArguments.get(0);
      case 0:
        return getContainedPrimitiveType(typeUtils);
      default:
        throw new AssertionError("Wrong number of type arguments: " + optionalType);
    }
  }

  String ofNullable() {
    return className.equals("java.util.Optional") ? "ofNullable" : "fromNullable";
  }

  private static final ImmutableMap<String, TypeKind> PRIMITIVE_TYPE_KINDS =
      ImmutableMap.of(
          "OptionalDouble", TypeKind.DOUBLE,
          "OptionalInt", TypeKind.INT,
          "OptionalLong", TypeKind.LONG);

  private TypeMirror getContainedPrimitiveType(Types typeUtils) {
    String simpleName = optionalType.asElement().getSimpleName().toString();
    TypeKind typeKind = PRIMITIVE_TYPE_KINDS.get(simpleName);
    Verify.verifyNotNull(typeKind, "Could not get contained type of %s", optionalType);
    return typeUtils.getPrimitiveType(typeKind);
  }
}
