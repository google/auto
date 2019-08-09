/*
 * Copyright 2014 Google LLC
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

import static com.google.common.base.StandardSystemProperty.JAVA_CLASS_PATH;
import static com.google.common.base.StandardSystemProperty.PATH_SEPARATOR;
import static com.google.common.truth.Truth.assertThat;
import static java.util.logging.Level.WARNING;
import static org.junit.Assert.fail;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.Reflection;
import com.google.escapevelocity.Template;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
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

    @Override
    Template parsedTemplate() {
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
    assertThat(HappyVars.IGNORED_STATIC_FINAL).isEqualTo("hatstand"); // avoids unused warning
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

  static class SubHappyVars extends HappyVars {
    Character character;

    @Override
    Template parsedTemplate() {
      return parsedTemplateForString(
          "integer=$integer string=$string list=$list character=$character");
    }
  }

  @Test
  public void testSubSub() {
    SubHappyVars vars = new SubHappyVars();
    vars.integer = 23;
    vars.string = "wibble";
    vars.list = ImmutableList.of(5, 17, 23);
    vars.character = 'ß';
    String expectedText = "integer=23 string=wibble list=[5, 17, 23] character=ß";
    String actualText = vars.toText();
    assertThat(actualText).isEqualTo(expectedText);
  }

  static class Private extends TemplateVars {
    Integer integer;
    private String unusedString;

    @Override
    Template parsedTemplate() {
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

    @Override
    Template parsedTemplate() {
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

  static class Primitive extends TemplateVars {
    int integer;
    String string;

    @Override
    Template parsedTemplate() {
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

  @Test
  public void testBrokenInputStream_IOException() throws Exception {
    doTestBrokenInputStream(new IOException("BrokenInputStream"));
  }

  @Test
  public void testBrokenInputStream_NullPointerException() throws Exception {
    doTestBrokenInputStream(new NullPointerException("BrokenInputStream"));
  }

  @Test
  public void testBrokenInputStream_IllegalStateException() throws Exception {
    doTestBrokenInputStream(new IllegalStateException("BrokenInputStream"));
  }

  // This is a complicated test that tries to simulates the failures that are worked around in
  // Template.parsedTemplateForResource. Those failures means that the InputStream returned by
  // ClassLoader.getResourceAsStream sometimes throws IOException or NullPointerException or
  // IllegalStateException while it is being read. To simulate that, we make a second ClassLoader
  // with the same configuration as the one that runs this test, and we override getResourceAsStream
  // so that it wraps the returned InputStream in a BrokenInputStream, which throws an exception
  // after a certain number of characters. We check that that exception was indeed seen, and that
  // we did indeed try to read the resource we're interested in, and that we succeeded in loading a
  // Template nevertheless.
  private void doTestBrokenInputStream(Exception exception) throws Exception {
    URLClassLoader shadowLoader = new ShadowLoader(getClass().getClassLoader(), exception);
    Runnable brokenInputStreamTest =
        (Runnable)
            shadowLoader
                .loadClass(BrokenInputStreamTest.class.getName())
                .getConstructor()
                .newInstance();
    brokenInputStreamTest.run();
  }

  private static class ShadowLoader extends URLClassLoader implements Callable<Set<String>> {

    private static final Logger logger = Logger.getLogger(ShadowLoader.class.getName());

    private final Exception exception;
    private final Set<String> result = new TreeSet<String>();

    ShadowLoader(ClassLoader original, Exception exception) {
      super(getClassPathUrls(original), original.getParent());
      this.exception = exception;
    }

    private static URL[] getClassPathUrls(ClassLoader original) {
      return original instanceof URLClassLoader
          ? ((URLClassLoader) original).getURLs()
          : parseJavaClassPath();
    }

    /**
     * Returns the URLs in the class path specified by the {@code java.class.path} {@linkplain
     * System#getProperty system property}.
     */
    // TODO(b/65488446): Use a new public API.
    private static URL[] parseJavaClassPath() {
      ImmutableList.Builder<URL> urls = ImmutableList.builder();
      for (String entry : Splitter.on(PATH_SEPARATOR.value()).split(JAVA_CLASS_PATH.value())) {
        try {
          try {
            urls.add(new File(entry).toURI().toURL());
          } catch (SecurityException e) { // File.toURI checks to see if the file is a directory
            urls.add(new URL("file", null, new File(entry).getAbsolutePath()));
          }
        } catch (MalformedURLException e) {
          logger.log(WARNING, "malformed classpath entry: " + entry, e);
        }
      }
      return urls.build().toArray(new URL[0]);
    }

    @Override
    public Set<String> call() throws Exception {
      return result;
    }

    @Override
    public InputStream getResourceAsStream(String resource) {
      // Make sure this is actually the resource we are expecting. If we're using JaCoCo or the
      // like, we might end up reading some other resource, and we don't want to break that.
      if (resource.startsWith("com/google/auto")) {
        result.add(resource);
        return new BrokenInputStream(super.getResourceAsStream(resource));
      } else {
        return super.getResourceAsStream(resource);
      }
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
          if (exception instanceof IOException) {
            throw (IOException) exception;
          }
          throw (RuntimeException) exception;
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
