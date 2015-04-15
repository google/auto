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
    Tokenizer tokenizer = new Tokenizer(s);
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

  // A simplistic Java tokenizer. This is different from the one in JavaTokenizer (which is only
  // needed for EclipseHack). The needs of the two tokenizers are very different: JavaTokenizer is
  // only needed to scan through an existing source file to find abstract method declarations, so
  // it can discard everything that isn't needed for that, including comments and string literals
  // for example. Meanwhile, this Tokenizer needs to return a sequence of tokens that can be used
  // to reconstruct the source code. JavaTokenizer also operates on a Reader (which in practice is
  // coming from a file), while here we already have the source code in a String, which means that
  // we can just return token boundaries rather than the tokens themselves.
  //
  // We are not dealing with arbitrary user code so we can assume there are no exotic things like
  // tabs or Unicode escapes that resolve into quotes. The purpose of the tokenizer here is to
  // return a sequence of offsets that split the string up in a way that allows us to work with
  // spaces without having to worry whether they are inside strings or comments. The particular
  // properties we use are that every string and character literal and every comment is a single
  // token; every newline plus all following indentation is a single token; and every other string
  // of consecutive spaces outside a comment or literal is a single token. That means that we can
  // safely compress a token that starts with a space into a single space, without falsely removing
  // indentation or changing the contents of strings.
  private static class Tokenizer {
    private final String s;

    Tokenizer(String s) {
      if (!s.endsWith("\n")) {
        s += "\n";
        // This allows us to avoid checking for the end of the string in most cases.
      }
      this.s = s;
    }

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
          return quoteEnd(start);
        default:
          // Every other character is considered to be its own token.
          return start + 1;
      }
    }

    int spaceEnd(int start) {
      assert s.charAt(start) == ' ' || s.charAt(start) == '\n';
      int i;
      for (i = start + 1; i < s.length() && s.charAt(i) == ' '; i++) {
      }
      return i;
    }

    int blockCommentEnd(int start) {
      assert s.charAt(start) == '/' && s.charAt(start + 1) == '*';
      int i;
      for (i = start + 1; s.charAt(i) != '*' || s.charAt(i + 1) != '/'; i++) {
      }
      return i;
    }

    int lineCommentEnd(int start) {
      assert s.charAt(start) == '/' && s.charAt(start + 1) == '/';
      int end = s.indexOf('\n', start + 2);
      assert end > 0;
      return end;
    }

    int quoteEnd(int start) {
      char quote = s.charAt(start);
      assert quote == '\'' || quote == '"';
      int i;
      for (i = start + 1; s.charAt(i) != quote; i++) {
        if (s.charAt(i) == '\\') {
          i++;
        }
      }
      return i + 1;
    }
  }
}
