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
package com.google.testing.compile;

import static com.google.common.base.Charsets.UTF_8;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.JavaFileObject.Kind.SOURCE;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import javax.annotation.processing.Processor;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTool;

/**
 * Utilities for performing compilation with {@code javac}.
 *
 * @author Gregory Kick
 */
final class Compilation {
  private Compilation() {}

  static Result compile(Iterable<? extends Processor> processors,
      Iterable<? extends JavaFileObject> sources) {
    JavacTool compiler = (JavacTool) ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnosticCollector =
        new DiagnosticCollector<JavaFileObject>();
    InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
        compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8));
    JavacTask task = compiler.getTask(
        null, // Ignore output because the diagnostic collector gets it
        fileManager,
        diagnosticCollector,
        ImmutableSet.<String>of(),
        ImmutableSet.<String>of(),
        sources);
    task.setProcessors(processors);
    // get elements, types and trees before you call generate()
    Elements elements = task.getElements();
    Types types = task.getTypes();
    Trees trees = Trees.instance(task);
    try {
      // Use generate() rather than call() because call() invalidates Elements, Trees and Types
      task.generate();
    } catch (IOException e) {
      throw new RuntimeException("Compilation failed for " + Iterables.toString(sources), e);
    }
    return new Result(elements, types, trees,
        diagnosticCollector.getDiagnostics(),
        fileManager.getOutputFiles());
  }

  static Iterable<? extends CompilationUnitTree> parse(
      Iterable<? extends JavaFileObject> sources) {
    JavacTool compiler = (JavacTool) ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnosticCollector =
        new DiagnosticCollector<JavaFileObject>();
    InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
        compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8));
    JavacTask task = compiler.getTask(
        null, // Ignore output because the diagnostic collector gets it
        fileManager,
        diagnosticCollector,
        ImmutableSet.<String>of(),
        ImmutableSet.<String>of(),
        sources);
    try {
      Iterable<? extends CompilationUnitTree> parsedCompilationUnits = task.parse();
      List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
      for (Diagnostic<?> diagnostic : diagnostics) {
        if (Diagnostic.Kind.ERROR == diagnostic.getKind()) {
          throw new IllegalStateException("error while parsing:\n"
              + Diagnostics.toString(diagnostics));
        }
      }
      return parsedCompilationUnits;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static final class Result {
    final Elements elements;
    final Types types;
    final Trees trees;
    final Iterable<Diagnostic<? extends JavaFileObject>> diagnostics;
    final ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>>
        diagnosticsByKind;
    final ImmutableListMultimap<JavaFileObject.Kind, JavaFileObject> generatedFilesByKind;

    Result(
        Elements elements,
        final Types types,
        final Trees trees,
        Iterable<Diagnostic<? extends JavaFileObject>> diagnostics,
        Iterable<JavaFileObject> generatedFiles) {
      this.elements = elements;
      this.types = types;
      this.trees = trees;
      this.diagnostics = diagnostics;
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
