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

import java.io.IOException;
import java.util.Arrays;

import javax.annotation.CheckReturnValue;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;

import org.truth0.FailureStrategy;
import org.truth0.subjects.Subject;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.sun.source.tree.CompilationUnitTree;

/**
 * A <a href="https://github.com/truth0/truth">Truth</a> {@link Subject} that evaluates the result
 * of a {@code javac} compilation.
 *
 * @author Gregory Kick
 */
public final class JavaSourcesSubject
    extends Subject<JavaSourcesSubject, Iterable<? extends JavaFileObject>> {
  JavaSourcesSubject(FailureStrategy failureStrategy, Iterable<? extends JavaFileObject> subject) {
    super(failureStrategy, subject);
  }

  public interface ChainingClause<T> {
    T and();
  }

  public interface FileClause extends ChainingClause<UnuccessfulCompilationClause> {
    LineClause in(JavaFileObject file);
  }

  public interface LineClause extends ChainingClause<UnuccessfulCompilationClause> {
    ColumnClause onLine(long lineNumber);
  }

  public interface ColumnClause extends ChainingClause<UnuccessfulCompilationClause> {
    ChainingClause<UnuccessfulCompilationClause> atColumn(long columnNumber);
  }

  public interface GeneratedPredicateClause {
    SuccessfulCompilationClause generatesSources(JavaFileObject first, JavaFileObject... rest);
    SuccessfulCompilationClause generatesFiles(JavaFileObject first, JavaFileObject... rest);
  }

  public interface SuccessfulCompilationClause extends ChainingClause<GeneratedPredicateClause> {}

  public interface UnuccessfulCompilationClause {
    FileClause hasError(String message);
  }

  @CheckReturnValue
  public CompilationClause processedWith(Processor first, Processor... rest) {
    return new CompilationClause(Lists.asList(first, rest));
  }

  private CompilationClause newCompilationClause(Iterable<? extends Processor> processors) {
    return new CompilationClause(processors);
  }

  public final class CompilationClause {
    private final ImmutableSet<Processor> processors;

    private CompilationClause() {
      this(ImmutableSet.<Processor>of());
    }

    private CompilationClause(Iterable<? extends Processor> processors) {
      this.processors = ImmutableSet.copyOf(processors);
    }

    public SuccessfulCompilationClause hasNoErrors() {
      Compilation.Result result = Compilation.compile(processors, getSubject());
      ImmutableList<Diagnostic<? extends JavaFileObject>> errors =
          result.diagnosticsByKind.get(Kind.ERROR);
      if (!errors.isEmpty()) {
        StringBuilder message = new StringBuilder("Compilation produced the following errors:\n");
        Joiner.on("\n").appendTo(message, errors);
        failureStrategy.fail(message.toString());
      }
      return new SuccessfulCompilationBuilder(result);
    }

    public FileClause hasError(String message) {
      Compilation.Result result = Compilation.compile(processors, getSubject());
      return new UnsuccessfulCompilationBuilder(result).hasError(message);
    }
  }

  public SuccessfulCompilationClause hasNoErrors() {
    return new CompilationClause().hasNoErrors();
  }

  public FileClause hasError(String message) {
    return new CompilationClause().hasError(message);
  }

  private final class UnsuccessfulCompilationBuilder implements UnuccessfulCompilationClause {
    private final Compilation.Result result;

    UnsuccessfulCompilationBuilder(Compilation.Result result) {
      this.result = result;
    }

    @Override
    public FileClause hasError(final String message) {
      FluentIterable<Diagnostic<? extends JavaFileObject>> diagnostics =
          FluentIterable.from(result.diagnosticsByKind.get(Kind.ERROR));
      final FluentIterable<Diagnostic<? extends JavaFileObject>> diagnosticsWithMessage =
          diagnostics.filter(new Predicate<Diagnostic<?>>() {
            @Override
            public boolean apply(Diagnostic<?> input) {
              return message.equals(input.getMessage(null));
            }
          });
      if (diagnosticsWithMessage.isEmpty()) {
        failureStrategy.fail(String.format(
            "Expected an error with message \"%s\", but only found %s", message,
            diagnostics.transform(
              new Function<Diagnostic<?>, String>() {
                @Override public String apply(Diagnostic<?> input) {
                  return "\"" + input.getMessage(null) + "\"";
                }
              })));
      }
      return new FileClause() {
        @Override
        public UnuccessfulCompilationClause and() {
          return UnsuccessfulCompilationBuilder.this;
        }

        @Override
        public LineClause in(final JavaFileObject file) {
          FluentIterable<Diagnostic<? extends JavaFileObject>> diagnosticsInFile =
              diagnosticsWithMessage.filter(new Predicate<Diagnostic<? extends FileObject>>() {
                @Override
                public boolean apply(Diagnostic<? extends FileObject> input) {
                  return file.toUri().getPath().equals(input.getSource().toUri().getPath());
                }
              });
          if (diagnosticsInFile.isEmpty()) {
            failureStrategy.fail(String.format(
                "Expected an error in %s, but only found errors in ", file.getName(),
                diagnosticsWithMessage.transform(
                    new Function<Diagnostic<? extends FileObject>, String>() {
                      @Override public String apply(Diagnostic<? extends FileObject> input) {
                        return input.getSource().getName();
                      }
                    })));
          }
          return new LineClause() {
            @Override public UnuccessfulCompilationClause and() {
              return UnsuccessfulCompilationBuilder.this;
            }

            @Override public ColumnClause onLine(final long lineNumber) {
              final FluentIterable<Diagnostic<? extends JavaFileObject>> diagnosticsOnLine =
                  diagnosticsWithMessage.filter(new Predicate<Diagnostic<?>>() {
                    @Override
                    public boolean apply(Diagnostic<?> input) {
                      return lineNumber == input.getLineNumber();
                    }
                  });
              if (diagnosticsOnLine.isEmpty()) {
                failureStrategy.fail(String.format(
                    "Expected an error on line %d, but only found errors on line(s) %s",
                    lineNumber, diagnosticsOnLine.transform(
                        new Function<Diagnostic<?>, Long>() {
                          @Override public Long apply(Diagnostic<?> input) {
                            return input.getLineNumber();
                          }
                        })));
              }
              return new ColumnClause() {
                @Override
                public UnuccessfulCompilationClause and() {
                  return UnsuccessfulCompilationBuilder.this;
                }

                @Override
                public ChainingClause<UnuccessfulCompilationClause> atColumn(
                    final long columnNumber) {
                  FluentIterable<Diagnostic<? extends JavaFileObject>> diagnosticsAtColumn =
                      diagnosticsOnLine.filter(new Predicate<Diagnostic<?>>() {
                        @Override
                        public boolean apply(Diagnostic<?> input) {
                          return columnNumber == input.getColumnNumber();
                        }
                      });
                  if (diagnosticsAtColumn.isEmpty()) {
                    failureStrategy.fail(String.format(
                        "Expected an error at column %d, but only found errors at column(s) %s",
                        columnNumber, diagnosticsOnLine.transform(
                            new Function<Diagnostic<?>, Long>() {
                              @Override public Long apply(Diagnostic<?> input) {
                                return input.getColumnNumber();
                              }
                            })));
                  }
                  return new ChainingClause<JavaSourcesSubject.UnuccessfulCompilationClause>() {
                    @Override public UnuccessfulCompilationClause and() {
                      return UnsuccessfulCompilationBuilder.this;
                    }
                  };
                }
              };
            }
          };
        }
      };
    }
  }

  private final class SuccessfulCompilationBuilder implements SuccessfulCompilationClause,
      GeneratedPredicateClause {
    private final Compilation.Result result;

    SuccessfulCompilationBuilder(Compilation.Result result) {
      this.result = result;
    }

    @Override
    public GeneratedPredicateClause and() {
      return this;
    }

    @Override
    public SuccessfulCompilationClause generatesSources(JavaFileObject first,
        JavaFileObject... rest) {
      ImmutableList<JavaFileObject> generatedSources =
          result.generatedFilesByKind.get(JavaFileObject.Kind.SOURCE);
      Iterable<? extends CompilationUnitTree> actualCompilationUnits =
          Compilation.parse(generatedSources);
      final EqualityScanner scanner = new EqualityScanner();
      for (final CompilationUnitTree expected : Compilation.parse(Lists.asList(first, rest))) {
        Optional<? extends CompilationUnitTree> found =
            Iterables.tryFind(actualCompilationUnits, new Predicate<CompilationUnitTree>() {
              @Override
              public boolean apply(CompilationUnitTree input) {
                return scanner.visitCompilationUnit(expected, input);
              }
            });
        if (!found.isPresent()) {
          failureStrategy.fail("Did not find a source file coresponding to "
              + expected.getSourceFile().getName());
        }
      }
      return this;
    }

    @Override
    public SuccessfulCompilationClause generatesFiles(JavaFileObject first,
        JavaFileObject... rest) {
      for (JavaFileObject expected : Lists.asList(first, rest)) {
        if (!wasGenerated(result, expected)) {
          failureStrategy.fail("Did not find a generated file corresponding to "
              + expected.getName());
        }
      }
      return this;
    }

    boolean wasGenerated(Compilation.Result result, JavaFileObject expected) {
      for (JavaFileObject generated : result.generatedFilesByKind.get(expected.getKind())) {
        try {
          if (Arrays.equals(
              ByteStreams.toByteArray(expected.openInputStream()),
              ByteStreams.toByteArray(generated.openInputStream()))) {
            return true;
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      return false;
    }
  }

  public static final class SingleSourceAdapter
      extends Subject<SingleSourceAdapter, JavaFileObject> {
    private final JavaSourcesSubject delegate;

    SingleSourceAdapter(FailureStrategy failureStrategy, JavaFileObject subject) {
      super(failureStrategy, subject);
      this.delegate =
          new JavaSourcesSubject(failureStrategy, ImmutableList.of(subject));
    }

    @CheckReturnValue
    public CompilationClause processedWith(Processor first, Processor... rest) {
      return delegate.newCompilationClause(Lists.asList(first, rest));
    }

    public SuccessfulCompilationClause hasNoErrors() {
      return delegate.hasNoErrors();
    }

    public FileClause hasError(String message) {
      return delegate.hasError(message);
    }
  }
}
