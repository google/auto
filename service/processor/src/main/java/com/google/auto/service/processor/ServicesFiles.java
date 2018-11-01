/*
 * Copyright (C) 2008 Google, Inc.
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
package com.google.auto.service.processor;

import static com.google.common.base.Charsets.UTF_8;

import com.google.common.io.Closer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A helper class for reading and writing Services files.
 */
final class ServicesFiles {
  public static final String SERVICES_PATH = "META-INF/services";

  private ServicesFiles() { }

  /**
   * Returns an absolute path to a service file given the class
   * name of the service.
   *
   * @param serviceName not {@code null}
   * @return SERVICES_PATH + serviceName
   */
  static String getPath(String serviceName) {
    return SERVICES_PATH + "/" + serviceName;
  }

  /**
   * Reads the set of service classes from a service file.
   *
   * @param input not {@code null}. Closed after use.
   * @return a not {@code null Set} of service class names.
   * @throws IOException
   */
  static Set<String> readServiceFile(InputStream input) throws IOException {
    HashSet<String> serviceClasses = new HashSet<String>();
    Closer closer = Closer.create();
    try {
      // TODO(gak): use CharStreams
      BufferedReader r = closer.register(new BufferedReader(new InputStreamReader(input, UTF_8)));
      String line;
      while ((line = r.readLine()) != null) {
        int commentStart = line.indexOf('#');
        if (commentStart >= 0) {
          line = line.substring(0, commentStart);
        }
        line = line.trim();
        if (!line.isEmpty()) {
          serviceClasses.add(line);
        }
      }
      return serviceClasses;
    } catch (Throwable t) {
      throw closer.rethrow(t);
    } finally {
      closer.close();
    }
  }

  /**
   * Writes the set of service class names to a service file.
   *
   * @param output not {@code null}. Not closed after use.
   * @param services a not {@code null Collection} of service class names.
   * @throws IOException
   */
  static void writeServiceFile(Collection<String> services, OutputStream output)
      throws IOException {
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, UTF_8));
    for (String service : services) {
      writer.write(service);
      writer.newLine();
    }
    writer.flush();
  }
}