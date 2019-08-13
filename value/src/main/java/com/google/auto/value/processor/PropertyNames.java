/*
 * Copyright 2018 Google LLC
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
package com.google.auto.value.processor;

import com.google.common.base.Strings;

/** Helper methods to create property names. */
class PropertyNames {
  /**
   * Returns the {@code propertyName} with its first character in lower case, but leaves it intact
   * if it starts with two capitals.
   *
   * <p>For consistency with JavaBeans, a getter called {@code getHTMLPage()} defines a property
   * called {@code HTMLPage}. The <a
   * href="https://docs.oracle.com/javase/8/docs/api/java/beans/Introspector.html#decapitalize-java.lang.String-">
   * rule</a> is: the name of the property is the part after {@code get} or {@code
   * is}, with the first letter lowercased <i>unless</i> the first two letters are uppercase. This
   * works well for the {@code HTMLPage} example, but in these more enlightened times we use {@code
   * HtmlPage} anyway, so the special behaviour is not useful, and of course it behaves poorly with
   * examples like {@code OAuth}. Nevertheless, we preserve it for compatibility.
   */
  static String decapitalizeLikeJavaBeans(String propertyName) {
    if (propertyName != null
        && propertyName.length() >= 2
        && Character.isUpperCase(propertyName.charAt(0))
        && Character.isUpperCase(propertyName.charAt(1))) {
      return propertyName;
    }
    return decapitalizeNormally(propertyName);
  }

  /**
   * Returns the {@code propertyName} with its first character in lower case.
   */
  static String decapitalizeNormally(String propertyName) {
    if (Strings.isNullOrEmpty(propertyName)) {
      return propertyName;
    }
    return Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
  }
}
