package com.google.testing.compile;

import javax.tools.JavaFileObject;

import org.truth0.FailureStrategy;
import org.truth0.subjects.SubjectFactory;

/**
 * A <a href="https://github.com/truth0/truth">Truth</a> {@link SubjectFactory} similar to
 * {@link JavaSourcesSubjectFactory}, but for working with single source files.
 *
 * @author Gregory Kick
 */
public final class JavaSourceSubjectFactory
    extends SubjectFactory<JavaSourcesSubject.SingleSourceAdapter, JavaFileObject> {
  public static JavaSourceSubjectFactory javaSource() {
    return new JavaSourceSubjectFactory();
  }

  private JavaSourceSubjectFactory() {}

  @Override
  public JavaSourcesSubject.SingleSourceAdapter getSubject(FailureStrategy failureStrategy,
      JavaFileObject subject) {
    return new JavaSourcesSubject.SingleSourceAdapter(failureStrategy, subject);
  }
}
