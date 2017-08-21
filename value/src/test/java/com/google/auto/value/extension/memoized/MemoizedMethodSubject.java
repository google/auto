/*
 * Copyright (C) 2016 Google, Inc.
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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

import com.google.auto.value.processor.AutoValueProcessor;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;

final class MemoizedMethodSubject extends Subject<MemoizedMethodSubject, String> {

  MemoizedMethodSubject(FailureStrategy failureStrategy, String subject) {
    super(failureStrategy, subject);
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
            getSubject(),
            "}");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new AutoValueProcessor(ImmutableList.of(new MemoizeExtension())))
        .failsToCompile()
        .withErrorContaining(error)
        .in(file)
        .onLine(6);
  }
}