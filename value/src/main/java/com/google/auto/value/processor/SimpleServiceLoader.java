/*
 * Copyright 2019 Google LLC
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
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.regex.Pattern;

/**
 * A replacement for {@link java.util.ServiceLoader} that avoids certain long-standing bugs. This
 * simpler implementation does not bother with lazy loading but returns all the service
 * implementations in one list. It makes sure that {@link URLConnection#setUseCaches} is called to
 * turn off jar caching, since that tends to lead to problems in versions before JDK 9.
 *
 * @see <a href="https://github.com/google/auto/issues/718">Issue #718</a>
 * @see <a href="https://bugs.openjdk.java.net/browse/JDK-8156014">JDK-8156014</a>
 */
public final class SimpleServiceLoader {
  private SimpleServiceLoader() {}

  public static <T> ImmutableList<T> load(Class<? extends T> service, ClassLoader loader) {
    return load(service, loader, Optional.empty());
  }

  public static <T> ImmutableList<T> load(
      Class<? extends T> service, ClassLoader loader, Optional<Pattern> allowedMissingClasses) {
    String resourceName = "META-INF/services/" + service.getName();
    List<URL> resourceUrls;
    try {
      resourceUrls = Collections.list(loader.getResources(resourceName));
    } catch (IOException e) {
      throw new ServiceConfigurationError("Could not look up " + resourceName, e);
    }
    ImmutableSet.Builder<Class<? extends T>> providerClasses = ImmutableSet.builder();
    for (URL resourceUrl : resourceUrls) {
      try {
        providerClasses.addAll(
            providerClassesFromUrl(resourceUrl, service, loader, allowedMissingClasses));
      } catch (IOException e) {
        throw new ServiceConfigurationError("Could not read " + resourceUrl, e);
      }
    }
    ImmutableList.Builder<T> providers = ImmutableList.builder();
    for (Class<? extends T> providerClass : providerClasses.build()) {
      try {
        T provider = providerClass.getConstructor().newInstance();
        providers.add(provider);
      } catch (ReflectiveOperationException e) {
        throw new ServiceConfigurationError("Could not construct " + providerClass.getName(), e);
      }
    }
    return providers.build();
  }

  private static <T> ImmutableSet<Class<? extends T>> providerClassesFromUrl(
      URL resourceUrl,
      Class<? extends T> service,
      ClassLoader loader,
      Optional<Pattern> allowedMissingClasses)
      throws IOException {
    ImmutableSet.Builder<Class<? extends T>> providerClasses = ImmutableSet.builder();
    URLConnection urlConnection = resourceUrl.openConnection();
    urlConnection.setUseCaches(false);
    List<String> lines;
    try (InputStream in = urlConnection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF_8))) {
      lines = reader.lines().collect(toList());
    }
    List<String> classNames =
        lines.stream()
            .map(SimpleServiceLoader::parseClassName)
            .flatMap(Streams::stream)
            .collect(toList());
    for (String className : classNames) {
      Class<?> c;
      try {
        c = Class.forName(className, false, loader);
      } catch (ClassNotFoundException e) {
        if (allowedMissingClasses.isPresent()
            && allowedMissingClasses.get().matcher(className).matches()) {
          continue;
        }
        throw new ServiceConfigurationError("Could not load " + className, e);
      }
      if (!service.isAssignableFrom(c)) {
        throw new ServiceConfigurationError(
            "Class " + className + " is not assignable to " + service.getName());
      }
      providerClasses.add(c.asSubclass(service));
    }
    return providerClasses.build();
  }

  private static Optional<String> parseClassName(String line) {
    int hash = line.indexOf('#');
    if (hash >= 0) {
      line = line.substring(0, hash);
    }
    line = line.trim();
    return line.isEmpty() ? Optional.empty() : Optional.of(line);
  }
}
