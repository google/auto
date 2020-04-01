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
package com.google.auto.value.extension.serializable.serializer.runtime;

import java.util.function.Function;

/** A utility for lambdas that throw exceptions. */
public final class FunctionWithExceptions {

  /** Creates a wrapper for lambdas that converts checked exceptions to runtime exceptions. */
  public static <I, O> Function<I, O> wrapper(FunctionWithException<I, O> fe) {
    return arg -> {
      try {
        return fe.apply(arg);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }

  /** A function that can throw an exception. */
  @FunctionalInterface
  public interface FunctionWithException<I, O> {
    O apply(I i) throws Exception;
  }

  private FunctionWithExceptions() {}
}
