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

import com.google.common.collect.ImmutableList;

/**
 * A node in the parse tree.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
abstract class Node {
  final int lineNumber;

  Node(int lineNumber) {
    this.lineNumber = lineNumber;
  }

  /**
   * Returns the result of evaluating this node in the given context. This result may be used as
   * part of a further operation, for example evaluating {@code 2 + 3} to 5 in order to set
   * {@code $x} to 5 in {@code #set ($x = 2 + 3)}. Or it may be used directly as part of the
   * template output, for example evaluating replacing {@code name} by {@code Fred} in
   * {@code My name is $name.}.
   */
  abstract Object evaluate(EvaluationContext context);

  EvaluationException evaluationException(String message) {
    return new EvaluationException("In expression on line " + lineNumber + ": " + message);
  }

  EvaluationException evaluationException(Throwable cause) {
    return new EvaluationException("In expression on line " + lineNumber + ": " + cause, cause);
  }

  /**
   * Returns an empty node in the parse tree. This is used for example to represent the trivial
   * "else" part of an {@code #if} that does not have an explicit {@code #else}.
   */
  static Node emptyNode(int lineNumber) {
    return new Cons(lineNumber, ImmutableList.<Node>of());
  }

  /**
   * Create a new parse tree node that is the concatenation of the given ones. Evaluating the
   * new node produces the same string as evaluating each of the given nodes and concatenating the
   * result.
   */
  static Node cons(int lineNumber, ImmutableList<Node> nodes) {
    return new Cons(lineNumber, nodes);
  }

  private static final class Cons extends Node {
    private final ImmutableList<Node> nodes;

    Cons(int lineNumber, ImmutableList<Node> nodes) {
      super(lineNumber);
      this.nodes = nodes;
    }

    @Override Object evaluate(EvaluationContext context) {
      StringBuilder sb = new StringBuilder();
      for (Node node : nodes) {
        sb.append(node.evaluate(context));
      }
      return sb.toString();
    }
  }
}
