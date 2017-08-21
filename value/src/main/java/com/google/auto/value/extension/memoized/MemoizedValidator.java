/*
 * Copyright (C) 2017 Google, Inc.
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
package com.google.auto.value.extension.memoized;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

/**
 * An annotation {@link Processor} that reports errors for {@link Memoized @Memoized} methods that
 * are not inside {@link AutoValue}-annotated classes.
 */
@AutoService(Processor.class)
public final class MemoizedValidator extends BasicAnnotationProcessor {

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {
    return ImmutableList.of(new ValidationStep(processingEnv.getMessager()));
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  private static final class ValidationStep implements ProcessingStep {
    private final Messager messager;
    
    ValidationStep(Messager messager) {
      this.messager = messager;
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotations() {
      return ImmutableSet.of(Memoized.class);
    }

    @Override
    public Set<Element> process(
        SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
      for (ExecutableElement method : methodsIn(elementsByAnnotation.values())) {
        if (!isAnnotationPresent(method.getEnclosingElement(), AutoValue.class)) {
          messager.printMessage(
              ERROR,
              "@Memoized methods must be declared only in @AutoValue classes",
              method,
              getAnnotationMirror(method, Memoized.class).get());
        }
      }
      return ImmutableSet.of();
    }
  }
}
