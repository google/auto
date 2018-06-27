package com.google.auto.value.processor;

/**
 * Helper methods to create property names.
 */
final class PropertyNames {

  /**
   * Like {@link #decapitalize(String)} but returns a property name that
   * starts with two capital letters as is (does nothing).
   */
  public static String decapitalizeIgnoreAcronyms(String propertyName) {
    if (propertyName != null &&
        propertyName.length() >= 2 &&
        Character.isUpperCase(propertyName.charAt(0)) &&
        Character.isUpperCase(propertyName.charAt(1))) {
      return propertyName;
    }
    return decapitalize(propertyName);
  }

  /**
   * Returns the {@code propertyName} with its first character in lower case.
   */
  public static String decapitalize(String propertyName) {
    if (propertyName == null || propertyName.length() == 0) {
      return propertyName;
    }
    return Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
  }
}
