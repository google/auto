/*
 * Copyright (C) 2013 Google Inc.
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

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link AbstractMethodLister}.
 *
 * @author Éamonn McManus
 */
@RunWith(JUnit4.class)
public class AbstractMethodListerTest {
  /** Test class for the abstractNoArgMethods() test cases. */
  public abstract static class Abstract {
    abstract int foo();
    abstract String baz(int x);
    abstract boolean bar();
    abstract void irrelevantVoid();
    abstract Thread baz();
    void irrelevant() {}
    int alsoIrrelevant() {
      return 0;
    }
  }

  /** Test class for the abstractNoArgMethods() test cases. */
  abstract static class AbstractSub extends Abstract {
    @Override boolean bar() {
      return false;
    }

    public abstract String buh();
  }

  // Test that the abstract no-arg non-void methods we scan out of the given class correspond
  // to the expected ones. We use the hallowed trick of looking up the class file as a resource
  // in order to be able to read its contents.
  private void testAbstractNoArgMethods(Class<?> abstractClass, String... expectedMethods)
      throws Exception {
    ClassLoader loader = abstractClass.getClassLoader();
    String resourceName = abstractClass.getName().replace('.', '/') + ".class";
    InputStream inputStream = loader.getResourceAsStream(resourceName);
    AbstractMethodLister abstractMethodLister = new AbstractMethodLister(inputStream);
    List<String> methods = abstractMethodLister.abstractNoArgMethods();
    assertEquals(Arrays.asList(expectedMethods), methods);
  }

  @Test
  public void testAbstractNoParent() throws Exception {
    testAbstractNoArgMethods(Abstract.class, "foo", "bar", "baz");
  }

  @Test
  public void testAbstractWithParent() throws Exception {
    testAbstractNoArgMethods(AbstractSub.class, "buh");
  }
}
