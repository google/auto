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

import com.google.auto.value.processor.escapevelocity.Template;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * A template and a set of variables to be substituted into that template. A concrete subclass of
 * this class defines a set of fields that are template variables, and an implementation of the
 * {@link #parsedTemplate()} method which is the template to substitute them into. Once the values
 * of the fields have been assigned, the {@link #toText()} method returns the result of substituting
 * them into the template.
 *
 * <p>The subclass must be a direct subclass of this class. Fields cannot be static unless they are
 * also final. They cannot be private, though they can be package-private if the class is in the
 * same package as this class. They cannot be primitive or null, so that there is a clear indication
 * when a field has not been set.
 *
 * @author Éamonn McManus
 */
abstract class TemplateVars {
  abstract Template parsedTemplate();

  private final ImmutableList<Field> fields;

  TemplateVars() {
    if (getClass().getSuperclass() != TemplateVars.class) {
      throw new IllegalArgumentException("Class must extend TemplateVars directly");
    }
    ImmutableList.Builder<Field> fields = ImmutableList.builder();
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
    this.fields = fields.build();
  }

  /**
   * Returns the result of substituting the variables defined by the fields of this class
   * (a concrete subclass of TemplateVars) into the template returned by {@link #parsedTemplate()}.
   */
  String toText() {
    Map<String, Object> vars = toVars();
    return parsedTemplate().evaluate(vars);
  }

  private Map<String, Object> toVars() {
    Map<String, Object> vars = Maps.newTreeMap();
    for (Field field : fields) {
      Object value = fieldValue(field, this);
      if (value == null) {
        throw new IllegalArgumentException("Field cannot be null (was it set?): " + field);
      }
      Object old = vars.put(field.getName(), value);
      if (old != null) {
        throw new IllegalArgumentException("Two fields called " + field.getName() + "?!");
      }
    }
    return ImmutableMap.copyOf(vars);
  }

  static Template parsedTemplateForResource(String resourceName) {
    InputStream in = TemplateVars.class.getResourceAsStream(resourceName);
    if (in == null) {
      throw new IllegalArgumentException("Could not find resource: " + resourceName);
    }
    try {
      return templateFromInputStream(in);
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    } catch (IOException | NullPointerException e) {
      // https://github.com/google/auto/pull/439 says that we can also get NullPointerException.
      return retryParseAfterException(resourceName, e);
    } finally {
      try {
        in.close();
      } catch (IOException ignored) {
        // We probably already got an IOException which we're propagating.
      }
    }
  }

  private static Template retryParseAfterException(String resourceName, Exception exception) {
    try {
      return parsedTemplateFromUrl(resourceName);
    } catch (Throwable t) {
      // Chain the original exception so we can see both problems.
      Throwable cause;
      for (cause = exception; cause.getCause() != null; cause = cause.getCause()) {
      }
      cause.initCause(t);
      throw new AssertionError(exception);
    }
  }

  private static Template templateFromInputStream(InputStream in)
      throws UnsupportedEncodingException, IOException {
    Reader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
    return Template.parseFrom(reader);
  }

  // This is an ugly workaround for https://bugs.openjdk.java.net/browse/JDK-6947916, as
  // reported in https://github.com/google/auto/issues/365.
  // The issue is that sometimes the InputStream returned by JarURLCollection.getInputStream()
  // can be closed prematurely, which leads to an IOException saying "Stream closed".
  // We catch all IOExceptions, and fall back on logic that opens the jar file directly and
  // loads the resource from it. Since that doesn't use JarURLConnection, it shouldn't be
  // susceptible to the same bug. We only use this as fallback logic rather than doing it always,
  // because jars are memory-mapped by URLClassLoader, so loading a resource in the usual way
  // through the getResourceAsStream should be a lot more efficient than reopening the jar.
  private static Template parsedTemplateFromUrl(String resourceName)
      throws URISyntaxException, IOException {
    URL resourceUrl = TemplateVars.class.getResource(resourceName);
    if (resourceUrl.getProtocol().equalsIgnoreCase("file")) {
      return parsedTemplateFromFile(resourceUrl);
    } else if (resourceUrl.getProtocol().equalsIgnoreCase("jar")) {
      return parsedTemplateFromJar(resourceUrl);
    } else {
      throw new AssertionError("Template fallback logic fails for: " + resourceUrl);
    }
  }

  private static Template parsedTemplateFromJar(URL resourceUrl)
      throws URISyntaxException, IOException {
    // Jar URLs look like this: jar:file:/path/to/file.jar!/entry/within/jar
    // So take apart the URL to open the jar /path/to/file.jar and read the entry
    // entry/within/jar from it.
    String resourceUrlString = resourceUrl.toString().substring("jar:".length());
    int bang = resourceUrlString.lastIndexOf('!');
    String entryName = resourceUrlString.substring(bang + 1);
    if (entryName.startsWith("/")) {
      entryName = entryName.substring(1);
    }
    URI jarUri = new URI(resourceUrlString.substring(0, bang));
    try (JarFile jar = new JarFile(new File(jarUri))) {
      JarEntry entry = jar.getJarEntry(entryName);
      InputStream in = jar.getInputStream(entry);
      return templateFromInputStream(in);
    }
  }

  // We don't really expect this case to arise, since the bug we're working around concerns jars
  // not individual files. However, when running the test for this workaround from Maven, we do
  // have files. That does mean the test is basically useless there, but Google's internal build
  // system does run it using a jar, so we do have coverage.
  private static Template parsedTemplateFromFile(URL resourceUrl)
      throws IOException, URISyntaxException {
    File resourceFile = new File(resourceUrl.toURI());
    try (InputStream in = new FileInputStream(resourceFile)) {
      return templateFromInputStream(in);
    }
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
