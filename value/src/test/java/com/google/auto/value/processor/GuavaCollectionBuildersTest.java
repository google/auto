/*
 * Copyright 2015 Google LLC
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.truth.Expect;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Validates the assumptions AutoValue makes about Guava immutable collection builders. We expect
 * for each public class {@code com.google.common.collect.ImmutableFoo} that:
 *
 * <ul>
 *   <li>it contains a public nested class {@code ImmutableFoo.Builder} with the same type
 *       parameters;
 *   <li>there is a public static method {@code ImmutableFoo.builder()} that returns {@code
 *       ImmutableFoo.Builder};
 *   <li>there is a method {@code ImmutableFoo.Builder.build()} that returns {@code ImmutableFoo};
 *   <li>and there is a method in {@code ImmutableFoo.Builder} called either {@code addAll} or
 *       {@code putAll} with a single parameter to which {@code ImmutableFoo} can be assigned.
 * </ul>
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class GuavaCollectionBuildersTest {
  private static final ImmutableSet<String> NON_BUILDABLE_COLLECTIONS =
      ImmutableSet.of("ImmutableCollection");

  @Rule public final Expect expect = Expect.create();

  @Test
  public void testImmutableBuilders() throws Exception {
    ClassPath classPath = ClassPath.from(getClass().getClassLoader());
    ImmutableSet<ClassPath.ClassInfo> classes = classPath.getAllClasses();
    int checked = 0;
    for (ClassPath.ClassInfo classInfo : classes) {
      if (classInfo.getPackageName().equals("com.google.common.collect")
          && classInfo.getSimpleName().startsWith("Immutable")
          && !NON_BUILDABLE_COLLECTIONS.contains(classInfo.getSimpleName())) {
        Class<?> c = Class.forName(classInfo.getName());
        if (Modifier.isPublic(c.getModifiers())) {
          checked++;
          checkImmutableClass(c);
        }
      }
    }
    expect.that(checked).isGreaterThan(10);
  }

  private void checkImmutableClass(Class<?> c)
      throws ClassNotFoundException, NoSuchMethodException {
    if (!Modifier.isPublic(c.getModifiers())) {
      return;
    }

    // We have a public static ImmutableFoo.builder()
    Method builderMethod = c.getMethod("builder");
    assertThat(Modifier.isStatic(builderMethod.getModifiers())).isTrue();

    // Its return type is Builder with the same type parameters.
    Type builderMethodReturn = builderMethod.getGenericReturnType();
    expect.that(builderMethodReturn).isInstanceOf(ParameterizedType.class);
    ParameterizedType builderMethodParameterizedReturn = (ParameterizedType) builderMethodReturn;
    Class<?> builderClass = Class.forName(c.getName() + "$Builder");
    expect.that(builderMethod.getReturnType()).isEqualTo(builderClass);
    expect
        .withMessage(c.getName())
        .that(Arrays.toString(builderMethodParameterizedReturn.getActualTypeArguments()))
        .isEqualTo(Arrays.toString(builderClass.getTypeParameters()));

    // The Builder has a public build() method that returns ImmutableFoo.
    Method buildMethod = builderClass.getMethod("build");
    expect.that(buildMethod.getReturnType()).isEqualTo(c);

    // The Builder has either an addAll or a putAll public method with a parameter that
    // ImmutableFoo can be assigned to.
    boolean found = false;
    for (Method m : builderClass.getMethods()) {
      if ((m.getName().equals("addAll") || m.getName().equals("putAll"))
          && m.getParameterTypes().length == 1) {
        Class<?> parameter = m.getParameterTypes()[0];
        if (parameter.isAssignableFrom(c)) {
          found = true;
          break;
        }
      }
    }
    expect.withMessage(builderClass.getName() + " has addAll or putAll").that(found).isTrue();
  }
}
