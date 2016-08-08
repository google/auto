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

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;

/**
 * A macro definition. Macros appear in templates using the syntax {@code #macro (m $x $y) ... #end}
 * and each one produces an instance of this class. Evaluating a macro involves setting the
 * parameters (here {$x $y)} and evaluating the macro body. Macro arguments are call-by-name, which
 * means that we need to set each parameter variable to the node in the parse tree that corresponds
 * to it, and arrange for that node to be evaluated when the variable is actually referenced.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
class Macro {
  private final int definitionLineNumber;
  private final String name;
  private final ImmutableList<String> parameterNames;
  private final Node body;

  Macro(int definitionLineNumber, String name, List<String> parameterNames, Node body) {
    this.definitionLineNumber = definitionLineNumber;
    this.name = name;
    this.parameterNames = ImmutableList.copyOf(parameterNames);
    this.body = body;
  }

  String name() {
    return name;
  }

  int parameterCount() {
    return parameterNames.size();
  }

  Object evaluate(EvaluationContext context, List<Node> thunks) {
    try {
      Verify.verify(thunks.size() == parameterNames.size(), "Argument mistmatch for %s", name);
      Map<String, Node> parameterThunks = Maps.newLinkedHashMap();
      for (int i = 0; i < parameterNames.size(); i++) {
        parameterThunks.put(parameterNames.get(i), thunks.get(i));
      }
      EvaluationContext newContext = new MacroEvaluationContext(parameterThunks, context);
      return body.evaluate(newContext);
    } catch (EvaluationException e) {
      EvaluationException newException = new EvaluationException(
          "In macro #" + name + " defined on line " + definitionLineNumber + ": " + e.getMessage());
      newException.setStackTrace(e.getStackTrace());
      throw e;
    }
  }

  /**
   * The context for evaluation within macros. This wraps an existing {@code EvaluationContext}
   * but intercepts reads of the macro's parameters so that they result in a call-by-name evaluation
   * of whatever was passed as the parameter. For example, if you write...
   * <pre>{@code
   * #macro (mymacro $x)
   * $x $x
   * #end
   * #mymacro($foo.bar(23))
   * }</pre>
   * ...then the {@code #mymacro} call will result in {@code $foo.bar(23)} being evaluated twice,
   * once for each time {@code $x} appears. The way this works is that {@code $x} is a <i>thunk</i>.
   * Historically a thunk is a piece of code to evaluate an expression in the context where it
   * occurs, for call-by-name procedures as in Algol 60. Here, it is not exactly a piece of code,
   * but it has the same responsibility.
   */
  static class MacroEvaluationContext implements EvaluationContext {
    private final Map<String, Node> parameterThunks;
    private final EvaluationContext originalEvaluationContext;

    MacroEvaluationContext(
        Map<String, Node> parameterThunks, EvaluationContext originalEvaluationContext) {
      this.parameterThunks = parameterThunks;
      this.originalEvaluationContext = originalEvaluationContext;
    }

    @Override
    public Object getVar(String var) {
      Node thunk = parameterThunks.get(var);
      if (thunk == null) {
        return originalEvaluationContext.getVar(var);
      } else {
        // Evaluate the thunk in the context where it appeared, not in this context. Otherwise
        // if you pass $x to a parameter called $x you would get an infinite recursion. Likewise
        // if you had #macro(mymacro $x $y) and a call #mymacro($y 23), you would expect that $x
        // would expand to whatever $y meant at the call site, rather than to the value of the $y
        // parameter.
        return thunk.evaluate(originalEvaluationContext);
      }
    }

    @Override
    public boolean varIsDefined(String var) {
      return parameterThunks.containsKey(var) || originalEvaluationContext.varIsDefined(var);
    }

    @Override
    public Runnable setVar(final String var, Object value) {
      // Copy the behaviour that #set will shadow a macro parameter, even though the Velocity peeps
      // seem to agree that that is not good.
      final Node thunk = parameterThunks.get(var);
      if (thunk == null) {
        return originalEvaluationContext.setVar(var, value);
      } else {
        parameterThunks.remove(var);
        final Runnable originalUndo = originalEvaluationContext.setVar(var, value);
        return new Runnable() {
          @Override
          public void run() {
            originalUndo.run();
            parameterThunks.put(var, thunk);
          }
        };
      }
    }
  }
}
