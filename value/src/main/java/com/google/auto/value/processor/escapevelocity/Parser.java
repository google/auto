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

import com.google.auto.value.processor.escapevelocity.Node.EofNode;
import com.google.auto.value.processor.escapevelocity.ReferenceNode.IndexReferenceNode;
import com.google.auto.value.processor.escapevelocity.ReferenceNode.MemberReferenceNode;
import com.google.auto.value.processor.escapevelocity.ReferenceNode.MethodReferenceNode;
import com.google.auto.value.processor.escapevelocity.ReferenceNode.PlainReferenceNode;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
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
   */
  Template parse() throws IOException {
    ImmutableList.Builder<Node> nodes = ImmutableList.builder();
    Node node;
    do {
      node = parseNode();
      nodes.add(node);
    } while (!(node instanceof EofNode));
    return new Template(nodes.build());
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
    while (Character.isSpaceChar(c)) {
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

  private Node parseDirective() throws IOException {
    throw parseException("# directives not yet supported");
  }

  /**
   * Parses and discards a comment, which is {@code ##} followed by any number of characters up to
   * and including the next newline.
   */
  private Node parseComment() throws IOException {
    while (c != '\n' && c != EOF) {
      next();
    }
    next();
    return Node.emptyNode(lineNumber());
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

  /**
   * Parses an expression, which can only occur within a directive like {@code #if} or {@code #set}.
   * This version only supports references, like {@code $x} or {@code $x.foo[$y]}, and literals,
   * like {@code 5} or {@code "hello"}, as expressions.
   */
  private ExpressionNode parseExpression() throws IOException {
    skipSpace();
    return parsePrimary();
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
    if (!isAsciiLetter(what.charAt(0))) {
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
