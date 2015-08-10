/*
 * Copyright (C) 2013 Google, Inc.
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

import java.io.IOException;
import java.io.Reader;

/**
 * A simplistic Java tokenizer that is just enough for {@link EclipseHack} to be able to scan Java
 * classes to find their abstract methods in order. This tokenizer can assume that the source code
 * is syntactically correct because the annotation processor won't run otherwise. It makes no effort
 * to account for Unicode escapes like {@code \}{@code u1234} but it is hard to imagine that
 * mattering. It also makes no effort to account for surrogate pairs, but again unless someone is
 * using such a pair in the name of one of the abstract methods we are looking for that should not
 * matter.
 *
 * @author Éamonn McManus
 */
class EclipseHackTokenizer {
  private final Reader reader;
  private char c;
  private static final char EOF = 0xffff;  // This is a noncharacter in the Unicode standard.

  EclipseHackTokenizer(Reader reader) {
    this.reader = reader;
    next();
  }

  /**
   * Returns the next token from the source code, or null if there are no more tokens. It is not
   * an error to call this method again after it has returned null, in which case it will return
   * null again. Much information is discarded: for example all numeric and string literals are
   * represented as {@code 0}. The returned string can be null but it cannot be empty, so it is safe
   * to check its first character if it is not null.
   */
  String nextToken() {
    // The invariant here is that when this method returns, c is the first character that is not
    // part of the previous token. This avoids having to look ahead, or "unget" characters.
    if (c == EOF) {
      return null;
    }
    // First, skip all space, comments of both varieties, and slashes that are not part of comments.
    // We're not interested in slashes for the analysis we do so this saves us from having to
    // recover from reading both the / and the b in a/b before realizing it is not a comment.
    skipSpaceAndCommentsAndSlashes();
    if (c == EOF) {
      return null;
    }
    if (c == '\'' || c == '"') {
      // We represent all strings and character literals as 0 because we don't care about them.
      skipCharacterOrStringLiteral();
      return "0";
    }
    if (c == '.') {
      // A dot might be the start of a floating point constant like .123 or it might be a standalone
      // token. If it is followed by a digit then it is the first case, and we will fall into the
      // next "if" to skip the number. Otherwise we return the dot token.
      next();
      if (!isAsciiDigit(c)) {
        return ".";
      }
    }
    if (isAsciiDigit(c)) {
      // We represent all numbers as 0 because we don't care about them.
      skipNumber();
      return "0";
    }
    if (Character.isJavaIdentifierStart(c)) {
      // We don't distinguish keywords from identifiers so anything that starts with a Java letter
      // is an identifier, which we scan and return as a token.
      return identifier();
    }
    char cc = c;
    next();
    return Character.toString(cc);
  }

  private static boolean isAsciiDigit(int c) {
    return '0' <= c && c <= '9';
  }

  // Scan a Java identifier whose first character is c, and return with c being the first
  // character after the identifier.
  private String identifier() {
    StringBuilder sb = new StringBuilder();
    while (Character.isJavaIdentifierPart(c)) {
      sb.append(c);
      next();
    }
    return sb.toString();
  }

  // Scan a Java number whose first character is c, and return with c being the first character
  // after the number. We use a very loose grammar to recognize numbers since we know that they
  // must be syntactically correct.
  private void skipNumber() {
    boolean lastWasE = false;
    while (c == '.' || Character.isLetterOrDigit(c) || (lastWasE && (c == '+' || c == '-'))) {
      lastWasE = (c == 'e' || c == 'E');
      next();
    }
  }

  // Skip over space and comments and slashes. On return, c is the first character that is not
  // any of these.
  private void skipSpaceAndCommentsAndSlashes() {
    while (true) {
      if (Character.isWhitespace(c)) {
        next();
        continue;
      }
      if (c != '/') {
        return;
      }
      next();
      switch (c) {
        case '/':
          skipSlashSlashComment();
          break;
        case '*':
          skipSlashStarComment();
          break;
      }
      // Now c is either the first character after a comment or the character immediately after /
      // that was neither // nor /*.
    }
  }

  // Scan a // comment. On entry, c is the second / in the comment. Since we are going to be
  // dropping all whitespace anyway we can return as soon as we see \n or \r with c equal to that.
  private void skipSlashSlashComment() {
    while (c != '\n' && c != '\r' && c != EOF) {
      next();
    }
  }

  // Scan a /* comment. On entry, c is the * in /* so we must skip it to avoid recognizing
  // /*/ as a complete comment. On return, c is the character after */ .
  private void skipSlashStarComment() {
    next();
    while (true) {
      switch (c) {
        case EOF:
          return;
        case '*':
          next();
          if (c == '/') {
            next();
            return;
          }
          break;
        default:
          next();
          break;
      }
    }
  }

  // Scan a character literal ('a', '\'', etc) or a string literal ("aa", "\"foo\"", etc).
  // On entry, c is the opening quote character and on return c is the character after the
  // corresponding closing quote. The only special treatment is to skip the character after \
  // so we don't prematurely stop when we see \' or \".
  private void skipCharacterOrStringLiteral() {
    char quote = c;  // ' or "
    next();
    while (c != quote && c != EOF) {
      if (c == '\\') {
        next();
      }
      next();
    }
    next();
  }

  // Set c to the next character from the input, or to EOF if there are no more characters.
  private void next() {
    if (c == EOF) {
      return;
    }
    try {
      int c1 = reader.read();
      if (c1 < 0) {
        c = EOF;
      } else {
        c = (char) c1;
      }
    } catch (IOException e) {
      c = EOF;
    }
  }
}