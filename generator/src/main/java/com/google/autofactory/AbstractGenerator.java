/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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
package com.google.autofactory;

import com.squareup.java.JavaWriter;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

public abstract class AbstractGenerator {

  private final ProcessingEnvironment env;

  protected AbstractGenerator(ProcessingEnvironment env) {
    this.env = env;
  }

  protected JavaWriter newWriter(String adapterName, TypeElement type) throws IOException {
    JavaFileObject sourceFile = env.getFiler().createSourceFile(adapterName, type);
    return new JavaWriter(sourceFile.openWriter());
  }

  protected void error(String format, Object... args) {
    env.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format(format, args));
  }

  protected void warn(String format, Object... args) {
    env.getMessager().printMessage(Diagnostic.Kind.WARNING, String.format(format, args));
  }

  static String fieldName(boolean disambiguateFields, Element field) {
    return (disambiguateFields ? "field_" : "") + field.getSimpleName().toString();
  }

  static String parameterName(boolean disambiguateFields, Element parameter) {
    return (disambiguateFields ? "parameter_" : "") + parameter.getSimpleName().toString();
  }

}