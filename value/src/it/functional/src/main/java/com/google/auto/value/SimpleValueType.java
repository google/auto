/*
 * Copyright 2012 Google LLC
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
package com.google.auto.value;

import java.util.Map;
import javax.annotation.Nullable;

/**
 * Simple value type for tests.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@AutoValue
public abstract class SimpleValueType {
  // The getters here are formatted as an illustration of what getters typically look in real
  // classes. In particular they have doc comments.

  /** Returns a string that is a nullable string. */
  @Nullable
  public abstract String string();

  /** Returns an integer that is an integer. */
  public abstract int integer();

  /** Returns a non-null map where the keys are strings and the values are longs. */
  public abstract Map<String, Long> map();

  public static SimpleValueType create(
      @Nullable String string, int integer, Map<String, Long> map) {
    // The subclass AutoValue_SimpleValueType is created by the annotation processor that is
    // triggered by the presence of the @AutoValue annotation. It has a constructor for each
    // of the abstract getter methods here, in order. The constructor stashes the values here
    // in private final fields, and each method is implemented to return the value of the
    // corresponding field.
    return new AutoValue_SimpleValueType(string, integer, map);
  }
}
