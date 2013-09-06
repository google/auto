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

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Performs superficial validation on {@link AutoFactory} applications.
 *
 * @author Gregory Kick
 */
final class AutoFactoryChecker {
  private final Messager messager;
  private final Elements elements;

  @Inject AutoFactoryChecker(Messager messager, Elements elements) {
    this.messager = messager;
    this.elements = elements;
  }

  void checkAutoFactoryElement(Element element) {
    AnnotationMirror autoFactoryMirror =
        Mirrors.getAnnotationMirror(element, AutoFactory.class).get();
    ImmutableMap<String, AnnotationValue> values = Mirrors.simplifyAnnotationValueMap(
        elements.getElementValuesWithDefaults(autoFactoryMirror));
    AnnotationValue classNameValue = values.get("className");
    String className = classNameValue.getValue().toString();
    if (className.isEmpty()) {
      messager.printMessage(NOTE, "Found an empty className value.  Using the default.", element,
          autoFactoryMirror, classNameValue);
    } else {
      if (!isValidIdentifier(className)) {
        messager.printMessage(ERROR,
            String.format("\"%s\" is not a valid Java identifier", className),
            element, autoFactoryMirror, classNameValue);
      }
    }
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
    if(!Character.isJavaIdentifierStart(identifier.charAt(0))) {
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
