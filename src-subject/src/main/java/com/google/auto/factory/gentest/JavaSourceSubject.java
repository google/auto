package com.google.auto.factory.gentest;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Locale;

import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.truth0.FailureStrategy;
import org.truth0.subjects.Subject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;

public final class JavaSourceSubject extends Subject<JavaSourceSubject, File> {
  private final JavacTool compiler;
  private final StandardJavaFileManager fileManager;

  public JavaSourceSubject(FailureStrategy failureStrategy, File subject) {
    super(failureStrategy, subject);
    this.compiler = (JavacTool) ToolProvider.getSystemJavaCompiler();
    this.fileManager = compiler.getStandardFileManager(null /* default diagnostic listener */,
        Locale.getDefault(), UTF_8);
  }

  public void isEquivalentTo(File other) {
    // TODO(gak): do something with the output
    StringWriter out = new StringWriter();
    JavacTask task = compiler.getTask(out, fileManager,
        null /* default diagnostic listener */, ImmutableSet.<String>of(),
        ImmutableSet.<String>of(),
        fileManager.getJavaFileObjectsFromFiles(ImmutableSet.of(getSubject(), other)));
    try {
      ImmutableList<CompilationUnitTree> compilationUnits =
          ImmutableList.copyOf(task.parse());
      checkState(compilationUnits.size() == 2);
      compilationUnits.get(0).accept(new EqualityScanner(failureStrategy),
          compilationUnits.get(1));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
