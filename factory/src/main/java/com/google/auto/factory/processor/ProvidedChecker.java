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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor6;

final class ProvidedChecker {
  private final Messager messager;

  ProvidedChecker(Messager messager) {
    this.messager = messager;
  }

  void checkProvidedParameter(Element element) {
    checkArgument(isAnnotationPresent(element, Provided.class), "%s not annoated with @Provided",
        element);
    element.accept(new ElementKindVisitor6<Void, Void>() {
      @Override
      protected Void defaultAction(Element e, Void p) {
        throw new AssertionError("Provided can only be applied to parameters");
      }

      @Override
      public Void visitVariableAsParameter(final VariableElement providedParameter, Void p) {
        providedParameter.getEnclosingElement().accept(new ElementKindVisitor6<Void, Void>() {
          @Override
          protected Void defaultAction(Element e, Void p) {
            raiseError(providedParameter, "@%s may only be applied to constructor parameters");
            return null;
          }

          @Override
          public Void visitExecutableAsConstructor(ExecutableElement constructor, Void p) {
            if (!(annotatedWithAutoFactory(constructor)
                || annotatedWithAutoFactory(constructor.getEnclosingElement()))) {
              raiseError(providedParameter,
                  "@%s may only be applied to constructors requesting an auto-factory");
            }
            return null;
          }
        }, p);
        return null;
      }
    }, null);
  }

  private void raiseError(VariableElement providedParameter, String messageFormat) {
    messager.printMessage(ERROR, String.format(messageFormat, Provided.class.getSimpleName()),
        providedParameter, Mirrors.getAnnotationMirror(providedParameter, Provided.class).get());
  }

  private static boolean annotatedWithAutoFactory(Element e) {
    return isAnnotationPresent(e, AutoFactory.class);
  }
}
