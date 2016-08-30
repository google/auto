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
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

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

  /**
   * A node in the parse tree representing an {@code #if} construct. All instances of this class
   * have a <i>true</i> subtree and a <i>false</i> subtree. For a plain {@code #if (cond) body
   * #end}, the false subtree will be empty. For {@code #if (cond1) body1 #elseif (cond2) body2
   * #else body3 #end}, the false subtree will contain a nested {@code IfNode}, as if {@code #else
   * #if} had been used instead of {@code #elseif}.
   */
  static class IfNode extends DirectiveNode {
    private final ExpressionNode condition;
    private final Node truePart;
    private final Node falsePart;

    IfNode(int lineNumber, ExpressionNode condition, Node trueNode, Node falseNode) {
      super(lineNumber);
      this.condition = condition;
      this.truePart = trueNode;
      this.falsePart = falseNode;
    }

    @Override Object evaluate(EvaluationContext context) {
      Node branch = condition.isDefinedAndTrue(context) ? truePart : falsePart;
      return branch.evaluate(context);
    }
  }

  /**
   * A node in the parse tree representing a {@code #foreach} construct. While evaluating
   * {@code #foreach ($x in $things)}, {$code $x} will be set to each element of {@code $things} in
   * turn. Once the loop completes, {@code $x} will go back to whatever value it had before, which
   * might be undefined. During loop execution, the variable {@code $foreach} is also defined.
   * Velocity defines a number of properties in this variable, but here we only support
   * {@code $foreach.hasNext}.
   */
  static class ForEachNode extends DirectiveNode {
    private final String var;
    private final ExpressionNode collection;
    private final Node body;

    ForEachNode(int lineNumber, String var, ExpressionNode in, Node body) {
      super(lineNumber);
      this.var = var;
      this.collection = in;
      this.body = body;
    }

    @Override
    Object evaluate(EvaluationContext context) {
      Object collectionValue = collection.evaluate(context);
      Iterable<?> iterable;
      if (collectionValue instanceof Iterable<?>) {
        iterable = (Iterable<?>) collectionValue;
      } else if (collectionValue instanceof Object[]) {
        iterable = Arrays.asList((Object[]) collectionValue);
      } else if (collectionValue instanceof Map<?, ?>) {
        iterable = ((Map<?, ?>) collectionValue).values();
      } else {
        throw new EvaluationException("Not iterable: " + collectionValue);
      }
      Runnable undo = context.setVar(var, null);
      StringBuilder sb = new StringBuilder();
      Iterator<?> it = iterable.iterator();
      Runnable undoForEach = context.setVar("foreach", new ForEachVar(it));
      while (it.hasNext()) {
        context.setVar(var, it.next());
        sb.append(body.evaluate(context));
      }
      undoForEach.run();
      undo.run();
      return sb.toString();
    }

    /**
     *  This class is the type of the variable {@code $foreach} that is defined within
     * {@code #foreach} loops. Its {@link #getHasNext()} method means that we can write
     * {@code #if ($foreach.hasNext)}.
     */
    private static class ForEachVar {
      private final Iterator<?> iterator;

      ForEachVar(Iterator<?> iterator) {
        this.iterator = iterator;
      }

      public boolean getHasNext() {
        return iterator.hasNext();
      }
    }
  }

  /**
   * A node in the parse tree representing a macro call. If the template contains a definition like
   * {@code #macro (mymacro $x $y) ... #end}, then a call of that macro looks like
   * {@code #mymacro (xvalue yvalue)}. The call is represented by an instance of this class. The
   * definition itself does not appear in the parse tree.
   *
   * <p>Evaluating a macro involves temporarily setting the parameter variables ({@code $x $y} in
   * the example) to thunks representing the argument expressions, evaluating the macro body, and
   * restoring any previous values that the parameter variables had.
   */
  static class MacroCallNode extends DirectiveNode {
    private final String name;
    private final ImmutableList<Node> thunks;
    private Macro macro;

    MacroCallNode(int lineNumber, String name, ImmutableList<Node> argumentNodes) {
      super(lineNumber);
      this.name = name;
      this.thunks = argumentNodes;
    }

    String name() {
      return name;
    }

    int argumentCount() {
      return thunks.size();
    }

    void setMacro(Macro macro) {
      this.macro = macro;
    }

    @Override
    Object evaluate(EvaluationContext context) {
      Verify.verifyNotNull(macro, "Macro #%s should have been linked", name);
      return macro.evaluate(context, thunks);
    }
  }
}
