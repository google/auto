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

package com.google.auto.common;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * A utility class that provides Android compatible alternatives to Guava's streaming APIs.
 *
 * <p>This is useful when the Android flavor of Guava somehow finds its way onto the processor
 * classpath.
 */
public final class MoreStreams {

  /** Returns a collector for an {@link ImmutableList}. */
  public static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
    return collectingAndThen(toList(), ImmutableList::copyOf);
  }

  /** Returns a collector for an {@link ImmutableSet}. */
  public static <T> Collector<T, ?, ImmutableSet<T>> toImmutableSet() {
    return collectingAndThen(toList(), ImmutableSet::copyOf);
  }

  /** Returns a collector for an {@link ImmutableMap}. */
  public static <T, K, V> Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
      Function<? super T, K> keyMapper, Function<? super T, V> valueMapper) {
    return Collectors.mapping(
        value -> Maps.immutableEntry(keyMapper.apply(value), valueMapper.apply(value)),
        Collector.of(
            ImmutableMap::builder,
            (ImmutableMap.Builder<K, V> builder, Map.Entry<K, V> entry) -> builder.put(entry),
            (left, right) -> left.putAll(right.build()),
            ImmutableMap.Builder::build));
  }

  /** Returns a collector for an {@link ImmutableBiMap}. */
  public static <T, K, V> Collector<T, ?, ImmutableBiMap<K, V>> toImmutableBiMap(
      Function<? super T, K> keyMapper, Function<? super T, V> valueMapper) {
    return Collectors.mapping(
        value -> Maps.immutableEntry(keyMapper.apply(value), valueMapper.apply(value)),
        Collector.of(
            ImmutableBiMap::builder,
            (ImmutableBiMap.Builder<K, V> builder, Map.Entry<K, V> entry) -> builder.put(entry),
            (left, right) -> left.putAll(right.build()),
            ImmutableBiMap.Builder::build));
  }

  private MoreStreams() {}
}
