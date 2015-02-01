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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map.Entry;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

/**
 * A value object representing a factory to be generated.
 *
 * @author Gregory Kick
 */
final class FactoryDescriptor {
  private static final CharMatcher identifierMatcher = new CharMatcher() {
    @Override
    public boolean matches(char c) {
      return Character.isJavaIdentifierPart(c);
    }
  };

  private final String name;
  private final String extendingType;
  private final ImmutableSortedSet<String> implementingTypes;
  private final boolean publicType;
  private final ImmutableSet<FactoryMethodDescriptor> methodDescriptors;
  private final ImmutableSet<ImplementationMethodDescriptor> implementationMethodDescriptors;
  private final ImmutableMap<Key, String> providerNames;

  FactoryDescriptor(String name, String extendingType, ImmutableSortedSet<String> implementingTypes,
      boolean publicType, ImmutableSet<FactoryMethodDescriptor> methodDescriptors,
      ImmutableSet<ImplementationMethodDescriptor> implementationMethodDescriptors) {
    this.name = checkNotNull(name);
    this.extendingType = checkNotNull(extendingType);
    this.implementingTypes = checkNotNull(implementingTypes);
    this.publicType = publicType;
    this.methodDescriptors = checkNotNull(methodDescriptors);
    this.implementationMethodDescriptors = checkNotNull(implementationMethodDescriptors);
    ImmutableSetMultimap.Builder<Key, String> providerNamesBuilder = ImmutableSetMultimap.builder();
    for (FactoryMethodDescriptor descriptor : methodDescriptors) {
      for (Parameter parameter : descriptor.providedParameters()) {
        providerNamesBuilder.putAll(parameter.asKey(), parameter.name());
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
          providersBuilder.put(key,
              identifierMatcher.replaceFrom(key.toString(), '_') + "Provider");
          break;
      }
    }
    this.providerNames = providersBuilder.build();
  }

  String name() {
    return name;
  }

  String extendingType() {
    return extendingType;
  }

  ImmutableSortedSet<String> implementingTypes() {
    return implementingTypes;
  }

  boolean publicType() {
    return publicType;
  }

  ImmutableSet<FactoryMethodDescriptor> methodDescriptors() {
    return methodDescriptors;
  }

  ImmutableSet<ImplementationMethodDescriptor> implementationMethodDescriptors() {
    return implementationMethodDescriptors;
  }

  ImmutableMap<Key, String> providerNames() {
    return providerNames;
  }
}
