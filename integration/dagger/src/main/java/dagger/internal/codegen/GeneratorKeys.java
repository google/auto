/*
 * Copyright (C) 2012 Square, Inc.
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
package dagger.internal.codegen;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Creates keys using javac's mirror APIs. Unlike {@code Keys}, this class uses
 * APIs not available on Android.
 */
final class GeneratorKeys {
  private static final String SET_PREFIX = Set.class.getName() + "<";

  private GeneratorKeys() {
  }

  /**
   * Returns the members injector key for the raw type of {@code type}.
   * Parameterized types are not currently supported for members injection in
   * generated code.
   */
  public static String rawMembersKey(TypeMirror type) {
    return "members/" + CodeGen.rawTypeToString(type, '$');
  }

  /** Returns the provider key for {@code type}. */
  public static String get(TypeMirror type) {
    StringBuilder result = new StringBuilder();
    CodeGen.typeToString(type, result, '$');
    return result.toString();
  }

  /** Returns the provided key for {@code method}. */
  public static String get(ExecutableElement method) {
    StringBuilder result = new StringBuilder();
    AnnotationMirror qualifier = getQualifier(method.getAnnotationMirrors(), method);
    if (qualifier != null) {
      qualifierToString(qualifier, result);
    }
    CodeGen.typeToString(method.getReturnType(), result, '$');
    return result.toString();
  }

  /** Returns the provided key for {@code method} wrapped by {@code Set}. */
  public static String getElementKey(ExecutableElement method) {
    StringBuilder result = new StringBuilder();
    AnnotationMirror qualifier = getQualifier(method.getAnnotationMirrors(), method);
    if (qualifier != null) {
      qualifierToString(qualifier, result);
    }
    result.append(SET_PREFIX);
    CodeGen.typeToString(method.getReturnType(), result, '$');
    result.append(">");
    return result.toString();
  }

  /** Returns the provider key for {@code variable}. */
  public static String get(VariableElement variable) {
    StringBuilder result = new StringBuilder();
    AnnotationMirror qualifier = getQualifier(variable.getAnnotationMirrors(), variable);
    if (qualifier != null) {
      qualifierToString(qualifier, result);
    }
    CodeGen.typeToString(variable.asType(), result, '$');
    return result.toString();
  }

  private static void qualifierToString(AnnotationMirror qualifier, StringBuilder result) {
    // TODO: guarantee that element values are sorted by name (if there are multiple)
    result.append('@');
    CodeGen.typeToString(qualifier.getAnnotationType(), result, '$');
    result.append('(');
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
        : qualifier.getElementValues().entrySet()) {
      result.append(entry.getKey().getSimpleName());
      result.append('=');
      result.append(entry.getValue().getValue());
    }
    result.append(")/");
  }

  private static AnnotationMirror getQualifier(
      List<? extends AnnotationMirror> annotations, Object member) {
    AnnotationMirror qualifier = null;
    for (AnnotationMirror annotation : annotations) {
      if (annotation.getAnnotationType().asElement().getAnnotation(Qualifier.class) == null) {
        continue;
      }
      if (qualifier != null) {
        throw new IllegalArgumentException("Too many qualifier annotations on " + member);
      }
      qualifier = annotation;
    }
    return qualifier;
  }
}
