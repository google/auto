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
package com.google.autofactory;

import java.util.EnumSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

import com.google.common.base.Predicate;

final class ElementPredicates {
  private ElementPredicates() { }

  static Predicate<Element> byModifiers(final Modifier first, final Modifier... rest) {
    return new Predicate<Element>() {
      @Override public boolean apply(Element element) {
        return element.getModifiers().containsAll(EnumSet.of(first, rest));
      }
    };
  }

  static Predicate<Element> byKind(final ElementKind first, final ElementKind... rest) {
    return new Predicate<Element>() {
      @Override public boolean apply(Element element) {
        return EnumSet.of(first, rest).contains(element.getKind());
      }
    };
  }
}