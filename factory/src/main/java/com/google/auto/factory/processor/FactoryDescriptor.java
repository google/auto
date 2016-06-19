/*
 * Copyright (C) 2013 Google, Inc.
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

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Map.Entry;

import javax.lang.model.type.TypeMirror;

/**
 * A value object representing a factory to be generated.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class FactoryDescriptor {
  private static final CharMatcher invalidIdentifierCharacters =
      new CharMatcher() {
        @Override
        public boolean matches(char c) {
          return !Character.isJavaIdentifierPart(c);
        }
      };

  abstract String name();
  abstract TypeMirror extendingType();
  abstract ImmutableSet<TypeMirror> implementingTypes();
  abstract boolean publicType();
  abstract ImmutableSet<FactoryMethodDescriptor> methodDescriptors();
  abstract ImmutableSet<ImplementationMethodDescriptor> implementationMethodDescriptors();
  abstract boolean allowSubclasses();
  abstract ImmutableMap<Key, String> providerNames();

  static FactoryDescriptor create(
      String name,
      TypeMirror extendingType,
      ImmutableSet<TypeMirror> implementingTypes,
      boolean publicType,
      ImmutableSet<FactoryMethodDescriptor> methodDescriptors,
      ImmutableSet<ImplementationMethodDescriptor> implementationMethodDescriptors,
      boolean allowSubclasses) {
    ImmutableSetMultimap.Builder<Key, String> providerNamesBuilder = ImmutableSetMultimap.builder();
    for (FactoryMethodDescriptor descriptor : methodDescriptors) {
      for (Parameter parameter : descriptor.providedParameters()) {
        providerNamesBuilder.put(parameter.key(), parameter.name());
      }
    }
    ImmutableMap.Builder<Key, String> providersBuilder = ImmutableMap.builder();
    for (Entry<Key, Collection<String>> entry : providerNamesBuilder.build().asMap().entrySet()) {
      Key key = entry.getKey();
      switch (entry.getValue().size()) {
        case 0:
          throw new AssertionError();
        case 1:
          providersBuilder.put(key, Iterables.getOnlyElement(entry.getValue()) + "Provider");
          break;
        default:
          providersBuilder.put(
              key, invalidIdentifierCharacters.replaceFrom(key.toString(), '_') + "Provider");
          break;
      }
    }

    ImmutableBiMap<FactoryMethodDescriptor, ImplementationMethodDescriptor>
        duplicateMethodDescriptors =
            createDuplicateMethodDescriptorsBiMap(
                methodDescriptors, implementationMethodDescriptors);

    ImmutableSet<FactoryMethodDescriptor> deduplicatedMethodDescriptors =
        getDeduplicatedMethodDescriptors(methodDescriptors, duplicateMethodDescriptors);

    ImmutableSet<ImplementationMethodDescriptor> deduplicatedImplementationMethodDescriptors =
        ImmutableSet.copyOf(
            Sets.difference(implementationMethodDescriptors, duplicateMethodDescriptors.values()));

    return new AutoValue_FactoryDescriptor(
        name,
        extendingType,
        implementingTypes,
        publicType,
        deduplicatedMethodDescriptors,
        deduplicatedImplementationMethodDescriptors,
        allowSubclasses,
        providersBuilder.build());
  }

  /**
   * Creates a bi-map of duplicate {@link ImplementationMethodDescriptor}s by their respective
   * {@link FactoryMethodDescriptor}.
   */
  private static ImmutableBiMap<FactoryMethodDescriptor, ImplementationMethodDescriptor>
      createDuplicateMethodDescriptorsBiMap(
          ImmutableSet<FactoryMethodDescriptor> factoryMethodDescriptors,
          ImmutableSet<ImplementationMethodDescriptor> implementationMethodDescriptors) {

    ImmutableBiMap.Builder<FactoryMethodDescriptor, ImplementationMethodDescriptor> builder =
        ImmutableBiMap.builder();

    for (FactoryMethodDescriptor factoryMethodDescriptor : factoryMethodDescriptors) {
      for (ImplementationMethodDescriptor implementationMethodDescriptor :
          implementationMethodDescriptors) {

        boolean areDuplicateMethodDescriptors =
            areDuplicateMethodDescriptors(factoryMethodDescriptor, implementationMethodDescriptor);
        if (areDuplicateMethodDescriptors) {
          builder.put(factoryMethodDescriptor, implementationMethodDescriptor);
          break;
        }
      }
    }

    return builder.build();
  }

  /**
   * Returns a set of deduplicated {@link FactoryMethodDescriptor}s from the set of original
   * descriptors and the bi-map of duplicate descriptors.
   *
   * <p>Modifies the duplicate {@link FactoryMethodDescriptor}s such that they are overriding and
   * reflect properties from the {@link ImplementationMethodDescriptor} they are implementing.
   */
  private static ImmutableSet<FactoryMethodDescriptor> getDeduplicatedMethodDescriptors(
      ImmutableSet<FactoryMethodDescriptor> methodDescriptors,
      ImmutableBiMap<FactoryMethodDescriptor, ImplementationMethodDescriptor>
          duplicateMethodDescriptors) {

    ImmutableSet.Builder<FactoryMethodDescriptor> deduplicatedMethodDescriptors =
        ImmutableSet.builder();

    for (FactoryMethodDescriptor methodDescriptor : methodDescriptors) {
      ImplementationMethodDescriptor duplicateMethodDescriptor =
          duplicateMethodDescriptors.get(methodDescriptor);

      FactoryMethodDescriptor newMethodDescriptor =
         (duplicateMethodDescriptor != null)
              ? methodDescriptor
                  .toBuilder()
                  .overridingMethod(true)
                  .publicMethod(duplicateMethodDescriptor.publicMethod())
                  .returnType(duplicateMethodDescriptor.returnType())
                  .build()
              : methodDescriptor;
      deduplicatedMethodDescriptors.add(newMethodDescriptor);
    }

    return deduplicatedMethodDescriptors.build();
  }

  /**
   * Returns true if the given {@link FactoryMethodDescriptor} and
   * {@link ImplementationMethodDescriptor} are duplicates.
   *
   * <p>Descriptors are duplicates if they have the same name and if they have the same passed types
   * in the same order.
   */
  private static boolean areDuplicateMethodDescriptors(
      FactoryMethodDescriptor factory,
      ImplementationMethodDescriptor implementation) {

    if (!factory.name().equals(implementation.name())) {
      return false;
    }

    // Descriptors are identical if they have the same passed types in the same order.
    return MoreTypes.equivalence().pairwise().equivalent(
        Iterables.transform(factory.passedParameters(), Parameter.parameterToType),
        Iterables.transform(implementation.passedParameters(), Parameter.parameterToType));
  }
}
