/*
 * Copyright 2013 Google LLC
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
package com.google.auto.factory.processor;

import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

/**
 * A value object representing a factory to be generated.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class FactoryDescriptor {
  private static final CharMatcher INVALID_IDENTIFIER_CHARACTERS =
      new CharMatcher() {
        @Override
        public boolean matches(char c) {
          return !Character.isJavaIdentifierPart(c);
        }
      };

  abstract PackageAndClass name();

  abstract ImmutableSet<AnnotationMirror> annotations();

  abstract TypeMirror extendingType();

  abstract ImmutableSet<TypeMirror> implementingTypes();

  abstract boolean publicType();

  abstract ImmutableSet<FactoryMethodDescriptor> methodDescriptors();

  abstract ImmutableSet<ImplementationMethodDescriptor> implementationMethodDescriptors();

  abstract boolean allowSubclasses();

  abstract ImmutableMap<Key, ProviderField> providers();

  final AutoFactoryDeclaration declaration() {
    // There is always at least one method descriptor.
    return methodDescriptors().iterator().next().declaration();
  }

  private static class UniqueNameSet {
    private final Set<String> uniqueNames = new HashSet<>();

    /**
     * Generates a unique name using {@code base}. If {@code base} has not yet been added, it will
     * be returned as-is. If your {@code base} is healthy, this will always return {@code base}.
     */
    String getUniqueName(CharSequence base) {
      String name = base.toString();
      for (int differentiator = 2; !uniqueNames.add(name); differentiator++) {
        name = base.toString() + differentiator;
      }
      return name;
    }
  }

  static Optional<FactoryDescriptor> create(
      PackageAndClass name,
      ImmutableSet<AnnotationMirror> annotations,
      TypeMirror extendingType,
      ImmutableSet<TypeMirror> implementingTypes,
      boolean publicType,
      ImmutableSet<FactoryMethodDescriptor> methodDescriptors,
      ImmutableSet<ImplementationMethodDescriptor> implementationMethodDescriptors,
      boolean allowSubclasses,
      Messager messager) {
    ImmutableMap.Builder<FactoryMethodDescriptor, ImplementationMethodDescriptor>
        duplicateMethodDescriptors = ImmutableMap.builder();
    ImmutableSet.Builder<ImplementationMethodDescriptor>
        deduplicatedImplementationMethodDescriptors = ImmutableSet.builder();
    boolean unmatchedMethods = false;
    for (ImplementationMethodDescriptor implementationMethod : implementationMethodDescriptors) {
      Optional<FactoryMethodDescriptor> matchingFactoryMethod =
          methodDescriptors.stream()
              .filter(
                  factoryMethod -> hasMatchingPassedParameters(factoryMethod, implementationMethod))
              .findFirst();
      if (matchingFactoryMethod.isPresent()) {
        if (implementationMethod.name().equals("create")) {
          duplicateMethodDescriptors.put(matchingFactoryMethod.get(), implementationMethod);
        } else {
          deduplicatedImplementationMethodDescriptors.add(implementationMethod);
        }
      } else {
        unmatchedMethods = true;
        AutoFactoryDeclaration declaration = methodDescriptors.iterator().next().declaration();
        messager.printMessage(
            Kind.ERROR,
            unmatchedImplementationMethodMessage(implementationMethod),
            declaration.target(),
            declaration.mirror());
      }
    }
    if (unmatchedMethods) {
      return Optional.empty();
    }

    ImmutableSet<FactoryMethodDescriptor> deduplicatedMethodDescriptors =
        getDeduplicatedMethodDescriptors(
            methodDescriptors, duplicateMethodDescriptors.buildKeepingLast());

    ImmutableSetMultimap.Builder<Key, Parameter> parametersForProviders =
        ImmutableSetMultimap.builder();
    for (FactoryMethodDescriptor descriptor : methodDescriptors) {
      for (Parameter parameter : descriptor.providedParameters()) {
        parametersForProviders.put(parameter.key(), parameter);
      }
    }
    ImmutableMap.Builder<Key, ProviderField> providersBuilder = ImmutableMap.builder();
    UniqueNameSet uniqueNames = new UniqueNameSet();
    parametersForProviders
        .build()
        .asMap()
        .forEach(
            (key, parameters) -> {
              switch (parameters.size()) {
                case 0:
                  throw new AssertionError();
                case 1:
                  Parameter parameter = Iterables.getOnlyElement(parameters);
                  providersBuilder.put(
                      key,
                      ProviderField.create(
                          uniqueNames.getUniqueName(parameter.name() + "Provider"),
                          key,
                          parameter.nullable()));
                  break;
                default:
                  String providerName =
                      uniqueNames.getUniqueName(
                          INVALID_IDENTIFIER_CHARACTERS.replaceFrom(key.toString(), '_')
                              + "Provider");
                  Optional<AnnotationMirror> nullable =
                      parameters.stream()
                          .map(Parameter::nullable)
                          .flatMap(Streams::stream)
                          .findFirst();
                  providersBuilder.put(key, ProviderField.create(providerName, key, nullable));
                  break;
              }
            });

    return Optional.of(
        new AutoValue_FactoryDescriptor(
            name,
            annotations,
            extendingType,
            implementingTypes,
            publicType,
            deduplicatedMethodDescriptors,
            deduplicatedImplementationMethodDescriptors.build(),
            allowSubclasses,
            providersBuilder.buildOrThrow()));
  }

  /**
   * Returns a set of deduplicated {@link FactoryMethodDescriptor}s from the set of original
   * descriptors and the map of duplicate descriptors.
   *
   * <p>Modifies the duplicate {@link FactoryMethodDescriptor}s such that they are overriding and
   * reflect properties from the {@link ImplementationMethodDescriptor} they are implementing.
   */
  private static ImmutableSet<FactoryMethodDescriptor> getDeduplicatedMethodDescriptors(
      ImmutableSet<FactoryMethodDescriptor> methodDescriptors,
      ImmutableMap<FactoryMethodDescriptor, ImplementationMethodDescriptor>
          duplicateMethodDescriptors) {

    ImmutableSet.Builder<FactoryMethodDescriptor> deduplicatedMethodDescriptors =
        ImmutableSet.builder();

    for (FactoryMethodDescriptor methodDescriptor : methodDescriptors) {
      ImplementationMethodDescriptor duplicateMethodDescriptor =
          duplicateMethodDescriptors.get(methodDescriptor);

      FactoryMethodDescriptor newMethodDescriptor =
          (duplicateMethodDescriptor != null)
              ? methodDescriptor.toBuilder()
                  .overridingMethod(true)
                  .publicMethod(duplicateMethodDescriptor.publicMethod())
                  .returnType(duplicateMethodDescriptor.returnType())
                  .exceptions(duplicateMethodDescriptor.exceptions())
                  .build()
              : methodDescriptor;
      deduplicatedMethodDescriptors.add(newMethodDescriptor);
    }

    return deduplicatedMethodDescriptors.build();
  }

  /**
   * Returns true if {@code factory} and {@code implementation} have the same passed parameter types
   * in the same order.
   */
  private static boolean hasMatchingPassedParameters(
      FactoryMethodDescriptor factory, ImplementationMethodDescriptor implementation) {
    return Iterables.elementsEqual(
        Iterables.transform(factory.passedParameters(), Parameter::type),
        Iterables.transform(implementation.passedParameters(), Parameter::type));
  }

  /**
   * Builds the diagnostic used when an {@code implementing}/{@code extending} abstract method has
   * no factory {@code create} overload with the same parameter types. A common cause is a missing
   * {@code @Provided} on a constructor parameter, which silently turns that parameter into a
   * factory argument and leaves the interface method unmatched.
   */
  private static String unmatchedImplementationMethodMessage(
      ImplementationMethodDescriptor implementationMethod) {
    String parameters =
        implementationMethod.passedParameters().stream()
            .map(parameter -> parameter.type().get().toString())
            .collect(joining(", "));
    return "The "
        + implementationMethod.name()
        + "("
        + parameters
        + ") method from an interface or superclass does not match any factory method. Factory"
        + " methods take the constructor parameters that are not annotated @Provided. If a"
        + " parameter was meant to be injected, annotate it with @Provided; otherwise update the"
        + " interface method to match the factory parameters.";
  }
}
