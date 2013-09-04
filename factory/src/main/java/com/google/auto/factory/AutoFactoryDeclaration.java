/*
 * Copyright (C) 2013 Google, Inc.
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
package com.google.auto.factory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Name;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

/**
 * This is a value object that mirrors the static declaration of an {@link AutoFactory} annotation.
 *
 * @author Gregory Kick
 */
final class AutoFactoryDeclaration {
  private final AnnotationMirror mirror;
  private final Optional<String> className;
  private final String extendingQualifiedName;
  private final ImmutableSet<String> implementingQualifiedNames;

  AutoFactoryDeclaration(AnnotationMirror mirror, String className,
      String extendingQualifiedName, ImmutableSet<String> implementingQualifiedNames) {
    this.mirror = mirror;
    this.className = className.isEmpty() ? Optional.<String>absent() : Optional.of(className);
    this.extendingQualifiedName = extendingQualifiedName;
    this.implementingQualifiedNames = implementingQualifiedNames;
  }

  AnnotationMirror mirror() {
    return mirror;
  }

  String getFactoryName(Name packageName, Name targetType) {
    StringBuilder builder = new StringBuilder(packageName);
    if (packageName.length() > 0) {
      builder.append('.');
    }
    if (className.isPresent()) {
      builder.append(className.get());
    } else {
      builder.append(targetType).append("Factory");
    }
    return builder.toString();
  }

  Optional<String> getClassName() {
    return className;
  }

  String extendingQualifiedName() {
    return extendingQualifiedName;
  }

  ImmutableSet<String> implementingQualifiedNames() {
    return implementingQualifiedNames;
  }

  static AutoFactoryDeclaration fromAnnotationMirror(Elements elements, AnnotationMirror mirror) {
    checkNotNull(mirror);
    checkArgument(Mirrors.getQualifiedName(mirror.getAnnotationType()).
        contentEquals(AutoFactory.class.getName()));
    Map<String, AnnotationValue> values =
        Mirrors.simplifyAnnotationValueMap(elements.getElementValuesWithDefaults(mirror));
    checkState(values.size() == 3);

    // value is a string, so we can just call toString
    String className = values.get("className").getValue().toString();
    AnnotationValue extendingValue = checkNotNull(values.get("extending"));
    String extendingQualifiedName = extendingValue.accept(new QualifiedNameValueVisitor(), null);
    AnnotationValue implementingValue = checkNotNull(values.get("implementing"));
    ImmutableSet<String> implementingQualifiedNames =
        implementingValue.accept(new SimpleAnnotationValueVisitor6<ImmutableSet<String>, Void>() {
          @Override
          protected ImmutableSet<String> defaultAction(Object o, Void p) {
            throw new AssertionError();
          }

          @Override
          public ImmutableSet<String> visitArray(List<? extends AnnotationValue> vals, Void p) {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            for (AnnotationValue annotationValue : vals) {
              builder.add(annotationValue.accept(new QualifiedNameValueVisitor(), null));
            }
            return builder.build();
          }
        }, null);
    return new AutoFactoryDeclaration(mirror, className, extendingQualifiedName,
        implementingQualifiedNames);
  }

  private static final class QualifiedNameValueVisitor
      extends SimpleAnnotationValueVisitor6<String, Void> {
    @Override
    protected String defaultAction(Object o, Void p) {
      throw new IllegalStateException();
    }

    @Override
    public String visitType(TypeMirror t, Void p) {
      return t.accept(new SimpleTypeVisitor6<String, Void>() {
        @Override
        protected String defaultAction(TypeMirror e, Void p) {
          throw new AssertionError();
        }

        @Override
        public String visitDeclared(DeclaredType t, Void p) {
          return Mirrors.getQualifiedName(t).toString();
        }
      }, null);
    }
  }
}
