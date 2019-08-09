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

/**
 * A class that is serializable with native Java serialization but not with GWT serialization.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@AutoValue
@GwtCompatible
abstract class NonSerializableGwtValueType implements Serializable {
  abstract String string();

  abstract int integer();

  static NonSerializableGwtValueType create(String string, int integer) {
    return new AutoValue_NonSerializableGwtValueType(string, integer);
  }
}
