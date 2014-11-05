package com.google.auto.value.processor;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

import com.google.common.io.Files;
import com.google.testing.compile.JavaFileObjects;

import junit.framework.TestCase;
import org.apache.velocity.runtime.log.JdkLogChute;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.tools.JavaFileObject;

/**
 * Ensure that we have foiled Velocity in its attempts to log by default. If users have Log4J in
 * their classpath then Velocity will typically create a velocity.log file which is of no interest
 * and is likely to cause confusion. This test checks that we have successfully disabled that
 * behaviour.
 *
 * @see <a href="https://github.com/google/auto/issues/151">Issue 151</a>
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class NoVelocityLoggingTest extends TestCase {
  public void testDontLog() throws IOException {
    File log = File.createTempFile("NoVelocityLoggingTest", "log");
    try {
      doTestDontLog(log);
    } finally {
      log.delete();
    }
  }

  private void doTestDontLog(File log) throws IOException {
    // Set things up so that if Velocity is successfully logging then we will see its log output
    // in the temporary file we have created. This depends on Velocity falling back on JDK logging,
    // so this test won't do anything useful if its classpath contains Log4J or Commons Logging or
    // any of the other exotic logging systems that Velocity will pounce on if it sees them.
    FileHandler fileHandler = new FileHandler(log.getPath());
    fileHandler.setFormatter(new SimpleFormatter());
    Logger logger = Logger.getLogger(JdkLogChute.DEFAULT_LOG_NAME);
    logger.addHandler(fileHandler);
    logger.setLevel(Level.ALL);
    LogManager logManager = LogManager.getLogManager();
    logManager.addLogger(logger);

    // Now do a random compilation that implies using AutoValueProcessor.
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract int buh();",
        "",
        "  public static Baz create(int buh) {",
        "    return new AutoValue_Baz(buh);",
        "  }",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .compilesWithoutError();

    // The log file should be empty.
    fileHandler.close();
    assertEquals("", Files.toString(log, StandardCharsets.UTF_8));
  }
}
