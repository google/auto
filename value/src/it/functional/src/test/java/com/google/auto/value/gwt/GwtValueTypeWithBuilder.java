/*
 * Copyright 2015 Google LLC
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
package com.google.auto.value.gwt;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A class that is serializable with both Java and GWT serialization.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@AutoValue
@GwtCompatible(serializable = true)
abstract class GwtValueTypeWithBuilder<T> implements Serializable {
  abstract String string();

  abstract int integer();

  @Nullable
  abstract GwtValueTypeWithBuilder<T> other();

  abstract List<GwtValueTypeWithBuilder<T>> others();

  abstract ImmutableList<T> list();

  abstract ImmutableList<T> otherList();

  abstract ImmutableList<String> listWithBuilder();

  static <T> Builder<T> builder() {
    return new AutoValue_GwtValueTypeWithBuilder.Builder<T>();
  }

  @AutoValue.Builder
  interface Builder<T> {
    Builder<T> string(String x);

    Builder<T> integer(int x);

    Builder<T> other(@Nullable GwtValueTypeWithBuilder<T> x);

    Builder<T> others(List<GwtValueTypeWithBuilder<T>> x);

    Builder<T> list(ImmutableList<T> x);

    Builder<T> otherList(List<T> x);

    ImmutableList.Builder<String> listWithBuilderBuilder();

    GwtValueTypeWithBuilder<T> build();
  }
}
