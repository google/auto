package com.google.autofactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.element.ElementKind.CLASS;

import java.lang.annotation.Annotation;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor6;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

final class Elements2 {
  private Elements2() {}

  static ImmutableSet<ExecutableElement> getConstructors(TypeElement type) {
    checkNotNull(type);
    checkArgument(type.getKind() == CLASS);
    ImmutableSet.Builder<ExecutableElement> constructors = ImmutableSet.builder();
    for (Element element : type.getEnclosedElements()) {
      constructors.addAll(element.accept(
          new ElementKindVisitor6<Optional<ExecutableElement>, Void> () {
            @Override
            protected Optional<ExecutableElement> defaultAction(Element e, Void p) {
              return Optional.absent();
            }

            @Override
            public Optional<ExecutableElement> visitExecutableAsConstructor(ExecutableElement e,
                Void p) {
              return Optional.of(e);
            }
          }, null).asSet());
    }
    return constructors.build();
  }

  static ImmutableSet<VariableElement> getAnnotatedParamters(ExecutableElement executableElement,
      Class<? extends Annotation> annotationType) {
    ImmutableSet.Builder<VariableElement> builder = ImmutableSet.builder();
    for (VariableElement variableElement : executableElement.getParameters()) {
      if (Mirrors.getAnnotationMirror(variableElement, annotationType).isPresent()) {
        builder.add(variableElement);
      }
    }
    return builder.build();
  }
}
