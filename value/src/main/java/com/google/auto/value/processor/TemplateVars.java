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
package com.google.auto.value.processor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A template and a set of variables to be substituted into that template. A concrete subclass of
 * this class defines a set of fields that are template variables, and an implementation of the
 * {@link #template()} method which is the template to substitute them into. Once the values of the
 * fields have been assigned, the {@link #toText()} method returns the result of substituting them
 * into the template.
 *
 * <p>The subclass must be a direct subclass of this class. Fields cannot be static unless they are
 * also final. They cannot be private, though they can be package-private if the class is in the
 * same package as this class. They cannot be primitive or null, so that there is a clear indication
 * when a field has not been set.
 *
 * @author Éamonn McManus
 */
abstract class TemplateVars {
  abstract Template template();

  private final List<Field> fields;

  TemplateVars() {
    if (getClass().getSuperclass() != TemplateVars.class) {
      throw new IllegalArgumentException("Class must extend TemplateVars directly");
    }
    List<Field> fields = new ArrayList<Field>();
    Field[] declaredFields = getClass().getDeclaredFields();
    for (Field field : declaredFields) {
      if (field.isSynthetic() || isStaticFinal(field)) {
        continue;
      }
      if (Modifier.isPrivate(field.getModifiers())) {
        throw new IllegalArgumentException("Field cannot be private: " + field);
      }
      if (Modifier.isStatic(field.getModifiers())) {
        throw new IllegalArgumentException("Field cannot be static unless also final: " + field);
      }
      if (field.getType().isPrimitive()) {
        throw new IllegalArgumentException("Field cannot be primitive: " + field);
      }
      fields.add(field);
    }
    this.fields = Collections.unmodifiableList(fields);
  }

  /**
   * Returns a string that is the concatenation of the given strings, with a newline added at
   * the end of each one. This saves having to type {@code "...\n" +} all the time.
   */
  static String concatLines(String... lines) {
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
      sb.append(line).append("\n");
    }
    return sb.toString();
  }

  /**
   * Returns the result of substituting the variables defined by the fields of this class
   * (a concrete subclass of TemplateVars) into the template returned by {@link #template()}.
   */
  String toText() {
    Map<String, Object> vars = toMap();
    return template().rewrite(vars);
  }

  private Map<String, Object> toMap() {
    Map<String, Object> map = new TreeMap<String, Object>();
    for (Field field : fields) {
      Object value = fieldValue(field, this);
      if (value == null) {
        throw new IllegalArgumentException("Field cannot be null (was it set?): " + field);
      }
      Object old = map.put(field.getName(), value);
      if (old != null) {
        throw new IllegalArgumentException("Two fields called " + field.getName() + "?!");
      }
    }
    return Collections.unmodifiableMap(map);
  }

  private static Object fieldValue(Field field, Object container) {
    try {
      return field.get(container);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean isStaticFinal(Field field) {
    int modifiers = field.getModifiers();
    return Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers);
  }
}
