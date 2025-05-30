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

import com.google.auto.value.AutoValue;
import java.util.Map;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
@SuppressWarnings("DefaultPackage")
public class PackagelessNestedValueType {
  @AutoValue
  public abstract static class Nested {
    abstract Map<Integer, String> numberNames();

    public static Nested create(Map<Integer, String> numberNames) {
      return new AutoValue_PackagelessNestedValueType_Nested(numberNames);
    }
  }
}
