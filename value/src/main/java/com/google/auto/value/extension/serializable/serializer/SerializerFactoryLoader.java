/*
 * Copyright 2020 Google LLC
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
package com.google.auto.value.extension.serializable.serializer;

import com.google.auto.value.extension.serializable.serializer.impl.SerializerFactoryImpl;
import com.google.auto.value.extension.serializable.serializer.interfaces.SerializerExtension;
import com.google.auto.value.extension.serializable.serializer.interfaces.SerializerFactory;
import com.google.auto.value.processor.SimpleServiceLoader;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;

/**
 * Builds a {@link SerializerFactory} populated with discovered {@link SerializerExtension}
 * instances.
 */
public final class SerializerFactoryLoader {

  /**
   * Returns a {@link SerializerFactory} with {@link SerializerExtension} instances provided by the
   * {@link java.util.ServiceLoader}.
   */
  public static SerializerFactory getFactory(ProcessingEnvironment processingEnv) {
    return new SerializerFactoryImpl(loadExtensions(processingEnv), processingEnv);
  }

  private static ImmutableList<SerializerExtension> loadExtensions(
      ProcessingEnvironment processingEnv) {
    // The below is a workaround for a test-building bug. We don't expect to support it indefinitely
    // so don't depend on it.
    String allowedMissingClasses =
        processingEnv.getOptions().get("allowedMissingSerializableExtensionClasses");
    Optional<Pattern> allowedMissingClassesPattern =
        Optional.ofNullable(allowedMissingClasses).map(Pattern::compile);
    try {
      return ImmutableList.copyOf(
          SimpleServiceLoader.load(
              SerializerExtension.class,
              SerializerFactoryLoader.class.getClassLoader(),
              allowedMissingClassesPattern));
    } catch (Throwable t) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "An exception occurred while looking for SerializerExtensions. No extensions will"
                  + " function.\n"
                  + Throwables.getStackTraceAsString(t));
      return ImmutableList.of();
    }
  }

  private SerializerFactoryLoader() {}
}
