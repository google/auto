/*
 * Copyright 2013 Google LLC
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
package com.google.auto.factory.processor;

import static com.google.auto.factory.processor.AutoFactoryDeclaration.Factory.isValidIdentifier;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AutoFactoryDeclarationTest {
  @Test
  public void identifiers() {
    assertThat(isValidIdentifier("String")).isTrue();
    assertThat(isValidIdentifier("9CantStartWithNumber")).isFalse();
    assertThat(isValidIdentifier("enum")).isFalse();
    assertThat(isValidIdentifier("goto")).isFalse();
    assertThat(isValidIdentifier("InvalidCharacter!")).isFalse();
  }
}
