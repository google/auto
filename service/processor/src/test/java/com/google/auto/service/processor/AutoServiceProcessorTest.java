/*
 * Copyright (C) 2008 Google, Inc.
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
package com.google.auto.service.processor;

import static com.google.auto.service.processor.AutoServiceProcessor.MISSING_SERVICES_ERROR;
import static com.google.testing.compile.JavaSourcesSubject.assertThat;

import com.google.testing.compile.JavaFileObjects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the {@link AutoServiceProcessor}.
 */
@RunWith(JUnit4.class)
public class AutoServiceProcessorTest {
  @Test
  public void autoService() {
      assertThat(
            JavaFileObjects.forResource("test/SomeService.java"),
            JavaFileObjects.forResource("test/SomeServiceProvider1.java"),
            JavaFileObjects.forResource("test/SomeServiceProvider2.java"),
            JavaFileObjects.forResource("test/Enclosing.java"),
            JavaFileObjects.forResource("test/AnotherService.java"),
            JavaFileObjects.forResource("test/AnotherServiceProvider.java"))
        .processedWith(new AutoServiceProcessor())
        .compilesWithoutError()
        .and().generatesFiles(
            JavaFileObjects.forResource("META-INF/services/test.SomeService"),
            JavaFileObjects.forResource("META-INF/services/test.AnotherService"));
  }

  @Test
  public void multiService() {
    assertThat(
            JavaFileObjects.forResource("test/SomeService.java"),
            JavaFileObjects.forResource("test/AnotherService.java"),
            JavaFileObjects.forResource("test/MultiServiceProvider.java"))
        .processedWith(new AutoServiceProcessor())
        .compilesWithoutError()
        .and().generatesFiles(
            JavaFileObjects.forResource("META-INF/services/test.SomeServiceMulti"),
            JavaFileObjects.forResource("META-INF/services/test.AnotherServiceMulti"));
  }

  @Test
  public void badMultiService() {
    assertThat(JavaFileObjects.forResource("test/NoServices.java"))
        .processedWith(new AutoServiceProcessor())
        .failsToCompile()
        .withErrorContaining(MISSING_SERVICES_ERROR);
  }
}
