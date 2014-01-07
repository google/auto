package com.google.auto.value.processor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class TemplateTest extends TestCase {
  public void testEmpty() {
    Template t = Template.compile("");
    assertEquals("", t.rewrite(Collections.<String, Object>emptyMap()));
  }

  public void testSimple() {
    String s = "They call me Mellow Yellow";
    Template t = Template.compile(s);
    assertEquals(s, t.rewrite(Collections.singletonMap("irrelevant", null)));
  }

  public void testVar() {
    Template t = Template.compile("one=$[one] two=$[two]");
    Map<String, ?> vars = ImmutableMap.of("one", 1, "two", 2);
    assertEquals("one=1 two=2", t.rewrite(vars));
  }

  public void testCompoundVar() {
    Template t = Template.compile("one=$[list.iterator.next]");
    Map<String, ?> vars = ImmutableMap.of("list", ImmutableList.of(1, 2, 3));
    assertEquals("one=1", t.rewrite(vars));
  }

  public void testSimpleConditional() {
    Template t = Template.compile("one=$[one?1] two=$[two?2]");
    Map<String, ?> vars = ImmutableMap.of("one", true, "two", false);
    assertEquals("one=1 two=", t.rewrite(vars));
  }

  public void testSimpleNegativeConditional() {
    Template t = Template.compile("one=$[notone!1] two=$[nottwo!2]");
    Map<String, ?> vars = ImmutableMap.of("notone", true, "nottwo", false);
    assertEquals("one= two=2", t.rewrite(vars));
  }

  public void testIfElse() {
    Template t = Template.compile("number=$[one?[1][$[nottwo![2][3]]]]");
    Map<String, ?> vars1 = ImmutableMap.of("one", true);
    assertEquals("number=1", t.rewrite(vars1));
    Map<String, ?> vars2 = ImmutableMap.of("one", false, "nottwo", false);
    assertEquals("number=2", t.rewrite(vars2));
    Map<String, ?> vars3 = ImmutableMap.of("one", false, "nottwo", true);
    assertEquals("number=3", t.rewrite(vars3));
  }

  public void testIterate() {
    Template t = Template.compile("list=$[list:i| |($[i])]");
    Map<String, ?> vars = ImmutableMap.of("list", Arrays.asList("foo", "bar"));
    assertEquals("list=(foo) (bar)", t.rewrite(vars));
  }

  public void testNestedIterate() {
    Template t = Template.compile("list=$[list:i| |($[list:j|+|($[i]$[j])])]");
    Map<String, ?> vars = ImmutableMap.of("list", Arrays.asList("foo", "bar"));
    assertEquals("list=((foofoo)+(foobar)) ((barfoo)+(barbar))", t.rewrite(vars));
  }

  public void testComment() {
    Template t = Template.compile("one\n#two\n  #three\nfour#five\n");
    assertEquals("one\nfour\n", t.rewrite(ImmutableMap.<String, Object>of()));
  }
}
