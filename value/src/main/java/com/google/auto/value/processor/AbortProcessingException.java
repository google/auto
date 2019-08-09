/*
 * Copyright 2014 Google LLC
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
package com.google.auto.value.processor;

/**
 * Exception thrown when annotation processing should be aborted for the current class. Processing
 * can continue on other classes. Throwing this exception does not cause a compiler error, so either
 * one should explicitly be emitted or it should be clear that the compiler will be producing its
 * own error for other reasons.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
@SuppressWarnings("serial")
class AbortProcessingException extends RuntimeException {}
