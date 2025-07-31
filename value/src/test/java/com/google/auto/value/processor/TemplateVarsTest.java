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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.escapevelocity.Template;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for FieldReader.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class TemplateVarsTest {
  static class HappyVars extends TemplateVars {
    Integer integer;
    String string;
    List<Integer> list;
    private static final String IGNORED_STATIC_FINAL = "hatstand";

    @Override
    Template parsedTemplate() {
      return parsedTemplateForString("integer=$integer string=$string list=$list");
    }
  }

  static Template parsedTemplateForString(String string) {
    try {
      Reader reader = new StringReader(string);
      return Template.parseFrom(reader);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  public void testHappy() {
    HappyVars happy = new HappyVars();
    happy.integer = 23;
    happy.string = "wibble";
    happy.list = ImmutableList.of(5, 17, 23);
    assertThat(HappyVars.IGNORED_STATIC_FINAL).isEqualTo("hatstand"); // avoids unused warning
    String expectedText = "integer=23 string=wibble list=[5, 17, 23]";
    String actualText = happy.toText();
    assertThat(actualText).isEqualTo(expectedText);
  }

  @Test
  public void testUnset() {
    HappyVars sad = new HappyVars();
    sad.integer = 23;
    sad.list = ImmutableList.of(23);
    try {
      sad.toText();
      fail("Did not get expected exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  static class SubHappyVars extends HappyVars {
    Character character;

    @Override
    Template parsedTemplate() {
      return parsedTemplateForString(
          "integer=$integer string=$string list=$list character=$character");
    }
  }

  @Test
  public void testSubSub() {
    SubHappyVars vars = new SubHappyVars();
    vars.integer = 23;
    vars.string = "wibble";
    vars.list = ImmutableList.of(5, 17, 23);
    vars.character = 'ß';
    String expectedText = "integer=23 string=wibble list=[5, 17, 23] character=ß";
    String actualText = vars.toText();
    assertThat(actualText).isEqualTo(expectedText);
  }

  static class Private extends TemplateVars {
    Integer integer;
    private String unusedString;

    @Override
    Template parsedTemplate() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testPrivate() {
    try {
      new Private();
      fail("Did not get expected exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  static class Static extends TemplateVars {
    Integer integer;
    static String string;

    @Override
    Template parsedTemplate() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testStatic() {
    try {
      new Static();
      fail("Did not get expected exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  static class Primitive extends TemplateVars {
    int integer;
    String string;

    @Override
    Template parsedTemplate() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testPrimitive() {
    try {
      new Primitive();
      fail("Did not get expected exception");
    } catch (IllegalArgumentException expected) {
    }
  }
}
