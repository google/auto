/*
 * Copyright (C) 2013 Google Inc.
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

import com.google.common.collect.ImmutableMultimap;
import java.io.StringReader;
import junit.framework.TestCase;

/**
 * Tests for {@link AbstractMethodExtractor}.
 *
 * @author Éamonn McManus
 */
public class AbstractMethodExtractorTest extends TestCase {
  public void testSimple() {
    String source = "package com.example;\n"
        + "import com.google.auto.value.AutoValue;\n"
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
    EclipseHackTokenizer tokenizer = new EclipseHackTokenizer(new StringReader(source));
    AbstractMethodExtractor extractor = new AbstractMethodExtractor();
    ImmutableMultimap<String, String> expected = ImmutableMultimap.of(
        "com.example.Foo", "one",
        "com.example.Foo", "two",
        "com.example.Foo", "three");
    ImmutableMultimap<String, String> actual = extractor.abstractMethods(tokenizer, "com.example");
    assertEquals(expected, actual);
  }

  public void testNested() {
    String source = "package com.example;\n"
        + "import com.google.auto.value.AutoValue;\n"
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
    EclipseHackTokenizer tokenizer = new EclipseHackTokenizer(new StringReader(source));
    AbstractMethodExtractor extractor = new AbstractMethodExtractor();
    ImmutableMultimap<String, String> expected = ImmutableMultimap.of(
        "com.example.Foo.Baz", "complicated",
        "com.example.Foo.Baz", "simple",
        "com.example.Foo.Bar", "whatever");
    ImmutableMultimap<String, String> actual = extractor.abstractMethods(tokenizer, "com.example");
    assertEquals(expected, actual);
  }

  public void testClassConstants() {
    // Regression test for a bug where String.class was parsed as introducing a class definition
    // of a later identifier.
    String source = "package com.example;\n"
        + "import com.google.auto.value.AutoValue;\n"
        + "import com.google.common.collect.ImmutableSet;\n"
        + "import com.google.common.labs.reflect.ValueType;\n"
        + "import com.google.common.primitives.Primitives;\n"
        + "public final class ProducerMetadata<T> extends ValueType {\n"
        + "  private static final ImmutableSet<Class<?>> ALLOWABLE_MAP_KEY_TYPES =\n"
        + "    ImmutableSet.<Class<?>>builder()\n"
        + "    .addAll(Primitives.allPrimitiveTypes())\n"
        + "    .addAll(Primitives.allWrapperTypes())\n"
        + "    .add(String.class)\n"
        + "    .add(Class.class)\n"
        + "    .build();\n"
        + "  @AutoValue abstract static class SourcedKeySet {\n"
        + "    abstract ImmutableSet<Key<?>> unknownSource();\n"
        + "    abstract ImmutableSet<Key<?>> fromInputs();\n"
        + "    abstract ImmutableSet<Key<?>> fromNodes();\n"
        + "    abstract ImmutableSet<Key<?>> all();\n"
        + "  }\n"
        + "}";
    EclipseHackTokenizer tokenizer = new EclipseHackTokenizer(new StringReader(source));
    AbstractMethodExtractor extractor = new AbstractMethodExtractor();
    ImmutableMultimap<String, String> expected = ImmutableMultimap.of(
        "com.example.ProducerMetadata.SourcedKeySet", "unknownSource",
        "com.example.ProducerMetadata.SourcedKeySet", "fromInputs",
        "com.example.ProducerMetadata.SourcedKeySet", "fromNodes",
        "com.example.ProducerMetadata.SourcedKeySet", "all");
    ImmutableMultimap<String, String> actual = extractor.abstractMethods(tokenizer, "com.example");
    assertEquals(expected, actual);
  }
}
