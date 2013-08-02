/*
 * Copyright (C) 2013 Google, Inc.
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
package com.google.auto.factory.gentest;

import static com.google.common.base.Charsets.UTF_8;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.JavaFileObject.Kind.SOURCE;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.truth0.FailureStrategy;
import org.truth0.subjects.Subject;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;

/**
 * A <a href="https://github.com/truth0/truth">Truth</a> {@link Subject} that evaluates Java source
 * {@linkplain File files} and compares them for equality based on the AST.
 *
 * @author Gregory Kick
 */
public final class JavaSourcesSubject
    extends Subject<JavaSourcesSubject, Iterable<? extends JavaFileObject>> {
  private final ImmutableList<Processor> processors;

  public JavaSourcesSubject(FailureStrategy failureStrategy,
      Iterable<? extends JavaFileObject> subject,
      ImmutableList<Processor> processors) {
    super(failureStrategy, subject);
    this.processors = processors;
  }

  private void checkEqualCompilationUnits(Iterable<? extends CompilationUnitTree> expected,
      Iterable<? extends CompilationUnitTree> actual) {
    EqualityScanner scanner = new EqualityScanner();
    ArrayList<CompilationUnitTree> expectedList = Lists.newArrayList(expected);
    for (CompilationUnitTree compilationUnit : actual) {
      Iterator<? extends CompilationUnitTree> expectedIterator = expectedList.iterator();
      boolean found = false;
      while (!found && expectedIterator.hasNext()) {
        boolean scannerResult =
            scanner.visitCompilationUnit(expectedIterator.next(), compilationUnit);
        if (scannerResult) {
          found = true;
          expectedIterator.remove();
        }
      }
      if (!found) {
        failureStrategy.fail("Oh noes!");
      }
    }
    if (!expectedList.isEmpty()) {
      failureStrategy.fail("still have some expecteds " + expectedList);
    }
  }

  public ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>>
      failsToCompile() {
    CompilationResult result = compile(getSubject());
    if (result.successful()) {
      failureStrategy.fail("This should have failed!");
    }
    return result.diagnosticsByKind;
  }

  public ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>> compiles() {
    CompilationResult result = compile(getSubject());
    if (!result.successful()) {
      failureStrategy.fail("Failed with some errors :(");
    }
    return result.diagnosticsByKind;
  }

  public ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>>
      generatesSources(JavaFileObject first, JavaFileObject... rest) {
    CompilationResult result = compile(getSubject());
    if (result.successful()) {
      checkEqualCompilationUnits(
          parse(Lists.asList(first, rest)),
          parse(result.generatedSources()));
    } else {
      failureStrategy.fail("Failed with some errors: " + result.output);
    }
    return result.diagnosticsByKind;
  }

  private CompilationResult compile(Iterable<? extends JavaFileObject> sources) {
    JavacTool compiler = (JavacTool) ToolProvider.getSystemJavaCompiler();
    StringWriter out = new StringWriter();
    DiagnosticCollector<JavaFileObject> diagnosticCollector =
        new DiagnosticCollector<JavaFileObject>();
    InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
        compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8));
    JavacTask task = compiler.getTask(out, fileManager, diagnosticCollector,
        ImmutableSet.<String>of(),
        ImmutableSet.<String>of(),
        sources);
    task.setProcessors(processors);
    task.call();
    return new CompilationResult(out.toString(), diagnosticCollector.getDiagnostics(),
        fileManager.getOutputFiles());
  }

  private static Iterable<? extends CompilationUnitTree> parse(
      Iterable<? extends JavaFileObject> sources) {
    JavacTool compiler = (JavacTool) ToolProvider.getSystemJavaCompiler();
    StringWriter out = new StringWriter();
    DiagnosticCollector<JavaFileObject> diagnosticCollector =
        new DiagnosticCollector<JavaFileObject>();
    InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
        compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8));
    JavacTask task = compiler.getTask(out, fileManager, diagnosticCollector,
        ImmutableSet.<String>of(),
        ImmutableSet.<String>of(),
        sources);
    try {
      Iterable<? extends CompilationUnitTree> parsedCompilationUnits = task.parse();
      for (Diagnostic<?> diagnostic : diagnosticCollector.getDiagnostics()) {
        if (Kind.ERROR == diagnostic.getKind()) {
          throw new RuntimeException("error while parsing: " + out);
        }
      }
      return parsedCompilationUnits;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static final class CompilationResult {
    final String output;
    final ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>>
        diagnosticsByKind;
    final ImmutableListMultimap<JavaFileObject.Kind, JavaFileObject> generatedFilesByKind;

    CompilationResult(String output, Iterable<Diagnostic<? extends JavaFileObject>> diagnostics,
        Iterable<JavaFileObject> generatedFiles) {
      this.output = output;
      this.diagnosticsByKind = Multimaps.index(diagnostics,
          new Function<Diagnostic<?>, Diagnostic.Kind>() {
            @Override public Diagnostic.Kind apply(Diagnostic<?> input) {
              return input.getKind();
            }
          });
      this.generatedFilesByKind = Multimaps.index(generatedFiles,
          new Function<JavaFileObject, JavaFileObject.Kind>() {
            @Override public JavaFileObject.Kind apply(JavaFileObject input) {
              return input.getKind();
            }
          });
    }

    boolean successful() {
      return diagnosticsByKind.get(ERROR).isEmpty();
    }

    ImmutableList<JavaFileObject> generatedSources() {
      return generatedFilesByKind.get(SOURCE);
    }
  }
}
