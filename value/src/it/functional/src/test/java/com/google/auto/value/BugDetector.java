/*
 * Copyright (C) 2018 Google, Inc.
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
import static com.google.testing.compile.CompilationSubject.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.tools.JavaFileObject;

/**
 * Detects javac bugs that might prevent tests from working.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
final class BugDetector {
  private BugDetector() {}

  /**
   * Returns true if {@link TypeMirror#accept} gives the unannotated type to the type visitor. It
   * should obviously receive the type that {@code accept} was called on, but in at least some
   * Java 8 versions it ends up being the unannotated one.
   */
  // I have not been able to find a reference for this bug.
  static boolean typeVisitorDropsAnnotations() {
    JavaFileObject testClass = JavaFileObjects.forSourceLines(
        "com.example.Test",
        "package com.example;",
        "",
        "import java.lang.annotation.*;",
        "",
        "abstract class Test {",
        "  @Target(ElementType.TYPE_USE)",
        "  @interface Nullable {}",
        "",
        "  @Override public abstract boolean equals(@Nullable Object x);",
        "}");
    BugDetectorProcessor bugDetectorProcessor = new BugDetectorProcessor();
    Compilation compilation =
        Compiler.javac().withProcessors(bugDetectorProcessor).compile(testClass);
    assertThat(compilation).succeeded();
    return bugDetectorProcessor.typeAnnotationsNotReturned;
  }

  @SupportedAnnotationTypes("*")
  @SupportedSourceVersion(SourceVersion.RELEASE_8)
  private static class BugDetectorProcessor extends AbstractProcessor {
    volatile boolean typeAnnotationsNotReturned;

    @Override
    public boolean process(
        Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (!roundEnv.processingOver()) {
        TypeElement test = processingEnv.getElementUtils().getTypeElement("com.example.Test");
        ExecutableElement equals = ElementFilter.methodsIn(test.getEnclosedElements()).get(0);
        assertThat(equals.getSimpleName().toString()).isEqualTo("equals");
        TypeMirror parameterType = equals.getParameters().get(0).asType();
        List<AnnotationMirror> annotationsFromVisitor =
            parameterType.accept(new BugDetectorVisitor(), null);
        typeAnnotationsNotReturned = annotationsFromVisitor.isEmpty();
      }
      return false;
    }

    private static class BugDetectorVisitor
        extends SimpleTypeVisitor8<List<AnnotationMirror>, Void> {
      @Override
      public List<AnnotationMirror> visitDeclared(DeclaredType t, Void p) {
        return Collections.unmodifiableList(t.getAnnotationMirrors());
      }
    }
  }
}
