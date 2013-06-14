package com.google.autofactory;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map.Entry;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;

final class FactoryDescriptor {
  private static final CharMatcher identifierMatcher = new CharMatcher() {
    @Override
    public boolean matches(char c) {
      return Character.isJavaIdentifierPart(c);
    }
  };

  private final String name;
  private final ImmutableSet<FactoryMethodDescriptor> methodDescriptors;
  private final ImmutableMap<Key, String> providerNames;

  FactoryDescriptor(String name, ImmutableSet<FactoryMethodDescriptor> methodDescriptors) {
    this.name = checkNotNull(name);
    this.methodDescriptors = checkNotNull(methodDescriptors);
    ImmutableSetMultimap.Builder<Key, String> builder = ImmutableSetMultimap.builder();
    for (FactoryMethodDescriptor descriptor : methodDescriptors) {
      for (Parameter parameter : descriptor.providedParameters()) {
        builder.putAll(parameter.asKey(), parameter.name());
      }
    }
    ImmutableMap.Builder<Key, String> providersBuilder = ImmutableMap.builder();
    for (Entry<Key, Collection<String>> entry : builder.build().asMap().entrySet()) {
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

  ImmutableSet<FactoryMethodDescriptor> methodDescriptors() {
    return methodDescriptors;
  }

  ImmutableMap<Key, String> providerNames() {
    return providerNames;
  }
}
