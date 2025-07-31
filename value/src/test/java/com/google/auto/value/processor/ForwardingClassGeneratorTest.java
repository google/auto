/*
 * Copyright 2022 Google LLC
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.stream;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.testing.compile.CompilationRule;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ForwardingClassGeneratorTest {
  @Rule public final CompilationRule compilationRule = new CompilationRule();

  public static class Simple implements Supplier<ImmutableList<Object>> {
    final int anInt;

    public Simple(int anInt) {
      this.anInt = anInt;
    }

    @Override
    public ImmutableList<Object> get() {
      return ImmutableList.of(anInt);
    }
  }

  @Test
  public void simple() throws Exception {
    testClass(Simple.class, ImmutableList.of(23));
  }

  public static class Outer {
    public static class Inner {}
  }

  public static class KitchenSink implements Supplier<ImmutableList<Object>> {
    final byte aByte;
    final short aShort;
    final int anInt;
    final long aLong;
    final float aFloat;
    final double aDouble;
    final char aChar;
    final boolean aBoolean;
    final String aString;
    final ImmutableList<String> aStringList;
    final String[] aStringArray;
    final byte[] aByteArray;
    final Outer.Inner anInner;

    public KitchenSink(
        byte aByte,
        short aShort,
        int anInt,
        long aLong,
        float aFloat,
        double aDouble,
        char aChar,
        boolean aBoolean,
        String aString,
        ImmutableList<String> aStringList,
        String[] aStringArray,
        byte[] aByteArray,
        Outer.Inner anInner) {
      this.aByte = aByte;
      this.aShort = aShort;
      this.anInt = anInt;
      this.aLong = aLong;
      this.aFloat = aFloat;
      this.aDouble = aDouble;
      this.aChar = aChar;
      this.aBoolean = aBoolean;
      this.aString = aString;
      this.aStringList = aStringList;
      this.aStringArray = aStringArray;
      this.aByteArray = aByteArray;
      this.anInner = anInner;
    }

    @Override
    public ImmutableList<Object> get() {
      return ImmutableList.of(
          aByte,
          aShort,
          anInt,
          aLong,
          aFloat,
          aDouble,
          aChar,
          aBoolean,
          aString,
          aStringList,
          aStringArray,
          aByteArray,
          anInner);
    }
  }

  @Test
  public void kitchenSink() throws Exception {
    testClass(
        KitchenSink.class,
        ImmutableList.of(
            (byte) 1,
            (short) 2,
            3,
            4L,
            5f,
            6d,
            '7',
            true,
            "9",
            ImmutableList.of("10"),
            new String[] {"11"},
            new byte[] {12},
            new Outer.Inner()));
  }

  /**
   * Tests that we can successfully generate a forwarding class that calls the constructor of the
   * given class. We'll then load the created class and call the forwarding method, checking that it
   * does indeed call the constructor.
   */
  private void testClass(
      Class<? extends Supplier<ImmutableList<Object>>> c,
      ImmutableList<Object> constructorParameters)
      throws ReflectiveOperationException {
    TypeElement typeElement = compilationRule.getElements().getTypeElement(c.getCanonicalName());
    ExecutableElement constructorExecutable =
        Iterables.getOnlyElement(constructorsIn(typeElement.getEnclosedElements()));
    ImmutableList<TypeMirror> parameterTypeMirrors =
        constructorExecutable.getParameters().stream()
            .map(Element::asType)
            .map(compilationRule.getTypes()::erasure)
            .collect(toImmutableList());
    String className = "com.example.Forwarder";
    byte[] bytes =
        ForwardingClassGenerator.makeConstructorForwarder(
            className, typeElement.asType(), parameterTypeMirrors);
    // Now load the class we just generated, and use reflection to call its forwarding method.
    // That should give us an instance of the target class `c`, obtained by the call to its
    // constructor from the forwarding method.
    ClassLoader loader =
        new ClassLoader() {
          @Override
          protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(className)) {
              return defineClass(className, bytes, 0, bytes.length);
            }
            throw new ClassNotFoundException(name);
          }
        };
    Class<?> forwardingClass = Class.forName(className, true, loader);
    Method ofMethod = stream(forwardingClass.getDeclaredMethods()).findFirst().get();
    assertThat(ofMethod.getName()).isEqualTo("of");
    ofMethod.setAccessible(true);
    Supplier<ImmutableList<Object>> constructed =
        c.cast(ofMethod.invoke(null, constructorParameters.toArray()));
    ImmutableList<Object> retrievedParameters = constructed.get();
    assertThat(retrievedParameters).isEqualTo(constructorParameters);
  }
}
