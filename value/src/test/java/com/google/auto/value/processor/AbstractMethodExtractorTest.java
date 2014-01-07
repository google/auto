package com.google.auto.value.processor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link AbstractMethodExtractor}.
 *
 * @author Éamonn McManus
 */
public class AbstractMethodExtractorTest extends TestCase {
  public void testSimple() {
    String source = "package com.example;\n"
        + "import com.google.common.labs.autovalue.AutoValue;\n"
        + "import java.util.Map;\n"
        + "@AutoValue"
        + "abstract class Foo {\n"
        + "  Foo(int one, String two, Map<String, String> three) {\n"
        + "    return new AutoValue_Foo(one, two, three);\n"
        + "  }\n"
        + "  abstract int one();\n"
        + "  abstract String two();\n"
        + "  abstract Map<String, String> three();\n"
        + "}\n";
    JavaTokenizer tokenizer = new JavaTokenizer(new StringReader(source));
    AbstractMethodExtractor extractor = new AbstractMethodExtractor();
    Map<String, List<String>> expected = ImmutableMap.<String, List<String>>of(
        "com.example.Foo", ImmutableList.of("one", "two", "three"));
    Map<String, List<String>> actual = extractor.abstractMethods(tokenizer, "com.example");
    assertEquals(expected, actual);
  }

  public void testNested() {
    String source = "package com.example;\n"
        + "import com.google.common.labs.autovalue.AutoValue;\n"
        + "import java.util.Map;\n"
        + "abstract class Foo {\n"
        + "  @AutoValue\n"
        + "  abstract class Baz {\n"
        + "    abstract <T extends Number & Comparable<T>> T complicated();\n"
        + "    abstract int simple();\n"
        + "    abstract class Irrelevant {\n"
        + "      void distraction() {\n"
        + "        abstract class FurtherDistraction {\n"
        + "          abstract int buh();\n"
        + "        }\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "  @AutoValue\n"
        + "  abstract class Bar {\n"
        + "    abstract String whatever();\n"
        + "  }\n"
        + "  abstract class AlsoIrrelevant {\n"
        + "    void distraction() {}\n"
        + "  }\n"
        + "}\n";
    JavaTokenizer tokenizer = new JavaTokenizer(new StringReader(source));
    AbstractMethodExtractor extractor = new AbstractMethodExtractor();
    Map<String, List<String>> expected = ImmutableMap.<String, List<String>>of(
        "com.example.Foo.Baz", ImmutableList.of("complicated", "simple"),
        "com.example.Foo.Bar", ImmutableList.of("whatever"));
    Map<String, List<String>> actual = extractor.abstractMethods(tokenizer, "com.example");
    assertEquals(expected, actual);
  }
}
