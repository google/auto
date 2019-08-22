/*
 * Copyright 2014 Google LLC
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
    StringBuilder out = new StringBuilder();
    JavaScanner scanner = new JavaScanner(s);
    s = scanner.string();
    int len = s.length();
    for (int start = 0, previous = 0, braces = 0, parens = 0, end = 0;
        start < len;
        previous = start, start = end) {
      end = scanner.tokenEnd(start);
      // The tokenized string always ends with \n so we can usually look at s.charAt(end) without
      // worrying about going past the end of the string.
      switch (s.charAt(start)) {
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
        case ' ':
          // This token is a string of consecutive spaces that is not at the start of a line.
          // Consecutive spaces at the start of a line are attached to the previous newline, and
          // we don't expect the first line to start with spaces. So we are going to compress this
          // into just one space, and we are going to delete it entirely if it follows '(' or
          // precedes a newline or one of the punctuation characters here.
          if (s.charAt(previous) != '(' && "\n.,;)".indexOf(s.charAt(end)) < 0) {
            out.append(' ');
          }
          continue;
        case '\n':
          // This token is a newline plus any following spaces (the indentation of the next line).
          // If it is followed by something other than a newline then we will output it. Otherwise,
          // it is part of a sequence of newlines but it is not the last one. If this is a context
          // where we delete blank lines, or if this is not the first new line in the sequence, or
          // if we are at the start of the file, we will delete this one. Otherwise we will output a
          // single newline with no following indentation. Contexts where we delete blank lines are
          // inside parentheses or inside more than one set of braces.
          if (end < len && s.charAt(end) != '\n') {
            if (out.length() == 0) {
              // Omit newlines at the very start of the file.
              start++;
            }
            break; // Output the newline and its following indentation.
          }
          if (parens == 0 && braces < 2 && s.charAt(previous) != '\n' && out.length() > 0) {
            out.append('\n');
          }
          continue;
        default:
          break;
      }
      out.append(s, start, end);
    }
    return out.toString();
  }
}
