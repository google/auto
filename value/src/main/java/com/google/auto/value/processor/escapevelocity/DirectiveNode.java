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

/**
 * A node in the parse tree that is a directive such as {@code #set ($x = $y)}
 * or {@code #if ($x) y #end}.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
abstract class DirectiveNode extends Node {
  DirectiveNode(int lineNumber) {
    super(lineNumber);
  }

  /**
   * A node in the parse tree representing a {@code #set} construct. Evaluating
   * {@code #set ($x = 23)} will set {@code $x} to the value 23. It does not in itself produce
   * any text in the output.
   *
   * <p>Velocity supports setting values within arrays or collections, with for example
   * {@code $set ($x[$i] = $y)}. That is not currently supported here.
   */
  static class SetNode extends DirectiveNode {
    private final String var;
    private final Node expression;

    SetNode(String var, Node expression) {
      super(expression.lineNumber);
      this.var = var;
      this.expression = expression;
    }

    @Override
    Object evaluate(EvaluationContext context) {
      context.setVar(var, expression.evaluate(context));
      return "";
    }
  }
}
