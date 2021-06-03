/*
 * Copyright 2018 Google LLC
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
package com.google.auto.value.processor;

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Expect;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PropertyNamesTest {
  @Rule public Expect expect = Expect.create();

  private static final ImmutableMap<String, String> NORMAL_CASES =
      ImmutableMap.<String, String>builder()
          .put("Foo", "foo")
          .put("foo", "foo")
          .put("X", "x")
          .put("x", "x")
          .put("", "")
          .build();

  @Test
  public void decapitalizeLikeJavaBeans() {
    NORMAL_CASES.forEach(
        (input, output) ->
            expect.that(PropertyNames.decapitalizeLikeJavaBeans(input)).isEqualTo(output));
    expect.that(PropertyNames.decapitalizeLikeJavaBeans(null)).isNull();
    expect.that(PropertyNames.decapitalizeLikeJavaBeans("HTMLPage")).isEqualTo("HTMLPage");
    expect.that(PropertyNames.decapitalizeLikeJavaBeans("OAuth")).isEqualTo("OAuth");
  }

  @Test
  public void decapitalizeNormally() {
    NORMAL_CASES.forEach(
        (input, output) ->
            expect.that(PropertyNames.decapitalizeNormally(input)).isEqualTo(output));
    expect.that(PropertyNames.decapitalizeNormally(null)).isNull();
    expect.that(PropertyNames.decapitalizeNormally("HTMLPage")).isEqualTo("hTMLPage");
    expect.that(PropertyNames.decapitalizeNormally("OAuth")).isEqualTo("oAuth");
  }
}
