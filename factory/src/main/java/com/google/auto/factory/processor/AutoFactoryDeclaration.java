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
package com.google.auto.factory.processor;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

import java.util.List;
import java.util.Map;

import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;

import com.google.auto.factory.AutoFactory;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

/**
 * This is a value object that mirrors the static declaration of an {@link AutoFactory} annotation.
 *
 * @author Gregory Kick
 */
final class AutoFactoryDeclaration {
  private final Element target;
  private final Optional<String> className;
  private final String extendingQualifiedName;
  private final ImmutableSet<String> implementingQualifiedNames;

  private AutoFactoryDeclaration(Element target, Optional<String> className,
      String extendingQualifiedName, ImmutableSet<String> implementingQualifiedNames) {
    this.target = target;
    this.className = className;
    this.extendingQualifiedName = extendingQualifiedName;
    this.implementingQualifiedNames = implementingQualifiedNames;
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

  Element target() {
    return target;
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

  static final class Factory {
    private final Elements elements;
    private final Messager messager;

    @Inject Factory(Elements elements, Messager messager) {
      this.elements = elements;
      this.messager = messager;
    }

    Optional<AutoFactoryDeclaration> createIfValid(Element element) {
      checkNotNull(element);
      AnnotationMirror mirror = Mirrors.getAnnotationMirror(element, AutoFactory.class).get();
      checkArgument(Mirrors.getQualifiedName(mirror.getAnnotationType()).
          contentEquals(AutoFactory.class.getName()));
      Map<String, AnnotationValue> values =
          Mirrors.simplifyAnnotationValueMap(elements.getElementValuesWithDefaults(mirror));
      checkState(values.size() == 3);

      // className value is a string, so we can just call toString
      AnnotationValue classNameValue = values.get("className");
      String className = classNameValue.getValue().toString();
      if (className.isEmpty()) {
        messager.printMessage(NOTE, "Found an empty className value.  Using the default.", element,
            mirror, classNameValue);
      } else if (!isValidIdentifier(className)) {
        messager.printMessage(ERROR,
            String.format("\"%s\" is not a valid Java identifier", className),
            element, mirror, classNameValue);
      }
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
      return Optional.of(new AutoFactoryDeclaration(element,
          className.isEmpty() ? Optional.<String>absent() : Optional.of(className),
          extendingQualifiedName,
          implementingQualifiedNames));
    }

    /**
     * From the
     * <a href="http://docs.oracle.com/javase/tutorial/java/nutsandbolts/_keywords.html">j2se</a>
     * documentation.
     */
    private static final ImmutableSet<String> KEYWORDS = ImmutableSet.of(
        "abstract",
        "assert",
        "boolean",
        "break",
        "byte",
        "case",
        "catch",
        "char",
        "class",
        "const",
        "continue",
        "default",
        "do",
        "double",
        "else",
        "enum",
        "extends",
        "final",
        "finally",
        "float",
        "for",
        "goto",
        "if",
        "implements",
        "import",
        "instanceof",
        "int",
        "interface",
        "long",
        "native",
        "new",
        "package",
        "private",
        "protected",
        "public",
        "return",
        "short",
        "static",
        "strictfp",
        "super",
        "switch",
        "synchronized",
        "this",
        "throw",
        "throws",
        "transient",
        "try",
        "void",
        "volatile",
        "while");

    static boolean isValidIdentifier(String identifier) {
      if (Strings.isNullOrEmpty(identifier)) {
        return false;
      }
      if (KEYWORDS.contains(identifier)) {
        return false;
      }
      if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
        return false;
      }
      for (int i = 1; i < identifier.length(); i++) {
        if (!Character.isJavaIdentifierPart(identifier.charAt(i))) {
          return false;
        }
      }
      return true;
    }
  }
}
