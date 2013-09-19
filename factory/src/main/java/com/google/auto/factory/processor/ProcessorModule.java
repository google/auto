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
package com.google.auto.factory.processor;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import dagger.Module;
import dagger.Provides;

/**
 * A Dagger {@link Module} that binds objects related to {@link Processor} instances. Most callers
 * will create an instance using the {@link ProcessingEnvironment} passed into
 * {@link AbstractProcessor#init}.
 *
 * @author Gregory Kick
 */
// TODO(gak): move this some place more common so that it can be shared amongst processors
@Module(library = true)
final class ProcessorModule {
  private final ProcessingEnvironment processingEnvironment;

  ProcessorModule(ProcessingEnvironment processingEnvironment) {
    this.processingEnvironment = checkNotNull(processingEnvironment);
  }

  @Provides ProcessingEnvironment provideProcessingEnvironment() {
    return processingEnvironment;
  }

  @Provides Elements provideElements(ProcessingEnvironment processingEnvironment) {
    return processingEnvironment.getElementUtils();
  }

  @Provides Filer provideFiler(ProcessingEnvironment processingEnvironment) {
    return processingEnvironment.getFiler();
  }

  @Provides Messager provideMessager(ProcessingEnvironment processingEnvironment) {
    return processingEnvironment.getMessager();
  }

  @Provides Types provideTypes(ProcessingEnvironment processingEnvironment) {
    return processingEnvironment.getTypeUtils();
  }
}
