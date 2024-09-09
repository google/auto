/*
 * Copyright 2024 Google LLC
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

import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.auto.value.processor.ClassNames.KOTLIN_METADATA_NAME;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.toMap;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import com.google.auto.common.AnnotationMirrors;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * Utilities for working with Kotlin metadata.
 *
 * <p>We use reflection to avoid referencing the Kotlin metadata API directly. AutoBuilder clients
 * that don't use Kotlin shouldn't have to have the Kotlin runtime on their classpath, even if it is
 * only the annotation-processing classpath.
 */
final class KotlinMetadata {
  private final ErrorReporter errorReporter;
  private boolean warnedAboutMissingMetadataApi;

  KotlinMetadata(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  /**
   * Use Kotlin reflection to build {@link Executable} instances for the constructors in {@code
   * ofClass} that include information about which parameters have default values.
   *
   * @param metadata the {@code @kotlin.Metadata} annotation on {@code ofClass}
   * @param ofClass the class whose constructors should be returned
   */
  ImmutableList<Executable> kotlinConstructorsIn(AnnotationMirror metadata, TypeElement ofClass) {
    if (!KOTLIN_METADATA_AVAILABLE) {
      if (!warnedAboutMissingMetadataApi) {
        warnedAboutMissingMetadataApi = true;
        errorReporter.reportWarning(
            ofClass,
            "[AutoBuilderNoMetadataApi] The Kotlin metadata API (kotlinx.metadata or"
                + " kotlin.metadata) is not available. You may need to add a dependency on"
                + " org.jetbrains.kotlin:kotlin-metadata-jvm.");
      }
      return ImmutableList.of();
    }
    try {
      return kotlinConstructorsFromReflection(metadata, ofClass);
    } catch (InvocationTargetException e) {
      throwIfUnchecked(e.getCause());
      // We don't expect the Kotlin API to throw checked exceptions.
      throw new LinkageError(e.getMessage(), e);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  private static ImmutableList<Executable> kotlinConstructorsFromReflection(
      AnnotationMirror metadata, TypeElement ofClass) throws ReflectiveOperationException {
    ImmutableMap<String, AnnotationValue> annotationValues =
        AnnotationMirrors.getAnnotationValuesWithDefaults(metadata).entrySet().stream()
            .collect(toImmutableMap(e -> e.getKey().getSimpleName().toString(), e -> e.getValue()));
    // We match the KmConstructor instances with the ExecutableElement instances based on the
    // parameter names. We could possibly just assume that the constructors are in the same order.
    Map<ImmutableSet<String>, ExecutableElement> map =
        constructorsIn(ofClass.getEnclosedElements()).stream()
            .collect(toMap(c -> parameterNames(c), c -> c, (a, b) -> a, LinkedHashMap::new));
    ImmutableMap<ImmutableSet<String>, ExecutableElement> paramNamesToConstructor =
        ImmutableMap.copyOf(map);
    KotlinClassHeader header =
        new KotlinClassHeader(
            (Integer) annotationValues.get("k").getValue(),
            intArrayValue(annotationValues.get("mv")),
            stringArrayValue(annotationValues.get("d1")),
            stringArrayValue(annotationValues.get("d2")),
            (String) annotationValues.get("xs").getValue(),
            (String) annotationValues.get("pn").getValue(),
            (Integer) annotationValues.get("xi").getValue());
    KotlinClassMetadata.Class classMetadata = KotlinClassMetadata.readLenient(header);
    KmClass kmClass = classMetadata.getKmClass();
    ImmutableList.Builder<Executable> kotlinConstructorsBuilder = ImmutableList.builder();
    for (KmConstructor constructor : kmClass.getConstructors()) {
      ImmutableSet.Builder<String> allBuilder = ImmutableSet.builder();
      ImmutableSet.Builder<String> optionalBuilder = ImmutableSet.builder();
      for (KmValueParameter param : constructor.getValueParameters()) {
        String name = param.getName();
        allBuilder.add(name);
        if (Attributes.getDeclaresDefaultValue(param)) {
          optionalBuilder.add(name);
        }
      }
      ImmutableSet<String> optional = optionalBuilder.build();
      ImmutableSet<String> all = allBuilder.build();
      ExecutableElement javaConstructor = paramNamesToConstructor.get(all);
      if (javaConstructor != null) {
        kotlinConstructorsBuilder.add(Executable.of(javaConstructor, optional));
      }
    }
    return kotlinConstructorsBuilder.build();
  }

  private static ImmutableSet<String> parameterNames(ExecutableElement executableElement) {
    return executableElement.getParameters().stream()
        .map(v -> v.getSimpleName().toString())
        .collect(toImmutableSet());
  }

  Optional<AnnotationMirror> kotlinMetadataAnnotation(Element element) {
    return element.getAnnotationMirrors().stream()
        .filter(
            a ->
                asTypeElement(a.getAnnotationType())
                    .getQualifiedName()
                    .contentEquals(KOTLIN_METADATA_NAME))
        .<AnnotationMirror>map(a -> a) // get rid of that stupid wildcard
        .findFirst();
  }

  private static int[] intArrayValue(AnnotationValue value) {
    @SuppressWarnings("unchecked")
    List<AnnotationValue> list = (List<AnnotationValue>) value.getValue();
    return list.stream().mapToInt(v -> (int) v.getValue()).toArray();
  }

  private static String[] stringArrayValue(AnnotationValue value) {
    @SuppressWarnings("unchecked")
    List<AnnotationValue> list = (List<AnnotationValue>) value.getValue();
    return list.stream().map(AnnotationValue::getValue).toArray(String[]::new);
  }

  // Wrapper classes for the Kotlin metadata API. These classes have the same names as the ones
  // from that API (minus the package of course), and use reflection to access the real API. This
  // allows us to write client code that is essentially the same as if we were using the real API.
  // Otherwise the logic would be obscured by all the reflective calls.

  private static class KotlinClassHeader {
    final Object /* KotlinClassHeader */ wrapped;

    KotlinClassHeader(
        Integer k, int[] mv, String[] d1, String[] d2, String xs, String pn, Integer xi)
        throws ReflectiveOperationException {
      this.wrapped = NEW_KOTLIN_CLASS_HEADER.newInstance(k, mv, d1, d2, xs, pn, xi);
    }
  }

  @SuppressWarnings({"JavaLangClash", "SameNameButDifferent"}) // "Class"
  private static class KotlinClassMetadata {
    static Class readLenient(KotlinClassHeader kotlinClassHeader)
        throws ReflectiveOperationException {
      return new Class(
          KOTLIN_CLASS_METADATA_READ_LENIENT.invoke(null, kotlinClassHeader.wrapped));
    }

    static class Class {
      final Object /* KotlinClassMetadata.Class */ wrapped;

      Class(Object /* KotlinClassMetadata.Class */ wrapped) {
        this.wrapped = wrapped;
      }

      KmClass getKmClass() throws ReflectiveOperationException {
        return new KmClass(KOTLIN_CLASS_METADATA_CLASS_GET_KM_CLASS.invoke(wrapped));
      }
    }
  }

  private static class KmClass {
    final Object /* KmClass */ wrapped;

    KmClass(Object wrapped) {
      this.wrapped = wrapped;
    }

    List<KmConstructor> getConstructors() throws ReflectiveOperationException {
      return ((List<?>) KM_CLASS_GET_CONSTRUCTORS.invoke(wrapped))
          .stream().map(KmConstructor::new).collect(toImmutableList());
    }
  }

  private static class KmConstructor {
    final Object /* KmConstructor */ wrapped;

    KmConstructor(Object wrapped) {
      this.wrapped = wrapped;
    }

    List<KmValueParameter> getValueParameters() throws ReflectiveOperationException {
      return ((List<?>) KM_CONSTRUCTOR_GET_VALUE_PARAMETERS.invoke(wrapped))
          .stream().map(KmValueParameter::new).collect(toImmutableList());
    }
  }

  private static class KmValueParameter {
    final Object /* KmValueParameter */ wrapped;

    KmValueParameter(Object wrapped) {
      this.wrapped = wrapped;
    }

    String getName() throws ReflectiveOperationException {
      return (String) KM_VALUE_PARAMETER_GET_NAME.invoke(wrapped);
    }
  }

  private static class Attributes {
    private Attributes() {}

    static boolean getDeclaresDefaultValue(KmValueParameter kmValueParameter)
        throws ReflectiveOperationException {
      return (boolean) ATTRIBUTES_GET_DECLARES_DEFAULT_VALUE.invoke(null, kmValueParameter.wrapped);
    }
  }

  private static final Constructor<?> NEW_KOTLIN_CLASS_HEADER;
  private static final Method KOTLIN_CLASS_METADATA_READ_LENIENT;
  private static final Method KOTLIN_CLASS_METADATA_CLASS_GET_KM_CLASS;
  private static final Method KM_CLASS_GET_CONSTRUCTORS;
  private static final Method KM_CONSTRUCTOR_GET_VALUE_PARAMETERS;
  private static final Method KM_VALUE_PARAMETER_GET_NAME;
  private static final Method ATTRIBUTES_GET_DECLARES_DEFAULT_VALUE;
  private static final boolean KOTLIN_METADATA_AVAILABLE;

  static {
    Constructor<?> newKotlinClassHeader = null;
    Method kotlinClassMetadataReadLenient = null;
    Method kotlinClassMetadataClassGetKmClass = null;
    Method kmClassGetConstructors = null;
    Method kmConstructorGetValueParameters = null;
    Method kmValueParameterGetName = null;
    Method attributeGetDeclaresDefaultValue = null;
    boolean kotlinMetadataAvailable = false;
    for (String prefix : new String[] {"kotlin.metadata.", "kotlinx.metadata."}) {
      try {
        Class<?> kotlinClassHeaderClass = Class.forName(prefix + "jvm.KotlinClassHeader");
        newKotlinClassHeader =
            kotlinClassHeaderClass.getConstructor(
                Integer.class,
                int[].class,
                String[].class,
                String[].class,
                String.class,
                String.class,
                Integer.class);
        Class<?> kotlinClassMetadataClass = Class.forName(prefix + "jvm.KotlinClassMetadata");
        // Load `kotlin.Metadata` in the same classloader as `kotlinClassHeaderClass`. They are
        // potentially from different artifacts so we could otherwise end up with a
        // `kotlin.Metadata` that is not actually the type of the `readLenient` parameter because of
        // differing classloaders.
        Class<?> kotlinMetadataClass =
            Class.forName("kotlin.Metadata", false, kotlinClassHeaderClass.getClassLoader());
        kotlinClassMetadataReadLenient =
            kotlinClassMetadataClass.getMethod("readLenient", kotlinMetadataClass);
        Class<?> kotlinClassMetadataClassClass =
            Class.forName(prefix + "jvm.KotlinClassMetadata$Class");
        Class<?> kmClassClass = Class.forName(prefix + "KmClass");
        kotlinClassMetadataClassGetKmClass = kotlinClassMetadataClassClass.getMethod("getKmClass");
        kmClassGetConstructors = kmClassClass.getMethod("getConstructors");
        Class<?> kmConstructorClass = Class.forName(prefix + "KmConstructor");
        kmConstructorGetValueParameters = kmConstructorClass.getMethod("getValueParameters");
        Class<?> kmValueParameterClass = Class.forName(prefix + "KmValueParameter");
        kmValueParameterGetName = kmValueParameterClass.getMethod("getName");
        Class<?> attributeClass = Class.forName(prefix + "Attributes");
        attributeGetDeclaresDefaultValue =
            attributeClass.getMethod("getDeclaresDefaultValue", kmValueParameterClass);
        kotlinMetadataAvailable = true;
        break;
      } catch (ReflectiveOperationException e) {
        // OK: The metadata API is unavailable with this prefix, and possibly with any prefix.
      }
    }
    NEW_KOTLIN_CLASS_HEADER = newKotlinClassHeader;
    KOTLIN_CLASS_METADATA_READ_LENIENT = kotlinClassMetadataReadLenient;
    KOTLIN_CLASS_METADATA_CLASS_GET_KM_CLASS = kotlinClassMetadataClassGetKmClass;
    KM_CLASS_GET_CONSTRUCTORS = kmClassGetConstructors;
    KM_CONSTRUCTOR_GET_VALUE_PARAMETERS = kmConstructorGetValueParameters;
    KM_VALUE_PARAMETER_GET_NAME = kmValueParameterGetName;
    ATTRIBUTES_GET_DECLARES_DEFAULT_VALUE = attributeGetDeclaresDefaultValue;
    KOTLIN_METADATA_AVAILABLE = kotlinMetadataAvailable;
  }
}
