/*
 * Copyright 2019 Google LLC
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

import java.util.List;
import javax.inject.Provider;

@AutoFactory
public class GenericFoo<A, B extends List<? extends A>, C, E extends Enum<E>> {

  private final A depA;
  private final B depB;
  private final IntAccessor depDIntAccessor;
  private final StringAccessor depDStringAccessor;
  private final E depE;

  <D extends IntAccessor & StringAccessor> GenericFoo(
      @Provided Provider<A> depA, B depB, D depD, E depE) {
    this.depA = depA.get();
    this.depB = depB;
    this.depDIntAccessor = depD;
    this.depDStringAccessor = depD;
    this.depE = depE;
  }

  public A getDepA() {
    return depA;
  }

  public B getDepB() {
    return depB;
  }

  public C passThrough(C value) {
    return value;
  }

  public IntAccessor getDepDIntAccessor() {
    return depDIntAccessor;
  }

  public StringAccessor getDepDStringAccessor() {
    return depDStringAccessor;
  }

  public E getDepE() {
    return depE;
  }

  public interface IntAccessor {}

  public interface StringAccessor {}

  public interface IntAndStringAccessor extends IntAccessor, StringAccessor {}

  public enum DepE {
    VALUE_1,
    VALUE_2
  }
}
