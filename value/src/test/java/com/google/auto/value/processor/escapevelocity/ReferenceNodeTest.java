/*
 * Copyright (C) 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.auto.value.processor.escapevelocity;

import static com.google.common.truth.Truth.assertThat;

import com.google.auto.value.processor.escapevelocity.ReferenceNode.MethodReferenceNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Primitives;
import com.google.common.truth.Expect;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ReferenceNode}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class ReferenceNodeTest {
  @Rule public Expect expect = Expect.create();

  // This is the exhaustive list from
  // https://docs.oracle.com/javase/specs/jls/se8/html/jls-5.html#jls-5.1.2.
  // We put the "from" type first for consistency with that list, even though that is inconsistent
  // with our method order (which is itself consistent with assignment, "to" on the left).
  private static final ImmutableSet<ImmutableList<Class<?>>> ASSIGNMENT_COMPATIBLE =
      makeAssignmentCompatibleSet();
  private static ImmutableSet<ImmutableList<Class<?>>> makeAssignmentCompatibleSet() {
    Class<?>[][] pairs = {
        {byte.class, short.class},
        {byte.class, int.class},
        {byte.class, long.class},
        {byte.class, float.class},
        {byte.class, double.class},
        {short.class, int.class},
        {short.class, long.class},
        {short.class, float.class},
        {short.class, double.class},
        {char.class, int.class},
        {char.class, long.class},
        {char.class, float.class},
        {char.class, double.class},
        {int.class, long.class},
        {int.class, float.class},
        {int.class, double.class},
        {long.class, float.class},
        {long.class, double.class},
        {float.class, double.class},
    };
    ImmutableSet.Builder<ImmutableList<Class<?>>> builder = ImmutableSet.builder();
    for (Class<?>[] pair : pairs) {
      builder.add(ImmutableList.copyOf(pair));
    }
    return builder.build();
  }

  @Test
  public void testPrimitiveTypeIsAssignmentCompatible() {
    for (Class<?> from : Primitives.allPrimitiveTypes()) {
      for (Class<?> to : Primitives.allPrimitiveTypes()) {
        boolean expected =
            (from == to || ASSIGNMENT_COMPATIBLE.contains(ImmutableList.of(from, to)));
        boolean actual =
            MethodReferenceNode.primitiveTypeIsAssignmentCompatible(to, from);
        expect
            .withMessage(from + " assignable to " + to)
            .that(expected).isEqualTo(actual);
      }
    }
  }

  @Test
  public void testVisibleMethod() throws Exception {
    Map<String, String> map = Collections.singletonMap("foo", "bar");
    Class<?> mapClass = map.getClass();
    assertThat(Modifier.isPublic(mapClass.getModifiers())).isFalse();
    Method size = map.getClass().getMethod("size");
    Method visibleSize = ReferenceNode.visibleMethod(size, mapClass);
    assertThat(visibleSize.invoke(map)).isEqualTo(1);
  }

  @Test
  public void testCompatibleArgs() {
    assertThat(MethodReferenceNode.compatibleArgs(
        new Class<?>[]{int.class}, ImmutableList.of((Object) 5))).isTrue();
  }
}
