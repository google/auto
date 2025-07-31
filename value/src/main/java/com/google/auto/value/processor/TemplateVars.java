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
package com.google.auto.value.processor;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.escapevelocity.Template;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;

/**
 * A template and a set of variables to be substituted into that template. A concrete subclass of
 * this class defines a set of fields that are template variables, and an implementation of the
 * {@link #parsedTemplate()} method which is the template to substitute them into. Once the values
 * of the fields have been assigned, the {@link #toText()} method returns the result of substituting
 * them into the template.
 *
 * <p>The subclass may be a direct subclass of this class or a more distant descendant. Every field
 * in the starting class and its ancestors up to this class will be included. Fields cannot be
 * static unless they are also final. They cannot be private, though they can be package-private if
 * the class is in the same package as this class. They cannot be primitive or null, so that there
 * is a clear indication when a field has not been set.
 *
 * @author Éamonn McManus
 */
abstract class TemplateVars {
  abstract Template parsedTemplate();

  private final ImmutableList<Field> fields;

  TemplateVars() {
    this.fields = getFields(getClass());
  }

  private static ImmutableList<Field> getFields(Class<?> c) {
    ImmutableList.Builder<Field> fieldsBuilder = ImmutableList.builder();
    while (c != TemplateVars.class) {
      addFields(fieldsBuilder, c.getDeclaredFields());
      c = c.getSuperclass();
    }
    return fieldsBuilder.build();
  }

  private static void addFields(
      ImmutableList.Builder<Field> fieldsBuilder, Field[] declaredFields) {
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
      fieldsBuilder.add(field);
    }
  }

  /**
   * Returns the result of substituting the variables defined by the fields of this class (a
   * concrete subclass of TemplateVars) into the template returned by {@link #parsedTemplate()}.
   */
  String toText() {
    ImmutableMap<String, Object> vars = toVars();
    return parsedTemplate().evaluate(vars);
  }

  private ImmutableMap<String, Object> toVars() {
    Map<String, Object> vars = new TreeMap<>();
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

  @Override
  public String toString() {
    return getClass().getSimpleName() + toVars();
  }

  static Template parsedTemplateForResource(String resourceName) {
    try {
      return Template.parseFrom(resourceName, TemplateVars::readerFromUrl);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // This is an ugly workaround for https://bugs.openjdk.java.net/browse/JDK-6947916, as
  // reported in https://github.com/google/auto/issues/365.
  // The issue is that sometimes the InputStream returned by JarURLCollection.getInputStream()
  // can be closed prematurely, which leads to an IOException saying "Stream closed".
  // To avoid that issue, we open the jar file directly and load the resource from it. Since that
  // doesn't use JarURLConnection, it shouldn't be susceptible to the same bug.
  private static Reader readerFromUrl(String resourceName) throws IOException {
    URL resourceUrl = TemplateVars.class.getResource(resourceName);
    if (resourceUrl == null) {
      throw new IllegalArgumentException("Could not find resource: " + resourceName);
    }
    try {
      if (Ascii.equalsIgnoreCase(resourceUrl.getProtocol(), "file")) {
        return readerFromFile(resourceUrl);
      } else if (Ascii.equalsIgnoreCase(resourceUrl.getProtocol(), "jar")) {
        return readerFromJar(resourceUrl);
      } else {
        return readerFromOther(resourceUrl);
      }
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  private static Reader readerFromJar(URL resourceUrl) throws URISyntaxException, IOException {
    // Jar URLs look like this: jar:file:/path/to/file.jar!/entry/within/jar
    // So take apart the URL to open the jar /path/to/file.jar and read the entry
    // entry/within/jar from it.
    // We could use the methods from JarURLConnection here, but that would risk provoking the same
    // problem that prompted this workaround in the first place.
    String resourceUrlString = resourceUrl.toString().substring("jar:".length());
    int bang = resourceUrlString.lastIndexOf('!');
    String entryName = resourceUrlString.substring(bang + 1);
    if (entryName.startsWith("/")) {
      entryName = entryName.substring(1);
    }
    URI jarUri = new URI(resourceUrlString.substring(0, bang));
    try (JarFile jar = new JarFile(new File(jarUri));
        InputStream in = jar.getInputStream(jar.getJarEntry(entryName))) {
      String contents = new String(ByteStreams.toByteArray(in), UTF_8);
      return new StringReader(contents);
    }
  }

  // In most execution environments, we'll be dealing with a jar, but we handle individual files
  // just for cases like running our tests with Maven.
  private static Reader readerFromFile(URL resourceUrl) throws IOException, URISyntaxException {
    File resourceFile = new File(resourceUrl.toURI());
    return new InputStreamReader(new FileInputStream(resourceFile), UTF_8);
  }

  // As a fallback, we handle other kinds of URL naively. For example, if we're executing in GraalVM
  // code, we might have a `resource:` URL. This code is not currently covered by unit tests.
  // See https://github.com/google/auto/issues/1783.
  private static Reader readerFromOther(URL resourceUrl) throws IOException {
    return new InputStreamReader(resourceUrl.openStream(), UTF_8);
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
