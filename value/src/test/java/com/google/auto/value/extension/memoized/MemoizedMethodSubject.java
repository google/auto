/*
 * Copyright 2016 Google LLC
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

package com.google.auto.value.extension.memoized;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.auto.value.extension.memoized.processor.MemoizeExtension;
import com.google.auto.value.processor.AutoValueProcessor;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;

final class MemoizedMethodSubject extends Subject {
  private final String actual;

  MemoizedMethodSubject(FailureMetadata failureMetadata, String actual) {
    super(failureMetadata, actual);
    this.actual = actual;
  }

  void hasError(String error) {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "Value",
            "import com.google.auto.value.AutoValue;",
            "import com.google.auto.value.extension.memoized.Memoized;",
            "",
            "@AutoValue abstract class Value {",
            "  abstract String string();",
            actual,
            "}");
    Compilation compilation =
        javac()
            .withProcessors(new AutoValueProcessor(ImmutableList.of(new MemoizeExtension())))
            .compile(file);
    assertThat(compilation).hadErrorContaining(error).inFile(file).onLineContaining(actual);
  }
}
