/*
 * Copyright 2013 Google LLC
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
package com.google.auto.factory.processor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.List;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;

final class AnnotationValues {
  private AnnotationValues() {}

  static boolean asBoolean(AnnotationValue value) {
    return value.accept(
        new SimpleAnnotationValueVisitor6<Boolean, Void>() {
          @Override
          protected Boolean defaultAction(Object o, Void p) {
            throw new IllegalArgumentException();
          }

          @Override
          public Boolean visitBoolean(boolean b, Void p) {
            return b;
          }
        },
        null);
  }

  static TypeElement asType(AnnotationValue value) {
    return value.accept(
        new SimpleAnnotationValueVisitor6<TypeElement, Void>() {
          @Override
          protected TypeElement defaultAction(Object o, Void p) {
            throw new IllegalArgumentException();
          }

          @Override
          public TypeElement visitType(TypeMirror t, Void p) {
            return t.accept(
                new SimpleTypeVisitor6<TypeElement, Void>() {
                  @Override
                  protected TypeElement defaultAction(TypeMirror e, Void p) {
                    throw new AssertionError();
                  }

                  @Override
                  public TypeElement visitDeclared(DeclaredType t, Void p) {
                    return Iterables.getOnlyElement(
                        ElementFilter.typesIn(ImmutableList.of(t.asElement())));
                  }
                },
                null);
          }
        },
        null);
  }

  static ImmutableList<? extends AnnotationValue> asList(AnnotationValue value) {
    return value.accept(
        new SimpleAnnotationValueVisitor6<ImmutableList<? extends AnnotationValue>, Void>() {
          @Override
          protected ImmutableList<? extends AnnotationValue> defaultAction(Object o, Void p) {
            throw new IllegalArgumentException();
          }

          @Override
          public ImmutableList<? extends AnnotationValue> visitArray(
              List<? extends AnnotationValue> vals, Void p) {
            return ImmutableList.copyOf(vals);
          }
        },
        null);
  }
}
