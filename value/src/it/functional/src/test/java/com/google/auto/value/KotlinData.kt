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
  val aString: String = "skidoo"
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
  val optionalString: String = "Skidoo"
)

// CharSequence is an interface so the parameter appears from Java as List<? extends CharSequence>,
// but getList() appears as returning List<CharSequence>.
data class KotlinDataWithList(val list: List<CharSequence>, val number: Int)
