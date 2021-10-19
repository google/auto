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
package com.google.auto.value.processor;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.processor.MissingTypes.MissingTypeException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.tools.Diagnostic;

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
  private abstract static class SourceFormVisitor
      extends SimpleAnnotationValueVisitor8<Void, StringBuilder> {
    @Override
    protected Void defaultAction(Object value, StringBuilder sb) {
      sb.append(value);
      return null;
    }

    @Override
    public Void visitArray(List<? extends AnnotationValue> values, StringBuilder sb) {
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
    public Void visitChar(char c, StringBuilder sb) {
      appendQuoted(sb, c);
      return null;
    }

    @Override
    public Void visitLong(long i, StringBuilder sb) {
      sb.append(i).append('L');
      return null;
    }

    @Override
    public Void visitDouble(double d, StringBuilder sb) {
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
    public Void visitFloat(float f, StringBuilder sb) {
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
    public Void visitEnumConstant(VariableElement c, StringBuilder sb) {
      sb.append(TypeEncoder.encode(c.asType())).append('.').append(c.getSimpleName());
      return null;
    }

    @Override
    public Void visitString(String s, StringBuilder sb) {
      appendQuoted(sb, s);
      return null;
    }

    @Override
    public Void visitType(TypeMirror classConstant, StringBuilder sb) {
      sb.append(TypeEncoder.encode(classConstant)).append(".class");
      return null;
    }
  }

  private static class InitializerSourceFormVisitor extends SourceFormVisitor {
    private final ProcessingEnvironment processingEnv;
    private final String memberName;
    private final Element errorContext;

    InitializerSourceFormVisitor(
        ProcessingEnvironment processingEnv, String memberName, Element errorContext) {
      this.processingEnv = processingEnv;
      this.memberName = memberName;
      this.errorContext = errorContext;
    }

    @Override
    public Void visitAnnotation(AnnotationMirror a, StringBuilder sb) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "@AutoAnnotation cannot yet supply a default value for annotation-valued member '"
                  + memberName
                  + "'",
              errorContext);
      sb.append("null");
      return null;
    }
  }

  private static class AnnotationSourceFormVisitor extends SourceFormVisitor {
    @Override
    public Void visitArray(List<? extends AnnotationValue> values, StringBuilder sb) {
      if (values.size() == 1) {
        // We can shorten @Foo(a = {23}) to @Foo(a = 23). For the specific case where `a` is
        // actually `value`, we'll already have shortened that in visitAnnotation, so effectively we
        // go from @Foo(value = {23}) to @Foo({23}) to @Foo(23).
        visit(values.get(0), sb);
        return null;
      }
      return super.visitArray(values, sb);
    }

    @Override
    public Void visitAnnotation(AnnotationMirror a, StringBuilder sb) {
      sb.append('@').append(TypeEncoder.encode(a.getAnnotationType()));
      ImmutableMap<ExecutableElement, AnnotationValue> map =
          ImmutableMap.copyOf(a.getElementValues());
      if (!map.isEmpty()) {
        sb.append('(');
        Optional<AnnotationValue> shortForm = shortForm(map);
        if (shortForm.isPresent()) {
          this.visit(shortForm.get(), sb);
        } else {
          String sep = "";
          for (Map.Entry<ExecutableElement, AnnotationValue> entry : map.entrySet()) {
            sb.append(sep).append(entry.getKey().getSimpleName()).append(" = ");
            sep = ", ";
            this.visit(entry.getValue(), sb);
          }
        }
        sb.append(')');
      }
      return null;
    }

    // We can shorten @Annot(value = 23) to @Annot(23).
    private static Optional<AnnotationValue> shortForm(
        Map<ExecutableElement, AnnotationValue> values) {
      if (values.size() == 1
          && Iterables.getOnlyElement(values.keySet()).getSimpleName().contentEquals("value")) {
        return Optional.of(Iterables.getOnlyElement(values.values()));
      }
      return Optional.empty();
    }
  }

  /**
   * Returns a string representation of the given annotation value, suitable for inclusion in a Java
   * source file as the initializer of a variable of the appropriate type.
   */
  static String sourceFormForInitializer(
      AnnotationValue annotationValue,
      ProcessingEnvironment processingEnv,
      String memberName,
      Element errorContext) {
    SourceFormVisitor visitor =
        new InitializerSourceFormVisitor(processingEnv, memberName, errorContext);
    StringBuilder sb = new StringBuilder();
    visitor.visit(annotationValue, sb);
    return sb.toString();
  }

  /**
   * Returns a string representation of the given annotation mirror, suitable for inclusion in a
   * Java source file to reproduce the annotation in source form.
   */
  static String sourceFormForAnnotation(AnnotationMirror annotationMirror) {
    // If a value in the annotation is a reference to a class constant and that class constant is
    // undefined, javac unhelpfully converts it into a string "<error>" and visits that instead. We
    // want to catch this case and defer processing to allow the class to be defined by another
    // annotation processor. So we look for annotation elements whose type is Class but whose
    // reported value is a string. Unfortunately we can't extract the ErrorType corresponding to the
    // missing class portably. With javac, the AttributeValue is a
    // com.sun.tools.javac.code.Attribute.UnresolvedClass, which has a public field classType that
    // is the ErrorType we need, but obviously that's nonportable and fragile.
    validateClassValues(annotationMirror);
    StringBuilder sb = new StringBuilder();
    new AnnotationSourceFormVisitor().visitAnnotation(annotationMirror, sb);
    return sb.toString();
  }

  /**
   * Throws an exception if this annotation contains a value for a Class element that is not
   * actually a type. The assumption is that the value is the string {@code "<error>"} which javac
   * presents when a Class value is an undefined type.
   */
  private static void validateClassValues(AnnotationMirror annotationMirror) {
    // A class literal can appear in three places:
    // * for an element of type Class, for example @SomeAnnotation(Foo.class);
    // * for an element of type Class[], for example @SomeAnnotation({Foo.class, Bar.class});
    // * inside a nested annotation, for example @SomeAnnotation(@Nested(Foo.class)).
    // These three possibilities are the three branches of the if/else chain below.
    annotationMirror
        .getElementValues()
        .forEach(
            (method, value) -> {
              TypeMirror type = method.getReturnType();
              if (isJavaLangClass(type) && !(value.getValue() instanceof TypeMirror)) {
                throw new MissingTypeException(null);
              } else if (type.getKind().equals(TypeKind.ARRAY)
                  && isJavaLangClass(MoreTypes.asArray(type).getComponentType())
                  && value.getValue() instanceof List<?>) {
                @SuppressWarnings("unchecked") // a List can only be a List<AnnotationValue> here
                List<AnnotationValue> values = (List<AnnotationValue>) value.getValue();
                if (values.stream().anyMatch(av -> !(av.getValue() instanceof TypeMirror))) {
                  throw new MissingTypeException(null);
                }
              } else if (type.getKind().equals(TypeKind.DECLARED)
                  && MoreTypes.asElement(type).getKind().equals(ElementKind.ANNOTATION_TYPE)
                  && value.getValue() instanceof AnnotationMirror) {
                validateClassValues((AnnotationMirror) value.getValue());
              }
            });
  }

  private static boolean isJavaLangClass(TypeMirror type) {
    return type.getKind().equals(TypeKind.DECLARED)
        && MoreTypes.asTypeElement(type).getQualifiedName().contentEquals("java.lang.Class");
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
