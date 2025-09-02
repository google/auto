/*
 * Copyright 2014 Google LLC
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
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A class that is serializable with both Java and GWT serialization.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@AutoValue
@GwtCompatible(serializable = true)
abstract class GwtValueType implements Serializable {
  abstract String string();

  abstract int integer();

  @Nullable
  abstract GwtValueType other();

  abstract List<GwtValueType> others();

  static GwtValueType create(String string, int integer, @Nullable GwtValueType other) {
    return create(string, integer, other, Collections.<GwtValueType>emptyList());
  }

  static GwtValueType create(
      String string, int integer, @Nullable GwtValueType other, List<GwtValueType> others) {
    return new AutoValue_GwtValueType(string, integer, other, others);
  }
}
