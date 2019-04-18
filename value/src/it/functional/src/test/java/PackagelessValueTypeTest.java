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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.google.common.collect.ImmutableMap;
import com.google.common.testing.NullPointerTester;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author emcmanus@google.com (Éamonn McManus) */
@RunWith(JUnit4.class)
public class PackagelessValueTypeTest {
  @Test
  public void testPackagelessValueType() {
    final String happy = "happy";
    final int testInt = 23;
    final Map<String, Long> testMap = ImmutableMap.of("happy", 23L);
    PackagelessValueType simple = PackagelessValueType.create(happy, testInt, testMap);
    assertSame(happy, simple.string());
    assertEquals(testInt, simple.integer());
    assertSame(testMap, simple.map());
    assertEquals(
        "PackagelessValueType{string=happy, integer=23, map={happy=23}}", simple.toString());
    int expectedHashCode = 1;
    expectedHashCode = (expectedHashCode * 1000003) ^ happy.hashCode();
    expectedHashCode = (expectedHashCode * 1000003) ^ ((Object) testInt).hashCode();
    expectedHashCode = (expectedHashCode * 1000003) ^ testMap.hashCode();
    assertEquals(expectedHashCode, simple.hashCode());
  }

  @Test
  public void testNestedValueType() {
    ImmutableMap<Integer, String> numberNames = ImmutableMap.of(1, "un", 2, "deux");
    PackagelessNestedValueType.Nested nested =
        PackagelessNestedValueType.Nested.create(numberNames);
    assertEquals(numberNames, nested.numberNames());
  }

  @Test
  public void testNull() {
    NullPointerTester tester = new NullPointerTester();
    tester.testAllPublicStaticMethods(PackagelessValueType.class);
  }
}
