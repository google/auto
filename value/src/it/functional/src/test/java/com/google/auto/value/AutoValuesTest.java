/*
 * Copyright (C) 2012 The Guava Authors
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

import junit.framework.TestCase;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class AutoValuesTest extends TestCase {
  public void testOuter() {
    SimpleValueTypeWithFactory.Factory c1 =
        AutoValues.using(SimpleValueTypeWithFactory.Factory.class);
    assertNotNull(c1);
    SimpleValueTypeWithFactory.Factory c2 =
        AutoValues.using(SimpleValueTypeWithFactory.Factory.class);
    assertSame(c1, c2);
  }

  public void testNested() {
    NestedValueType.Nested.Factory c1 = AutoValues.using(NestedValueType.Nested.Factory.class);
    assertNotNull(c1);
    NestedValueType.Nested.Factory c2 = AutoValues.using(NestedValueType.Nested.Factory.class);
    assertSame(c1, c2);
  }
}