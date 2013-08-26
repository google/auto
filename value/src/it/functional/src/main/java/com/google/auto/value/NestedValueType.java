package com.google.auto.value;

import java.util.Map;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class NestedValueType {
  @AutoValue
  public abstract static class Nested {
    abstract Map<Integer, String> numberNames();

    public static Nested create(Map<Integer, String> numberNames) {
      return AutoValues.using(Factory.class).create(numberNames);
    }

    interface Factory {
      Nested create(Map<Integer, String> numberNames);
    }
  }
}