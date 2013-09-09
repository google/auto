/*
 * Copyright (C) 2013 Google, Inc.
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
package com.google.auto.factory;

import static com.google.auto.factory.AutoFactoryDeclaration.Factory.isValidIdentifier;
import static org.truth0.Truth.ASSERT;

import org.junit.Test;

public class AutoFactoryDeclarationTest {
  @Test public void identifiers() {
    ASSERT.that(isValidIdentifier("String")).isTrue();
    ASSERT.that(isValidIdentifier("9CantStartWithNumber")).isFalse();
    ASSERT.that(isValidIdentifier("enum")).isFalse();
    ASSERT.that(isValidIdentifier("goto")).isFalse();
    ASSERT.that(isValidIdentifier("InvalidCharacter!")).isFalse();
  }
}
