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

package com.google.auto.value.extension.toprettystring.processor;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashSet;
import java.util.stream.Collector;

final class ToPrettyStringCollectors {
  static <E> Collector<E, ?, ImmutableList<E>> toImmutableList() {
    return collectingAndThen(toList(), ImmutableList::copyOf);
  }

  static <E> Collector<E, ?, ImmutableSet<E>> toImmutableSet() {
    return collectingAndThen(toCollection(LinkedHashSet::new), ImmutableSet::copyOf);
  }

  private ToPrettyStringCollectors() {}
}
