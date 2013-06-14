package com.google.autofactory;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Map.Entry;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.SimpleElementVisitor6;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;


final class Mirrors {
  private Mirrors() { }

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
   * Returns an annotation value map  with {@link String} keys instead of {@link ExecutableElement}
   * instances.
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
}
