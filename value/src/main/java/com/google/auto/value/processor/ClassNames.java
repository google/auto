/*
 * Copyright 2018 Google LLC
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

/**
 * Names of classes that are referenced in the processors.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
final class ClassNames {
  private ClassNames() {}

  static final String AUTO_VALUE_PACKAGE_NAME = "com.google.auto.value.";
  static final String AUTO_ANNOTATION_NAME = AUTO_VALUE_PACKAGE_NAME + "AutoAnnotation";
  static final String AUTO_ONE_OF_NAME = AUTO_VALUE_PACKAGE_NAME + "AutoOneOf";
  static final String AUTO_VALUE_NAME = AUTO_VALUE_PACKAGE_NAME + "AutoValue";
  static final String AUTO_VALUE_BUILDER_NAME = AUTO_VALUE_NAME + ".Builder";
  static final String AUTO_BUILDER_NAME = AUTO_VALUE_PACKAGE_NAME + "AutoBuilder";
  static final String COPY_ANNOTATIONS_NAME = AUTO_VALUE_NAME + ".CopyAnnotations";
  static final String KOTLIN_METADATA_NAME = "kotlin.Metadata";
}
