/*
 * Copyright 2024 Google LLC
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

/**
 * JSR-330-compatible factories.
 */
module com.google.auto.factory {
  requires com.google.auto.common;
  requires com.google.auto.service;
  requires com.google.auto.value.annotations;
  requires incap;
  requires com.squareup.javapoet;
  requires java.compiler;

  exports com.google.auto.factory;
  exports com.google.auto.factory.processor;

  provides javax.annotation.processing.Processor
      with com.google.auto.factory.processor.AutoFactoryProcessor;
}