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
package com.google.auto.factory;

import javax.inject.Provider;

@AutoFactory(implementing = FactoryInterface.class)
public final class Foo {
  private final String name;
  private final Dependency dependency;
  private final Provider<Dependency> dependencyProvider;
  private final int primitive;
  private final int qualifiedPrimitive;

  Foo(
      String name,
      @Provided Dependency dependency,
      @Provided @Qualifier Provider<Dependency> dependencyProvider,
      @Provided int primitive,
      @Provided @Qualifier int qualifiedPrimitive) {
    this.name = name;
    this.dependency = dependency;
    this.dependencyProvider = dependencyProvider;
    this.primitive = primitive;
    this.qualifiedPrimitive = qualifiedPrimitive;
  }

  // Generates second factory method with a different name for the Dependency dependency.
  // Tests http://b/21632171.
  Foo(
      Object name,
      @Provided Dependency dependency2,
      @Provided @Qualifier Provider<Dependency> dependencyProvider,
      @Provided int primitive,
      @Provided @Qualifier int qualifiedPrimitive) {
    this(name.toString(), dependency2, dependencyProvider, primitive, qualifiedPrimitive);
  }

  String name() {
    return name;
  }

  Dependency dependency() {
    return dependency;
  }

  Provider<Dependency> dependencyProvider() {
    return dependencyProvider;
  }

  int primitive() {
    return primitive;
  }

  int qualifiedPrimitive() {
    return qualifiedPrimitive;
  }
}
