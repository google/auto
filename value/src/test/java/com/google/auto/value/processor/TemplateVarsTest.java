/*
 * Copyright (C) 2014 Google Inc.
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
import static org.junit.Assert.fail;

import com.google.auto.value.processor.escapevelocity.Template;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.Reflection;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for FieldReader.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class TemplateVarsTest {
  static class HappyVars extends TemplateVars {
    Integer integer;
    String string;
    List<Integer> list;
    private static final String IGNORED_STATIC_FINAL = "hatstand";

    @Override Template parsedTemplate() {
      return parsedTemplateForString("integer=$integer string=$string list=$list");
    }
  }

  static Template parsedTemplateForString(String string) {
    try {
      Reader reader = new StringReader(string);
      return Template.parseFrom(reader);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  public void testHappy() {
    HappyVars happy = new HappyVars();
    happy.integer = 23;
    happy.string = "wibble";
    happy.list = ImmutableList.of(5, 17, 23);
    assertThat(HappyVars.IGNORED_STATIC_FINAL).isEqualTo("hatstand");  // avoids unused warning
    String expectedText = "integer=23 string=wibble list=[5, 17, 23]";
    String actualText = happy.toText();
    assertThat(actualText).isEqualTo(expectedText);
  }

  @Test
  public void testUnset() {
    HappyVars sad = new HappyVars();
    sad.integer = 23;
    sad.list = ImmutableList.of(23);
    try {
      sad.toText();
      fail("Did not get expected exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  static class SubSub extends HappyVars {}

  @Test
  public void testSubSub() {
    try {
      new SubSub();
      fail("Did not get expected exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  static class Private extends TemplateVars {
    Integer integer;
    private String unusedString;

    @Override Template parsedTemplate() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testPrivate() {
    try {
      new Private();
      fail("Did not get expected exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  static class Static extends TemplateVars {
    Integer integer;
    static String string;

    @Override Template parsedTemplate() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testStatic() {
    try {
      new Static();
      fail("Did not get expected exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  static class Primitive extends TemplateVars{
    int integer;
    String string;

    @Override Template parsedTemplate() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testPrimitive() {
    try {
      new Primitive();
      fail("Did not get expected exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  // This is a complicated test that tries to simulate the failure that is worked around in
  // Template.parsedTemplateForResource. That failure means that the InputStream returned by
  // ClassLoader.getResourceAsStream sometimes throws IOException while it is being read. To
  // simulate that, we make a second ClassLoader with the same configuration as the one that
  // runs this test, and we override getResourceAsStream so that it wraps the returned InputStream
  // in a BrokenInputStream, which throws an exception after a certain number of characters.
  // We check that that exception was indeed seen, and that we did indeed try to read the resource
  // we're interested in, and that we succeeded in loading a Template nevertheless.
  @Test
  public void testBrokenInputStream() throws Exception {
    URLClassLoader myLoader = (URLClassLoader) getClass().getClassLoader();
    URLClassLoader shadowLoader = new ShadowLoader(myLoader);
    Runnable brokenInputStreamTest =
        (Runnable) shadowLoader
            .loadClass(BrokenInputStreamTest.class.getName())
            .getConstructor()
            .newInstance();
    brokenInputStreamTest.run();
  }

  private static class ShadowLoader extends URLClassLoader implements Callable<Set<String>> {
    private final Set<String> result = new TreeSet<String>();

    ShadowLoader(URLClassLoader original) {
      super(original.getURLs(), original.getParent());
    }

    @Override
    public Set<String> call() throws Exception {
      return result;
    }

    @Override
    public InputStream getResourceAsStream(String resource) {
      result.add(resource);
      return new BrokenInputStream(super.getResourceAsStream(resource));
    }

    private class BrokenInputStream extends InputStream {
      private final InputStream original;
      private int count = 0;

      BrokenInputStream(InputStream original) {
        this.original = original;
      }

      @Override
      public int read() throws IOException {
        if (++count > 10) {
          result.add("threw");
          throw new IOException("BrokenInputStream");
        }
        return original.read();
      }
    }
  }

  public static class BrokenInputStreamTest implements Runnable {
    @Override
    public void run() {
      Template template = TemplateVars.parsedTemplateForResource("autovalue.vm");
      assertThat(template).isNotNull();
      String resourceName =
          Reflection.getPackageName(getClass()).replace('.', '/') + "/autovalue.vm";
      @SuppressWarnings("unchecked")
      Callable<Set<String>> myLoader = (Callable<Set<String>>) getClass().getClassLoader();
      try {
        Set<String> result = myLoader.call();
        assertThat(result).contains(resourceName);
        assertThat(result).contains("threw");
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }
  }
}
