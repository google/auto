/*
 * Copyright 2014 Google LLC
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

import com.google.errorprone.annotations.FormatMethod;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

/**
 * Handle error reporting for an annotation processor.
 *
 * @author Éamonn McManus
 */
class ErrorReporter {
  private final Messager messager;
  private int errorCount;

  ErrorReporter(ProcessingEnvironment processingEnv) {
    this.messager = processingEnv.getMessager();
  }

  /**
   * Issue a compilation note.
   *
   * @param e the element to which it pertains
   * @param format the format string for the text of the note
   * @param args arguments for the format string
   */
  @FormatMethod
  void reportNote(Element e, String format, Object... args) {
    messager.printMessage(Diagnostic.Kind.NOTE, String.format(format, args), e);
  }

  /**
   * Issue a compilation warning.
   *
   * @param e the element to which it pertains
   * @param format the format string for the text of the warning
   * @param args arguments for the format string
   */
  @FormatMethod
  void reportWarning(Element e, String format, Object... args) {
    messager.printMessage(Diagnostic.Kind.WARNING, String.format(format, args), e);
  }

  /**
   * Issue a compilation error. This method does not throw an exception, since we want to continue
   * processing and perhaps report other errors. It is a good idea to introduce a test case in
   * CompilationTest for any new call to reportError(...) to ensure that we continue correctly after
   * an error.
   *
   * @param e the element to which it pertains
   * @param format the format string for the text of the warning
   * @param args arguments for the format string
   */
  @FormatMethod
  void reportError(Element e, String format, Object... args) {
    messager.printMessage(Diagnostic.Kind.ERROR, String.format(format, args), e);
    errorCount++;
  }

  /**
   * Issue a compilation error and abandon the processing of this class. This does not prevent the
   * processing of other classes.
   *
   * @param e the element to which it pertains
   * @param format the format string for the text of the error
   * @param args arguments for the format string
   * @return This method does not return, but is declared with an exception return type so you
   *     can write {@code throw abortWithError(...)} to tell the compiler that.
   * @throws AbortProcessingException always
   */
  @FormatMethod
  AbortProcessingException abortWithError(Element e, String format, Object... args) {
    reportError(e, format, args);
    throw new AbortProcessingException();
  }

  /** The number of errors that have been output by calls to {@link #reportError}. */
  int errorCount() {
    return errorCount;
  }

  /** Abandon the processing of this class if any errors have been output. */
  void abortIfAnyError() {
    if (errorCount > 0) {
      throw new AbortProcessingException();
    }
  }
}
