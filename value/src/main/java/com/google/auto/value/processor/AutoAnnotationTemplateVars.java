/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.value.processor.escapevelocity.Template;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * The variables to substitute into the autoannotation.vm template.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@SuppressWarnings("unused")  // the fields in this class are only read via reflection
class AutoAnnotationTemplateVars extends TemplateVars {
  /**
   * The members of the annotation being implemented.
   */
  Map<String, AutoAnnotationProcessor.Member> members;

  /**
   * The parameters in the {@code @AutoAnnotation} method, which are also the constructor parameters
   * in the generated class.
   */
  Map<String, AutoAnnotationProcessor.Parameter> params;

  /**
   * The fully-qualified names of the classes to be imported in the generated class.
   */
  SortedSet<String> imports;

  /**
   * The spelling of the javax.annotation.Generated class: Generated or javax.annotation.Generated.
   */
  String generated;

  /** The spelling of the java.util.Arrays class: Arrays or java.util.Arrays. */
  String arrays;

  /**
   * The package of the class containing the {@code @AutoAnnotation} annotation, which is also the
   * package where the annotation implementation will be generated.
   */
  String pkg;

  /**
   * The simple name of the generated class, like {@code AutoAnnotation_Foo_bar}.
   */
  String className;

  /**
   * The name of the annotation interface as it can be referenced in the generated code.
   */
  String annotationName;

  /**
   * The fully-qualified name of the annotation interface.
   */
  String annotationFullName;

  /**
   * The wrapper types (like {@code Integer.class}) that are referenced in collection parameters
   * (like {@code List<Integer>}).
   */
  Set<Class<?>> wrapperTypesUsedInCollections;

  /**
   * True if this annotation is marked {@code @GwtCompatible}. That means that we can't use
   * {@code clone()} to make a copy of an array.
   */
  Boolean gwtCompatible;

  private static final Template TEMPLATE = parsedTemplateForString(AutoAnnotationVm.VM);

  @Override
  Template parsedTemplate() {
    return TEMPLATE;
  }
}
