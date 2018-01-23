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
import com.google.auto.value.processor.escapevelocity.TokenNode.NestedTokenNode;
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
  private final String resourceName;
  private final Template.ResourceOpener resourceOpener;

  /**
   * The invariant of this parser is that {@code c} is always the next character of interest.
   * This means that we almost never have to "unget" a character by reading too far. For example,
   * after we parse an integer, {@code c} will be the first character after the integer, which is
   * exactly the state we will be in when there are no more digits.
   *
   * <p>Sometimes we need to read two characters ahead, and in that case we use {@link #pushback}.
   */
  private int c;

  /**
   * A single character of pushback. If this is not negative, the {@link #next()} method will
   * return it instead of reading a character.
   */
  private int pushback = -1;

  Parser(Reader reader, String resourceName, Template.ResourceOpener resourceOpener)
      throws IOException {
    this.reader = new LineNumberReader(reader);
    this.reader.setLineNumber(1);
    next();
    this.resourceName = resourceName;
    this.resourceOpener = resourceOpener;
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
    ImmutableList<Node> tokens = parseTokens();
    return new Reparser(tokens).reparse();
  }

  private ImmutableList<Node> parseTokens() throws IOException {
    ImmutableList.Builder<Node> tokens = ImmutableList.builder();
    Node token;
    do {
      token = parseNode();
      tokens.add(token);
    } while (!(token instanceof EofNode));
    return tokens.build();
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
      if (pushback < 0) {
        c = reader.read();
      } else {
        c = pushback;
        pushback = -1;
      }
    }
  }

  /**
   * Saves the current character {@code c} to be read again, and sets {@code c} to the given
   * {@code c1}. Suppose the text contains {@code xy} and we have just read {@code y}.
   * So {@code c == 'y'}. Now if we execute {@code pushback('x')}, we will have
   * {@code c == 'x'} and the next call to {@link #next()} will set {@code c == 'y'}. Subsequent
   * calls to {@code next()} will continue reading from {@link #reader}. So the pushback
   * essentially puts us back in the state we were in before we read {@code y}.
   */
  private void pushback(int c1) {
    pushback = c;
    c = c1;
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
      switch (c) {
        case '#':
          return parseLineComment();
        case '*':
          return parseBlockComment();
        case '[':
          return parseHashSquare();
        case '{':
          return parseDirective();
        default:
          if (isAsciiLetter(c)) {
            return parseDirective();
          } else {
            // For consistency with Velocity, we treat # not followed by a letter or one of the
            // characters above as a plain character, and we treat #$foo as a literal # followed by
            // the reference $foo.
            return parsePlainText('#');
          }
      }
    }
    if (c == EOF) {
      return new EofNode(resourceName, lineNumber());
    }
    return parseNonDirective();
  }

  private Node parseHashSquare() throws IOException {
    // We've just seen #[ which might be the start of a #[[quoted block]]#. If the next character
    // is not another [ then it's not a quoted block, but it *is* a literal #[ followed by whatever
    // that next character is.
    assert c == '[';
    next();
    if (c != '[') {
      return parsePlainText(new StringBuilder("#["));
    }
    int startLine = lineNumber();
    next();
    StringBuilder sb = new StringBuilder();
    while (true) {
      if (c == EOF) {
        throw new ParseException(
            "Unterminated #[[ - did not see matching ]]#", resourceName, startLine);
      }
      if (c == '#') {
        // This might be the last character of ]]# or it might just be a random #.
        int len = sb.length();
        if (len > 1 && sb.charAt(len - 1) == ']' && sb.charAt(len - 2) == ']') {
          next();
          break;
        }
      }
      sb.append((char) c);
      next();
    }
    String quoted = sb.substring(0, sb.length() - 2);
    return new ConstantExpressionNode(resourceName, lineNumber(), quoted);
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
   *                <parse-token> |
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
    switch (directive) {
      case "end":
        node = new EndTokenNode(resourceName, lineNumber());
        break;
      case "if":
      case "elseif":
        node = parseIfOrElseIf(directive);
        break;
      case "else":
        node = new ElseTokenNode(resourceName, lineNumber());
        break;
      case "foreach":
        node = parseForEach();
        break;
      case "set":
        node = parseSet();
        break;
      case "parse":
        node = parseParse();
        break;
      case "macro":
        node = parseMacroDefinition();
        break;
      default:
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
   * Parses a {@code #parse} token from the reader. <pre>{@code
   * <parse-token> -> #parse ( <string-literal> )
   * }</pre>
   *
   * <p>The way this works is inconsistent with Velocity. In Velocity, the {@code #parse} directive
   * is evaluated when it is encountered during template evaluation. That means that the argument
   * can be a variable, and it also means that you can use {@code #if} to choose whether or not
   * to do the {@code #parse}. Neither of those is true in EscapeVelocity. The contents of the
   * {@code #parse} are integrated into the containing template pretty much as if they had been
   * written inline. That also means that EscapeVelocity allows forward references to macros
   * inside {@code #parse} directives, which Velocity does not.
   */
  private Node parseParse() throws IOException {
    expect('(');
    skipSpace();
    if (c != '"') {
      throw parseException("#parse only supported with string literal argument");
    }
    String nestedResourceName = readStringLiteral();
    expect(')');
    try (Reader nestedReader = resourceOpener.openResource(nestedResourceName)) {
      Parser nestedParser = new Parser(nestedReader, nestedResourceName, resourceOpener);
      ImmutableList<Node> nestedTokens = nestedParser.parseTokens();
      return new NestedTokenNode(nestedResourceName, nestedTokens);
    }
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
    return new MacroDefinitionTokenNode(resourceName, lineNumber(), name, parameterNames.build());
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
    return new DirectiveNode.MacroCallNode(
        resourceName, lineNumber(), directive, parameterNodes.build());
  }

  /**
   * Parses and discards a line comment, which is {@code ##} followed by any number of characters
   * up to and including the next newline.
   */
  private Node parseLineComment() throws IOException {
    int lineNumber = lineNumber();
    while (c != '\n' && c != EOF) {
      next();
    }
    next();
    return new CommentTokenNode(resourceName, lineNumber);
  }

  /**
   * Parses and discards a block comment, which is {@code #*} followed by everything up to and
   * including the next {@code *#}.
   */
  private Node parseBlockComment() throws IOException {
    assert c == '*';
    int startLine = lineNumber();
    int lastC = '\0';
    next();
    while (!(lastC == '*' && c == '#')) {
      if (c == EOF) {
        throw new ParseException(
            "Unterminated #* - did not see matching *#", resourceName, startLine);
      }
      lastC = c;
      next();
    }
    next();
    return new CommentTokenNode(resourceName, startLine);
  }

  /**
   * Parses plain text, which is text that contains neither {@code $} nor {@code #}. The given
   * {@code firstChar} is the first character of the plain text, and {@link #c} is the second
   * (if the plain text is more than one character).
   */
  private Node parsePlainText(int firstChar) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.appendCodePoint(firstChar);
    return parsePlainText(sb);
  }

  private Node parsePlainText(StringBuilder sb) throws IOException {
    literal:
    while (true) {
      switch (c) {
        case EOF:
        case '$':
        case '#':
          break literal;
        default:
          // Just some random character.
      }
      sb.appendCodePoint(c);
      next();
    }
    return new ConstantExpressionNode(resourceName, lineNumber(), sb.toString());
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
  private Node parseReference() throws IOException {
    if (c == '{') {
      next();
      if (!isAsciiLetter(c)) {
        return parsePlainText(new StringBuilder("${"));
      }
      ReferenceNode node = parseReferenceNoBrace();
      expect('}');
      return node;
    } else {
      return parseReferenceNoBrace();
    }
  }

  /**
   * Same as {@link #parseReference()}, except it really must be a reference. A {@code $} in
   * normal text doesn't start a reference if it is not followed by an identifier. But in an
   * expression, for example in {@code #if ($x == 23)}, {@code $} must be followed by an
   * identifier.
   */
  private ReferenceNode parseRequiredReference() throws IOException {
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
    ReferenceNode lhs = new PlainReferenceNode(resourceName, lineNumber(), id);
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
   * <reference-member> -> .<id><reference-property-or-method><reference-suffix>
   * <reference-property-or-method> -> <id> |
   *                                   <id> ( <method-parameter-list> )
   * }</pre>
   *
   * @param lhs the reference node representing what appears to the left of the dot, like the
   *     {@code $x} in {@code $x.foo} or {@code $x.foo()}.
   */
  private ReferenceNode parseReferenceMember(ReferenceNode lhs) throws IOException {
    assert c == '.';
    next();
    if (!isAsciiLetter(c)) {
      // We've seen something like `$foo.!`, so it turns out it's not a member after all.
      pushback('.');
      return lhs;
    }
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
   *     {@code $x} in {@code $x.foo()}.
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
   *     {@code $x} in {@code $x[$i]}.
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
      node = parseRequiredReference();
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
    return new ConstantExpressionNode(resourceName, lineNumber(), readStringLiteral());
  }

  private String readStringLiteral() throws IOException {
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
    return sb.toString();
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
    return new ConstantExpressionNode(resourceName, lineNumber(), value);
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
    return new ConstantExpressionNode(resourceName, lineNumber(), value);
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
    return new ParseException(message, resourceName, lineNumber(), context.toString());
  }
}
