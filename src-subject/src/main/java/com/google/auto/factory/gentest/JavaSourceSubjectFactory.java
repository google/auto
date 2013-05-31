package com.google.auto.factory.gentest;

import java.io.File;

import org.truth0.FailureStrategy;
import org.truth0.subjects.SubjectFactory;

public class JavaSourceSubjectFactory extends SubjectFactory<JavaSourceSubject, File> {
  public static final JavaSourceSubjectFactory JAVA_SOURCE = new JavaSourceSubjectFactory();

  @Override
  public JavaSourceSubject getSubject(FailureStrategy failureStrategy, File subject) {
    return new JavaSourceSubject(failureStrategy, subject);
  }
}
