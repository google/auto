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

import com.google.auto.value.processor.escapevelocity.DirectiveNode.SetNode;
import com.google.auto.value.processor.escapevelocity.ExpressionNode.BinaryExpressionNode;
import com.google.auto.value.processor.escapevelocity.ExpressionNode.NotExpressionNode;
import com.google.auto.value.processor.escapevelocity.ReferenceNode.IndexReferenceNode;
import com.google.auto.value.processor.escapevelocity.ReferenceNode.MemberReferenceNode;
import com.google.auto.value.processor.escapevelocity.ReferenceNode.MethodReferenceNode;
import com.google.auto.value.processor.escapevelocity.ReferenceNode.PlainReferenceNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.CommentTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.ElseIfTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.ElseTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.EndTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.EofNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.ForEachTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.IfTokenNode;
import com.google.auto.value.processor.escapevelocity.TokenNode.MacroDefinitionTokenNode;
import com.google.common.base.CharMatcher;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Chars;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;

/**
 * A parser that reads input from the given {@link Reader} and parses it to produce a
 * {@link Template}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
class Parser {
  private static final int EOF = -1;

  private final LineNumberReader reader;

  /**
   * The invariant of this parser is that {@code c} is always the next character of interest.
   * This means that we never have to "unget" a character by reading too far. For example, after
   * we parse an integer, {@code c} will be the first character after the integer, which is exactly
   * the state we will be in when there are no more digits.
   */
  private int c;

  Parser(Reader reader) throws IOException {
    this.reader = new LineNumberReader(reader);
    this.reader.setLineNumber(1);
    next();
  }

  /**
   * Parse the input completely to produce a {@link Template}.
   *
   * <p>Parsing happens in two phases. First, we parse a sequence of "tokens", where tokens include
   * entire references such as <pre>
   *    ${x.foo()[23]}
   * </pre>or entire directives such as<pre>
   *    #set ($x = $y + $z)
   * </pre>But tokens do not span complex constructs. For example,<pre>
   *    #if ($x == $y) something #end
   * </pre>is three tokens:<pre>
   *    #if ($x == $y)
   *    (literal text " something ")
   *   #end
   * </pre>
   *
   * <p>The second phase then takes the sequence of tokens and constructs a parse tree out of it.
   * Some nodes in the parse tree will be unchanged from the token sequence, such as the <pre>
   *    ${x.foo()[23]}
   *    #set ($x = $y + $z)
   * </pre> examples above. But a construct such as the {@code #if ... #end} mentioned above will
   * become a single IfNode in the parse tree in the second phase.
   *
   * <p>The main reason for this approach is that Velocity has two kinds of lexical contexts. At the
   * top level, there can be arbitrary literal text; references like <code>${x.foo()}</code>; and
   * directives like {@code #if} or {@code #set}. Inside the parentheses of a directive, however,
   * neither arbitrary text nor directives can appear, but expressions can, so we need to tokenize
   * the inside of <pre>
   *    #if ($x == $a + $b)
   * </pre> as the five tokens "$x", "==", "$a", "+", "$b". Rather than having a classical
   * parser/lexer combination, where the lexer would need to switch between these two modes, we
   * replace the lexer with an ad-hoc parser that is the first phase described above, and we
   * define a simple parser over the resultant tokens that is the second phase.
   */
  Template parse() throws IOException {
    ImmutableList.Builder<Node> tokens = ImmutableList.builder();
    Node token;
    do {
      token = parseNode();
      tokens.add(token);
    } while (!(token instanceof EofNode));
    return new Reparser(tokens.build()).reparse();
  }

  private int lineNumber() {
    return reader.getLineNumber();
  }

  /**
   * Gets the next character from the reader and assigns it to {@code c}. If there are no more
   * characters, sets {@code c} to {@link #EOF} if it is not already.
   */
  private void next() throws IOException {
    if (c != EOF) {
      c = reader.read();
    }
  }

  /**
   * If {@code c} is a space character, keeps reading until {@code c} is a non-space character or
   * there are no more characters.
   */
  private void skipSpace() throws IOException {
    while (Character.isWhitespace(c)) {
      next();
    }
  }

  /**
   * Gets the next character from the reader, and if it is a space character, keeps reading until
   * a non-space character is found.
   */
  private void nextNonSpace() throws IOException {
    next();
    skipSpace();
  }

  /**
   * Skips any space in the reader, and then throws an exception if the first non-space character
   * found is not the expected one. Sets {@code c} to the first character after that expected one.
   */
  private void expect(char expected) throws IOException {
    skipSpace();
    if (c == expected) {
      next();
    } else {
      throw parseException("Expected " + expected);
    }
  }

  /**
   * Parses a single node from the reader, as part of the first parsing phase.
   * <pre>{@code
   * <template> -> <empty> |
   *               <directive> <template> |
   *               <non-directive> <template>
   * }</pre>
   */
  private Node parseNode() throws IOException {
    if (c == '#') {
      next();
      if (c == '#') {
        return parseComment();
      } else {
        return parseDirective();
      }
    }
    if (c == EOF) {
      return new EofNode(lineNumber());
    }
    return parseNonDirective();
  }

  /**
   * Parses a single non-directive node from the reader.
   * <pre>{@code
   * <non-directive> -> <reference> |
   *                    <text containing neither $ nor #>
   * }</pre>
   */
  private Node parseNonDirective() throws IOException {
    if (c == '$') {
      next();
      if (isAsciiLetter(c) || c == '{') {
        return parseReference();
      } else {
        return parsePlainText('$');
      }
    } else {
      int firstChar = c;
      next();
      return parsePlainText(firstChar);
    }
  }

  /**
   * Parses a single directive token from the reader. Directives can be spelled with or without
   * braces, for example {@code #if} or {@code #{if}}. We omit the brace spelling in the productions
   * here: <pre>{@code
   * <directive> -> <if-token> |
   *                <else-token> |
   *                <elseif-token> |
   *                <end-token> |
   *                <foreach-token> |
   *                <set-token> |
   *                <macro-token> |
   *                <macro-call> |
   *                <comment>
   * }</pre>
   */
  private Node parseDirective() throws IOException {
    String directive;
    if (c == '{') {
      next();
      directive = parseId("Directive inside #{...}");
      expect('}');
    } else {
      directive = parseId("Directive");
    }
    Node node;
    if (directive.equals("end")) {
      node = new EndTokenNode(lineNumber());
    } else if (directive.equals("if") || directive.equals("elseif")) {
      node = parseIfOrElseIf(directive);
    } else if (directive.equals("else")) {
      node = new ElseTokenNode(lineNumber());
    } else if (directive.equals("foreach")) {
      node = parseForEach();
    } else if (directive.equals("set")) {
      node = parseSet();
    } else if (directive.equals("macro")) {
      node = parseMacroDefinition();
    } else {
      node = parsePossibleMacroCall(directive);
    }
    // Velocity skips a newline after any directive.
    // TODO(emcmanus): in fact it also skips space before the newline, which should be implemented.
    if (c == '\n') {
      next();
    }
    return node;
  }

  /**
   * Parses the condition following {@code #if} or {@code #elseif}.
   * <pre>{@code
   * <if-token> -> #if ( <condition> )
   * <elseif-token> -> #elseif ( <condition> )
   * }</pre>
   *
   * @param directive either {@code "if"} or {@code "elseif"}.
   */
  private Node parseIfOrElseIf(String directive) throws IOException {
    expect('(');
    ExpressionNode condition = parseExpression();
    expect(')');
    return directive.equals("if") ? new IfTokenNode(condition) : new ElseIfTokenNode(condition);
  }

  /**
   * Parses a {@code #foreach} token from the reader. <pre>{@code
   * <foreach-token> -> #foreach ( $<id> in <expression> )
   * }</pre>
   */
  private Node parseForEach() throws IOException {
    expect('(');
    expect('$');
    String var = parseId("For-each variable");
    skipSpace();
    boolean bad = false;
    if (c != 'i') {
      bad = true;
    } else {
      next();
      if (c != 'n') {
        bad = true;
      }
    }
    if (bad) {
      throw parseException("Expected 'in' for #foreach");
    }
    next();
    ExpressionNode collection = parseExpression();
    expect(')');
    return new ForEachTokenNode(var, collection);
  }

  /**
   * Parses a {@code #set} token from the reader. <pre>{@code
   * <set-token> -> #set ( $<id> = <expression>)
   * }</pre>
   */
  private Node parseSet() throws IOException {
    expect('(');
    expect('$');
    String var = parseId("#set variable");
    expect('=');
    ExpressionNode expression = parseExpression();
    expect(')');
    return new SetNode(var, expression);
  }

  /**
   * Parses a {@code #macro} token from the reader. <pre>{@code
   * <macro-token> -> #macro ( <id> <macro-parameter-list> )
   * <macro-parameter-list> -> <empty> |
   *                           $<id> <macro-parameter-list>
   * }</pre>
   *
   * <p>Macro parameters are not separated by commas, though method-reference parameters are.
   */
  private Node parseMacroDefinition() throws IOException {
    expect('(');
    skipSpace();
    String name = parseId("Macro name");
    ImmutableList.Builder<String> parameterNames = ImmutableList.builder();
    while (true) {
      skipSpace();
      if (c == ')') {
        next();
        break;
      }
      if (c != '$') {
        throw parseException("Macro parameters should look like $name");
      }
      next();
      parameterNames.add(parseId("Macro parameter name"));
    }
    return new MacroDefinitionTokenNode(lineNumber(), name, parameterNames.build());
  }

  /**
   * Parses an identifier after {@code #} that is not one of the standard directives. The assumption
   * is that it is a call of a macro that is defined in the template. Macro definitions are
   * extracted from the template during the second parsing phase (and not during evaluation of the
   * template as you might expect). This means that a macro can be called before it is defined.
   * <pre>{@code
   * <macro-call> -> # <id> ( <expression-list> )
   * <expression-list> -> <empty> |
   *                      <expression> <optional-comma> <expression-list>
   * <optional-comma> -> <empty> | ,
   * }</pre>
   */
  private Node parsePossibleMacroCall(String directive) throws IOException {
    skipSpace();
    if (c != '(') {
      throw parseException("Unrecognized directive #" + directive);
    }
    next();
    ImmutableList.Builder<Node> parameterNodes = ImmutableList.builder();
    while (true) {
      skipSpace();
      if (c == ')') {
        next();
        break;
      }
      parameterNodes.add(parsePrimary());
      if (c == ',') {
        // The documentation doesn't say so, but you can apparently have an optional comma in
        // macro calls.
        next();
      }
    }
    return new DirectiveNode.MacroCallNode(lineNumber(), directive, parameterNodes.build());
  }

  /**
   * Parses and discards a comment, which is {@code ##} followed by any number of characters up to
   * and including the next newline.
   */
  private Node parseComment() throws IOException {
    int lineNumber = lineNumber();
    while (c != '\n' && c != EOF) {
      next();
    }
    next();
    return new CommentTokenNode(lineNumber);
  }

  /**
   * Parses plain text, which is text that contains neither {@code $} nor {@code #}. The given
   * {@code firstChar} is the first character of the plain text, and {@link #c} is the second
   * (if the plain text is more than one character).
   */
  private Node parsePlainText(int firstChar) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.appendCodePoint(firstChar);

    literal:
    while (true) {
      switch (c) {
        case EOF:
        case '$':
        case '#':
          break literal;
      }
      sb.appendCodePoint(c);
      next();
    }
    return new ConstantExpressionNode(lineNumber(), sb.toString());
  }

  /**
   * Parses a reference, which is everything that can start with a {@code $}. References can
   * optionally be enclosed in braces, so {@code $x} and {@code ${x}} are the same. Braces are
   * useful when text after the reference would otherwise be parsed as part of it. For example,
   * {@code ${x}y} is a reference to the variable {@code $x}, followed by the plain text {@code y}.
   * Of course {@code $xy} would be a reference to the variable {@code $xy}.
   * <pre>{@code
   * <reference> -> $<reference-no-brace> |
   *                ${<reference-no-brace>}
   * }</pre>
   *
   * <p>On entry to this method, {@link #c} is the character immediately after the {@code $}.
   */
  private ReferenceNode parseReference() throws IOException {
    if (c == '{') {
      next();
      ReferenceNode node = parseReferenceNoBrace();
      expect('}');
      return node;
    } else {
      return parseReferenceNoBrace();
    }
  }

  /**
   * Parses a reference, in the simple form without braces.
   * <pre>{@code
   * <reference-no-brace> -> <id><reference-suffix>
   * }</pre>
   */
  private ReferenceNode parseReferenceNoBrace() throws IOException {
    String id = parseId("Reference");
    ReferenceNode lhs = new PlainReferenceNode(lineNumber(), id);
    return parseReferenceSuffix(lhs);
  }

  /**
   * Parses the modifiers that can appear at the tail of a reference.
   * <pre>{@code
   * <reference-suffix> -> <empty> |
   *                       <reference-member> |
   *                       <reference-index>
   * }</pre>
   *
   * @param lhs the reference node representing the first part of the reference
   * {@code $x} in {@code $x.foo} or {@code $x.foo()}, or later {@code $x.y} in {@code $x.y.z}.
   */
  private ReferenceNode parseReferenceSuffix(ReferenceNode lhs) throws IOException {
    switch (c) {
      case '.':
        return parseReferenceMember(lhs);
      case '[':
        return parseReferenceIndex(lhs);
      default:
        return lhs;
    }
  }

  /**
   * Parses a reference member, which is either a property reference like {@code $x.y} or a method
   * call like {@code $x.y($z)}.
   * <pre>{@code
   * <reference-member> -> .<id><reference-method-or-property><reference-suffix>
   * <reference-method-or-property> -> <id> |
   *                                   <id> ( <method-parameter-list> )
   * }</pre>
   *
   * @param lhs the reference node representing what appears to the left of the dot, like the
   * {@code $x} in {@code $x.foo} or {@code $x.foo()}.
   */
  private ReferenceNode parseReferenceMember(ReferenceNode lhs) throws IOException {
    assert c == '.';
    next();
    String id = parseId("Member");
    ReferenceNode reference;
    if (c == '(') {
      reference = parseReferenceMethodParams(lhs, id);
    } else {
      reference = new MemberReferenceNode(lhs, id);
    }
    return parseReferenceSuffix(reference);
  }

  /**
   * Parses the parameters to a method reference, like {@code $foo.bar($a, $b)}.
   * <pre>{@code
   * <method-parameter-list> -> <empty> |
   *                            <non-empty-method-parameter-list>
   * <non-empty-method-parameter-list> -> <expression> |
   *                                      <expression> , <non-empty-method-parameter-list>
   * }</pre>
   *
   * @param lhs the reference node representing what appears to the left of the dot, like the
   * {@code $x} in {@code $x.foo()}.
   */
  private ReferenceNode parseReferenceMethodParams(ReferenceNode lhs, String id)
      throws IOException {
    assert c == '(';
    nextNonSpace();
    ImmutableList.Builder<ExpressionNode> args = ImmutableList.builder();
    if (c != ')') {
      args.add(parseExpression());
      while (c == ',') {
        nextNonSpace();
        args.add(parseExpression());
      }
      if (c != ')') {
        throw parseException("Expected )");
      }
    }
    assert c == ')';
    next();
    return new MethodReferenceNode(lhs, id, args.build());
  }

  /**
   * Parses an index suffix to a method, like {@code $x[$i]}.
   * <pre>{@code
   * <reference-index> -> [ <expression> ]
   * }</pre>
   *
   * @param lhs the reference node representing what appears to the left of the dot, like the
   * {@code $x} in {@code $x[$i]}.
   */
  private ReferenceNode parseReferenceIndex(ReferenceNode lhs) throws IOException {
    assert c == '[';
    next();
    ExpressionNode index = parseExpression();
    if (c != ']') {
      throw parseException("Expected ]");
    }
    next();
    ReferenceNode reference = new IndexReferenceNode(lhs, index);
    return parseReferenceSuffix(reference);
  }

  enum Operator {
    /**
     * A dummy operator with low precedence. When parsing subexpressions, we always stop when we
     * reach an operator of lower precedence than the "current precedence". For example, when
     * parsing {@code 1 + 2 * 3 + 4}, we'll stop parsing the subexpression {@code * 3 + 4} when
     * we reach the {@code +} because it has lower precedence than {@code *}. This dummy operator,
     * then, behaves like {@code +} when the minimum precedence is {@code *}. We also return it
     * if we're looking for an operator and don't find one. If this operator is {@code ⊙}, it's as
     * if our expressions are bracketed with it, like {@code ⊙ 1 + 2 * 3 + 4 ⊙}.
     */
    STOP("", 0),

    // If a one-character operator is a prefix of a two-character operator, like < and <=, then
    // the one-character operator must come first.
    OR("||", 1),
    AND("&&", 2),
    EQUAL("==", 3), NOT_EQUAL("!=", 3),
    LESS("<", 4), LESS_OR_EQUAL("<=", 4), GREATER(">", 4), GREATER_OR_EQUAL(">=", 4),
    PLUS("+", 5), MINUS("-", 5),
    TIMES("*", 6), DIVIDE("/", 6), REMAINDER("%", 6);

    final String symbol;
    final int precedence;

    Operator(String symbol, int precedence) {
      this.symbol = symbol;
      this.precedence = precedence;
    }

    @Override
    public String toString() {
      return symbol;
    }
  }

  /**
   * Maps a code point to the operators that begin with that code point. For example, maps
   * {@code <} to {@code LESS} and {@code LESS_OR_EQUAL}.
   */
  private static final ImmutableListMultimap<Integer, Operator> CODE_POINT_TO_OPERATORS;
  static {
    ImmutableListMultimap.Builder<Integer, Operator> builder = ImmutableListMultimap.builder();
    for (Operator operator : Operator.values()) {
      if (operator != Operator.STOP) {
        builder.put((int) operator.symbol.charAt(0), operator);
      }
    }
    CODE_POINT_TO_OPERATORS = builder.build();
  }

  /**
   * Parses an expression, which can occur within a directive like {@code #if} or {@code #set},
   * or within a reference like {@code $x[$a + $b]} or {@code $x.m($a + $b)}.
   * <pre>{@code
   * <expression> -> <and-expression> |
   *                 <expression> || <and-expression>
   * <and-expression> -> <relational-expression> |
   *                     <and-expression> && <relational-expression>
   * <equality-exression> -> <relational-expression> |
   *                         <equality-expression> <equality-op> <relational-expression>
   * <equality-op> -> == | !=
   * <relational-expression> -> <additive-expression> |
   *                            <relational-expression> <relation> <additive-expression>
   * <relation> -> < | <= | > | >=
   * <additive-expression> -> <multiplicative-expression> |
   *                          <additive-expression> <add-op> <multiplicative-expression>
   * <add-op> -> + | -
   * <multiplicative-expression> -> <unary-expression> |
   *                                <multiplicative-expression> <mult-op> <unary-expression>
   * <mult-op> -> * | / | %
   * }</pre>
   */
  private ExpressionNode parseExpression() throws IOException {
    ExpressionNode lhs = parseUnaryExpression();
    return new OperatorParser().parse(lhs, 1);
  }

  /**
   * An operator-precedence parser for the binary operations we understand. It implements an
   * <a href="http://en.wikipedia.org/wiki/Operator-precedence_parser">algorithm</a> from Wikipedia
   * that uses recursion rather than having an explicit stack of operators and values.
   */
  private class OperatorParser {
    /**
     * The operator we have just scanned, in the same way that {@link #c} is the character we have
     * just read. If we were not able to scan an operator, this will be {@link Operator#STOP}.
     */
    private Operator currentOperator;

    OperatorParser() throws IOException {
      nextOperator();
    }

    /**
     * Parse a subexpression whose left-hand side is {@code lhs} and where we only consider
     * operators with precedence at least {@code minPrecedence}.
     *
     * @return the parsed subexpression
     */
    ExpressionNode parse(ExpressionNode lhs, int minPrecedence) throws IOException {
      while (currentOperator.precedence >= minPrecedence) {
        Operator operator = currentOperator;
        ExpressionNode rhs = parseUnaryExpression();
        nextOperator();
        while (currentOperator.precedence > operator.precedence) {
          rhs = parse(rhs, currentOperator.precedence);
        }
        lhs = new BinaryExpressionNode(lhs, operator, rhs);
      }
      return lhs;
    }

    /**
     * Updates {@link #currentOperator} to be an operator read from the input,
     * or {@link Operator#STOP} if there is none.
     */
    private void nextOperator() throws IOException {
      skipSpace();
      ImmutableList<Operator> possibleOperators = CODE_POINT_TO_OPERATORS.get(c);
      if (possibleOperators.isEmpty()) {
        currentOperator = Operator.STOP;
        return;
      }
      char firstChar = Chars.checkedCast(c);
      next();
      Operator operator = null;
      for (Operator possibleOperator : possibleOperators) {
        if (possibleOperator.symbol.length() == 1) {
          Verify.verify(operator == null);
          operator = possibleOperator;
        } else if (possibleOperator.symbol.charAt(1) == c) {
          next();
          operator = possibleOperator;
        }
      }
      if (operator == null) {
        throw parseException(
            "Expected " + Iterables.getOnlyElement(possibleOperators) + ", not just " + firstChar);
      }
      currentOperator = operator;
    }
  }

  /**
   * Parses an expression not containing any operators (except inside parentheses).
   * <pre>{@code
   * <unary-expression> -> <primary> |
   *                       ( <expression> ) |
   *                       ! <unary-expression>
   * }</pre>
   */
  private ExpressionNode parseUnaryExpression() throws IOException {
    skipSpace();
    ExpressionNode node;
    if (c == '(') {
      nextNonSpace();
      node = parseExpression();
      expect(')');
      skipSpace();
      return node;
    } else if (c == '!') {
      next();
      node = new NotExpressionNode(parseUnaryExpression());
      skipSpace();
      return node;
    } else {
      return parsePrimary();
    }
  }

  /**
   * Parses an expression containing only literals or references.
   * <pre>{@code
   * <primary> -> <reference> |
   *              <string-literal> |
   *              <integer-literal> |
   *              <boolean-literal>
   * }</pre>
   */
  private ExpressionNode parsePrimary() throws IOException {
    ExpressionNode node;
    if (c == '$') {
      next();
      node = parseReference();
    } else if (c == '"') {
      node = parseStringLiteral();
    } else if (c == '-') {
      // Velocity does not have a negation operator. If we see '-' it must be the start of a
      // negative integer literal.
      next();
      node = parseIntLiteral("-");
    } else if (isAsciiDigit(c)) {
      node = parseIntLiteral("");
    } else if (isAsciiLetter(c)) {
      node = parseBooleanLiteral();
    } else {
      throw parseException("Expected an expression");
    }
    skipSpace();
    return node;
  }

  private ExpressionNode parseStringLiteral() throws IOException {
    assert c == '"';
    StringBuilder sb = new StringBuilder();
    next();
    while (c != '"') {
      if (c == '\n' || c == EOF) {
        throw parseException("Unterminated string constant");
      }
      if (c == '$' || c == '\\') {
        // In real Velocity, you can have a $ reference expanded inside a "" string literal.
        // There are also '' string literals where that is not so. We haven't needed that yet
        // so it's not supported.
        throw parseException(
            "Escapes or references in string constants are not currently supported");
      }
      sb.appendCodePoint(c);
      next();
    }
    next();
    return new ConstantExpressionNode(lineNumber(), sb.toString());
  }

  private ExpressionNode parseIntLiteral(String prefix) throws IOException {
    StringBuilder sb = new StringBuilder(prefix);
    while (isAsciiDigit(c)) {
      sb.appendCodePoint(c);
      next();
    }
    Integer value = Ints.tryParse(sb.toString());
    if (value == null) {
      throw parseException("Invalid integer: " + sb);
    }
    return new ConstantExpressionNode(lineNumber(), value);
  }

  /**
   * Parses a boolean literal, either {@code true} or {@code false}.
   * <boolean-literal> -> true |
   *                      false
   */
  private ExpressionNode parseBooleanLiteral() throws IOException {
    String s = parseId("Identifier without $");
    boolean value;
    if (s.equals("true")) {
      value = true;
    } else if (s.equals("false")) {
      value = false;
    } else {
      throw parseException("Identifier in expression must be preceded by $ or be true or false");
    }
    return new ConstantExpressionNode(lineNumber(), value);
  }

  private static final CharMatcher ASCII_LETTER =
      CharMatcher.inRange('A', 'Z')
          .or(CharMatcher.inRange('a', 'z'))
          .precomputed();

  private static final CharMatcher ASCII_DIGIT =
      CharMatcher.inRange('0', '9')
          .precomputed();

  private static final CharMatcher ID_CHAR =
      ASCII_LETTER
          .or(ASCII_DIGIT)
          .or(CharMatcher.anyOf("-_"))
          .precomputed();

  private static boolean isAsciiLetter(int c) {
    return (char) c == c && ASCII_LETTER.matches((char) c);
  }

  private static boolean isAsciiDigit(int c) {
    return (char) c == c && ASCII_DIGIT.matches((char) c);
  }

  private static boolean isIdChar(int c) {
    return (char) c == c && ID_CHAR.matches((char) c);
  }

  /**
   * Parse an identifier as specified by the
   * <a href="http://velocity.apache.org/engine/devel/vtl-reference-guide.html#Variables">VTL
   * </a>. Identifiers are ASCII: starts with a letter, then letters, digits, {@code -} and
   * {@code _}.
   */
  private String parseId(String what) throws IOException {
    if (!isAsciiLetter(c)) {
      throw parseException(what + " should start with an ASCII letter");
    }
    StringBuilder id = new StringBuilder();
    while (isIdChar(c)) {
      id.appendCodePoint(c);
      next();
    }
    return id.toString();
  }

  /**
   * Returns an exception to be thrown describing a parse error with the given message, and
   * including information about where it occurred.
   */
  private ParseException parseException(String message) throws IOException {
    StringBuilder context = new StringBuilder();
    if (c == EOF) {
      context.append("EOF");
    } else {
      int count = 0;
      while (c != EOF && count < 20) {
        context.appendCodePoint(c);
        next();
        count++;
      }
      if (c != EOF) {
        context.append("...");
      }
    }
    return new ParseException(message, lineNumber(), context.toString());
  }
}
