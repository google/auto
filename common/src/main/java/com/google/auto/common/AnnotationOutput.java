/*
 * Copyright 2014 Google LLC
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

import static com.google.auto.common.MoreTypes.asTypeElement;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Handling of default values for annotation members.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
final class AnnotationOutput {
  private AnnotationOutput() {} // There are no instances of this class.

  /**
   * Visitor that produces a string representation of an annotation value, suitable for inclusion in
   * a Java source file as an annotation member or as the initializer of a variable of the
   * appropriate type. The syntax for the two is the same except for annotation members that are
   * themselves annotations. Within an annotation, an annotation member can be written as
   * {@code @NestedAnnotation(...)}, while in an initializer it must be written as an object, for
   * example the construction of an {@code @AutoAnnotation} class. That's why we have this abstract
   * class and two concrete subclasses.
   */
  private static class SourceFormVisitor
      extends SimpleAnnotationValueVisitor8<@Nullable Void, StringBuilder> {

    private String formatType(TypeMirror typeMirror) {
      return asTypeElement(typeMirror).getQualifiedName().toString();
    }

    @Override
    protected @Nullable Void defaultAction(Object value, StringBuilder sb) {
      sb.append(value);
      return null;
    }

    @Override
    public @Nullable Void visitArray(List<? extends AnnotationValue> values, StringBuilder sb) {
      sb.append('{');
      String sep = "";
      for (AnnotationValue value : values) {
        sb.append(sep);
        visit(value, sb);
        sep = ", ";
      }
      sb.append('}');
      return null;
    }

    @Override
    public @Nullable Void visitChar(char c, StringBuilder sb) {
      appendQuoted(sb, c);
      return null;
    }

    @Override
    public @Nullable Void visitLong(long i, StringBuilder sb) {
      sb.append(i).append('L');
      return null;
    }

    @Override
    public @Nullable Void visitDouble(double d, StringBuilder sb) {
      if (Double.isNaN(d)) {
        sb.append("Double.NaN");
      } else if (d == Double.POSITIVE_INFINITY) {
        sb.append("Double.POSITIVE_INFINITY");
      } else if (d == Double.NEGATIVE_INFINITY) {
        sb.append("Double.NEGATIVE_INFINITY");
      } else {
        sb.append(d);
      }
      return null;
    }

    @Override
    public @Nullable Void visitFloat(float f, StringBuilder sb) {
      if (Float.isNaN(f)) {
        sb.append("Float.NaN");
      } else if (f == Float.POSITIVE_INFINITY) {
        sb.append("Float.POSITIVE_INFINITY");
      } else if (f == Float.NEGATIVE_INFINITY) {
        sb.append("Float.NEGATIVE_INFINITY");
      } else {
        sb.append(f).append('F');
      }
      return null;
    }

    @Override
    public @Nullable Void visitEnumConstant(VariableElement c, StringBuilder sb) {
      sb.append(formatType(c.asType())).append('.').append(c.getSimpleName());
      return null;
    }

    @Override
    public @Nullable Void visitString(String s, StringBuilder sb) {
      appendQuoted(sb, s);
      return null;
    }

    @Override
    public @Nullable Void visitType(TypeMirror classConstant, StringBuilder sb) {
      sb.append(formatType(classConstant)).append(".class");
      return null;
    }

    @Override
    public @Nullable Void visitAnnotation(AnnotationMirror a, StringBuilder sb) {
      sb.append('@').append(formatType(a.getAnnotationType()));
      ImmutableMap<ExecutableElement, AnnotationValue> map =
          ImmutableMap.copyOf(a.getElementValues());
      if (!map.isEmpty()) {
        sb.append('(');
        Optional<AnnotationValue> shortForm = shortForm(map);
        if (shortForm.isPresent()) {
          this.visit(maybeShorten(shortForm.get()), sb);
        } else {
          String sep = "";
          for (Map.Entry<ExecutableElement, AnnotationValue> entry : map.entrySet()) {
            sb.append(sep).append(entry.getKey().getSimpleName()).append(" = ");
            sep = ", ";
            this.visit(maybeShorten(entry.getValue()), sb);
          }
        }
        sb.append(')');
      }
      return null;
    }
  }

  private static AnnotationValue maybeShorten(AnnotationValue value) {
    return ARRAY_VISITOR.visit(value, value);
  }

  private static final AnnotationValueVisitor<AnnotationValue, AnnotationValue> ARRAY_VISITOR =
      new SimpleAnnotationValueVisitor8<AnnotationValue, AnnotationValue>() {
        @Override
        public AnnotationValue visitArray(
            List<? extends AnnotationValue> values, AnnotationValue input) {
          if (values.size() == 1) {
            // We can shorten @Foo(a = {23}) to @Foo(a = 23). For the specific case where `a` is
            // actually `value`, we'll already have shortened that in visitAnnotation, so
            // effectively we go from @Foo(value = {23}) to @Foo({23}) to @Foo(23).
            return Iterables.getOnlyElement(values);
          }
          return input;
        }

        @Override
        protected AnnotationValue defaultAction(Object o, AnnotationValue input) {
          return input;
        }
      };

  // We can shorten @Annot(value = 23) to @Annot(23).
  private static Optional<AnnotationValue> shortForm(
      Map<ExecutableElement, AnnotationValue> values) {
    if (values.size() == 1
        && Iterables.getOnlyElement(values.keySet()).getSimpleName().contentEquals("value")) {
      return Optional.of(Iterables.getOnlyElement(values.values()));
    }
    return Optional.empty();
  }

  /**
   * Returns a string representation of the given annotation value, suitable for inclusion in a Java
   * source file as the initializer of a variable of the appropriate type.
   */
  static String toString(AnnotationValue annotationValue) {
    StringBuilder sb = new StringBuilder();
    new SourceFormVisitor().visit(annotationValue, sb);
    return sb.toString();
  }

  /**
   * Returns a string representation of the given annotation mirror, suitable for inclusion in a
   * Java source file to reproduce the annotation in source form.
   */
  static String toString(AnnotationMirror annotationMirror) {
    StringBuilder sb = new StringBuilder();
    new SourceFormVisitor().visitAnnotation(annotationMirror, sb);
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
