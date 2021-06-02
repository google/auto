/*
 * Copyright 2021 Google LLC
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
package com.google.auto.value;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GradleTest {
  @Rule public TemporaryFolder fakeProject = new TemporaryFolder();

  private static final String BUILD_GRADLE_TEXT =
      String.join(
          "\n",
          "plugins {",
          "  id 'java-library'",
          "}",
          "repositories {",
          "  maven { url = uri('${localRepository}') }",
          "}",
          "dependencies {",
          "  compileOnlyApi     "
              + " 'com.google.auto.value:auto-value-annotations:${autoValueVersion}'",
          "  annotationProcessor 'com.google.auto.value:auto-value:${autoValueVersion}'",
          "}");

  private static final String FOO_TEXT =
      String.join(
          "\n",
          "package com.example;",
          "",
          "import com.google.auto.value.AutoValue;",
          "",
          "@AutoValue",
          "abstract class Foo {",
          "  abstract String bar();",
          "",
          "  static Foo of(String bar) {",
          "    return new AutoValue_Foo(bar);",
          "  }",
          "}");

  private static final Optional<File> GRADLE_INSTALLATION = getGradleInstallation();

  @Test
  public void basic() throws IOException {
    String autoValueVersion = System.getProperty("autoValueVersion");
    assertThat(autoValueVersion).isNotNull();
    String localRepository = System.getProperty("localRepository");
    assertThat(localRepository).isNotNull();

    // Set up the fake Gradle project.
    String buildGradleText = expandSystemProperties(BUILD_GRADLE_TEXT);
    writeFile(fakeProject.newFile("build.gradle").toPath(), buildGradleText);
    Path srcDir = fakeProject.newFolder("src", "main", "java", "com", "example").toPath();
    writeFile(srcDir.resolve("Foo.java"), FOO_TEXT);

    // Build it the first time.
    BuildResult result1 = buildFakeProject();
    assertThat(result1.getOutput())
        .contains(
            "Full recompilation is required because no incremental change information is"
                + " available");
    Path output =
        fakeProject
            .getRoot()
            .toPath()
            .resolve("build/classes/java/main/com/example/AutoValue_Foo.class");
    assertThat(Files.exists(output)).isTrue();

    // Add a source file to the project.
    String barText = FOO_TEXT.replace("Foo", "Bar");
    Path barFile = srcDir.resolve("Bar.java");
    writeFile(barFile, barText);

    // Build it a second time.
    BuildResult result2 = buildFakeProject();
    assertThat(result2.getOutput()).doesNotContain("Full recompilation is required");

    // Remove the second source file and build a third time. If incremental annotation processing
    // is not working, this will produce a message like this:
    //   Full recompilation is required because com.google.auto.value.processor.AutoValueProcessor
    //   is not incremental
    Files.delete(barFile);
    BuildResult result3 = buildFakeProject();
    assertThat(result3.getOutput()).doesNotContain("Full recompilation is required");
  }

  private BuildResult buildFakeProject() throws IOException {
    GradleRunner runner =
        GradleRunner.create()
            .withProjectDir(fakeProject.getRoot())
            .withArguments("--info", "compileJava");
    if (GRADLE_INSTALLATION.isPresent()) {
      runner.withGradleInstallation(GRADLE_INSTALLATION.get());
    } else {
      runner.withGradleVersion(GradleVersion.current().getVersion());
    }
    return runner.build();
  }

  private static Optional<File> getGradleInstallation() {
    String gradleHome = System.getenv("GRADLE_HOME");
    if (gradleHome != null) {
      File gradleHomeFile = new File(gradleHome);
      if (gradleHomeFile.isDirectory()) {
        return Optional.of(new File(gradleHome));
      }
    }
    try {
      Path gradleExecutable = Paths.get("/usr/bin/gradle");
      Path gradleLink = gradleExecutable.resolveSibling(Files.readSymbolicLink(gradleExecutable));
      if (!gradleLink.endsWith("bin/gradle")) {
        return Optional.empty();
      }
      Path installationPath = gradleLink.getParent().getParent();
      if (!Files.isDirectory(installationPath)) {
        return Optional.empty();
      }
      Optional<Path> coreJar;
      Pattern corePattern = Pattern.compile("gradle-core-([0-9]+)\\..*\\.jar");
      try (Stream<Path> files = Files.walk(installationPath.resolve("lib"))) {
        coreJar =
            files
                .filter(
                    p -> {
                      Matcher matcher = corePattern.matcher(p.getFileName().toString());
                      if (matcher.matches()) {
                        int version = Integer.parseInt(matcher.group(1));
                        if (version >= 5) {
                          return true;
                        }
                      }
                      return false;
                    })
                .findFirst();
      }
      return coreJar.map(unused -> installationPath.toFile());
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static String expandSystemProperties(String s) {
    for (String name : System.getProperties().stringPropertyNames()) {
      String value = System.getProperty(name);
      s = s.replace("${" + name + "}", value);
    }
    return s;
  }

  private static void writeFile(Path file, String text) throws IOException {
    Files.write(file, ImmutableList.of(text), UTF_8);
  }
}
