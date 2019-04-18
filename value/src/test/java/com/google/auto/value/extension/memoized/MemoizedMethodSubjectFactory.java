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

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

final class MemoizedMethodSubjectFactory implements Subject.Factory<MemoizedMethodSubject, String> {

  static MemoizedMethodSubjectFactory memoizeMethod() {
    return new MemoizedMethodSubjectFactory();
  }

  static MemoizedMethodSubject assertThatMemoizeMethod(String method) {
    return assertAbout(memoizeMethod()).that(method);
  }

  @Override
  public MemoizedMethodSubject createSubject(FailureMetadata failureMetadata, String that) {
    return new MemoizedMethodSubject(failureMetadata, that);
  }
}
