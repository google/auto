/*
 * Copyright (C) 2012 Google, Inc.
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
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Methods related to the {@link AutoValue} annotation.
 *
 * @author Éamonn McManus
 * @author David Beaumont
 */
public final class AutoValues {
  // There are no instances of this class.
  private AutoValues() {}

  // Map from the Factory interface, like Contact.Factory.class, to an object implementing that
  // interface. The values in this Map are WeakReferences because otherwise this Map would prevent
  // the ClassLoader of the @AutoValue class from ever being garbage-collected.
  private static final Map<Class<?>, WeakReference<Object>> classToFactory =
      new WeakHashMap<Class<?>, WeakReference<Object>>();

  private static Class<?> implementationClassForFactoryInterface(Class<?> factoryInterface)
      throws ClassNotFoundException {
    Class<?> classToConstruct = factoryInterface.getDeclaringClass();
    String classToConstructName = classToConstruct.getName();
    int lastDot = classToConstructName.lastIndexOf('.');
    // lastDot is -1 if the class is in the default package, but the code should work then too.
    String packageNamePlusDot = classToConstructName.substring(0, lastDot + 1);
    String simpleName = classToConstructName.substring(lastDot + 1);
    String factoryClassName =
        packageNamePlusDot + "AutoValueFactory_" + simpleName.replace('$', '_');
    return Class.forName(factoryClassName, false, classToConstruct.getClassLoader());
  }

  /**
   * Returns an instance of the given factory class that can be used to create instances of
   * the related {@link @AutoValue} class. For example:
   * <pre>
   * {@code @AutoValue}
   * {@code public abstract class Contact {
   *   public abstract String name();
   *   public abstract List<String> phoneNumbers();
   *   public abstract int sortOrder();
   *
   *   public static Contact create(String name, List<String> phoneNumbers, int sortOrder) {
   *     return AutoValues.using(Factory.class).create(name, phoneNumbers, sortOrder);
   *   }
   *
   *   interface Factory {
   *     Contact create(String name, List<String> phoneNumbers, int sortOrder);
   *   }
   * }}</pre>
   */
  public static <T> T using(Class<? extends T> factoryInterface) {
    Object factory;
    synchronized (classToFactory) {
      WeakReference<Object> weakRef = classToFactory.get(factoryInterface);
      factory = (weakRef == null) ? null : weakRef.get();
      if (factory == null) {
        try {
          Class<?> implementationClass = implementationClassForFactoryInterface(factoryInterface);
          factory = implementationClass.newInstance();
        } catch (Exception e) {  // Eventually, ReflectiveOperationException
          throw new RuntimeException(e);
        }
        classToFactory.put(factoryInterface, new WeakReference<Object>(factory));
      }
    }
    return factoryInterface.cast(factory);
  }
}