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

import static com.google.auto.factory.processor.Elements2.isValidSupertypeForClass;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

import java.util.Map;

import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

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
  private final TypeElement extendingType;
  private final ImmutableSet<TypeElement> implementingTypes;

  private AutoFactoryDeclaration(Element target, Optional<String> className,
      TypeElement extendingType, ImmutableSet<TypeElement> implementingTypes) {
    this.target = target;
    this.className = className;
    this.extendingType = extendingType;
    this.implementingTypes = implementingTypes;
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

  TypeElement extendingType() {
    return extendingType;
  }

  ImmutableSet<TypeElement> implementingTypes() {
    return implementingTypes;
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
        return Optional.absent();
      }

      AnnotationValue extendingValue = checkNotNull(values.get("extending"));
      TypeElement extendingType = AnnotationValues.asType(extendingValue);
      if (extendingType == null) {
        messager.printMessage(ERROR, "Unable to find the type: "
            + extendingValue.getValue().toString(),
                element, mirror, extendingValue);
        return Optional.absent();
      } else if (!isValidSupertypeForClass(extendingType)) {
        messager.printMessage(ERROR,
            String.format("%s is not a valid supertype for a factory. "
                + "Supertypes must be non-final classes.",
                    extendingType.getQualifiedName()),
            element, mirror, extendingValue);
        return Optional.absent();
      }

      AnnotationValue implementingValue = checkNotNull(values.get("implementing"));
      ImmutableSet.Builder<TypeElement> builder = ImmutableSet.builder();
      for (AnnotationValue implementingTypeValue : AnnotationValues.asList(implementingValue)) {
        builder.add(AnnotationValues.asType(implementingTypeValue));
      }
      ImmutableSet<TypeElement> implementingTypes = builder.build();
      return Optional.of(new AutoFactoryDeclaration(element,
          className.isEmpty() ? Optional.<String>absent() : Optional.of(className),
          extendingType,
          implementingTypes));
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
