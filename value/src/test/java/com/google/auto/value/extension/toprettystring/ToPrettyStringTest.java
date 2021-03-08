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

package com.google.auto.value.extension.toprettystring;

import static com.google.common.truth.Truth.assertThat;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.toprettystring.ToPrettyStringTest.CollectionSubtypesWithFixedTypeParameters.StringList;
import com.google.auto.value.extension.toprettystring.ToPrettyStringTest.CollectionSubtypesWithFixedTypeParameters.StringMap;
import com.google.auto.value.extension.toprettystring.ToPrettyStringTest.PropertyHasToPrettyString.HasToPrettyString;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.primitives.ImmutableIntArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@SuppressWarnings("AutoValueImmutableFields")
@RunWith(JUnit4.class)
public class ToPrettyStringTest {
  @AutoValue
  abstract static class Primitives {
    abstract int i();

    abstract long l();

    abstract byte b();

    abstract short s();

    abstract char c();

    abstract float f();

    abstract double d();

    abstract boolean bool();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void primitives() {
    Primitives valueType =
        new AutoValue_ToPrettyStringTest_Primitives(
            1, 2L, (byte) 3, (short) 4, 'C', 6.6f, 7.7, false);

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "Primitives {"
                + "\n  i = 1,"
                + "\n  l = 2,"
                + "\n  b = 3,"
                + "\n  s = 4,"
                + "\n  c = C,"
                + "\n  f = 6.6,"
                + "\n  d = 7.7,"
                + "\n  bool = false,"
                + "\n}");
  }

  @AutoValue
  abstract static class PrimitiveArray {
    @Nullable
    @SuppressWarnings("mutable")
    abstract long[] longs();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void primitiveArray() {
    PrimitiveArray valueType =
        new AutoValue_ToPrettyStringTest_PrimitiveArray(new long[] {1L, 2L, 10L, 200L});

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrimitiveArray {"
                + "\n  longs = ["
                + "\n    1,"
                + "\n    2,"
                + "\n    10,"
                + "\n    200,"
                + "\n  ],"
                + "\n}");
  }

  @Test
  public void primitiveArray_empty() {
    PrimitiveArray valueType = new AutoValue_ToPrettyStringTest_PrimitiveArray(new long[0]);

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrimitiveArray {" // force newline
                + "\n  longs = [],"
                + "\n}");
  }

  @Test
  public void primitiveArray_null() {
    PrimitiveArray valueType = new AutoValue_ToPrettyStringTest_PrimitiveArray(null);

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrimitiveArray {" // force newline
                + "\n  longs = null,"
                + "\n}");
  }

  @AutoValue
  abstract static class PrettyCollection {
    @Nullable
    abstract Collection<Object> collection();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void prettyCollection() {
    PrettyCollection valueType =
        new AutoValue_ToPrettyStringTest_PrettyCollection(ImmutableList.of("hello", "world"));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrettyCollection {"
                + "\n  collection = ["
                + "\n    hello,"
                + "\n    world,"
                + "\n  ],"
                + "\n}");
  }

  @Test
  public void prettyCollection_elementsWithNewlines() {
    PrettyCollection valueType =
        new AutoValue_ToPrettyStringTest_PrettyCollection(
            ImmutableList.of("hello\nworld\nnewline"));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrettyCollection {"
                + "\n  collection = ["
                + "\n    hello"
                + "\n    world"
                + "\n    newline,"
                + "\n  ],"
                + "\n}");
  }

  @Test
  public void prettyCollection_empty() {
    PrettyCollection valueType =
        new AutoValue_ToPrettyStringTest_PrettyCollection(ImmutableList.of());

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrettyCollection {" // force newline
                + "\n  collection = [],"
                + "\n}");
  }

  @Test
  public void prettyCollection_null() {
    PrettyCollection valueType = new AutoValue_ToPrettyStringTest_PrettyCollection(null);

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrettyCollection {" // force newline
                + "\n  collection = null,"
                + "\n}");
  }

  @AutoValue
  abstract static class NestedCollection {
    @Nullable
    abstract Collection<Collection<Object>> nestedCollection();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void nestedCollection() {
    NestedCollection valueType =
        new AutoValue_ToPrettyStringTest_NestedCollection(
            Arrays.asList(
                ImmutableList.of("hello", "world"),
                ImmutableList.of("hello2", "world2"),
                null,
                Arrays.asList("not null", null)));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "NestedCollection {"
                + "\n  nestedCollection = ["
                + "\n    ["
                + "\n      hello,"
                + "\n      world,"
                + "\n    ],"
                + "\n    ["
                + "\n      hello2,"
                + "\n      world2,"
                + "\n    ],"
                + "\n    null,"
                + "\n    ["
                + "\n      not null,"
                + "\n      null,"
                + "\n    ],"
                + "\n  ],"
                + "\n}");
  }

  @Test
  public void nestedCollection_elementsWithNewlines() {
    NestedCollection valueType =
        new AutoValue_ToPrettyStringTest_NestedCollection(
            ImmutableList.of(
                ImmutableList.of((Object) "hello\nworld\nnewline", "hello2\nworld2\nnewline2")));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "NestedCollection {"
                + "\n  nestedCollection = ["
                + "\n    ["
                + "\n      hello"
                + "\n      world"
                + "\n      newline,"
                + "\n      hello2"
                + "\n      world2"
                + "\n      newline2,"
                + "\n    ],"
                + "\n  ],"
                + "\n}");
  }

  @Test
  public void nestedCollection_empty() {
    NestedCollection valueType =
        new AutoValue_ToPrettyStringTest_NestedCollection(ImmutableList.of());

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "NestedCollection {" // force newline
                + "\n  nestedCollection = [],"
                + "\n}");
  }

  @Test
  public void nestedCollection_nestedEmpty() {
    NestedCollection valueType =
        new AutoValue_ToPrettyStringTest_NestedCollection(
            ImmutableList.of(ImmutableList.of(), ImmutableList.of()));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "NestedCollection {"
                + "\n  nestedCollection = ["
                + "\n    [],"
                + "\n    [],"
                + "\n  ],"
                + "\n}");
  }

  @Test
  public void nestedCollection_null() {
    NestedCollection valueType = new AutoValue_ToPrettyStringTest_NestedCollection(null);

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "NestedCollection {" // force newline
                + "\n  nestedCollection = null,"
                + "\n}");
  }

  @AutoValue
  abstract static class ImmutablePrimitiveArray {
    @Nullable
    abstract ImmutableIntArray immutableIntArray();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void immutablePrimitiveArray() {
    ImmutablePrimitiveArray valueType =
        new AutoValue_ToPrettyStringTest_ImmutablePrimitiveArray(ImmutableIntArray.of(1, 2));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "ImmutablePrimitiveArray {"
                + "\n  immutableIntArray = ["
                + "\n    1,"
                + "\n    2,"
                + "\n  ],"
                + "\n}");
  }

  @Test
  public void immutablePrimitiveArray_empty() {
    ImmutablePrimitiveArray valueType =
        new AutoValue_ToPrettyStringTest_ImmutablePrimitiveArray(ImmutableIntArray.of());

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "ImmutablePrimitiveArray {" // force newline
                + "\n  immutableIntArray = [],"
                + "\n}");
  }

  @Test
  public void immutablePrimitiveArray_null() {
    ImmutablePrimitiveArray valueType =
        new AutoValue_ToPrettyStringTest_ImmutablePrimitiveArray(null);

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "ImmutablePrimitiveArray {" // force newline
                + "\n  immutableIntArray = null,"
                + "\n}");
  }

  @AutoValue
  abstract static class PrettyMap {
    @Nullable
    abstract Map<Object, Object> map();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void prettyMap() {
    PrettyMap valueType = new AutoValue_ToPrettyStringTest_PrettyMap(ImmutableMap.of(1, 2));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrettyMap {" // force newline
                + "\n  map = {"
                + "\n    1: 2,"
                + "\n  },"
                + "\n}");
  }

  @Test
  public void prettyMap_keysAndValuesWithNewlines() {
    PrettyMap valueType =
        new AutoValue_ToPrettyStringTest_PrettyMap(
            ImmutableMap.of(
                "key1\nnewline", "value1\nnewline", "key2\nnewline", "value2\nnewline"));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrettyMap {"
                + "\n  map = {"
                + "\n    key1"
                + "\n    newline: value1"
                + "\n    newline,"
                + "\n    key2"
                + "\n    newline: value2"
                + "\n    newline,"
                + "\n  },"
                + "\n}");
  }

  @Test
  public void prettyMap_empty() {
    PrettyMap valueType = new AutoValue_ToPrettyStringTest_PrettyMap(ImmutableMap.of());

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrettyMap {" // force newline
                + "\n  map = {},"
                + "\n}");
  }

  @Test
  public void prettyMap_null() {
    PrettyMap valueType = new AutoValue_ToPrettyStringTest_PrettyMap(null);

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrettyMap {" // force newline
                + "\n  map = null,"
                + "\n}");
  }

  @AutoValue
  abstract static class MapOfMaps {
    @Nullable
    abstract Map<Map<Object, Object>, Map<Object, Object>> mapOfMaps();

    @ToPrettyString
    abstract String toPrettyString();
  }

  private static <K, V> Map<K, V> mapWithNulls(K k, V v) {
    Map<K, V> map = new LinkedHashMap<>();
    map.put(k, v);
    return map;
  }

  @Test
  public void mapOfMaps() {
    Map<Map<Object, Object>, Map<Object, Object>> mapOfMaps = new LinkedHashMap<>();
    mapOfMaps.put(ImmutableMap.of("k1_k", "k1_v"), ImmutableMap.of("v1_k", "v1_v"));
    mapOfMaps.put(ImmutableMap.of("k2_k", "k2_v"), ImmutableMap.of("v2_k", "v2_v"));
    mapOfMaps.put(mapWithNulls("keyForNullValue", null), mapWithNulls(null, "valueForNullKey"));
    mapOfMaps.put(null, ImmutableMap.of("nullKeyKey", "nullKeyValue"));
    mapOfMaps.put(ImmutableMap.of("nullValueKey", "nullValueValue"), null);
    mapOfMaps.put(
        ImmutableMap.of("keyForMapOfNullsKey", "keyForMapOfNullsValue"), mapWithNulls(null, null));
    MapOfMaps valueType = new AutoValue_ToPrettyStringTest_MapOfMaps(mapOfMaps);
    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "MapOfMaps {"
                + "\n  mapOfMaps = {"
                + "\n    {"
                + "\n      k1_k: k1_v,"
                + "\n    }: {"
                + "\n      v1_k: v1_v,"
                + "\n    },"
                + "\n    {"
                + "\n      k2_k: k2_v,"
                + "\n    }: {"
                + "\n      v2_k: v2_v,"
                + "\n    },"
                + "\n    {"
                + "\n      keyForNullValue: null,"
                + "\n    }: {"
                + "\n      null: valueForNullKey,"
                + "\n    },"
                + "\n    null: {"
                + "\n      nullKeyKey: nullKeyValue,"
                + "\n    },"
                + "\n    {"
                + "\n      nullValueKey: nullValueValue,"
                + "\n    }: null,"
                + "\n    {"
                + "\n      keyForMapOfNullsKey: keyForMapOfNullsValue,"
                + "\n    }: {"
                + "\n      null: null,"
                + "\n    },"
                + "\n  },"
                + "\n}");
  }

  @Test
  public void mapOfMaps_elementsWithNewlines() {
    MapOfMaps valueType =
        new AutoValue_ToPrettyStringTest_MapOfMaps(
            ImmutableMap.of(
                ImmutableMap.of((Object) "k_k\nnewline", (Object) "k_v\nnewline"),
                ImmutableMap.of((Object) "v_k\nnewline", (Object) "v_v\nnewline")));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "MapOfMaps {"
                + "\n  mapOfMaps = {"
                + "\n    {"
                + "\n      k_k"
                + "\n      newline: k_v"
                + "\n      newline,"
                + "\n    }: {"
                + "\n      v_k"
                + "\n      newline: v_v"
                + "\n      newline,"
                + "\n    },"
                + "\n  },"
                + "\n}");
  }

  @Test
  public void mapOfMaps_empty() {
    MapOfMaps valueType = new AutoValue_ToPrettyStringTest_MapOfMaps(ImmutableMap.of());

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "MapOfMaps {" // force newline
                + "\n  mapOfMaps = {},"
                + "\n}");
  }

  @Test
  public void mapOfMaps_nestedEmpty() {
    MapOfMaps valueType =
        new AutoValue_ToPrettyStringTest_MapOfMaps(
            ImmutableMap.of(ImmutableMap.of(), ImmutableMap.of()));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "MapOfMaps {" // force newline
                + "\n  mapOfMaps = {"
                + "\n    {}: {},"
                + "\n  },"
                + "\n}");
  }

  @Test
  public void mapOfMaps_null() {
    MapOfMaps valueType = new AutoValue_ToPrettyStringTest_MapOfMaps(null);

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "MapOfMaps {" // force newline
                + "\n  mapOfMaps = null,"
                + "\n}");
  }

  @AutoValue
  abstract static class PrettyMultimap {
    @Nullable
    abstract Multimap<Object, Object> multimap();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void prettyMultimap() {
    PrettyMultimap valueType =
        new AutoValue_ToPrettyStringTest_PrettyMultimap(
            ImmutableMultimap.builder().putAll("k", "v1", "v2").build());

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrettyMultimap {" // force newline
                + "\n  multimap = {"
                + "\n    k: ["
                + "\n      v1,"
                + "\n      v2,"
                + "\n    ],"
                + "\n  },"
                + "\n}");
  }

  @Test
  public void prettyMultimap_keysAndValuesWithNewlines() {
    PrettyMultimap valueType =
        new AutoValue_ToPrettyStringTest_PrettyMultimap(
            ImmutableMultimap.builder()
                .putAll("key\nnewline", "value1\nnewline", "value2\nnewline")
                .build());

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrettyMultimap {"
                + "\n  multimap = {"
                + "\n    key"
                + "\n    newline: ["
                + "\n      value1"
                + "\n      newline,"
                + "\n      value2"
                + "\n      newline,"
                + "\n    ],"
                + "\n  },"
                + "\n}");
  }

  @Test
  public void prettyMultimap_empty() {
    PrettyMultimap valueType =
        new AutoValue_ToPrettyStringTest_PrettyMultimap(ImmutableMultimap.of());

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrettyMultimap {" // force newline
                + "\n  multimap = {},"
                + "\n}");
  }

  @Test
  public void prettyMultimap_null() {
    PrettyMultimap valueType = new AutoValue_ToPrettyStringTest_PrettyMultimap(null);

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PrettyMultimap {" // force newline
                + "\n  multimap = null,"
                + "\n}");
  }

  @AutoValue
  abstract static class JavaOptional {
    @Nullable
    abstract java.util.Optional<Object> optional();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void javaOptional_present() {
    JavaOptional valueType =
        new AutoValue_ToPrettyStringTest_JavaOptional(java.util.Optional.of("hello, world"));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "JavaOptional {" // force newline
                + "\n  optional = hello, world,"
                + "\n}");
  }

  @Test
  public void javaOptional_empty() {
    JavaOptional valueType =
        new AutoValue_ToPrettyStringTest_JavaOptional(java.util.Optional.empty());

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "JavaOptional {" // force newline
                + "\n  optional = <empty>,"
                + "\n}");
  }

  @Test
  public void javaOptional_valueWithNewlines() {
    JavaOptional valueType =
        new AutoValue_ToPrettyStringTest_JavaOptional(
            java.util.Optional.of("optional\nwith\nnewline"));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "JavaOptional {" // force newline
                + "\n  optional = optional"
                + "\n  with"
                + "\n  newline,"
                + "\n}");
  }

  @Test
  public void javaOptional_null() {
    @SuppressWarnings("NullOptional")
    JavaOptional valueType = new AutoValue_ToPrettyStringTest_JavaOptional(null);

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "JavaOptional {" // force newline
                + "\n  optional = null,"
                + "\n}");
  }

  @AutoValue
  abstract static class GuavaOptional {
    @Nullable
    abstract com.google.common.base.Optional<Object> optional();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void guavaOptional_present() {
    GuavaOptional valueType =
        new AutoValue_ToPrettyStringTest_GuavaOptional(
            com.google.common.base.Optional.of("hello, world"));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "GuavaOptional {" // force newline
                + "\n  optional = hello, world,"
                + "\n}");
  }

  @Test
  public void guavaOptional_absent() {
    GuavaOptional valueType =
        new AutoValue_ToPrettyStringTest_GuavaOptional(com.google.common.base.Optional.absent());

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "GuavaOptional {" // force newline
                + "\n  optional = <absent>,"
                + "\n}");
  }

  @Test
  public void guavaOptional_valueWithNewlines() {
    GuavaOptional valueType =
        new AutoValue_ToPrettyStringTest_GuavaOptional(
            com.google.common.base.Optional.of("optional\nwith\nnewline"));

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "GuavaOptional {" // force newline
                + "\n  optional = optional"
                + "\n  with"
                + "\n  newline,"
                + "\n}");
  }

  @Test
  public void guavaOptional_null() {
    @SuppressWarnings("NullOptional")
    GuavaOptional valueType = new AutoValue_ToPrettyStringTest_GuavaOptional(null);

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "GuavaOptional {" // force newline
                + "\n  optional = null,"
                + "\n}");
  }

  @AutoValue
  abstract static class NestAllTheThings {
    @Nullable
    abstract com.google.common.base.Optional<
            java.util.Optional<
                List< // open list
                    Map<ImmutableIntArray, Multimap<int[][], Object>>
                // close list
                >>>
        value();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void nestAllTheThings() {
    NestAllTheThings valueType =
        new AutoValue_ToPrettyStringTest_NestAllTheThings(
            com.google.common.base.Optional.of(
                java.util.Optional.of(
                    ImmutableList.of(
                        ImmutableMap.of(
                            ImmutableIntArray.of(-1, -2, -3),
                            ImmutableMultimap.of(
                                new int[][] {{1, 2}, {3, 4, 5}, {}}, "value\nwith\nnewline"))))));
    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "NestAllTheThings {"
                + "\n  value = ["
                + "\n    {"
                + "\n      ["
                + "\n        -1,"
                + "\n        -2,"
                + "\n        -3,"
                + "\n      ]: {"
                + "\n        ["
                + "\n          ["
                + "\n            1,"
                + "\n            2,"
                + "\n          ],"
                + "\n          ["
                + "\n            3,"
                + "\n            4,"
                + "\n            5,"
                + "\n          ],"
                + "\n          [],"
                + "\n        ]: ["
                + "\n          value"
                + "\n          with"
                + "\n          newline,"
                + "\n        ],"
                + "\n      },"
                + "\n    },"
                + "\n  ],"
                + "\n}");
  }

  @AutoValue
  abstract static class WithCustomName {
    abstract int i();

    @ToPrettyString
    abstract String customName();
  }

  @Test
  public void withCustomName() {
    WithCustomName valueType = new AutoValue_ToPrettyStringTest_WithCustomName(1);

    assertThat(valueType.customName())
        .isEqualTo(
            "WithCustomName {" // force newline
                + "\n  i = 1,"
                + "\n}");
  }

  @AutoValue
  abstract static class OverridesToString {
    abstract int i();

    @ToPrettyString
    @Override
    public abstract String toString();
  }

  @Test
  public void overridesToString() {
    OverridesToString valueType = new AutoValue_ToPrettyStringTest_OverridesToString(1);

    assertThat(valueType.toString())
        .isEqualTo(
            "OverridesToString {" // force newline
                + "\n  i = 1,"
                + "\n}");
  }

  @AutoValue
  abstract static class PropertyHasToPrettyString {
    static class HasToPrettyString<A> {
      @Override
      public String toString() {
        throw new AssertionError();
      }

      @ToPrettyString
      String toPrettyString() {
        return "custom\n@ToPrettyString\nmethod";
      }
    }

    static class HasInheritedToPrettyString extends HasToPrettyString<String> {}

    interface HasToPrettyStringInInterface {
      @ToPrettyString
      default String toPrettyString() {
        return "custom\n@ToPrettyString\nmethod\ninterface";
      }
    }

    static class HasToPrettyStringFromSuperInterface implements HasToPrettyStringInInterface {}

    abstract HasToPrettyString<String> parameterizedWithString();

    abstract HasToPrettyString<Void> parameterizedWithVoid();

    abstract HasInheritedToPrettyString superclass();

    abstract HasToPrettyStringFromSuperInterface superinterface();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void propertyHasToPrettyString() {
    PropertyHasToPrettyString valueType =
        new AutoValue_ToPrettyStringTest_PropertyHasToPrettyString(
            new PropertyHasToPrettyString.HasToPrettyString<>(),
            new PropertyHasToPrettyString.HasToPrettyString<>(),
            new PropertyHasToPrettyString.HasInheritedToPrettyString(),
            new PropertyHasToPrettyString.HasToPrettyStringFromSuperInterface());

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "PropertyHasToPrettyString {"
                + "\n  parameterizedWithString = custom"
                + "\n  @ToPrettyString"
                + "\n  method,"
                + "\n  parameterizedWithVoid = custom"
                + "\n  @ToPrettyString"
                + "\n  method,"
                + "\n  superclass = custom"
                + "\n  @ToPrettyString"
                + "\n  method,"
                + "\n  superinterface = custom"
                + "\n  @ToPrettyString"
                + "\n  method"
                + "\n  interface,"
                + "\n}");
  }

  @AutoValue
  abstract static class CollectionSubtypesWithFixedTypeParameters {
    static class StringList extends ArrayList<String> {}

    static class StringMap extends LinkedHashMap<String, String> {}

    abstract StringList list();

    abstract StringMap map();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void fixedTypeParameters() {
    StringList stringList = new StringList();
    stringList.addAll(ImmutableList.of("a", "b", "c"));
    StringMap stringMap = new StringMap();
    stringMap.putAll(ImmutableMap.of("A", "a", "B", "b"));
    CollectionSubtypesWithFixedTypeParameters valueType =
        new AutoValue_ToPrettyStringTest_CollectionSubtypesWithFixedTypeParameters(
            stringList, stringMap);

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "CollectionSubtypesWithFixedTypeParameters {"
                + "\n  list = ["
                + "\n    a,"
                + "\n    b,"
                + "\n    c,"
                + "\n  ],"
                + "\n  map = {"
                + "\n    A: a,"
                + "\n    B: b,"
                + "\n  },"
                + "\n}");
  }

  @AutoValue
  abstract static class JavaBeans {
    abstract int getInt();

    abstract boolean isBoolean();

    abstract String getNotAJavaIdentifier();

    @ToPrettyString
    abstract String toPrettyString();
  }

  @Test
  public void javaBeans() {
    JavaBeans valueType = new AutoValue_ToPrettyStringTest_JavaBeans(4, false, "not");

    assertThat(valueType.toPrettyString())
        .isEqualTo(
            "JavaBeans {"
                + "\n  int = 4,"
                + "\n  boolean = false,"
                + "\n  notAJavaIdentifier = not,"
                + "\n}");

    // Check to make sure that we use the same property names that AutoValue does. This is mostly
    // defensive, since in some scenarios AutoValue considers the property names of a java bean as
    // having the prefix removed.
    assertThat(valueType.toString())
        .isEqualTo("JavaBeans{int=4, boolean=false, notAJavaIdentifier=not}");
  }
}
