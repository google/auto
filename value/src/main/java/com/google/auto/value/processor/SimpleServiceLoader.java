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

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceConfigurationError;

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
    String resourceName = "META-INF/services/" + service.getName();
    List<URL> resourceUrls;
    try {
      resourceUrls = Collections.list(loader.getResources(resourceName));
    } catch (IOException e) {
      throw new ServiceConfigurationError("Could not look up " + resourceName, e);
    }
    ImmutableList.Builder<T> providers = ImmutableList.builder();
    for (URL resourceUrl : resourceUrls) {
      try {
        providers.addAll(providersFromUrl(resourceUrl, service, loader));
      } catch (IOException e) {
        throw new ServiceConfigurationError("Could not read " + resourceUrl, e);
      }
    }
    return providers.build();
  }

  private static <T> ImmutableList<T> providersFromUrl(
      URL resourceUrl, Class<T> service, ClassLoader loader) throws IOException {
    ImmutableList.Builder<T> providers = ImmutableList.builder();
    URLConnection urlConnection = resourceUrl.openConnection();
    urlConnection.setUseCaches(false);
    try (InputStream in = urlConnection.getInputStream();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      for (String line : reader.lines().collect(toList())) {
        Optional<String> maybeClassName = parseClassName(line);
        if (maybeClassName.isPresent()) {
          String className = maybeClassName.get();
          Class<?> c;
          try {
            c = Class.forName(className, false, loader);
          } catch (ClassNotFoundException e) {
            throw new ServiceConfigurationError("Could not load " + className, e);
          }
          if (!service.isAssignableFrom(c)) {
            throw new ServiceConfigurationError(
                "Class " + className + " is not assignable to " + service.getName());
          }
          try {
            Object provider = c.getConstructor().newInstance();
            providers.add(service.cast(provider));
          } catch (ReflectiveOperationException e) {
            throw new ServiceConfigurationError("Could not construct " + className, e);
          }
        }
      }
      return providers.build();
    }
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
