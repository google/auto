/*
 * Copyright 2023 Google LLC
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

import static com.google.auto.common.MoreStreams.toImmutableSet;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.Reflection;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * Reflective access to Kotlin metadata.
 *
 * <p>We use Java reflection to access the Kotlin metadata API, so that we don't need a compile-time
 * dependency on that API. This means that AutoValue can ship without a dependency on a particular
 * version of the API, so projects can use AutoValue with whatever version of the API suits them.
 */
final class KotlinMetadata {
  /** The {@code kotlin.Metadata} annotation class. */
  private static final Class<? extends Annotation> KOTLIN_METADATA_ANNOTATION;

  /** The {@code kotlinx.metadata.jvm.KotlinClassHeader} constructor. */
  private static final Constructor<?> KOTLIN_CLASS_HEADER_CONSTRUCTOR;

  /** The {@code kotlinx.metadata.jvm.KotlinClassMetadata.read(kotlin.Metadata)} method. */
  private static final Method KOTLIN_CLASS_METADATA_READ;

  /** The {@code kotlinx.metadata.jvm.KotlinClassMetadata.Class.toKmClass()} method. */
  private static final Method KOTLIN_CLASS_METADATA_CLASS_TO_KM_CLASS;

  /** The {@code kotlinx.metadata.KmClass.getConstructors()} method. */
  private static final Method KM_CLASS_GET_CONSTRUCTORS;

  /** The {@code kotlinx.metadata.KmConstructor.getValueParameters()} method. */
  private static final Method KM_CONSTRUCTOR_GET_VALUE_PARAMETERS;

  /** The {@code kotlinx.metadata.KmValueParameter.getName()} method. */
  private static final Method KM_VALUE_PARAMETER_GET_NAME;

  /** The {@code kotlinx.metadata.KmValueParameter.getFlags()} method. */
  private static final Method KM_VALUE_PARAMETER_GET_FLAGS;

  /** The {@code kotlinx.metadata.Flag.ValueParameter.DECLARES_DEFAULT_VALUE} field. */
  private static final Field FLAG_VALUE_PARAMETER_DECLARES_DEFAULT_VALUE;

  /** The {@code kotlinx.metadata.Flag.invoke} method. */
  private static final Method FLAG_INVOKE;

  /** An exception that may have occurred while trying to look up any of the above. */
  private static final ReflectiveOperationException KOTLIN_API_REFLECTIVE_OPERATION_EXCEPTION;

  static {
    Class<? extends Annotation> kotlinMetadataAnnotation = null;
    Constructor<?> newKotlinClassHeader = null;
    Method kotlinClassMetadataRead = null;
    Method kotlinClassMetadataClassToKmClass = null;
    Method kmClassGetConstructors = null;
    Method kmConstuctorGetValueParameters = null;
    Method kmValueParameterGetName = null;
    Method kmValueParameterGetFlags = null;
    Field flagValueParameterDeclaresDefaultValue = null;
    Method flagInvoke = null;
    boolean shouldWork = false;
    ReflectiveOperationException kotlinApiReflectiveOperationException = null;
    try {
      kotlinMetadataAnnotation = Class.forName("kotlin.Metadata").asSubclass(Annotation.class);
      Class<?> kotlinClassHeader = Class.forName("kotlinx.metadata.jvm.KotlinClassHeader");
      shouldWork = true; // If we get the above but not the below, something is wrong.
      newKotlinClassHeader =
          kotlinClassHeader.getConstructor(
              Integer.class,
              int[].class,
              String[].class,
              String[].class,
              String.class,
              String.class,
              Integer.class);
      Class<?> kotlinClassMetadata = Class.forName("kotlinx.metadata.jvm.KotlinClassMetadata");
      Class<?> kotlinMetadata = Class.forName("kotlin.Metadata");
      kotlinClassMetadataRead = kotlinClassMetadata.getMethod("read", kotlinMetadata);
      Class<?> kotlinClassMetadataClass =
          Class.forName("kotlinx.metadata.jvm.KotlinClassMetadata$Class");
      kotlinClassMetadataClassToKmClass = kotlinClassMetadataClass.getMethod("toKmClass");
      Class<?> kmClass = Class.forName("kotlinx.metadata.KmClass");
      kmClassGetConstructors = kmClass.getMethod("getConstructors");
      Class<?> kmConstuctor = Class.forName("kotlinx.metadata.KmConstructor");
      kmConstuctorGetValueParameters = kmConstuctor.getMethod("getValueParameters");
      Class<?> kmValueParameter = Class.forName("kotlinx.metadata.KmValueParameter");
      kmValueParameterGetName = kmValueParameter.getMethod("getName");
      kmValueParameterGetFlags = kmValueParameter.getMethod("getFlags");
      Class<?> flagValueParameter = Class.forName("kotlinx.metadata.Flag$ValueParameter");
      flagValueParameterDeclaresDefaultValue =
          flagValueParameter.getField("DECLARES_DEFAULT_VALUE");
      Class<?> flag = Class.forName("kotlinx.metadata.Flag");
      flagInvoke = flag.getMethod("invoke", int.class);
    } catch (ReflectiveOperationException e) {
      if (shouldWork) {
        kotlinApiReflectiveOperationException = e;
      }
    }
    KOTLIN_METADATA_ANNOTATION = kotlinMetadataAnnotation;
    KOTLIN_CLASS_HEADER_CONSTRUCTOR = newKotlinClassHeader;
    KOTLIN_CLASS_METADATA_READ = kotlinClassMetadataRead;
    KOTLIN_CLASS_METADATA_CLASS_TO_KM_CLASS = kotlinClassMetadataClassToKmClass;
    KM_CLASS_GET_CONSTRUCTORS = kmClassGetConstructors;
    KM_CONSTRUCTOR_GET_VALUE_PARAMETERS = kmConstuctorGetValueParameters;
    KM_VALUE_PARAMETER_GET_NAME = kmValueParameterGetName;
    KM_VALUE_PARAMETER_GET_FLAGS = kmValueParameterGetFlags;
    FLAG_VALUE_PARAMETER_DECLARES_DEFAULT_VALUE = flagValueParameterDeclaresDefaultValue;
    FLAG_INVOKE = flagInvoke;
    KOTLIN_API_REFLECTIVE_OPERATION_EXCEPTION = kotlinApiReflectiveOperationException;
  }

  /**
   * A copy of the Java equivalent of {@code kotlin.Metadata} which we will access through a {@link
   * Proxy}.
   */
  interface KotlinMetadataAnnotation {
    int k();

    int[] mv();

    String[] d1();

    String[] d2();

    String xs();

    String pn();

    int xi();
  }

  /**
   * Returns an implementation of {@link KotlinMetadataAnnotation} that forwards its methods to the
   * given annotation instance. It is expected that that instance has the same methods, or a
   * superset of them.
   */
  private static KotlinMetadataAnnotation annotationProxy(Annotation annotation) {
    InvocationHandler invocationHandler =
        (unusedProxy, method, args) -> {
          try {
            Method annotationMethod = annotation.annotationType().getMethod(method.getName());
            return annotationMethod.invoke(annotation, args);
          } catch (ReflectiveOperationException e) {
            throw new VerifyException(e);
          }
        };
    return Reflection.newProxy(KotlinMetadataAnnotation.class, invocationHandler);
  }

  static final AtomicBoolean complained = new AtomicBoolean(false);

  /** Returns an equivalent of the {@code kotlin.Metadata} on the given element, if there is one. */
  static Optional<KotlinMetadataAnnotation> kotlinMetadataAnnotation(Element element) {
    if (KOTLIN_METADATA_ANNOTATION == null) {
      return Optional.empty();
    }
    Annotation annotation = element.getAnnotation(KOTLIN_METADATA_ANNOTATION);
    return Optional.ofNullable(annotation).map(KotlinMetadata::annotationProxy);
  }

  /** Returns a list of the Kotlin constructors in {@code ofClass}. */
  static ImmutableList<Executable> kotlinConstructorsIn(
      ErrorReporter errorReporter, KotlinMetadataAnnotation metadata, TypeElement ofClass) {
    // We match the KmConstructor instances with the ExecutableElement instances based on the
    // parameter names. We could possibly just assume that the constructors are in the same order.
    Map<ImmutableSet<String>, ExecutableElement> map =
        constructorsIn(ofClass.getEnclosedElements()).stream()
            .collect(toMap(c -> parameterNames(c), c -> c, (a, b) -> a, LinkedHashMap::new));
    ImmutableMap<ImmutableSet<String>, ExecutableElement> paramNamesToConstructor =
        ImmutableMap.copyOf(map);
    try {
      if (KOTLIN_API_REFLECTIVE_OPERATION_EXCEPTION != null) {
        // We weren't able to get all the Methods etc, so complain if we haven't already.
        throw KOTLIN_API_REFLECTIVE_OPERATION_EXCEPTION;
      }
      return kotlinConstructorsIn(metadata, paramNamesToConstructor);
    } catch (ReflectiveOperationException e) {
      if (!complained.getAndSet(true)) {
        errorReporter.reportWarning(ofClass, "Exception reading Kotlin metadata: %s", e);
      }
      return ImmutableList.of();
    }
  }

  private static ImmutableList<Executable> kotlinConstructorsIn(
      KotlinMetadataAnnotation metadata,
      ImmutableMap<ImmutableSet<String>, ExecutableElement> paramNamesToConstructor)
      throws ReflectiveOperationException {
    // header = new KotlinClassHeader(...);
    Object header =
        KOTLIN_CLASS_HEADER_CONSTRUCTOR.newInstance(
            metadata.k(),
            metadata.mv(),
            metadata.d1(),
            metadata.d2(),
            metadata.xs(),
            metadata.pn(),
            metadata.xi());

    // KotlinClassMetadata.Class classMetadata = KotlinClassMetadata.read(header);
    Object classMetadata = KOTLIN_CLASS_METADATA_READ.invoke(null, header);

    // KmClass kmClass = classMetadata.toKmClass();
    Object kmClass = KOTLIN_CLASS_METADATA_CLASS_TO_KM_CLASS.invoke(classMetadata);

    // List<KmConstructor> kmConstructors = kmClass.getConstructors()
    List<?> kmConstructors = (List<?>) KM_CLASS_GET_CONSTRUCTORS.invoke(kmClass);

    ImmutableList.Builder<Executable> kotlinConstructorsBuilder = ImmutableList.builder();
    for (Object kmConstructor : kmConstructors) {
      ImmutableSet.Builder<String> allBuilder = ImmutableSet.builder();
      ImmutableSet.Builder<String> optionalBuilder = ImmutableSet.builder();

      // List<KmValueParameter> params = kmConstructor.getValueParameters();
      List<?> params = (List<?>) KM_CONSTRUCTOR_GET_VALUE_PARAMETERS.invoke(kmConstructor);
      for (Object param : params) {
        String name = (String) KM_VALUE_PARAMETER_GET_NAME.invoke(param);
        allBuilder.add(name);

        // Flag flag = Flag.ValueParameter.DECLARES_DEFAULT_VALUE
        Object flag = FLAG_VALUE_PARAMETER_DECLARES_DEFAULT_VALUE.get(null);
        // if (flag.invoke(param.getFlags()) ...
        int flags = (Integer) KM_VALUE_PARAMETER_GET_FLAGS.invoke(param);
        if ((Boolean) FLAG_INVOKE.invoke(flag, flags)) {
          optionalBuilder.add(name);
        }

        ImmutableSet<String> optional = optionalBuilder.build();
        ImmutableSet<String> all = allBuilder.build();
        ExecutableElement javaConstructor = paramNamesToConstructor.get(all);
        if (javaConstructor != null) {
          kotlinConstructorsBuilder.add(Executable.of(javaConstructor, optional));
        }
      }
    }
    return kotlinConstructorsBuilder.build();
  }

  private static ImmutableSet<String> parameterNames(ExecutableElement executableElement) {
    return executableElement.getParameters().stream()
        .map(v -> v.getSimpleName().toString())
        .collect(toImmutableSet());
  }

  private KotlinMetadata() {}
}
