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
package com.google.auto.value.eclipse;

import com.google.auto.value.AutoValue;
import com.google.auto.value.nullness.Nullable;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;

/**
 * A class that exercises Eclipse's null analysis. In {@code CompileWithEclipseTest}, we configure
 * the compiler to use nullness annotations that are not Eclipse's own but custom alternatives
 * defined here. Eventually we will use the <a href="http://jspecify.org">JSpecify</a> annotations
 * when those are public.
 */
public final class EclipseNullable {
  @AutoValue
  abstract static class Nullables {
    abstract int primitiveInt();

    abstract Integer nonNullableInteger();

    abstract @Nullable Integer nullableInteger();

    abstract List<@Nullable Integer> listOfNullableIntegers();

    abstract Map.@Nullable Entry<String, Integer> nullableMapEntry();

    @SuppressWarnings("mutable")
    abstract int @Nullable [] nullableArrayOfInts();

    abstract ImmutableList<String> immutableListOfStrings();

    @AutoValue.Builder
    interface Builder {
      Builder setPrimitiveInt(int x);

      Builder setNonNullableInteger(Integer x);

      Builder setNullableInteger(@Nullable Integer x);

      Builder setListOfNullableIntegers(List<@Nullable Integer> x);

      Builder setNullableMapEntry(Map.@Nullable Entry<String, Integer> x);

      Builder setNullableArrayOfInts(int @Nullable [] x);

      Builder setImmutableListOfStrings(ImmutableList<String> x);

      ImmutableList.Builder<String> immutableListOfStringsBuilder();

      Nullables build();
    }
  }

  private EclipseNullable() {}
}
