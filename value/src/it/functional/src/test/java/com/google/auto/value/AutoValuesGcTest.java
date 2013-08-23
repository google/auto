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

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.testing.GcFinalization;

/**
 * Check that calling AutoValues.using(c) does not prevent the ClassLoader of c from being
 * garbage-collected.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class AutoValuesGcTest extends TestCase {
  // This ClassLoader effectively delegates all ClassLoading to its parent (which has the same
  // URLs), except for classes that have "SimpleValueType" in the name, which it loads itself.
  // The aim is to share the real (non-shadow) AutoValues class, but have the
  // SimpleValueTypeWithFactory class be loaded by a temporary ClassLoader (this one) which we can
  // later expect to be garbage-collected.
  private static class ShadowClassLoader extends URLClassLoader {
    ShadowClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      Class<?> loaded = findLoadedClass(name);
      if (loaded != null) {
        return loaded;
      } else if (name.contains("SimpleValueType")) {
        Class<?> found = super.findClass(name);
        if (resolve) {
          resolveClass(found);
        }
        return found;
      } else {
        return super.loadClass(name, resolve);
      }
    }
  }

  private WeakReference<?> shadowLoad() throws Exception {
    // Test will fail if its loader is not a URLClassLoader.
    URLClassLoader originalClassLoader = (URLClassLoader) getClass().getClassLoader();
    URLClassLoader shadowClassLoader =
        new ShadowClassLoader(originalClassLoader.getURLs(), originalClassLoader);
    Class<?> shadowSimpleValueTypeWithFactory =
        Class.forName(SimpleValueTypeWithFactory.class.getName(), true, shadowClassLoader);
    // Check that we really do have a shadow SimpleValueTypeWithFactory.class.
    assertFalse(shadowSimpleValueTypeWithFactory.equals(SimpleValueTypeWithFactory.class));
    Class<?> shadowAutoValues =
        Class.forName(AutoValues.class.getName(), true, shadowClassLoader);
    // Check that we are sharing the "real" AutoValues class.
    assertEquals(AutoValues.class, shadowAutoValues);

    Method shadowSimpleValueTypeWithFactoryCreate = shadowSimpleValueTypeWithFactory.getMethod(
        "create", String.class, int.class, Map.class);
    shadowSimpleValueTypeWithFactoryCreate.invoke(null, "foo", 23, Collections.emptyMap());
    // We've invoked AutoValues.using on the shadow SimpleValueTypeWithFactory, so we now want to
    // check that that doesn't stop the shadow ClassLoader from being GC'd. We do that by waiting
    // for this WeakReference to be cleared.
    return new WeakReference<Object>(shadowClassLoader);
  }

  public void testCanGc() throws Exception {
    WeakReference<?> weakRef = shadowLoad();
    GcFinalization.awaitClear(weakRef);
  }
}