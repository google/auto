/*
 * Copyright (C) 2014 The Guava Authors
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

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static org.truth0.Truth.ASSERT;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;

import junit.framework.TestCase;
import org.apache.velocity.app.Velocity;

import java.net.URLClassLoader;

/**
 * Test that Velocity doesn't cause the processor to fail in unusual class-loading environments.
 * It has a bunch of overengineered "managers" like LogManager and ResourceManager that it wants
 * to load, and the ClassLoader that it uses to load them is the context class loader. If that
 * loader sees a different copy of Velocity (even if it is the same version) then Velocity will
 * fail to initialize.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class VelocityClassLoaderTest extends TestCase {
  /**
   * Make a different ClassLoader that loads the same URLs as this one, and use it to compile
   * an {@code @AutoValue} class. If Velocity loads its managers using the context class loader,
   * and that loader is still the original one that loaded this test, then it will find the
   * original copy of the Velocity classes rather than the one from the new loader, and fail.
   *
   * <p>This test assumes that the test class was loaded by a URLClassLoader and that that loader's
   * URLs also include the Velocity classes.
   */
  public void testClassLoaderHack() throws Exception {
    URLClassLoader myLoader = (URLClassLoader) getClass().getClassLoader();
    URLClassLoader newLoader = new URLClassLoader(myLoader.getURLs(), myLoader.getParent());
    String velocityClassName = Velocity.class.getName();
    Class<?> myVelocity = myLoader.loadClass(velocityClassName);
    Class<?> newVelocity = newLoader.loadClass(velocityClassName);
    ASSERT.that(myVelocity).isNotEqualTo(newVelocity);
    Runnable test = (Runnable) newLoader.loadClass(RunInClassLoader.class.getName()).newInstance();
    ASSERT.that(test.getClass()).isNotEqualTo(RunInClassLoader.class);
    test.run();
  }

  public static class RunInClassLoader implements Runnable {
    @Override
    public void run() {
      String source = Joiner.on('\n').join(ImmutableList.of(
          "package foo.bar;",
          "import " + AutoValue.class.getName() + ";",
          "@AutoValue abstract class Test {",
          "  abstract int baz();",
          "  static Test create(int baz) {",
          "    return new AutoValue_Test(baz);",
          "  }",
          "}"));
      ASSERT.about(javaSource())
          .that(JavaFileObjects.forSourceString("foo.bar.Test", source))
          .processedWith(new AutoValueProcessor())
          .compilesWithoutError();
    }
  }
}
