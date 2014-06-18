package com.google.auto.value.processor;

import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.node.SimpleNode;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

/**
 * Tests for FieldReader.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class TemplateVarsTest extends TestCase {
  static class HappyVars extends TemplateVars {
    Integer integer;
    String string;
    List<Integer> list;
    private static final String IGNORED_STATIC_FINAL = "hatstand";

    @Override SimpleNode parsedTemplate() {
      return parsedTemplateForString("integer=$integer string=$string list=$list");
    }
  }

  static SimpleNode parsedTemplateForString(String string) {
    try {
      Reader reader = new StringReader(string);
      return RuntimeSingleton.parse(reader, string);
    } catch (ParseException e) {
      throw new AssertionError(e);
    }
  }

  public void testHappy() {
    HappyVars happy = new HappyVars();
    happy.integer = 23;
    happy.string = "wibble";
    happy.list = ImmutableList.of(5, 17, 23);
    assertEquals("hatstand", HappyVars.IGNORED_STATIC_FINAL);  // just to avoid unused warning
    String expectedText = "integer=23 string=wibble list=[5, 17, 23]";
    String actualText = happy.toText();
    assertEquals(expectedText, actualText);
  }

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

  static class SubSub extends HappyVars {}

  public void testSubSub() {
    try {
      new SubSub();
      fail("Did not get expected exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  static class Private extends TemplateVars {
    Integer integer;
    private String string;

    @Override SimpleNode parsedTemplate() {
      throw new UnsupportedOperationException();
    }
  }

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

    @Override SimpleNode parsedTemplate() {
      throw new UnsupportedOperationException();
    }
  }

  public void testStatic() {
    try {
      new Static();
      fail("Did not get expected exception");
    } catch (IllegalArgumentException expected) {
    }
  }

  static class Primitive extends TemplateVars{
    int integer;
    String string;

    @Override SimpleNode parsedTemplate() {
      throw new UnsupportedOperationException();
    }
  }

  public void testPrimitive() {
    try {
      new Primitive();
      fail("Did not get expected exception");
    } catch (IllegalArgumentException expected) {
    }
  }
}
