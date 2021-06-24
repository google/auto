/*
 * Copyright 2012 Google LLC
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

import com.google.escapevelocity.Template;

/**
 * The variables to substitute into the autovalue.vm template.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@SuppressWarnings("unused") // the fields in this class are only read via reflection
class AutoValueTemplateVars extends AutoValueOrBuilderTemplateVars {

  /**
   * The encoding of the {@code @GwtCompatible} annotation to add to this class, or an empty string
   * if there is none. A non-empty value will look something like {@code
   * "@`com.google.common.annotations.GwtCompatible`(serializable = true)"}, where the {@code ``}
   * represent the encoding used by {@link TypeEncoder}.
   */
  String gwtCompatibleAnnotation;

  /** The simple name of the generated subclass. */
  String subclass;
  /**
   * The modifiers (for example {@code final} or {@code abstract}) for the generated subclass,
   * followed by a space if they are not empty.
   */
  String modifiers;

  private static final Template TEMPLATE = parsedTemplateForResource("autovalue.vm");

  @Override
  Template parsedTemplate() {
    return TEMPLATE;
  }
}
