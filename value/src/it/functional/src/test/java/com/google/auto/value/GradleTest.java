package com.google.auto.value;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GradleTest {
  @Rule public TemporaryFolder fakeProject = new TemporaryFolder();

  private static final String BUILD_GRADLE_TEXT =
      String.join("\n",
          "plugins {",
          "  id 'java-library'",
          "}",
          "repositories {",
          "  maven { url = uri('${localRepository}') }",
          "}",
          "dependencies {",
          "  compileOnlyApi      'com.google.auto.value:auto-value-annotations:${autoValueVersion}'",
          "  annotationProcessor 'com.google.auto.value:auto-value:${autoValueVersion}'",
          "  testImplementation  'junit:junit:${junitVersion}'",
          "}");

  private static final String FOO_TEXT =
      String.join("\n",
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
    writeFile(fakeProject.newFile("build.gradle"), buildGradleText);
    File srcDir = fakeProject.newFolder("src", "main", "java", "com", "example");
    writeFile(new File(srcDir, "Foo.java"), FOO_TEXT);

    // Build it the first time.
    BuildResult result1 = buildFakeProject();
    assertThat(result1.getOutput()).contains(
        "Full recompilation is required because no incremental change information is available");
    File output = new File(
        fakeProject.getRoot(), "build/classes/java/main/com/example/AutoValue_Foo.class");
    assertThat(output.exists()).isTrue();

    // Add a source file to the project.
    String barText = FOO_TEXT.replace("Foo", "Bar");
    File barFile = new File(srcDir, "Bar.java");
    writeFile(new File(srcDir, "Bar.java"), barText);

    // Build it a second time.
    BuildResult result2 = buildFakeProject();
    assertThat(result2.getOutput()).doesNotContain("Full recompilation is required");

    // Remove the second source file and build a third time. If incremental annotation processing
    // is not working, this will produce a message like this:
    //   Full recompilation is required because com.google.auto.value.processor.AutoValueProcessor
    //   is not incremental
    assertThat(barFile.delete()).isTrue();
    BuildResult result3 = buildFakeProject();
    assertThat(result3.getOutput()).doesNotContain("Full recompilation is required");
  }

  private BuildResult buildFakeProject() throws IOException {
    GradleRunner runner = GradleRunner.create()
        .withProjectDir(fakeProject.getRoot())
        .withArguments("--info", "build");
    if (GRADLE_INSTALLATION.isPresent()) {
      runner.withGradleInstallation(GRADLE_INSTALLATION.get());
    } else {
      runner.withGradleVersion("7.0");
    }
    return runner.build();
  }

  private static Optional<File> getGradleInstallation() {
    String gradleHome = System.getenv("GRADLE_HOME");
    if (gradleHome != null) {
      File gradleHomeFile = new File(gradleHome);
      if (gradleHomeFile.isDirectory()) {
        System.err.println("Found GRADLE_HOME: " + gradleHome);
        return Optional.of(new File(gradleHome));
      } else {
        System.err.println("GRADLE_HOME is set to " + gradleHome + " which does not exist");
      }
    }
    Path installationPath;
    try {
      Path gradleExecutable = Paths.get("/usr/bin/gradle");
      Path gradleLink = Files.readSymbolicLink(gradleExecutable);
      if (!gradleLink.isAbsolute()) {
        gradleLink = gradleExecutable.getParent().resolve(gradleLink);
      }
      if (!gradleLink.toString().endsWith("/bin/gradle")) {
        System.err.println("Does not end with .../bin/gradle: " + gradleLink);
        return Optional.empty();
      }
      installationPath = gradleLink.getParent().getParent();
      if (!Files.isDirectory(installationPath)) {
        System.err.println("Is not a directory: " + installationPath);
        return Optional.empty();
      }
    } catch (IOException e) {
      System.err.println(e);
      return Optional.empty();
    }
    Optional<Path> coreJar;
    Pattern corePattern = Pattern.compile("gradle-core-([0-9]+)\\..*\\.jar");
    try (Stream<Path> files = Files.walk(installationPath.resolve("lib"))) {
      coreJar =
          files.filter(p -> {
            Matcher matcher = corePattern.matcher(p.getFileName().toString());
            if (matcher.matches()) {
              int version = Integer.parseInt(matcher.group(1));
              System.err.println("Found version " + version + " in " + p);
              if (version >= 7) {
                return true;
              }
            }
            return false;
          }).findFirst();
    } catch (IOException e) {
      return Optional.empty();
    }
    return coreJar.map(unused -> installationPath.toFile());
  }

  private static String expandSystemProperties(String s) {
    for (String name : System.getProperties().stringPropertyNames()) {
      String value = System.getProperty(name);
      s = s.replace("${" + name + "}", value);
    }
    return s;
  }

  private static void writeFile(File file, String text) throws IOException {
    try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
      writer.print(text);
      if (!text.endsWith("\n")) {
        writer.println();
      }
    }
  }
}
