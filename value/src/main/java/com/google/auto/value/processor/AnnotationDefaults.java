/*
 * Copyright (C) 2014 Google, Inc.
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

import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.tools.Diagnostic;

/**
 * Handling of default values for annotation members.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
class AnnotationDefaults {
  private final ProcessingEnvironment processingEnv;
  private final TypeSimplifier typeSimplifier;
  private final ExecutableElement annotatedMethod;

  AnnotationDefaults(
      ProcessingEnvironment processingEnv,
      TypeSimplifier typeSimplifier,
      ExecutableElement annotatedMethod) {
    this.processingEnv = processingEnv;
    this.typeSimplifier = typeSimplifier;
    this.annotatedMethod = annotatedMethod;
  }

  /**
   * Visitor that produces a string representation of an annotation value, suitable for inclusion
   * in a Java source file as the initializer of a variable of the appropriate type.
   */
  private class SourceFormVisitor extends SimpleAnnotationValueVisitor6<StringBuilder, Void> {
    private final StringBuilder sb;
    private final ExecutableElement memberMethod;

    SourceFormVisitor(StringBuilder sb, ExecutableElement memberMethod) {
      this.sb = sb;
      this.memberMethod = memberMethod;
    }

    @Override
    protected StringBuilder defaultAction(Object value, Void p) {
      return sb.append(value);
    }

    @Override
    public StringBuilder visitAnnotation(AnnotationMirror a, Void p) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          "@AutoAnnotation cannot yet supply a default value for annotation-valued member '"
              + memberMethod.getSimpleName() + "'",
          annotatedMethod);
      return sb.append("null");
    }

    @Override
    public StringBuilder visitArray(List<? extends AnnotationValue> values, Void p) {
      sb.append('{');
      String sep = "";
      for (AnnotationValue value : values) {
        sb.append(sep);
        visit(value);
        sep = ", ";
      }
      return sb.append('}');
    }

    @Override
    public StringBuilder visitChar(char c, Void p) {
      return appendQuoted(sb, c);
    }

    @Override
    public StringBuilder visitLong(long i, Void p) {
      return sb.append(i).append('L');
    }

    @Override
    public StringBuilder visitDouble(double d, Void p) {
      if (Double.isNaN(d)) {
        return sb.append("Double.NaN");
      } else if (d == Double.POSITIVE_INFINITY) {
        return sb.append("Double.POSITIVE_INFINITY");
      } else if (d == Double.NEGATIVE_INFINITY) {
        return sb.append("Double.NEGATIVE_INFINITY");
      } else {
        return sb.append(d);
      }
    }

    @Override
    public StringBuilder visitFloat(float f, Void p) {
      if (Float.isNaN(f)) {
        return sb.append("Float.NaN");
      } else if (f == Float.POSITIVE_INFINITY) {
        return sb.append("Float.POSITIVE_INFINITY");
      } else if (f == Float.NEGATIVE_INFINITY) {
        return sb.append("Float.NEGATIVE_INFINITY");
      } else {
        return sb.append(f).append('F');
      }
    }

    @Override
    public StringBuilder visitEnumConstant(VariableElement c, Void p) {
      return sb.append(typeSimplifier.simplify(c.asType())).append('.').append(c.getSimpleName());
    }

    @Override
    public StringBuilder visitString(String s, Void p) {
      return appendQuoted(sb, s);
    }

    @Override
    public StringBuilder visitType(TypeMirror classConstant, Void p) {
      return sb.append(typeSimplifier.simplify(classConstant)).append(".class");
    }
  }

  /**
   * Returns a string representation of the default value of the given annotation member, suitable
   * for inclusion in a Java source file as the initializer of a variable of the appropriate type.
   */
  String sourceForm(ExecutableElement memberMethod) {
    AnnotationValue defaultValue = memberMethod.getDefaultValue();
    TypeMirror type = memberMethod.getReturnType();
    StringBuilder sb = new StringBuilder();
    SourceFormVisitor visitor = new SourceFormVisitor(sb, memberMethod);
    visitor.visit(defaultValue);
    return sb.toString();
  }

  private static StringBuilder appendQuoted(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      appendEscaped(sb, s.charAt(i));
    }
    return sb.append('"');
  }

  private static StringBuilder appendQuoted(StringBuilder sb, char c) {
    sb.append('\'');
    appendEscaped(sb, c);
    return sb.append('\'');
  }

  private static void appendEscaped(StringBuilder sb, char c) {
    switch (c) {
    case '\\':
    case '"':
    case '\'':
      sb.append('\\').append(c);
      break;
    case '\n':
      sb.append("\\n");
      break;
    case '\r':
      sb.append("\\r");
      break;
    case '\t':
      sb.append("\\t");
      break;
    default:
      if (c < 0x20) {
        sb.append(String.format("\\%03o", (int) c));
      } else if (c < 0x7f || Character.isLetter(c)) {
        sb.append(c);
      } else {
        sb.append(String.format("\\u%04x", (int) c));
      }
      break;
    }
  }
}
