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

import java.util.Map;

/**
 * Simple value type that uses a factory for construction.
 *
 * @see SimpleValueTypeTest
 * @author emcmanus@google.com (Éamonn McManus)
 */
@AutoValue
public abstract class SimpleValueTypeWithFactory {
  /**
   * @return A string that is a non-null string.
   */
  public abstract String string();

  /**
   * @return An integer that is an integer.
   */
  public abstract int integer();

  /**
   * @return A non-null map where the keys are strings and the values are longs.
   */
  public abstract Map<String, Long> map();

  public static SimpleValueTypeWithFactory
      create(String string, int integer, Map<String, Long> map) {
    return AutoValues.using(Factory.class).create(string, integer, map);
  }

  /**
   * The presence of an interface called exactly "Factory" in a class annotated with
   * {@code @AutoValue} causes the annotation processor to generate an implementation of that
   * interface that the {@link AutoValues#using} method knows how to find. That implementation
   * in turn knows how to instantiate the subclass of this class (SimpleValueTypeWithFactory)
   * that the annotation processor also generates.
   */
  interface Factory {
    SimpleValueTypeWithFactory create(String string, int integer, Map<String, Long> map);
  }
}