/*
 * Copyright (C) 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.google.auto.value.processor.escapevelocity;

import java.util.Map;
import java.util.TreeMap;

/**
 * The context of a template evaluation. This consists of the template variables and the template
 * macros. The template variables start with the values supplied by the evaluation call, and can
 * be changed by {@code #set} directives and during the execution of {@code #foreach} and macro
 * calls. The macros are extracted from the template during parsing and never change thereafter.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
interface EvaluationContext {
  Object getVar(String var);

  boolean varIsDefined(String var);

  /**
   * Sets the given variable to the given value.
   *
   * @return a Runnable that will restore the variable to the value it had before. If the variable
   *     was undefined before this method was executed, the Runnable will make it undefined again.
   *     This allows us to restore the state of {@code $x} after {@code #foreach ($x in ...)}.
   */
  Runnable setVar(final String var, Object value);

  class PlainEvaluationContext implements EvaluationContext {
    private final Map<String, Object> vars;

    PlainEvaluationContext(Map<String, ?> vars) {
      this.vars = new TreeMap<String, Object>(vars);
    }

    @Override
    public Object getVar(String var) {
      return vars.get(var);
    }

    @Override
    public boolean varIsDefined(String var) {
      return vars.containsKey(var);
    }

    @Override
    public Runnable setVar(final String var, Object value) {
      Runnable undo;
      if (vars.containsKey(var)) {
        final Object oldValue = vars.get(var);
        undo = new Runnable() {
          @Override public void run() {
            vars.put(var, oldValue);
          }
        };
      } else {
        undo = new Runnable() {
          @Override public void run() {
            vars.remove(var);
          }
        };
      }
      vars.put(var, value);
      return undo;
    }
  }
}
