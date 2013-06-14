package com.google.autofactory;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.SimpleElementVisitor6;

import com.google.common.base.Joiner;
import com.google.common.base.Joiner.MapJoiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;


final class Mirrors {
  private Mirrors() {}

  static Name getQualifiedName(DeclaredType type) {
    return type.asElement().accept(new SimpleElementVisitor6<Name, Void>() {
      @Override
      protected Name defaultAction(Element e, Void p) {
        throw new AssertionError("DeclaredTypes should be TypeElements");
      }

      @Override
      public Name visitType(TypeElement e, Void p) {
        return e.getQualifiedName();
      }
    }, null);
  }

  /**
   * Returns an annotation value map (as returned by {@link Elements#getElementValuesWithDefaults})
   * with {@link String} keys instead of {@link ExecutableElement} instances.
   */
  static ImmutableMap<String, AnnotationValue> simplifyAnnotationValueMap(
      Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValueMap) {
    ImmutableMap.Builder<String, AnnotationValue> builder = ImmutableMap.builder();
    for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
        : annotationValueMap.entrySet()) {
      builder.put(entry.getKey().getSimpleName().toString(), entry.getValue());
    }
    return builder.build();
  }

  /**
   * Get the {@link AnnotationMirror} for the type {@code annotationType} present on the given
   * {@link Element} if it exists.
   */
  static Optional<AnnotationMirror> getAnnotationMirror(Element element,
      Class<? extends Annotation> annotationType) {
    String annotationName = annotationType.getName();
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      if (getQualifiedName(annotationMirror.getAnnotationType()).contentEquals(annotationName)) {
        return Optional.of(annotationMirror);
      }
    }
    return Optional.absent();
  }

  private static final MapJoiner annotationValueJoiner = Joiner.on(", ")
      .withKeyValueSeparator(" = ");

//  static String toDeclarationString(AnnotationMirror mirror) {
//    StringBuilder builder = new StringBuilder()
//        .append('@')
//        .append(mirror.getAnnotationType());
//    Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues =
//        mirror.getElementValues();
//    switch (elementValues.size()) {
//      case 0:
//        break;
//      case 1:
//        Entry<? extends ExecutableElement, ? extends AnnotationValue> entry =
//            Iterables.getOnlyElement(elementValues.entrySet());
//        if ("value".equals(entry.getKey().getSimpleName())) {
//          builder.append('(').append(entry.getValue()).append(')');
//          break;
//        }
//      default:
//
//        break;
//    }
//  }

  static void appendDeclarationString(StringBuilder builder, AnnotationMirror mirror) {
    builder.append('@').append(mirror.getAnnotationType());
    Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues =
        mirror.getElementValues();
    switch (elementValues.size()) {
      case 0:
        break;
      case 1:
        Entry<? extends ExecutableElement, ? extends AnnotationValue> entry =
        Iterables.getOnlyElement(elementValues.entrySet());
        if ("value".equals(entry.getKey().getSimpleName())) {
          builder.append('(');
          // invoke visitor
          builder.append(')');
          break;
        }
      default:
        appendValueMap(builder, elementValues);
        break;
    }
  }

  private static void appendValueMap(StringBuilder builder,
      Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {

  }

  private static final class StringAppendingVisitor
      extends SimpleAnnotationValueVisitor6<Void, StringBuilder> {
    @Override
    protected Void defaultAction(Object o, StringBuilder p) {
      p.append(o);
      return null;
    }

    @Override
    public Void visitAnnotation(AnnotationMirror a, StringBuilder p) {
      appendDeclarationString(p, a);
      return null;
    }

    @Override
    public Void visitArray(List<? extends AnnotationValue> vals, StringBuilder p) {
      p.append('{');
      for (AnnotationValue val : vals) {
        val.accept(this, p);
      }
      p.append('}');
      return super.visitArray(vals, p);
    }

    @Override
    public Void visitString(String s, StringBuilder p) {
      p.append('\"').append(s).append('\"');
      return null;
    }

    @Override
    public Void visitEnumConstant(VariableElement c, StringBuilder p) {
      return super.visitEnumConstant(c, p);
    }
  }
}
