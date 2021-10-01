/*
 * Copyright 2021 Google LLC
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
package tests;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

class Generics {
  interface Bar {}

  interface Foo<M extends Bar> {}

  interface FooFactory<M extends Bar> {
    Foo<M> create();
  }

  // The generated FooImplFactory should also have an <M extends Bar> type parameter, so we can
  // have FooImplFactory<M extends Bar> implements FooFactory<M>.
  @AutoFactory(implementing = FooFactory.class)
  static final class FooImpl<M extends Bar> implements Foo<M> {
    FooImpl() {}
  }

  // The generated ExplicitFooImplFactory should have an <M extends Bar> type parameter, which
  // serves both for FooFactory<M> and for Provider<M> in the constructor.
  @AutoFactory(implementing = FooFactory.class)
  static final class ExplicitFooImpl<M extends Bar> implements Foo<M> {
    ExplicitFooImpl(@Provided M unused) {}
  }

  abstract static class FooFactoryClass<M extends Bar> {
    abstract Foo<M> create();
  }

  @AutoFactory(extending = FooFactoryClass.class)
  static final class FooImplWithClass<M extends Bar> implements Foo<M> {}
}
