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
package com.google.auto.value.gwt;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.auto.value.processor.AutoValueProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.lang.model.SourceVersion;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test of generated source for GWT serialization classes.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@RunWith(JUnit4.class)
public class GwtCompilationTest {
  private static final JavaFileObject GWT_COMPATIBLE =
      JavaFileObjects.forSourceLines(
          "com.google.annotations.GwtCompatible",
          "package com.google.annotations;",
          "",
          "public @interface GwtCompatible {",
          "  boolean serializable() default false;",
          "}");

  /**
   * Test where the serialized properties don't include generics, so no {@code @SuppressWarnings}
   * annotation is needed. We explicitly check that one is not included anyway, because Eclipse for
   * example can be configured to complain about unnecessary warning suppression.
   */
  @Test
  public void testBasic() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.annotations.GwtCompatible;",
            "",
            "@AutoValue",
            "@GwtCompatible(serializable = true)",
            "public abstract class Baz {",
            "  public abstract int buh();",
            "",
            "  public static Baz create(int buh) {",
            "    return new AutoValue_Baz(buh);",
            "  }",
            "}");
    JavaFileObject expectedOutput =
        JavaFileObjects.forSourceLines(
            "foo.bar.AutoValue_Baz_CustomFieldSerializer",
            "package foo.bar;",
            "",
            "import com.google.gwt.user.client.rpc.CustomFieldSerializer;",
            "import com.google.gwt.user.client.rpc.SerializationException;",
            "import com.google.gwt.user.client.rpc.SerializationStreamReader;",
            "import com.google.gwt.user.client.rpc.SerializationStreamWriter;",
            "import " + generatedAnnotationType() + ";",
            "",
            "@Generated(\"com.google.auto.value.processor.AutoValueProcessor\")",
            "public final class AutoValue_Baz_CustomFieldSerializer"
                + " extends CustomFieldSerializer<AutoValue_Baz> {",
            "",
            "  public static AutoValue_Baz instantiate(",
            "      SerializationStreamReader streamReader) throws SerializationException {",
            "    int buh = streamReader.readInt();",
            "    return new AutoValue_Baz(buh);",
            "  }",
            "",
            "  public static void serialize(",
            "      SerializationStreamWriter streamWriter,",
            "      AutoValue_Baz instance) throws SerializationException {",
            "    streamWriter.writeInt(instance.buh());",
            "  }",
            "",
            "  public static void deserialize(",
            "      @SuppressWarnings(\"unused\") SerializationStreamReader streamReader,",
            "      @SuppressWarnings(\"unused\") AutoValue_Baz instance) {",
            "  }",
            "",
            "  @SuppressWarnings(\"unused\") private int dummy_3f8e1b04;",
            "",
            "  @Override",
            "  public void deserializeInstance(",
            "      SerializationStreamReader streamReader,",
            "      AutoValue_Baz instance) {",
            "    deserialize(streamReader, instance);",
            "  }",
            "",
            "  @Override",
            "  public boolean hasCustomInstantiateInstance() {",
            "    return true;",
            "  }",
            "",
            "  @Override",
            "  public AutoValue_Baz instantiateInstance(",
            "      SerializationStreamReader streamReader) throws SerializationException {",
            "    return instantiate(streamReader);",
            "  }",
            "",
            "  @Override",
            "  public void serializeInstance(",
            "    SerializationStreamWriter streamWriter,",
            "    AutoValue_Baz instance) throws SerializationException {",
            "    serialize(streamWriter, instance);",
            "  }",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject, GWT_COMPATIBLE);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Baz_CustomFieldSerializer")
        .hasSourceEquivalentTo(expectedOutput);
  }

  /**
   * Test where the serialized properties don't include generics, so a {@code @SuppressWarnings}
   * annotation is needed.
   */
  @Test
  public void testSuppressWarnings() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.annotations.GwtCompatible;",
            "",
            "import java.util.List;",
            "",
            "@AutoValue",
            "@GwtCompatible(serializable = true)",
            "public abstract class Baz {",
            "  public abstract List<String> buh();",
            "",
            "  public static Baz create(List<String> buh) {",
            "    return new AutoValue_Baz(buh);",
            "  }",
            "}");
    JavaFileObject expectedOutput =
        JavaFileObjects.forSourceLines(
            "foo.bar.AutoValue_Baz_CustomFieldSerializer",
            "package foo.bar;",
            "",
            "import com.google.gwt.user.client.rpc.CustomFieldSerializer;",
            "import com.google.gwt.user.client.rpc.SerializationException;",
            "import com.google.gwt.user.client.rpc.SerializationStreamReader;",
            "import com.google.gwt.user.client.rpc.SerializationStreamWriter;",
            "import java.util.List;",
            "import " + generatedAnnotationType() + ";",
            "",
            "@Generated(\"com.google.auto.value.processor.AutoValueProcessor\")",
            "public final class AutoValue_Baz_CustomFieldSerializer"
                + " extends CustomFieldSerializer<AutoValue_Baz> {",
            "",
            "  public static AutoValue_Baz instantiate(",
            "      SerializationStreamReader streamReader) throws SerializationException {",
            "    @SuppressWarnings(\"unchecked\")",
            "    List<String> buh = (List<String>) streamReader.readObject();",
            "    return new AutoValue_Baz(buh);",
            "  }",
            "",
            "  public static void serialize(",
            "      SerializationStreamWriter streamWriter,",
            "      AutoValue_Baz instance) throws SerializationException {",
            "    streamWriter.writeObject(instance.buh());",
            "  }",
            "",
            "  public static void deserialize(",
            "      @SuppressWarnings(\"unused\") SerializationStreamReader streamReader,",
            "      @SuppressWarnings(\"unused\") AutoValue_Baz instance) {",
            "  }",
            "",
            "  @SuppressWarnings(\"unused\") private int dummy_949e312e;",
            "",
            "  @Override",
            "  public void deserializeInstance(",
            "      SerializationStreamReader streamReader,",
            "      AutoValue_Baz instance) {",
            "    deserialize(streamReader, instance);",
            "  }",
            "",
            "  @Override",
            "  public boolean hasCustomInstantiateInstance() {",
            "    return true;",
            "  }",
            "",
            "  @Override",
            "  public AutoValue_Baz instantiateInstance(",
            "      SerializationStreamReader streamReader) throws SerializationException {",
            "    return instantiate(streamReader);",
            "  }",
            "",
            "  @Override",
            "  public void serializeInstance(",
            "    SerializationStreamWriter streamWriter,",
            "    AutoValue_Baz instance) throws SerializationException {",
            "    serialize(streamWriter, instance);",
            "  }",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject, GWT_COMPATIBLE);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Baz_CustomFieldSerializer")
        .hasSourceEquivalentTo(expectedOutput);
  }

  /**
   * Test builders and classes that are generic (as opposed to just containing properties with
   * generics).
   */
  @Test
  public void testBuildersAndGenerics() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "import com.google.annotations.GwtCompatible;",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            "",
            "@AutoValue",
            "@GwtCompatible(serializable = true)",
            "public abstract class Baz<K extends Comparable<K>, V extends K> {",
            "  public abstract Map<K, V> map();",
            "  public abstract ImmutableMap<K, V> immutableMap();",
            "",
            "  public static <K extends Comparable<K>, V extends K> Builder<K, V> builder() {",
            "    return new AutoValue_Baz.Builder<K, V>();",
            "  }",
            "",
            "  @AutoValue.Builder",
            "  public interface Builder<K extends Comparable<K>, V extends K> {",
            "    Builder<K, V> map(Map<K, V> map);",
            "    ImmutableMap.Builder<K, V> immutableMapBuilder();",
            "    Baz<K, V> build();",
            "  }",
            "}");
    JavaFileObject expectedOutput =
        JavaFileObjects.forSourceLines(
            "foo.bar.AutoValue_Baz_CustomFieldSerializer",
            "package foo.bar;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import com.google.gwt.user.client.rpc.CustomFieldSerializer;",
            "import com.google.gwt.user.client.rpc.SerializationException;",
            "import com.google.gwt.user.client.rpc.SerializationStreamReader;",
            "import com.google.gwt.user.client.rpc.SerializationStreamWriter;",
            "import java.util.Map;",
            "import " + generatedAnnotationType() + ";",
            "",
            "@Generated(\"com.google.auto.value.processor.AutoValueProcessor\")",
            "public final class AutoValue_Baz_CustomFieldSerializer"
                + "<K extends Comparable<K>, V extends K>"
                + " extends CustomFieldSerializer<AutoValue_Baz<K, V>> {",
            "",
            "  public static <K extends Comparable<K>, V extends K> AutoValue_Baz<K, V>"
                + " instantiate(",
            "      SerializationStreamReader streamReader) throws SerializationException {",
            "    @SuppressWarnings(\"unchecked\")",
            "    Map<K, V> map = (Map<K, V>) streamReader.readObject();",
            "    @SuppressWarnings(\"unchecked\")",
            "    ImmutableMap<K, V> immutableMap = (ImmutableMap<K, V>) streamReader.readObject();",
            "    AutoValue_Baz.Builder<K, V> builder$ = new AutoValue_Baz.Builder<K, V>();",
            "    builder$.map(map);",
            "    builder$.immutableMapBuilder().putAll(immutableMap);",
            "    return (AutoValue_Baz<K, V>) builder$.build();",
            "  }",
            "",
            "  public static <K extends Comparable<K>, V extends K> void serialize(",
            "      SerializationStreamWriter streamWriter,",
            "      AutoValue_Baz<K, V> instance) throws SerializationException {",
            "    streamWriter.writeObject(instance.map());",
            "    streamWriter.writeObject(instance.immutableMap());",
            "  }",
            "",
            "  public static <K extends Comparable<K>, V extends K> void deserialize(",
            "      @SuppressWarnings(\"unused\") SerializationStreamReader streamReader,",
            "      @SuppressWarnings(\"unused\") AutoValue_Baz<K, V> instance) {",
            "  }",
            "",
            "  @SuppressWarnings(\"unused\")",
            "  private int dummy_2865d9ec;",
            "",
            "  @Override",
            "  public void deserializeInstance(",
            "      SerializationStreamReader streamReader,",
            "      AutoValue_Baz<K, V> instance) {",
            "    deserialize(streamReader, instance);",
            "  }",
            "",
            "  @Override",
            "  public boolean hasCustomInstantiateInstance() {",
            "    return true;",
            "  }",
            "",
            "  @Override",
            "  public AutoValue_Baz<K, V> instantiateInstance(",
            "      SerializationStreamReader streamReader) throws SerializationException {",
            "    return instantiate(streamReader);",
            "  }",
            "",
            "  @Override",
            "  public void serializeInstance(",
            "    SerializationStreamWriter streamWriter,",
            "    AutoValue_Baz<K, V> instance) throws SerializationException {",
            "    serialize(streamWriter, instance);",
            "  }",
            "}");
    Compilation compilation =
        javac().withProcessors(new AutoValueProcessor()).compile(javaFileObject, GWT_COMPATIBLE);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("foo.bar.AutoValue_Baz_CustomFieldSerializer")
        .hasSourceEquivalentTo(expectedOutput);
  }

  private String generatedAnnotationType() {
    return isJavaxAnnotationProcessingGeneratedAvailable()
        ? "javax.annotation.processing.Generated"
        : "javax.annotation.Generated";
  }

  private boolean isJavaxAnnotationProcessingGeneratedAvailable() {
    return SourceVersion.latestSupported().compareTo(SourceVersion.RELEASE_8) > 0;
  }
}
