/*
 * Copyright (C) 2014 Google Inc.
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
 * Postprocessor that runs over the output of the template engine in order to make it look nicer.
 * Mostly, this involves removing surplus horizontal and vertical space.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
class Reformatter {
  static String fixup(String s) {
    s = removeTrailingSpace(s);
    s = compressBlankLines(s);
    s = compressSpace(s);
    return s;
  }

  private static String removeTrailingSpace(String s) {
    // Remove trailing space from all lines. This is mainly to make it easier to find
    // blank lines later.
    if (!s.endsWith("\n")) {
      s += '\n';
    }
    StringBuilder sb = new StringBuilder(s.length());
    int start = 0;
    while (start < s.length()) {
      int nl = s.indexOf('\n', start);
      int i = nl - 1;
      while (i >= start && s.charAt(i) == ' ') {
        i--;
      }
      sb.append(s.substring(start, i + 1)).append('\n');
      start = nl + 1;
    }
    return sb.toString();
  }

  private static String compressBlankLines(String s) {
    // Remove extra blank lines. An "extra" blank line is either a blank line where the previous
    // line was also blank; or a blank line that appears inside parentheses or inside more than one
    // set of braces. This means that we preserve blank lines inside our top-level class, but not
    // within our generated methods.
    StringBuilder sb = new StringBuilder(s.length());
    int braces = 0;
    int parens = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '(':
          parens++;
          break;
        case ')':
          parens--;
          break;
        case '{':
          braces++;
          break;
        case '}':
          braces--;
          break;
        case '\n':
          int j = i + 1;
          while (j < s.length() && s.charAt(j) == '\n') {
            j++;
          }
          if (j > i + 1) {
            if (parens == 0 && braces <= 1) {
              sb.append("\n");
            }
            i = j - 1;
          }
          break;
      }
      sb.append(c);
    }
    return sb.toString();
  }

  private static String compressSpace(String s) {
    // Remove extra spaces. An "extra" space is one that is not part of the indentation at the start
    // of a line, and where the next character is also a space or a right paren or a semicolon
    // or a dot or a comma, or the preceding character is a left paren.
    // TODO(emcmanus): consider merging all three passes using this tokenization approach.
    StringBuilder sb = new StringBuilder(s.length());
    JavaScanner tokenizer = new JavaScanner(s);
    int len = s.length();
    int end;
    for (int start = 0; start < len; start = end) {
      end = tokenizer.tokenEnd(start);
      if (s.charAt(start) == ' ') {
        // Since we consider a newline plus following indentation to be a single token, we only
        // see a token starting with ' ' if it is in the middle of a line.
        if (sb.charAt(sb.length() - 1) == '(') {
          continue;
        }
        // Since we ensure that the tokenized string ends with \n, and a whitespace token stops
        // at \n, it is safe to look at end.
        char nextC = s.charAt(end);
        if (".,;)".indexOf(nextC) >= 0) {
          continue;
        }
        sb.append(' ');
      } else {
        sb.append(s.substring(start, end));
      }
    }
    return sb.toString();
  }
}
