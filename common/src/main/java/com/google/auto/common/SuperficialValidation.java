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
package com.google.auto.common;

import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.AbstractElementVisitor6;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;

/**
 * A utility class that traverses {@link Element} instances and ensures that all type information
 * is present and resolvable.
 *
 * @author Gregory Kick
 */
public final class SuperficialValidation {
  public static boolean validateElements(Iterable<? extends Element> elements) {
    for (Element element : elements) {
      if (!validateElement(element)) {
        return false;
      }
    }
    return true;
  }

  private static final ElementVisitor<Boolean, Void> ELEMENT_VALIDATING_VISITOR =
      new AbstractElementVisitor6<Boolean, Void>() {
        @Override public Boolean visitPackage(PackageElement e, Void p) {
          return validateElements(e.getEnclosedElements());
        }

        @Override public Boolean visitType(TypeElement e, Void p) {
          return isValidBaseElement(e)
              && validateElements(e.getTypeParameters())
              && validateTypes(e.getInterfaces())
              && validateType(e.getSuperclass());
        }

        @Override public Boolean visitVariable(VariableElement e, Void p) {
          return isValidBaseElement(e);
        }

        @Override public Boolean visitExecutable(ExecutableElement e, Void p) {
          AnnotationValue defaultValue = e.getDefaultValue();
          return isValidBaseElement(e)
              && (defaultValue == null || validateAnnotationValue(defaultValue))
              && validateType(e.getReturnType())
              && validateTypes(e.getThrownTypes())
              && validateElements(e.getTypeParameters())
              && validateElements(e.getParameters());
        }

        @Override public Boolean visitTypeParameter(TypeParameterElement e, Void p) {
          return isValidBaseElement(e)
              && validateTypes(e.getBounds());
        }
      };

  public static boolean validateElement(Element element) {
     return element.accept(ELEMENT_VALIDATING_VISITOR, null);
  }

  private static boolean isValidBaseElement(Element e) {
    return validateType(e.asType())
        && validateAnnotations(e.getAnnotationMirrors())
        && validateElements(e.getEnclosedElements());
  }

  private static boolean validateTypes(Iterable<? extends TypeMirror> types) {
    for (TypeMirror type : types) {
      if (!validateType(type)) {
        return false;
      }
    }
    return true;
  }

  private static final TypeVisitor<Boolean, Void> TYPE_VALIDATING_VISITOR =
      new SimpleTypeVisitor6<Boolean, Void>() {
        @Override
        protected Boolean defaultAction(TypeMirror e, Void p) {
          return true;
        }

        @Override
        public Boolean visitArray(ArrayType t, Void p) {
          return validateType(t.getComponentType());
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, Void p) {
          return validateTypes(t.getTypeArguments());
        }

        @Override
        public Boolean visitError(ErrorType t, Void p) {
          return false;
        }

        @Override
        public Boolean visitTypeVariable(TypeVariable t, Void p) {
          return validateType(t.getLowerBound()) && validateType(t.getUpperBound());
        }

        @Override
        public Boolean visitWildcard(WildcardType t, Void p) {
          TypeMirror extendsBound = t.getExtendsBound();
          TypeMirror superBound = t.getSuperBound();
          return (extendsBound == null || validateType(extendsBound))
              && (superBound == null || validateType(superBound));
        }

        @Override
        public Boolean visitExecutable(ExecutableType t, Void p) {
          return validateTypes(t.getParameterTypes())
              && validateType(t.getReturnType())
              && validateTypes(t.getThrownTypes())
              && validateTypes(t.getTypeVariables());
        }
      };

  private static boolean validateType(TypeMirror type) {
    return type.accept(TYPE_VALIDATING_VISITOR, null);
  }

  private static boolean validateAnnotations(
      Iterable<? extends AnnotationMirror> annotationMirrors) {
    for (AnnotationMirror annotationMirror : annotationMirrors) {
      if (!validateAnnotation(annotationMirror)) {
        return false;
      }
    }
    return true;
  }

  private static boolean validateAnnotation(AnnotationMirror annotationMirror) {
    return validateType(annotationMirror.getAnnotationType())
        && validateAnnotationValues(annotationMirror.getElementValues().values());
  }

  private static boolean validateAnnotationValues(
      Iterable<? extends AnnotationValue> annotationValues) {
    for (AnnotationValue annotationMirror : annotationValues) {
      if (!validateAnnotationValue(annotationMirror)) {
        return false;
      }
    }
    return true;
  }

  private static final AnnotationValueVisitor<Boolean, Void> ANNOTATION_VALUE_VALIDATING_VISITOR =
      new SimpleAnnotationValueVisitor6<Boolean, Void>() {
        @Override protected Boolean defaultAction(Object o, Void p) {
          return true;
        }

        @Override public Boolean visitAnnotation(AnnotationMirror a, Void p) {
          return validateAnnotation(a);
        }

        @Override public Boolean visitArray(List<? extends AnnotationValue> values, Void p) {
          for (AnnotationValue value : values) {
            if (!value.accept(this, null)) {
              return false;
            }
          }
          return true;
        }

        @Override public Boolean visitEnumConstant(VariableElement c, Void p) {
          return validateElement(c);
        }

        @Override public Boolean visitType(TypeMirror t, Void p) {
          return validateType(t);
        }
      };

  private static boolean validateAnnotationValue(AnnotationValue annotationValue) {
    return annotationValue.accept(ANNOTATION_VALUE_VALIDATING_VISITOR, null);
  }
}
