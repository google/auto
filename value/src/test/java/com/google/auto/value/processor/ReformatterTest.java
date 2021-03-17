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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link Reformatter}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class ReformatterTest {
  @Test
  public void simple() {
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
        "package com.latin.declension;\n"
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
    assertThat(Reformatter.fixup(input)).isEqualTo(output);
  }

  @Test
  public void specialSpaces() {
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
        "package com.example.whatever;\n"
            + "\n"
            + "public class SomeClass {\n"
            + "  static final String STRING = \"  hello  world  \\n\";\n"
            + "  static final String STRING_WITH_QUOTES = \" \\\"quote  me  now  \\\"  \";\n"
            + "  static final int INT = /* not a string \" */ 23;\n"
            + "  static final char QUOTE = '\"';\n"
            + "  static final char QUOTE2 = '\\\"';\n"
            + "}\n";
    assertThat(Reformatter.fixup(input)).isEqualTo(output);
  }

  @Test
  public void noTrailingNewline() {
    String input = "package com.example.whatever;\n\npublic class SomeClass {}";
    String output = input + "\n";
    assertThat(Reformatter.fixup(input)).isEqualTo(output);
  }

  @Test
  public void indent() {
    String input =
        "  class Test {\n"
            + "private final int field;\n"
            + "\n"
            + "Test(\n"
            + "@Interesting Integer field,\n"
            + "boolean ignored) {\n"
            + "this.field = field;\n"
            + "}\n"
            + "\n"
            + "@Override\n"
            + "public boolean equals(Object x) {\n"
            + "return x instanceof Test\n"
            + "&& ((Test) x).field == field;\n"
            + "// interesting\n"
            + "}\n"
            + "\n"
            + "@Override\n"
            + "public String toString() {\n"
            + "return \"Test{\"\n"
            + "+ \"field=\" + field\n"
            + "+ \"}\";\n"
            + "}\n"
            + "}\n";
    String output =
        "class Test {\n"
            + "  private final int field;\n"
            + "\n"
            + "  Test(\n"
            + "      @Interesting Integer field,\n"
            + "      boolean ignored) {\n"
            + "    this.field = field;\n"
            + "  }\n"
            + "\n"
            + "  @Override\n"
            + "  public boolean equals(Object x) {\n"
            + "    return x instanceof Test\n"
            + "        && ((Test) x).field == field;\n"
            + "    // interesting\n"
            + "  }\n"
            + "\n"
            + "  @Override\n"
            + "  public String toString() {\n"
            + "    return \"Test{\"\n"
            + "        + \"field=\" + field\n"
            + "        + \"}\";\n"
            + "  }\n"
            + "}\n";
    assertThat(Reformatter.fixup(input)).isEqualTo(output);
  }
}
