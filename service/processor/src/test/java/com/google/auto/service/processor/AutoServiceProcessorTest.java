/*
 * Copyright 2008 Google LLC
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

import static com.google.auto.service.processor.AutoServiceProcessor.CONTAINS_CONSECUTIVE_DOT;
import static com.google.auto.service.processor.AutoServiceProcessor.CONTAINS_SINGLE_DOT;
import static com.google.auto.service.processor.AutoServiceProcessor.MISSING_PLUGIN_NAME_ERROR;
import static com.google.auto.service.processor.AutoServiceProcessor.MISSING_SERVICES_ERROR;
import static com.google.auto.service.processor.AutoServiceProcessor.NOT_ALLOWED_NAMESPACES;
import static com.google.auto.service.processor.AutoServiceProcessor.STARTS_OR_ENDS_WITH_DOT;
import static com.google.auto.service.processor.AutoServiceProcessor.UNSPPORTED_CHARACTERS;
import static com.google.testing.compile.JavaSourcesSubject.assertThat;

import com.google.common.io.ByteSource;
import com.google.testing.compile.JavaFileObjects;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.tools.StandardLocation;

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

  @Test
  public void pluginName() {
    String data = "implementation-class=test.AnotherPlugin";
    ByteSource expected = ByteSource.wrap(data.getBytes());
    assertThat(JavaFileObjects.forResource("test/AnotherPlugin.java"))
        .processedWith(new AutoServiceProcessor())
        .compilesWithoutError()
        .and()
        .generatesFileNamed(StandardLocation.CLASS_OUTPUT, "", "META-INF/gradle-plugins/com.example.properties")
        .withContents(expected);
  }

  @Test
  public void noPluginName() {
    assertThat(JavaFileObjects.forResource("test/NoPluginNamePlugin.java"))
        .processedWith(new AutoServiceProcessor())
        .failsToCompile()
        .withErrorContaining(MISSING_PLUGIN_NAME_ERROR);
  }

  @Test
  public void noDotPlugin() {
    assertThat(JavaFileObjects.forResource("test/NoDotPlugin.java"))
        .processedWith(new AutoServiceProcessor())
        .failsToCompile()
        .withErrorContaining(CONTAINS_SINGLE_DOT);
  }

  @Test
  public void startsWithDotPlugin() {
    assertThat(JavaFileObjects.forResource("test/StartsWithDotPlugin.java"))
        .processedWith(new AutoServiceProcessor())
        .failsToCompile()
        .withErrorContaining(STARTS_OR_ENDS_WITH_DOT);
  }

  @Test
  public void endsWithDotPlugin() {
    assertThat(JavaFileObjects.forResource("test/EndsWithDotPlugin.java"))
        .processedWith(new AutoServiceProcessor())
        .failsToCompile()
        .withErrorContaining(STARTS_OR_ENDS_WITH_DOT);
  }

  @Test
  public void consecutiveDotsPlugin() {
    assertThat(JavaFileObjects.forResource("test/ConsecutiveDotsPlugin.java"))
        .processedWith(new AutoServiceProcessor())
        .failsToCompile()
        .withErrorContaining(CONTAINS_CONSECUTIVE_DOT);
  }

  @Test
  public void unSupportedCharactersPlugin() {
    assertThat(JavaFileObjects.forResource("test/UnSupportedCharactersPlugin.java"))
        .processedWith(new AutoServiceProcessor())
        .failsToCompile()
        .withErrorContaining(UNSPPORTED_CHARACTERS);
  }

  @Test
  public void notAllowedNamespacePlugin() {
    assertThat(JavaFileObjects.forResource("test/NotAllowedNamespacePlugin.java"))
        .processedWith(new AutoServiceProcessor())
        .failsToCompile()
        .withErrorContaining(NOT_ALLOWED_NAMESPACES);
  }
}
