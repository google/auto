/*
 * Copyright 2015 Google LLC
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

/**
 * A simplistic Java scanner. This scanner returns a sequence of tokens that can be used to
 * reconstruct the source code. Since the source code is coming from a string, the scanner in fact
 * just returns token boundaries rather than the tokens themselves.
 *
 * <p>We are not dealing with arbitrary user code so we can assume there are no exotic things like
 * tabs or Unicode escapes that resolve into quotes. The purpose of the scanner here is to return a
 * sequence of offsets that split the string up in a way that allows us to work with spaces without
 * having to worry whether they are inside strings or comments. The particular properties we use are
 * that every string and character literal and every comment is a single token; every newline plus
 * all following indentation is a single token; and every other string of consecutive spaces outside
 * a comment or literal is a single token. That means that we can safely compress a token that
 * starts with a space into a single space, without falsely removing indentation or changing the
 * contents of strings.
 *
 * <p>In addition to real Java syntax, this scanner recognizes tokens of the form {@code `text`},
 * which are used in the templates to wrap fully-qualified type names, so that they can be extracted
 * and replaced by imported names if possible.
 *
 * @author Éamonn McManus
 */
class JavaScanner {
  private final String s;

  JavaScanner(String s) {
    this.s = s.endsWith("\n") ? s : (s + '\n');
    // This allows us to avoid checking for the end of the string in most cases.
  }

  /**
   * Returns the string being scanned, which is either the original input string or that string plus
   * a newline.
   */
  String string() {
    return s;
  }

  /** Returns the position at which this token ends and the next token begins. */
  int tokenEnd(int start) {
    if (start >= s.length()) {
      return s.length();
    }
    switch (s.charAt(start)) {
      case ' ':
      case '\n':
        return spaceEnd(start);
      case '/':
        if (s.charAt(start + 1) == '*') {
          return blockCommentEnd(start);
        } else if (s.charAt(start + 1) == '/') {
          return lineCommentEnd(start);
        } else {
          return start + 1;
        }
      case '\'':
      case '"':
      case '`':
        return quoteEnd(start);
      default:
        // Every other character is considered to be its own token.
        return start + 1;
    }
  }

  private int spaceEnd(int start) {
    assert s.charAt(start) == ' ' || s.charAt(start) == '\n';
    int i;
    for (i = start + 1; i < s.length() && s.charAt(i) == ' '; i++) {}
    return i;
  }

  private int blockCommentEnd(int start) {
    assert s.charAt(start) == '/' && s.charAt(start + 1) == '*';
    int i;
    for (i = start + 2; s.charAt(i) != '*' || s.charAt(i + 1) != '/'; i++) {}
    return i + 2;
  }

  private int lineCommentEnd(int start) {
    assert s.charAt(start) == '/' && s.charAt(start + 1) == '/';
    int end = s.indexOf('\n', start + 2);
    assert end > 0;
    return end;
  }

  private int quoteEnd(int start) {
    char quote = s.charAt(start);
    assert quote == '\'' || quote == '"' || quote == '`';
    int i;
    for (i = start + 1; s.charAt(i) != quote; i++) {
      if (s.charAt(i) == '\\') {
        i++;
      }
    }
    return i + 1;
  }
}
