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
package com.google.auto.value

import com.google.common.collect.ImmutableList

data class KotlinData(val int: Int, val string: String)

data class KotlinDataWithNullable(val anInt: Int?, val aString: String?)

data class KotlinDataWithDefaults(
  val anInt: Int = 23,
  val anImmutableList: ImmutableList<String> = ImmutableList.of("foo"),
  val notDefaulted: Long,
  val aString: String = "skidoo",
)

// Exactly 8 defaulted properties, in case we have a problem with sign-extending byte bitmasks.
data class KotlinDataEightDefaults(
  val a1: Int = 1,
  val a2: Int = 2,
  val a3: Int = 3,
  val a4: Int = 4,
  val a5: Int = 5,
  val a6: Int = 6,
  val a7: Int = 7,
  val a8: Int = 8,
)

data class KotlinDataSomeDefaults(
  val requiredInt: Int,
  val requiredString: String,
  val optionalInt: Int = 23,
  val optionalString: String = "Skidoo",
)

/**
 * Class with 2 required properties and 31 optional ones. This validates that we use the total count
 * of properties to compute how many default-value bitmasks the Kotlin constructor has. Using just
 * the number of optional properties would be wrong, and would show up as passing only one `int`
 * bitmask instead of two.
 */
data class KotlinDataSomeDefaultsBig(
  val requiredInt: Int,
  val requiredString: String,
  val a1: Int = 1,
  val a2: Int = 2,
  val a3: Int = 3,
  val a4: Int = 4,
  val a5: Int = 5,
  val a6: Int = 6,
  val a7: Int = 7,
  val a8: Int = 8,
  val a9: Int = 9,
  val a10: Int = 10,
  val a11: Int = 11,
  val a12: Int = 12,
  val a13: Int = 13,
  val a14: Int = 14,
  val a15: Int = 15,
  val a16: Int = 16,
  val a17: Int = 17,
  val a18: Int = 18,
  val a19: Int = 19,
  val a20: Int = 20,
  val a21: Int = 21,
  val a22: Int = 22,
  val a23: Int = 23,
  val a24: Int = 24,
  val a25: Int = 25,
  val a26: Int = 26,
  val a27: Int = 27,
  val a28: Int = 28,
  val a29: Int = 29,
  val a30: Int = 30,
  val a31: Int = 31,
)

// CharSequence is an interface so the parameter appears from Java as List<? extends CharSequence>,
// but getList() appears as returning List<CharSequence>.
data class KotlinDataWithList(val list: List<CharSequence>, val number: Int)
