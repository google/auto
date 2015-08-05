package com.google.auto.value.processor;

import junit.framework.TestCase;

import java.io.StringReader;

/**
 * Unit tests for {@link EclipseHackTokenizer}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class EclipseHackTokenizerTest extends TestCase {
  public void testSimple() {
    // Construct a string containing the tokens produced from this source code, with a space after
    // each one, and compare it with what we expect from the tokenization.
    String source = "package com.example;\n"
        + "import com.example.foo.Bar;\n"
        + "\n"
        + "/**\n"
        + " * Fictitious Foo class.\n"
        + " */\n"
        + "public class Foo {   // comment\n"
        + "  abstract int bar();\n"
        + "  Foo() {}\n"
        + "  public static create(int bar) {\n"
        + "    System.out.println(\"hello, \\\"world\\\"\");\n"
        + "    int x = 1729;\n"
        + "    float f = 1.2e+3;\n"
        + "    char c1 = 'x';\n"
        + "    char c2 = '\\'';\n"
        + "    char c3 = '\\\\';\n"
        + "    return new AutoValue_Foo(bar);\n"
        + "  }\n"
        + "}\n";
    String expectedTokens = "package com . example ; "
        + "import com . example . foo . Bar ; "
        + "public class Foo { "
        + "abstract int bar ( ) ; "
        + "Foo ( ) { } "
        + "public static create ( int bar ) { "
        + "System . out . println ( 0 ) ; "
        + "int x = 0 ; "
        + "float f = 0 ; "
        + "char c1 = 0 ; "
        + "char c2 = 0 ; "
        + "char c3 = 0 ; "
        + "return new AutoValue_Foo ( bar ) ; "
        + "} "
        + "} ";
    EclipseHackTokenizer tokenizer = new EclipseHackTokenizer(new StringReader(source));
    StringBuilder tokenStringBuilder = new StringBuilder();
    String token;
    while ((token = tokenizer.nextToken()) != null) {
      assertFalse(token.contains(" "));
      tokenStringBuilder.append(token).append(' ');
      if (tokenStringBuilder.length() > 1000) {
        // The tokenizer must be stuck in a loop returning the same token over and over.
        fail("Too many tokens: " + tokenStringBuilder);
      }
    }
    String tokens = tokenStringBuilder.toString();
    assertEquals(expectedTokens, tokens);
  }
}
