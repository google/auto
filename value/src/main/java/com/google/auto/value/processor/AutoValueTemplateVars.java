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
package com.google.auto.value.processor;

import java.util.List;
import java.util.SortedSet;

/**
 * The template for AutoValue_Foo classes, and the variables to substitute into that template.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@SuppressWarnings("unused")  // the fields in this class are only read via reflection
class AutoValueTemplateVars extends TemplateVars {
  /** The properties defined by the parent class's abstract methods. */
  List<AutoValueProcessor.Property> props;

  /** Whether to generate an equals(Object) method. */
  Boolean equals;
  /** Whether to generate a hashCode() method. */
  Boolean hashCode;
  /** Whether to generate a toString() method. */
  Boolean toString;

  /** The fully-qualified names of the classes to be imported in the generated class. */
  SortedSet<String> imports;

  /** The spelling of the java.util.Arrays class: Arrays or java.util.Arrays. */
  String javaUtilArraysSpelling;

  /** The text of the serialVersionUID constant, or empty if there is none. */
  String serialVersionUID;

  /** Whether to generate a Parcelable creator. */
  Boolean parcelable;

  /**
   * The package of the class with the {@code @AutoValue} annotation and its generated subclass.
   */
  String pkg;
  /**
   * The name of the class with the {@code @AutoValue} annotation, including containing
   * classes but not including the package name.
   */
  String origClass;
  /** The simple name of the class with the {@code @AutoValue} annotation. */
  String simpleClassName;
  /** The simple name of the generated subclass. */
  String subclass;

  /**
   * The formal generic signature of the class with the {@code @AutoValue} annotation and its
   * generated subclass. This is empty, or contains type variables with optional bounds,
   * for example {@code <K, V extends K>}.
   */
  String formalTypes;
  /**
   * The generic signature used by the generated subclass for its superclass reference.
   * This is empty, or contains only type variables with no bounds, for example
   * {@code <K, V>}.
   */
  String actualTypes;
  /**
   * The generic signature in {@link #actualTypes} where every variable has been replaced
   * by a wildcard, for example {@code <?, ?>}.
   */
  String wildcardTypes;

  // The code below uses a small templating language. This is not hugely readable, but is much more
  // so than sb.append(this).append(that) with ifs and fors scattered around everywhere.
  // See the Template class for an explanation of the various constructs.
  private static final String TEMPLATE_STRING = concatLines(
      // CHECKSTYLE:OFF:OperatorWrap
      // Package declaration
      "$[pkg?package $[pkg];\n]",

      // Imports
      "$[imports:i||import $[i];\n]",

      // Class declaration
      "final class $[subclass]$[formalTypes] extends $[origClass]$[actualTypes] {",

      // Fields
      "$[props:p||  private final $[p.type] $[p];\n]",

      // Constructor
      "  $[subclass](\n      $[props:p|,\n      |$[p.type] $[p]]) {",
      "$[props:p|\n|$[p.primitive!$[p.nullable!    if ($[p] == null) {",
      "      throw new NullPointerException(\"Null $[p]\");",
      "    }",
      "]]" +
      "    this.$[p] = $[p];]",
      "  }",

      // Property getters
      "$[props:p|\n|\n  @Override",
      "  $[p.access]$[p.type] $[p]() {",
      "    return $[p.array?[$[p.nullable?$[p] == null ? null : ]$[p].clone()][$[p]]];",
      "  }]",

      // toString()
      "$[toString?\n  @Override",
      "  public String toString() {",
      "    return \"$[simpleClassName]{\"$[props?\n        + \"]" +
      "$[props:p|\n        + \", |" +
                "$[p]=\" + $[p.array?[$[javaUtilArraysSpelling].toString($[p])][$[p]]]]",
      "        + \"}\";",
      "  }]",

      // equals(Object)
      "$[equals?\n  @Override",
      "  public boolean equals(Object o) {",
      "    if (o == this) {",
      "      return true;",
      "    }",
      "    if (o instanceof $[origClass]) {",
      "      $[origClass]$[wildcardTypes] that = ($[origClass]$[wildcardTypes]) o;",
      "      return $[props!true]" +
                   "$[props:p|\n          && |($[p.equalsThatExpression])];",
      "    }",
      "    return false;",
      "  }]",

      // hashCode()
      "$[hashCode?",
      "  @Override",
      "  public int hashCode() {",
      "    int h = 1;",
      "$[props:p||" +
      "    h *= 1000003;",
      "    h ^= $[p.hashCodeExpression];",
      "]" +
      "    return h;",
      "  }]" +

      // serialVersionUID
      "$[serialVersionUID?\n\n  private static final long serialVersionUID = $[serialVersionUID];]",

          // parcelable
      "$[parcelable?\n\n",
      "  public static final android.os.Parcelable.Creator<$[origClass]> CREATOR = new android.os.Parcelable.Creator<$[origClass]>() {",
      "    @Override public $[origClass] createFromParcel(android.os.Parcel in) {",
      "      return new $[subclass](in);",
      "    }",
      "    @Override public $[origClass][] newArray(int size) {",
      "      return new $[origClass][size];",
      "    }",
      "  };",
      "",
      "  private final static java.lang.ClassLoader CL = $[subclass].class.getClassLoader();",
      "",
      "  private $[subclass](android.os.Parcel in) {",
      "    this(\n      $[props:p|,\n      |($[p.castType]) in.readValue(CL)]);",
      "  }",
      "",
      "  @Override public void writeToParcel(android.os.Parcel dest, int flags) {",
      "$[props:p||    dest.writeValue($[p]);\n]",
      "  }",
      "",
      "  @Override public int describeContents() {",
      "    return 0;",
      "  }",
      "]",

      "}"
      // CHECKSTYLE:ON
  );
  private static final Template TEMPLATE = Template.compile(TEMPLATE_STRING);

  @Override
  Template template() {
    return TEMPLATE;
  }
}
