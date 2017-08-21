/*
 * Copyright (C) 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.auto.value.processor.escapevelocity;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Expect;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class TemplateTest {
  @Rule public TestName testName = new TestName();
  @Rule public Expect expect = Expect.create();
  @Rule public ExpectedException thrown = ExpectedException.none();

  private RuntimeInstance velocityRuntimeInstance;

  @Before
  public void setUp() {
    velocityRuntimeInstance = new RuntimeInstance();

    // Ensure that $undefinedvar will produce an exception rather than outputting $undefinedvar.
    velocityRuntimeInstance.setProperty(RuntimeConstants.RUNTIME_REFERENCES_STRICT, "true");
    velocityRuntimeInstance.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
        new NullLogChute());

    // Disable any logging that Velocity might otherwise see fit to do.
    velocityRuntimeInstance.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogChute());

    velocityRuntimeInstance.init();
  }

  private void compare(String template) {
    compare(template, ImmutableMap.<String, Object>of());
  }

  private void compare(String template, Map<String, ?> vars) {
    compare(template, Suppliers.ofInstance(vars));
  }

  /**
   * Checks that the given template and the given variables produce identical results with
   * Velocity and EscapeVelocity. This uses a {@code Supplier} to define the variables to cover
   * test cases that involve modifying the values of the variables. Otherwise the run using
   * Velocity would change those values so that the run using EscapeVelocity would not be starting
   * from the same point.
   */
  private void compare(String template, Supplier<? extends Map<String, ?>> varsSupplier) {
    Map<String, ?> velocityVars = varsSupplier.get();
    String velocityRendered = velocityRender(template, velocityVars);
    Map<String, ?> escapeVelocityVars = varsSupplier.get();
    String escapeVelocityRendered;
    try {
      escapeVelocityRendered =
          Template.parseFrom(new StringReader(template)).evaluate(escapeVelocityVars);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    String failure = "from velocity: <" + velocityRendered + ">\n"
        + "from escape velocity: <" + escapeVelocityRendered + ">\n";
    expect.withMessage(failure).that(escapeVelocityRendered).isEqualTo(velocityRendered);
  }

  private String velocityRender(String template, Map<String, ?> vars) {
    VelocityContext velocityContext = new VelocityContext(new TreeMap<String, Object>(vars));
    StringWriter writer = new StringWriter();
    SimpleNode parsedTemplate;
    try {
      parsedTemplate = velocityRuntimeInstance.parse(
          new StringReader(template), testName.getMethodName());
    } catch (org.apache.velocity.runtime.parser.ParseException e) {
      throw new AssertionError(e);
    }
    boolean rendered = velocityRuntimeInstance.render(
        velocityContext, writer, parsedTemplate.getTemplateName(), parsedTemplate);
    assertThat(rendered).isTrue();
    return writer.toString();
  }

  @Test
  public void empty() {
    compare("");
  }

  @Test
  public void literalOnly() {
    compare("In the reign of James the Second \n It was generally reckoned\n");
  }

  @Test
  public void comment() {
    compare("line 1 ##\n  line 2");
  }

  @Test
  public void substituteNoBraces() {
    compare(" $x ", ImmutableMap.of("x", 1729));
    compare(" ! $x ! ", ImmutableMap.of("x", 1729));
  }

  @Test
  public void substituteWithBraces() {
    compare("a${x}\nb", ImmutableMap.of("x", "1729"));
  }

  @Test
  public void substitutePropertyNoBraces() {
    compare("=$t.name=", ImmutableMap.of("t", Thread.currentThread()));
  }

  @Test
  public void substitutePropertyWithBraces() {
    compare("=${t.name}=", ImmutableMap.of("t", Thread.currentThread()));
  }

  @Test
  public void substituteNestedProperty() {
    compare("\n$t.name.empty\n", ImmutableMap.of("t", Thread.currentThread()));
  }

  @Test
  public void substituteMethodNoArgs() {
    compare("<$c.size()>", ImmutableMap.of("c", ImmutableMap.of()));
  }

  @Test
  public void substituteMethodOneArg() {
    compare("<$list.get(0)>", ImmutableMap.of("list", ImmutableList.of("foo")));
  }

  @Test
  public void substituteMethodTwoArgs() {
    compare("\n$s.indexOf(\"bar\", 2)\n", ImmutableMap.of("s", "barbarbar"));
  }

  @Test
  public void substituteMethodNoSynthetic() {
    // If we aren't careful, we'll see both the inherited `Set<K> keySet()` from Map
    // and the overridden `ImmutableSet<K> keySet()` in ImmutableMap.
    compare("$map.keySet()", ImmutableMap.of("map", ImmutableMap.of("foo", "bar")));
  }

  @Test
  public void substituteIndexNoBraces() {
    compare("<$map[\"x\"]>", ImmutableMap.of("map", ImmutableMap.of("x", "y")));
  }

  @Test
  public void substituteIndexWithBraces() {
    compare("<${map[\"x\"]}>", ImmutableMap.of("map", ImmutableMap.of("x", "y")));
  }

  @Test
  public void substituteIndexThenProperty() {
    compare("<$map[2].name>", ImmutableMap.of("map", ImmutableMap.of(2, getClass())));
  }

  @Test
  public void variableNameCantStartWithNonAscii() {
    compare("<$Éamonn>", ImmutableMap.<String, Object>of());
  }

  @Test
  public void variableNamesAreAscii() {
    compare("<$Pádraig>", ImmutableMap.of("P", "(P)"));
  }

  @Test
  public void variableNameCharacters() {
    compare("<AZaz-foo_bar23>", ImmutableMap.of("AZaz-foo_bar23", "(P)"));
  }

  public static class Indexable {
    public String get(String y) {
      return "[" + y + "]";
    }
  }

  @Test
  public void substituteExoticIndex() {
    // Any class with a get(X) method can be used with $x[i]
    compare("<$x[\"foo\"]>", ImmutableMap.of("x", new Indexable()));
  }

  @Test
  public void simpleSet() {
    compare("$x#set ($x = 17)#set ($y = 23) ($x, $y)", ImmutableMap.of("x", 1));
  }

  @Test
  public void newlineAfterSet() {
    compare("foo #set ($x = 17)\nbar", ImmutableMap.<String, Object>of());
  }

  @Test
  public void newlineInSet() {
    compare("foo #set ($x\n  = 17)\nbar $x", ImmutableMap.<String, Object>of());
  }

  @Test
  public void expressions() {
    compare("#set ($x = 1 + 1) $x");
    compare("#set ($x = 1 + 2 * 3) $x");
    compare("#set ($x = (1 + 1 == 2)) $x");
    compare("#set ($x = (1 + 1 != 2)) $x");
    compare("#set ($x = 22 - 7) $x");
    compare("#set ($x = 22 / 7) $x");
    compare("#set ($x = 22 % 7) $x");
  }

  @Test
  public void associativity() {
    compare("#set ($x = 3 - 2 - 1) $x");
    compare("#set ($x = 16 / 4 / 4) $x");
  }

  @Test
  public void precedence() {
    compare("#set ($x = 1 + 2 + 3 * 4 * 5 + 6) $x");
    compare("#set($x=1+2+3*4*5+6)$x");
    compare("#set ($x = 1 + 2 * 3 == 3 * 2 + 1) $x");
  }

  @Test
  public void and() {
    compare("#set ($x = false && false) $x");
    compare("#set ($x = false && true) $x");
    compare("#set ($x = true && false) $x");
    compare("#set ($x = true && true) $x");
  }

  @Test
  public void or() {
    compare("#set ($x = false || false) $x");
    compare("#set ($x = false || true) $x");
    compare("#set ($x = true || false) $x");
    compare("#set ($x = true || true) $x");
  }

  @Test
  public void not() {
    compare("#set ($x = !true) $x");
    compare("#set ($x = !false) $x");
  }

  @Test
  public void truthValues() {
    compare("#set ($x = $true && true) $x", ImmutableMap.of("true", true));
    compare("#set ($x = $false && true) $x", ImmutableMap.of("false", false));
    compare("#set ($x = $emptyCollection && true) $x",
        ImmutableMap.of("emptyCollection", ImmutableList.of()));
    compare("#set ($x = $emptyString && true) $x", ImmutableMap.of("emptyString", ""));
  }

  @Test
  public void numbers() {
    compare("#set ($x = 0) $x");
    compare("#set ($x = -1) $x");
    compare("#set ($x = " + Integer.MAX_VALUE + ") $x");
    compare("#set ($x = " + Integer.MIN_VALUE + ") $x");
  }

  private static final String[] RELATIONS = {"==", "!=", "<", ">", "<=", ">="};

  @Test
  public void intRelations() {
    int[] numbers = {-1, 0, 1, 17};
    for (String relation : RELATIONS) {
      for (int a : numbers) {
        for (int b : numbers) {
          compare("#set ($x = $a " + relation + " $b) $x",
              ImmutableMap.<String, Object>of("a", a, "b", b));
        }
      }
    }
  }

  @Test
  public void relationPrecedence() {
    compare("#set ($x = 1 < 2 == 2 < 1) $x");
    compare("#set ($x = 2 < 1 == 2 < 1) $x");
  }

  /**
   * Tests the surprising definition of equality mentioned in
   * {@link ExpressionNode.EqualsExpressionNode}.
   */
  @Test
  public void funkyEquals() {
    compare("#set ($t = (123 == \"123\")) $t");
    compare("#set ($f = (123 == \"1234\")) $f");
    compare("#set ($x = ($sb1 == $sb2)) $x", ImmutableMap.of(
        "sb1", (Object) new StringBuilder("123"),
        "sb2", (Object) new StringBuilder("123")));
  }

  @Test
  public void ifTrueNoElse() {
    compare("x#if (true)y #end z");
    compare("x#if (true)y #end  z");
    compare("x#if (true)y #end\nz");
    compare("x#if (true)y #end\n z");
    compare("x#if (true) y #end\nz");
    compare("x#if (true)\ny #end\nz");
    compare("x#if (true) y #end\nz");
    compare("$x #if (true) y #end $x ", ImmutableMap.of("x", "!"));
  }

  @Test
  public void ifFalseNoElse() {
    compare("x#if (false)y #end z");
    compare("x#if (false)y #end\nz");
    compare("x#if (false)y #end\n z");
    compare("x#if (false) y #end\nz");
    compare("x#if (false)\ny #end\nz");
    compare("x#if (false) y #end\nz");
  }

  @Test
  public void ifTrueWithElse() {
    compare("x#if (true) a #else b #end z");
  }

  @Test
  public void ifFalseWithElse() {
    compare("x#if (false) a #else b #end z");
  }

  @Test
  public void ifTrueWithElseIf() {
    compare("x#if (true) a #elseif (true) b #else c #end z");
  }

  @Test
  public void ifFalseWithElseIfTrue() {
    compare("x#if (false) a #elseif (true) b #else c #end z");
  }

  @Test
  public void ifFalseWithElseIfFalse() {
    compare("x#if (false) a #elseif (false) b #else c #end z");
  }

  @Test
  public void ifBraces() {
    compare("x#{if}(false)a#{elseif}(false)b #{else}c#{end}z");
  }
  @Test
  public void ifUndefined() {
    compare("#if ($undefined) really? #else indeed #end");
  }

  @Test
  public void forEach() {
    compare("x#foreach ($x in $c) <$x> #end y",
        ImmutableMap.of("c", ImmutableList.of()));
    compare("x#foreach ($x in $c) <$x> #end y",
        ImmutableMap.of("c", ImmutableList.of("foo", "bar", "baz")));
    compare("x#foreach ($x in $c) <$x> #end y",
        ImmutableMap.of("c", new String[] {"foo", "bar", "baz"}));
    compare("x#foreach ($x in $c) <$x> #end y",
        ImmutableMap.of("c", ImmutableMap.of("foo", "bar", "baz", "buh")));
  }

  @Test
  public void forEachHasNext() {
    compare("x#foreach ($x in $c) <$x#if ($foreach.hasNext), #end> #end y",
        ImmutableMap.of("c", ImmutableList.of()));
    compare("x#foreach ($x in $c) <$x#if ($foreach.hasNext), #end> #end y",
        ImmutableMap.of("c", ImmutableList.of("foo", "bar", "baz")));
  }

  @Test
  public void nestedForEach() {
    String template =
        "$x #foreach ($x in $listOfLists)\n"
        + "  #foreach ($y in $x)\n"
        + "    ($y)#if ($foreach.hasNext), #end\n"
        + "  #end#if ($foreach.hasNext); #end\n"
        + "#end\n"
        + "$x\n";
    Object listOfLists = ImmutableList.of(
        ImmutableList.of("foo", "bar", "baz"), ImmutableList.of("fred", "jim", "sheila"));
    compare(template, ImmutableMap.of("x", 23, "listOfLists", listOfLists));
  }

  @Test
  public void forEachScope() {
    String template =
        "$x #foreach ($x in $list)\n"
        + "[$x]\n"
        + "#set ($x = \"bar\")\n"
        + "#set ($othervar = \"baz\")\n"
        + "#end\n"
        + "$x $othervar";
    compare(
        template, ImmutableMap.of("x", "foo", "list", ImmutableList.of("blim", "blam", "blum")));
  }

  @Test
  public void setSpacing() {
    // The spacing in the output from #set is eccentric.
    compare("x#set ($x = 0)x");
    compare("x #set ($x = 0)x");
    compare("x #set ($x = 0) x");
    compare("$x#set ($x = 0)x", ImmutableMap.of("x", "!"));

    // Velocity WTF: the #set eats the space after $x and other references, so the output is <!x>.
    compare("$x  #set ($x = 0)x", ImmutableMap.of("x", "!"));
    compare("$x.length()  #set ($x = 0)x", ImmutableMap.of("x", "!"));
    compare("$x.empty  #set ($x = 0)x", ImmutableMap.of("x", "!"));
    compare("$x[0]  #set ($x = 0)x", ImmutableMap.of("x", ImmutableList.of("!")));

    compare("x#set ($x = 0)\n  $x!");

    compare("x  #set($x = 0)  #set($x = 0)  #set($x = 0)  y");

    compare("x ## comment\n  #set($x = 0)  y");
  }

  @Test
  public void simpleMacro() {
    String template =
        "xyz\n"
        + "#macro (m)\n"
        + "hello world\n"
        + "#end\n"
        + "#m() abc #m()\n";
    compare(template);
  }

  @Test
  public void macroWithArgs() {
    String template =
        "$x\n"
        + "#macro (m $x $y)\n"
        + "  #if ($x < $y) less #else greater #end\n"
        + "#end\n"
        + "#m(17 23) #m(23 17) #m(17 17)\n"
        + "$x";
    compare(template, ImmutableMap.of("x", "tiddly"));
  }

  /**
   * Tests defining a macro inside a conditional. This proves that macros are not evaluated in the
   * main control flow, but rather are extracted at parse time. It also tests what happens if there
   * is more than one definition of the same macro. (It is not apparent from the test, but it is the
   * first definition that is retained.)
   */
  @Test
  public void conditionalMacroDefinition() {
    String templateFalse =
        "#if (false)\n"
        + "  #macro (m) foo #end\n"
        + "#else\n"
        + "  #macro (m) bar #end\n"
        + "#end\n"
        + "#m()\n";
    compare(templateFalse);

    String templateTrue =
        "#if (true)\n"
        + "  #macro (m) foo #end\n"
        + "#else\n"
        + "  #macro (m) bar #end\n"
        + "#end\n"
        + "#m()\n";
    compare(templateTrue);
  }

  /**
   * Tests referencing a macro before it is defined. Since macros are extracted at parse time but
   * references are only used at evaluation time, this works.
   */
  @Test
  public void forwardMacroReference() {
    String template =
        "#m(17)\n"
        + "#macro (m $x)\n"
        + "  !$x!\n"
        + "#end";
    compare(template);
  }

  @Test
  public void macroArgsSeparatedBySpaces() {
    String template =
        "#macro (sum $x $y $z)\n"
        + "  #set ($sum = $x + $y + $z)\n"
        + "  $sum\n"
        + "#end\n"
        + "#sum ($list[0] $list.get(1) 5)\n";
    compare(template, ImmutableMap.of("list", ImmutableList.of(3, 4)));
  }

  @Test
  public void macroArgsSeparatedByCommas() {
    String template =
        "#macro (sum $x $y $z)\n"
        + "  #set ($sum = $x + $y + $z)\n"
        + "  $sum\n"
        + "#end\n"
        + "#sum ($list[0],$list.get(1),5)\n";
    compare(template, ImmutableMap.of("list", ImmutableList.of(3, 4)));
  }

  // The following tests are based on http://wiki.apache.org/velocity/MacroEvaluationStrategy.
  // They verify some of the trickier details of Velocity's call-by-name semantics.

  @Test
  public void callBySharing() {
    // The example on the web page is wrong because $map.put('x', 'a') evaluates to null, which
    // Velocity rejects as a render error. We fix this by ensuring that the returned previous value
    // is not null.
    // Here, the value of $y should not be affected by #set($x = "a"), even though the name passed
    // to $x is $y.
    String template =
        "#macro(callBySharing $x $map)\n"
        + "  #set($x = \"a\")\n"
        + "  $map.put(\"x\", \"a\")\n"
        + "#end\n"
        + "#callBySharing($y $map)\n"
        + "y is $y\n"
        + "map[x] is $map[\"x\"]\n";
    Supplier<Map<String, Object>> makeMap = new Supplier<Map<String, Object>>() {
      @Override public Map<String, Object> get() {
        return ImmutableMap.<String, Object>of(
            "y", "y",
            "map", new HashMap<String, Object>(ImmutableMap.of("x", (Object) "foo")));
      }
    };
    compare(template, makeMap);
  }

  @Test
  public void callByMacro() {
    // Since #callByMacro1 never references its argument, $x.add("t") is never evaluated during it.
    // Since #callByMacro2 references its argument twice, $x.add("t") is evaluated twice during it.
    String template =
        "#macro(callByMacro1 $p)\n"
        + "  not using\n"
        + "#end\n"
        + "#macro(callByMacro2 $p)\n"
        + "  using: $p\n"
        + "  using again: $p\n"
        + "  using again: $p\n"
        + "#end\n"
        + "#callByMacro1($x.add(\"t\"))\n"
        + "x = $x\n"
        + "#callByMacro2($x.add(\"t\"))\n"
        + "x = $x\n";
    Supplier<Map<String, Object>> makeMap = new Supplier<Map<String, Object>>() {
      @Override public Map<String, Object> get() {
        return ImmutableMap.<String, Object>of("x", new ArrayList<Object>());
      }
    };
    compare(template, makeMap);
  }

  @Test
  public void callByValue() {
    // The assignments to the macro parameters $a and $b cause those parameters to be shadowed,
    // so the output is: a b becomes b a.
    String template =
        "#macro(callByValueSwap $a $b)\n"
        + "  $a $b becomes\n"
        + "  #set($tmp = $a)\n"
        + "  #set($a = $b)\n"
        + "  #set($b = $tmp)\n"
        + "  $a $b\n"
        + "#end"
        + "#callByValueSwap(\"a\", \"b\")";
    compare(template);
  }

  // First "Call by macro expansion example" doesn't apply as long as we don't have map literals.

  @Test
  public void nameCaptureSwap() {
    // Here, the arguments $a and $b are variables rather than literals, which means that their
    // values change when we set those variables. #set($tmp = $a) changes the meaning of $b since
    // $b is the name $tmp. So #set($a = $b) shadows parameter $a with the value of $tmp, which we
    // have just set to "a". Then #set($b = $tmp) shadows parameter $b also with the value of $tmp.
    // The end result is: a b becomes a a.
    String template =
        "#macro(nameCaptureSwap $a $b)\n"
        + "  $a $b becomes\n"
        + "  #set($tmp = $a)\n"
        + "  #set($a = $b)\n"
        + "  #set($b = $tmp)\n"
        + "  $a $b\n"
        + "#end\n"
        + "#set($x = \"a\")\n"
        + "#set($tmp = \"b\")\n"
        + "#nameCaptureSwap($x $tmp)";
    compare(template);
  }

  @Test
  public void undefinedMacro() throws IOException {
    String template = "#oops()";
    thrown.expect(ParseException.class);
    thrown.expectMessage("#oops is neither a standard directive nor a macro that has been defined");
    Template.parseFrom(new StringReader(template));
  }

  @Test
  public void macroArgumentMismatch() throws IOException {
    String template =
        "#macro (twoArgs $a $b) $a $b #end\n"
        + "#twoArgs(23)\n";
    thrown.expect(ParseException.class);
    thrown.expectMessage("Wrong number of arguments to #twoArgs: expected 2, got 1");
    Template.parseFrom(new StringReader(template));
  }

}
