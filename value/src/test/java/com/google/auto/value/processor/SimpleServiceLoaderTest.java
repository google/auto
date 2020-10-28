/*
 * Copyright 2019 Google LLC
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
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SimpleServiceLoaderTest {
  @Test
  public void loadOnce() throws Exception {
    ClassLoader loader =
        loaderForJarWithEntries(
            CharSequence.class.getName(), String.class.getName(), StringBuilder.class.getName());

    ImmutableList<CharSequence> providers = SimpleServiceLoader.load(CharSequence.class, loader);

    // The provider entry for java.lang.String should have caused us to call new String(), which
    // will produce this "" in the providers.
    assertThat(providers).contains("");
    List<Class<?>> classes = providers.stream().map(Object::getClass).collect(toList());
    assertThat(classes).containsExactly(String.class, StringBuilder.class).inOrder();
  }

  // Sometimes you can have the same jar appear more than once in the classpath, perhaps in
  // different versions. In that case we don't want to instantiate the same class more than once.
  // This test checks that we don't.
  @Test
  public void loadWithDuplicates() throws Exception {
    ClassLoader loader1 =
        loaderForJarWithEntries(
            CharSequence.class.getName(), String.class.getName(), StringBuilder.class.getName());
    ClassLoader loader2 =
        loaderForJarWithEntries(
            CharSequence.class.getName(), String.class.getName(), StringBuilder.class.getName());
    ClassLoader combinedLoader =
        new ClassLoader() {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            List<URL> urls = new ArrayList<>(Collections.list(loader1.getResources(name)));
            urls.addAll(Collections.list(loader2.getResources(name)));
            return Collections.enumeration(urls);
          }
        };

    ImmutableList<CharSequence> providers =
        SimpleServiceLoader.load(CharSequence.class, combinedLoader);

    assertThat(providers).contains("");
    List<Class<?>> classes = providers.stream().map(Object::getClass).collect(toList());
    assertThat(classes).containsExactly(String.class, StringBuilder.class).inOrder();
  }

  @Test
  public void blankLinesAndComments() throws Exception {
    ClassLoader loader =
        loaderForJarWithEntries(
            CharSequence.class.getName(),
            "",
            "# this is a comment",
            "   # this is also a comment",
            "  java.lang.String  # this is a comment after a class name");

    ImmutableList<CharSequence> providers = SimpleServiceLoader.load(CharSequence.class, loader);

    assertThat(providers).containsExactly("");
  }

  @Test
  public void loadTwiceFromSameLoader() throws Exception {
    ClassLoader loader =
        loaderForJarWithEntries(
            CharSequence.class.getName(), String.class.getName(), StringBuilder.class.getName());

    ImmutableList<CharSequence> providers1 = SimpleServiceLoader.load(CharSequence.class, loader);
    ImmutableList<CharSequence> providers2 = SimpleServiceLoader.load(CharSequence.class, loader);

    List<Class<?>> classes1 = providers1.stream().map(Object::getClass).collect(toList());
    List<Class<?>> classes2 = providers2.stream().map(Object::getClass).collect(toList());
    assertThat(classes2).containsExactlyElementsIn(classes1).inOrder();
  }

  @Test
  public void loadTwiceFromDifferentLoaders() throws Exception {
    URL jarUrl =
        urlForJarWithEntries(
            CharSequence.class.getName(), String.class.getName(), StringBuilder.class.getName());
    ClassLoader loader1 = new URLClassLoader(new URL[] {jarUrl});

    ImmutableList<CharSequence> providers1 = SimpleServiceLoader.load(CharSequence.class, loader1);
    // We should have called `new String()`, so the result should contain "".
    assertThat(providers1).contains("");

    ClassLoader loader2 = new URLClassLoader(new URL[] {jarUrl});
    ImmutableList<CharSequence> providers2 = SimpleServiceLoader.load(CharSequence.class, loader2);

    List<Class<?>> classes1 = providers1.stream().map(Object::getClass).collect(toList());
    List<Class<?>> classes2 = providers2.stream().map(Object::getClass).collect(toList());
    assertThat(classes2).containsExactlyElementsIn(classes1).inOrder();
  }

  @Test
  public void noProviders() throws Exception {
    ClassLoader loader = loaderForJarWithEntries(CharSequence.class.getName());

    ImmutableList<CharSequence> providers = SimpleServiceLoader.load(CharSequence.class, loader);

    assertThat(providers).isEmpty();
  }

  @Test
  public void classNotFound() throws Exception {
    ClassLoader loader =
        loaderForJarWithEntries(CharSequence.class.getName(), "this.is.not.a.Class");

    try {
      SimpleServiceLoader.load(CharSequence.class, loader);
      fail();
    } catch (ServiceConfigurationError expected) {
      assertThat(expected).hasMessageThat().startsWith("Could not load ");
    }
  }

  @Test
  public void wrongTypeClass() throws Exception {
    ClassLoader loader = loaderForJarWithEntries(CharSequence.class.getName(), "java.lang.Thread");

    try {
      SimpleServiceLoader.load(CharSequence.class, loader);
      fail();
    } catch (ServiceConfigurationError expected) {
      assertThat(expected).hasMessageThat().startsWith("Class java.lang.Thread is not assignable");
    }
  }

  @Test
  public void couldNotConstruct() throws Exception {
    ClassLoader loader = loaderForJarWithEntries("java.lang.System", "java.lang.System");

    try {
      SimpleServiceLoader.load(System.class, loader);
      fail();
    } catch (ServiceConfigurationError expected) {
      assertThat(expected).hasMessageThat().startsWith("Could not construct");
    }
  }

  @Test
  public void brokenLoader() {
    ClassLoader loader =
        new URLClassLoader(new URL[0]) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            throw new IOException("bang");
          }
        };

    try {
      SimpleServiceLoader.load(CharSequence.class, loader);
      fail();
    } catch (ServiceConfigurationError expected) {
      assertThat(expected).hasMessageThat().startsWith("Could not look up");
      assertThat(expected).hasCauseThat().hasMessageThat().isEqualTo("bang");
    }
  }

  private static ClassLoader loaderForJarWithEntries(String service, String... lines)
      throws IOException {
    URL jarUrl = urlForJarWithEntries(service, lines);
    return new URLClassLoader(new URL[] {jarUrl});
  }

  private static URL urlForJarWithEntries(String service, String... lines) throws IOException {
    File jar = File.createTempFile("SimpleServiceLoaderTest", "jar");
    jar.deleteOnExit();
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
      JarEntry jarEntry = new JarEntry("META-INF/services/" + service);
      out.putNextEntry(jarEntry);
      // It would be bad practice to use try-with-resources below, because closing the PrintWriter
      // would close the JarOutputStream.
      PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
      for (String line : lines) {
        writer.println(line);
      }
      writer.flush();
    }
    return jar.toURI().toURL();
  }
}
