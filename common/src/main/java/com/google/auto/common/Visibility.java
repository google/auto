/*
 * Copyright 2014 Google LLC
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Comparators.min;
import static javax.lang.model.element.ElementKind.PACKAGE;

import com.google.common.base.Enums;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents the visibility of a given {@link Element}: {@code public}, {@code protected},
 * {@code private} or default/package-private.
 *
 * <p>The constants for this enum are ordered according by increasing visibility.
 *
 * @author Gregory Kick
 */
public enum Visibility {
  PRIVATE,
  DEFAULT,
  PROTECTED,
  PUBLIC;

  // TODO(ronshapiro): remove this and reference ElementKind.MODULE directly once we start building
  // with -source 9
  private static final @Nullable ElementKind MODULE =
      Enums.getIfPresent(ElementKind.class, "MODULE").orNull();

  /**
   * Returns the visibility of the given {@link Element}. While package and module elements don't
   * technically have a visibility associated with them, this method returns {@link #PUBLIC} for
   * them.
   */
  public static Visibility ofElement(Element element) {
    checkNotNull(element);
    // packages and module don't have modifiers, but they're obviously "public"
    if (element.getKind().equals(PACKAGE) || element.getKind().equals(MODULE)) {
      return PUBLIC;
    }
    Set<Modifier> modifiers = element.getModifiers();
    if (modifiers.contains(Modifier.PRIVATE)) {
      return PRIVATE;
    } else if (modifiers.contains(Modifier.PROTECTED)) {
      return PROTECTED;
    } else if (modifiers.contains(Modifier.PUBLIC)) {
      return PUBLIC;
    } else {
      return DEFAULT;
    }
  }

  /**
   * Returns effective visibility of the given element meaning that it takes into account the
   * visibility of its enclosing elements.
   */
  public static Visibility effectiveVisibilityOfElement(Element element) {
    checkNotNull(element);
    Visibility effectiveVisibility = PUBLIC;
    Element currentElement = element;
    while (currentElement != null) {
      effectiveVisibility = min(effectiveVisibility, ofElement(currentElement));
      currentElement = currentElement.getEnclosingElement();
    }
    return effectiveVisibility;
  }
}
