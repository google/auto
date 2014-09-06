package com.google.auto.value.processor;

import junit.framework.TestCase;

/**
 * Unit tests for {@link Reformatter}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class ReformatterTest extends TestCase {
  public void testSimple() {
    String input =
        "\n"
        + "package com.latin.declension;  \n"
        + "\n"
        + "\n"
        + "public  class  Idem  {  \n"
        + "  \n"
        + "  Eadem   idem  ;  \n"
        + "\n"
        + "  Eundem eandem ( Idem  eiusdem  )  {\n"
        + "\n"
        + "    eiusdem (   eiusdem  )  ;  \n"
        + "\n"
        + "    eidem_eidem_eidem( ) ;\n"
        + "\n"
        + "  }\n"
        + "\n"
        + "\n"
        + "  Eodem ( Eadem eodem ) { }\n";
    String output =
        "\n"
        + "package com.latin.declension;\n"
        + "\n"
        + "public class Idem {\n"
        + "\n"
        + "  Eadem idem;\n"
        + "\n"
        + "  Eundem eandem (Idem eiusdem) {\n"
        + "    eiusdem (eiusdem);\n"
        + "    eidem_eidem_eidem();\n"
        + "  }\n"
        + "\n"
        + "  Eodem (Eadem eodem) { }\n";
    assertEquals(output, Reformatter.fixup(input));
  }

  public void testSpecialSpaces() {
    String input =
        "\n"
        + "package com.example.whatever;\n"
        + "\n"
        + "public class SomeClass {\n"
        + "  static final String STRING = \"  hello  world  \\n\";  \n"
        + "  static final String STRING_WITH_QUOTES = \" \\\"quote  me  now  \\\"  \"  ;\n"
        + "  static final int INT = /* not a string \" */  23  ;\n"
        + "  static final char QUOTE = '\"'  ;\n"
        + "  static final char QUOTE2 = '\\\"'  ;\n"
        + "}\n";
    String output =
        "\n"
        + "package com.example.whatever;\n"
        + "\n"
        + "public class SomeClass {\n"
        + "  static final String STRING = \"  hello  world  \\n\";\n"
        + "  static final String STRING_WITH_QUOTES = \" \\\"quote  me  now  \\\"  \";\n"
        + "  static final int INT = /* not a string \" */ 23;\n"
        + "  static final char QUOTE = '\"';\n"
        + "  static final char QUOTE2 = '\\\"';\n"
        + "}\n";
    assertEquals(output, Reformatter.fixup(input));
  }
}
