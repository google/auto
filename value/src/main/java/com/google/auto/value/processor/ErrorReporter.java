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

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

/**
 * Handle error reporting for an annotation processor.
 *
 * @see AutoValue
 * @author Éamonn McManus
 */
class ErrorReporter {
  private final Messager messager;

  ErrorReporter(ProcessingEnvironment processingEnv) {
    this.messager = processingEnv.getMessager();
  }

  /**
   * Issue a compilation note.
   *
   * @param msg the text of the note
   * @param e the element to which it pertains
   */
  void reportNote(String msg, Element e) {
    messager.printMessage(Diagnostic.Kind.NOTE, msg, e);
  }

  /**
   * Issue a compilation warning.
   *
   * @param msg the text of the warning
   * @param e the element to which it pertains
   */
  void reportWarning(String msg, Element e) {
    messager.printMessage(Diagnostic.Kind.WARNING, msg, e);
  }

  /**
   * Issue a compilation error. This method does not throw an exception, since we want to continue
   * processing and perhaps report other errors. It is a good idea to introduce a test case in
   * CompilationTest for any new call to reportError(...) to ensure that we continue correctly after
   * an error.
   *
   * @param msg the text of the warning
   * @param e the element to which it pertains
   */
  void reportError(String msg, Element e) {
    messager.printMessage(Diagnostic.Kind.ERROR, msg, e);
  }

  /**
   * Issue a compilation error and abandon the processing of this class. This does not prevent the
   * processing of other classes.
   *
   * @param msg the text of the error
   * @param e the element to which it pertains
   */
  void abortWithError(String msg, Element e) {
    reportError(msg, e);
    throw new AbortProcessingException();
  }
}