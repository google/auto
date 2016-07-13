/*
 * Copyright (C) 2016 Google Inc.
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
package com.google.auto.value.mixedannotation;

import java.util.Map;
import com.google.auto.value.AutoValue;

/**
 * For testing that mixed annotations don't create duplicate annotations in the generated code.
 */
@AutoValue
public abstract class SimpleMixedUsingType {
  @Nullable public abstract String string();
  public static SimpleMixedUsingType create(
      @Nullable String string) {
    return new AutoValue_SimpleMixedUsingType(string);
  }
}
