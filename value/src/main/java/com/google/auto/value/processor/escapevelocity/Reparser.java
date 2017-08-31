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

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.google.auto.value.processor.escapevelocity;

import static com.google.auto.value.processor.escapevelocity.Node.emptyNode;

import com.google.auto.value.processor.escapevelocity.DirectiveNode.ForEachNode;
import com.google.auto.value.processor.escapevelocity.DirectiveNode.IfNode;
import com.google.auto.value.processor.escapevelocity.DirectiveNode.MacroCallNode;
import com.google.auto.value.processor.escapevelocity.DirectiveNode.SetNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.CommentTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.ElseIfTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.ElseTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.EndTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.EofNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.ForEachTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.IfOrElseIfTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.IfTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.MacroDefinitionTokenNode;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;

/**
 * The second phase of parsing. See {@link Parser#parse()} for a description of the phases and why
 * we need them.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
class Reparser {
  private static final ImmutableSet<Class<? extends TokenNode>> END_SET =
      ImmutableSet.<Class<? extends TokenNode>>of(EndTokenNode.class);
  private static final ImmutableSet<Class<? extends TokenNode>> EOF_SET =
      ImmutableSet.<Class<? extends TokenNode>>of(EofNode.class);
  private static final ImmutableSet<Class<? extends TokenNode>> ELSE_ELSE_IF_END_SET =
      ImmutableSet.<Class<? extends TokenNode>>of(
          ElseTokenNode.class, ElseIfTokenNode.class, EndTokenNode.class);

  /**
   * The nodes that make up the input sequence. Nodes are removed one by one from this list as
   * parsing proceeds. At any time, {@link #currentNode} is the node being examined.
   */
  private final ImmutableList<Node> nodes;

  /**
   * The index of the node we are currently looking at while parsing.
   */
  private int nodeIndex;

  /**
   * Macros are removed from the input as they are found. They do not appear in the output parse
   * tree. Macro definitions are not executed in place but are all applied before template rendering
   * starts. This means that a macro can be referenced before it is defined.
   */
  private final Map<String, Macro> macros;

  Reparser(ImmutableList<Node> nodes) {
    this.nodes = removeSpaceBeforeSet(nodes);
    this.nodeIndex = 0;
    this.macros = Maps.newTreeMap();
  }

  Template reparse() {
    Node root = parseTo(EOF_SET, new EofNode(1));
    linkMacroCalls();
    return new Template(root);
  }

  /**
   * Returns a copy of the given list where spaces have been moved where appropriate after {@code
   * #set}. This hack is needed to match what appears to be special treatment in Apache Velocity of
   * spaces before {@code #set} directives. If you have <i>thing</i> <i>whitespace</i> {@code #set},
   * then the whitespace is deleted if the <i>thing</i> is a comment ({@code ##...\n}); a reference
   * ({@code $x} or {@code $x.foo} etc); a macro definition; or another {@code #set}.
   */
  private static ImmutableList<Node> removeSpaceBeforeSet(ImmutableList<Node> nodes) {
    assert Iterables.getLast(nodes) instanceof EofNode;
    // Since the last node is EofNode, the i + 1 and i + 2 accesses below are safe.
    ImmutableList.Builder<Node> newNodes = ImmutableList.builder();
    for (int i = 0; i < nodes.size(); i++) {
      Node nodeI = nodes.get(i);
      newNodes.add(nodeI);
      if (shouldDeleteSpaceBetweenThisAndSet(nodeI)
          && isWhitespaceLiteral(nodes.get(i + 1))
          && nodes.get(i + 2) instanceof SetNode) {
        // Skip the space.
        i++;
      }
    }
    return newNodes.build();
  }

  private static boolean shouldDeleteSpaceBetweenThisAndSet(Node node) {
    return node instanceof CommentTokenNode
        || node instanceof ReferenceNode
        || node instanceof SetNode
        || node instanceof MacroDefinitionTokenNode;
  }

  private static boolean isWhitespaceLiteral(Node node) {
    if (node instanceof ConstantExpressionNode) {
      Object constant = node.evaluate(null);
      return constant instanceof String && CharMatcher.whitespace().matchesAllOf((String) constant);
    }
    return false;
  }

  /**
   * Parse subtrees until one of the token types in {@code stopSet} is encountered.
   * If this is the top level, {@code stopSet} will include {@link EofNode} so parsing will stop
   * when it reaches the end of the input. Otherwise, if an {@code EofNode} is encountered it is an
   * error because we have something like {@code #if} without {@code #end}.
   *
   * @param stopSet the kinds of tokens that will stop the parse. For example, if we are parsing
   *     after an {@code #if}, we will stop at any of {@code #else}, {@code #elseif},
   *     or {@code #end}.
   * @param forWhat the token that triggered this call, for example the {@code #if} whose
   *     {@code #end} etc we are looking for.
   *
   * @return a Node that is the concatenation of the parsed subtrees
   */
  private Node parseTo(Set<Class<? extends TokenNode>> stopSet, TokenNode forWhat) {
    ImmutableList.Builder<Node> nodeList = ImmutableList.builder();
    while (true) {
      Node currentNode = currentNode();
      if (stopSet.contains(currentNode.getClass())) {
        break;
      }
      if (currentNode instanceof EofNode) {
        throw new ParseException(
            "Reached end of file while parsing " + forWhat.name(), forWhat.lineNumber);
      }
      Node parsed;
      if (currentNode instanceof TokenNode) {
        parsed = parseTokenNode();
      } else {
        parsed = currentNode;
        nextNode();
      }
      nodeList.add(parsed);
    }
    return Node.cons(forWhat.lineNumber, nodeList.build());
  }

  private Node currentNode() {
    return nodes.get(nodeIndex);
  }

  private Node nextNode() {
    Node currentNode = currentNode();
    if (currentNode instanceof EofNode) {
      return currentNode;
    } else {
      nodeIndex++;
      return currentNode();
    }
  }

  private Node parseTokenNode() {
    TokenNode tokenNode = (TokenNode) currentNode();
    nextNode();
    if (tokenNode instanceof CommentTokenNode) {
      return emptyNode(tokenNode.lineNumber);
    } else if (tokenNode instanceof IfTokenNode) {
      return parseIfOrElseIf((IfTokenNode) tokenNode);
    } else if (tokenNode instanceof ForEachTokenNode) {
      return parseForEach((ForEachTokenNode) tokenNode);
    } else if (tokenNode instanceof MacroDefinitionTokenNode) {
      return parseMacroDefinition((MacroDefinitionTokenNode) tokenNode);
    } else {
      throw new IllegalArgumentException(
          "Unexpected token: " + tokenNode.name() + " on line " + tokenNode.lineNumber);
    }
  }

  private Node parseForEach(ForEachTokenNode forEach) {
    Node body = parseTo(END_SET, forEach);
    nextNode();  // Skip #end
    return new ForEachNode(forEach.lineNumber, forEach.var, forEach.collection, body);
  }

  private Node parseIfOrElseIf(IfOrElseIfTokenNode ifOrElseIf) {
    Node truePart = parseTo(ELSE_ELSE_IF_END_SET, ifOrElseIf);
    Node falsePart;
    Node token = currentNode();
    nextNode();  // Skip #else or #elseif (cond) or #end.
    if (token instanceof EndTokenNode) {
      falsePart = emptyNode(token.lineNumber);
    } else if (token instanceof ElseTokenNode) {
      falsePart = parseTo(END_SET, ifOrElseIf);
      nextNode();  // Skip #end
    } else if (token instanceof ElseIfTokenNode) {
      // We've seen #if (condition1) ... #elseif (condition2). currentToken is the first token
      // after (condition2). We pretend that we've just seen #if (condition2) and parse out
      // the remainder (which might have further #elseif and final #else). Then we pretend that
      // we actually saw #if (condition1) ... #else #if (condition2) ...remainder ... #end #end.
      falsePart = parseIfOrElseIf((ElseIfTokenNode) token);
    } else {
      throw new AssertionError(currentNode());
    }
    return new IfNode(ifOrElseIf.lineNumber, ifOrElseIf.condition, truePart, falsePart);
  }

  private Node parseMacroDefinition(MacroDefinitionTokenNode macroDefinition) {
    Node body = parseTo(END_SET, macroDefinition);
    nextNode();  // Skip #end
    if (!macros.containsKey(macroDefinition.name)) {
      Macro macro = new Macro(
          macroDefinition.lineNumber, macroDefinition.name, macroDefinition.parameterNames, body);
      macros.put(macroDefinition.name, macro);
    }
    return emptyNode(macroDefinition.lineNumber);
  }

  private void linkMacroCalls() {
    for (Node node : nodes) {
      if (node instanceof MacroCallNode) {
        linkMacroCall((MacroCallNode) node);
      }
    }
  }

  private void linkMacroCall(MacroCallNode macroCall) {
    Macro macro = macros.get(macroCall.name());
    if (macro == null) {
      throw new ParseException(
          "#" + macroCall.name()
              + " is neither a standard directive nor a macro that has been defined",
          macroCall.lineNumber);
    }
    if (macro.parameterCount() != macroCall.argumentCount()) {
      throw new ParseException(
          "Wrong number of arguments to #" + macroCall.name()
              + ": expected " + macro.parameterCount()
              + ", got " + macroCall.argumentCount(),
          macroCall.lineNumber);
    }
    macroCall.setMacro(macro);
  }
}
