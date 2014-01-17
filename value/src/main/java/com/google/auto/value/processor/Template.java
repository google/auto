/*
 * Copyright (C) 2012 Google, Inc.
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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template processor for a small templating language.
 * The idea is that a certain numver of variables are defined beforehand, and you can then write:
 * <ul>
 *   <li>$[var] to get the toString() value of that variable
 *   <li>$[var?text] to get the text only if that variable is "true", using a JavaScript-ish
 *     notion of truth where null and 0 and empty strings are false. (That's horrible in
 *     JavaScript but expedient here.)
 *   <li>$[var!text] to get the text only if that variable is "false".
 *   <li>$[var?[text1][text2]] to get text1 if the variable is "true" and text2 if it is "false"
 *   <li>$[var![text1][text2]] to get text1 if the variable is "false" and text2 if it is "true"
 *   <li>$[iterablevar:loopvar|sep|text] to get the text repeated for each element of iterablevar,
 *     which must be an Iterable, with the sep string appearing between each repetition, and
 *     loopvar defined to be the element for substitutions within the text. For example if
 *     the variable "strings" is ["foo", "bar", "baz"] then "$[strings:string|, |'$[string]']"
 *     will become "'foo', 'bar', 'baz'".
 * </ul>
 * <p>Everywhere you can write "var" you can also write "var.foo" to use the result of calling
 * the public foo() method on that variable, and also "var.foo.bar" etc.
 *
 * @author Éamonn McManus
 */
class Template {
  private final String template;
  private final Node rootNode;

  private Template(String template) {
    this.template = stripComments(template);
    rootNode = parse(this.template, 0, this.template.length());
  }

  static Template compile(String template) {
    return new Template(template);
  }

  String rewrite(Map<String, ?> vars) {
    StringBuilder sb = new StringBuilder();
    rootNode.appendTo(sb, this, vars);
    return sb.toString();
  }

  private static Node parse(String template, int start, int stop) {
    List<Node> nodes = new ArrayList<Node>();
    int index = start;
    while (index < stop) {
      int dollar = indexOf(template, "$[", index, stop);
      int endLiteral = (dollar < 0) ? stop : dollar;
      if (endLiteral > index) {
        nodes.add(new LiteralNode(index, template.substring(index, endLiteral)));
        index = endLiteral;
      }
      if (dollar >= 0) {
        int closeSquare = matchingCloseSquare(template, dollar);
        nodes.add(parseDollar(template, dollar, closeSquare));
        index = closeSquare + 1;
      }
    }
    assert index == stop;
    return new CompoundNode(start, nodes);
  }

  private static Node parseDollar(String template, int dollar, int closeSquare) {
    assert template.startsWith("$[", dollar);
    assert template.charAt(closeSquare) == ']';
    int afterOpen = dollar + "$[".length();
    int i = scanVarRef(template, afterOpen, closeSquare);
    String varRef = template.substring(afterOpen, i);
    Node node;
    char c = template.charAt(i);
    switch (c) {
      case ']':
        node = new VarNode(i, varRef);
        break;
      case '?':
      case '!':
        node = parseConditional(template, dollar, varRef, c, i + 1, closeSquare);
        break;
      case ':':
        node = parseIteration(template, dollar, varRef, i + 1, closeSquare);
        break;
      default:
        throw new IllegalArgumentException(
            "Unexpected character after variable name at " + excerpt(template, dollar));
    }
    return node;
  }

  // $[var?trueText] $[var?[trueText][falseText]
  // $[var!falseText] $[var![falseText][trueText]
  private static ConditionalNode parseConditional(
      String template, int dollar, String varRef, char queryOrBang, int afterQueryOrBang,
      int stop) {
    assert template.charAt(stop) == ']';
    Node firstNode;
    Node secondNode;
    if (template.charAt(afterQueryOrBang) == '[') {
      int endFirstPart = matchingCloseSquare(template, afterQueryOrBang);
      if (template.charAt(endFirstPart + 1) != '['
          || matchingCloseSquare(template, endFirstPart + 1) != stop - 1) {
        throw new IllegalArgumentException(
            "Could not scan [firstPart][secondPart] at " + excerpt(template, afterQueryOrBang + 1));
      }
      firstNode = parse(template, afterQueryOrBang + 1, endFirstPart);
      secondNode = parse(template, endFirstPart + 2, stop - 1);
    } else {
      firstNode = parse(template, afterQueryOrBang, stop);
      secondNode = new LiteralNode(stop, "");
    }
    Node[] nodes; // [0] is false case, [1] is true case.
    if (queryOrBang == '?') {
      nodes = new Node[] {secondNode, firstNode};
    } else {
      assert queryOrBang == '!';
      nodes = new Node[] {firstNode, secondNode};
    }
    return new ConditionalNode(dollar, varRef, nodes);
  }

  // $[iterablevar:loopvar|sep|text]
  private static IterationNode parseIteration(
      String template, int dollar, String varRef, int afterColon, int stop) {
    int firstBar = indexOf(template, "|", afterColon, stop);
    int secondBar = indexOf(template, "|", firstBar + 1, stop);
    // If firstBar is -1, firstBar + 1 is 0, and secondBar will be before the starting point
    // (including the -1 case).
    if (secondBar < afterColon) {
      throw new IllegalArgumentException(
          "Expected $[listVar:iterVar|sep|...] at " + excerpt(template, dollar));
    }
    String iterationVarName = template.substring(afterColon, firstBar);
    String separator = template.substring(firstBar + 1, secondBar);
    Node iterated = parse(template, secondBar + 1, stop);
    return new IterationNode(dollar, varRef, iterationVarName, separator, iterated);
  }

  private static int indexOf(String container, String pattern, int start, int stop) {
    for (int i = start; i < stop; i++) {
      if (container.startsWith(pattern, i)) {
        return i;
      }
    }
    return -1;
  }

  private static final Pattern varRefPattern =
      Pattern.compile("\\p{javaJavaIdentifierPart}+(\\.\\p{javaJavaIdentifierPart}+)*");

  private static int scanVarRef(String text, int start, int stop) {
    Matcher matcher = varRefPattern.matcher(text.substring(start, stop));
    if (matcher.lookingAt()) {
      return start + matcher.end();
    } else {
      throw new IllegalArgumentException("Expected id after $[ at " + excerpt(text, start));
    }
  }

  // Return the index of the first ] that matches the first [ at or after i.
  private static int matchingCloseSquare(String s, int i) {
    int squares = 0;
    for (int j = i; j < s.length(); j++) {
      char c = s.charAt(j);
      if (c == '[') {
        squares++;
      } else if (c == ']') {
        squares--;
        if (squares == 0) {
          return j;
        }
      }
    }
    throw new IllegalArgumentException("No closing ] to match text starting " + excerpt(s, i));
  }

  // Remove comments that begin with #, which are comments to help the reader of the template itself
  // rather than the reader of the generated code. We do not currently have a quote mechanism that
  // would allow you to get a literal # into the output text.
  // If a line consists of nothing other than a # comment, we delete it completely rather than
  // leaving a blank line in the output.
  private static final Pattern entireLineCommentPattern =
      Pattern.compile("^\\s*#.*$\n", Pattern.MULTILINE);
  private static final Pattern midLineCommentPattern =
      Pattern.compile("#.*$", Pattern.MULTILINE);

  private static String stripComments(String template) {
    template = entireLineCommentPattern.matcher(template).replaceAll("");
    return midLineCommentPattern.matcher(template).replaceAll("");
  }

  private static final int EXCERPT_LENGTH = 40;

  private static String excerpt(String s, int startI) {
    if (s.length() - startI <= EXCERPT_LENGTH) {
      return s.substring(startI);
    } else {
      return s.substring(startI, startI + EXCERPT_LENGTH) + "...";
    }
  }

  // A node in the syntax tree.
  private abstract static class Node {
    final int templateIndex;

    Node(int templateIndex) {
      this.templateIndex = templateIndex;
    }

    abstract void appendTo(StringBuilder sb, Template template, Map<String, ?> vars);
  }

  // A node representing literal text.
  private static class LiteralNode extends Node {
    private final String text;

    LiteralNode(int templateIndex, String text) {
      super(templateIndex);
      this.text = text;
    }

    @Override
    void appendTo(StringBuilder sb, Template template, Map<String, ?> vars) {
      sb.append(text);
    }
  }

  // A node that is the composition of a sequence of other nodes.
  private static class CompoundNode extends Node {
    private final List<Node> nodes;

    CompoundNode(int templateIndex, List<Node> nodes) {
      super(templateIndex);
      this.nodes = nodes;
    }

    @Override
    void appendTo(StringBuilder sb, Template template, Map<String, ?> vars) {
      for (Node node : nodes) {
        node.appendTo(sb, template, vars);
      }
    }
  }

  // Parent class for nodes that reference variables, i.e. start with $[var
  private abstract static class VarRefNode extends Node {
    private final String varRef;

    VarRefNode(int templateIndex, String varRef) {
      super(templateIndex);
      this.varRef = varRef;
    }

    // Get the value of the variable, including resolving compound.names
    Object getVar(Map<String, ?> vars, Template template) {
      // We already checked that varRef is sane using varRefPattern, which protects us against
      // String.split's funkier behaviours.
      String[] parts = varRef.split("\\.");
      Object value = vars.get(parts[0]);
      if (value == null) {
        throw new IllegalArgumentException("Reference to undefined var $[" + parts[0] + "] at "
            + excerpt(template.template, templateIndex));
      }
      for (int i = 1; i < parts.length; i++) {
        String part = parts[i];
        Method method = findPublicMethod(value.getClass(), part);
        if (method == null) {
          throw new IllegalArgumentException("No method \"" + part + "\" in " + value.getClass());
        }
        try {
          value = method.invoke(value);
        } catch (Exception e) {
          throw new IllegalArgumentException(
              "Failed to invoke " + value.getClass().getName() + "." + part + "() on " + value, e);
        }
      }
      return value;
    }

    // This method works around the irritating problem that a Method referencing a public method
    // in a non-public class cannot be invoked (without setAccessible), even if a Method referencing
    // the same method in a public superclass or interface could be. For example, a Method
    // referencing Collections.SingletonList.iterator() can't be invoked even though a Method
    // referencing List.iterator() could on the same object.
    private static Method findPublicMethod(Class<?> c, String methodName) {
      try {
        Method method = c.getMethod(methodName);
        if (Modifier.isPublic(c.getModifiers())
            || c.getName().startsWith("com.google.auto.value")) {
          // Hack to allow us not to make AutoValueProcessor.Property a public class.
          return method;
        }
        if (c.getSuperclass() != null) {
          method = findPublicMethod(c.getSuperclass(), methodName);
        }
        if (method == null) {
          for (Class<?> intf : c.getInterfaces()) {
            method = findPublicMethod(intf, methodName);
            if (method != null) {
              break;
            }
          }
        }
        return method;
      } catch (NoSuchMethodException e) {
        return null;
      }
    }
  }

  // $[var]
  private static class VarNode extends VarRefNode {
    VarNode(int templateIndex, String varRef) {
      super(templateIndex, varRef);
    }

    @Override
    void appendTo(StringBuilder sb, Template template, Map<String, ?> vars) {
      Object value = getVar(vars, template);
      sb.append(value);
    }
  }

  // $[var?trueText] $[var?[trueText][falseText]
  // $[var!falseText] $[var![falseText][trueText]
  private static class ConditionalNode extends VarRefNode {
    private final Node[] nodes;  // [0] is false, [1] is true

    ConditionalNode(int templateIndex, String varRef, Node[] nodes) {
      super(templateIndex, varRef);
      assert nodes.length == 2;
      this.nodes = nodes;
    }

    @Override
    void appendTo(StringBuilder sb, Template template, Map<String, ?> vars) {
      Object value = getVar(vars, template);
      boolean truth = truth(value, template);
      Node node = truth ? nodes[1] : nodes[0];
      node.appendTo(sb, template, vars);
    }

    private boolean truth(Object x, Template template) {
      if (x == null) {
        return false;
      } else if (x instanceof Boolean) {
        return (Boolean) x;
      } else if (x instanceof Number) {
        return ((Number) x).doubleValue() != 0;
      } else if (x instanceof CharSequence) {
        return ((CharSequence) x).length() != 0;
      } else if (x instanceof Iterable<?>) {
        return ((Iterable<?>) x).iterator().hasNext();
      } else {
        throw new IllegalArgumentException("Don't know how to evaluate the truth of " + x + " at "
            + excerpt(template.template, templateIndex));
      }
    }
  }

  // $[iterablevar:iterationVar|separator|iteratedText]
  private static class IterationNode extends VarRefNode {
    private final String iterationVarName;
    private final String separator;
    private final Node iteratedNode;

    IterationNode(int templateIndex, String varRef, String iterationVarName, String separator,
        Node iteratedNode) {
      super(templateIndex, varRef);
      this.iterationVarName = iterationVarName;
      this.separator = separator;
      this.iteratedNode = iteratedNode;
    }

    @Override
    void appendTo(StringBuilder sb, Template template, Map<String, ?> vars) {
      if (vars.containsKey(iterationVarName)) {
        throw new IllegalArgumentException("Iteration variable name " + iterationVarName
            + " is already defined at " + excerpt(template.template, templateIndex));
      }
      Map<String, Object> newVars = new HashMap<String, Object>(vars);
      Object iterableValue = getVar(vars, template);
      if (!(iterableValue instanceof Iterable<?>)) {
        throw new IllegalArgumentException("Value (" + iterableValue + ") is not Iterable at "
            + excerpt(template.template, templateIndex));
      }
      Iterable<?> iterable = (Iterable<?>) iterableValue;
      String sep = "";
      for (Object value : iterable) {
        newVars.put(iterationVarName, value);
        sb.append(sep);
        iteratedNode.appendTo(sb, template, newVars);
        sep = separator;
      }
    }
  }
}