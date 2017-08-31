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
import java.util.List;

/**
 * A parsing node that will be deleted during the construction of the parse tree, to be replaced
 * by a higher-level construct such as {@link DirectiveNode.IfNode}. See {@link Parser#parse()}
 * for a description of the way these tokens work.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
abstract class TokenNode extends Node {
  TokenNode(int lineNumber) {
    super(lineNumber);
  }

  /**
   * This method always throws an exception because a node like this should never be found in the
   * final parse tree.
   */
  @Override Object evaluate(EvaluationContext vars) {
    throw new UnsupportedOperationException();
  }

  /**
   * The name of the token, for use in parse error messages.
   */
  abstract String name();

  /**
   * A synthetic node that represents the end of the input. This node is the last one in the
   * initial token string and also the last one in the parse tree.
   */
  static final class EofNode extends TokenNode {
    EofNode(int lineNumber) {
      super(lineNumber);
    }

    @Override
    String name() {
      return "end of file";
    }
  }

  static final class EndTokenNode extends TokenNode {
    EndTokenNode(int lineNumber) {
      super(lineNumber);
    }

    @Override String name() {
      return "#end";
    }
  }

  /**
   * A node in the parse tree representing a comment. Comments are introduced by {@code ##} and
   * extend to the end of the line. The only reason for recording comment nodes is so that we can
   * skip space between a comment and a following {@code #set}, to be compatible with Velocity
   * behaviour.
   */
  static class CommentTokenNode extends TokenNode {
    CommentTokenNode(int lineNumber) {
      super(lineNumber);
    }

    @Override String name() {
      return "##";
    }
  }

  abstract static class IfOrElseIfTokenNode extends TokenNode {
    final ExpressionNode condition;

    IfOrElseIfTokenNode(ExpressionNode condition) {
      super(condition.lineNumber);
      this.condition = condition;
    }
  }

  static final class IfTokenNode extends IfOrElseIfTokenNode {
    IfTokenNode(ExpressionNode condition) {
      super(condition);
    }

    @Override String name() {
      return "#if";
    }
  }

  static final class ElseIfTokenNode extends IfOrElseIfTokenNode {
    ElseIfTokenNode(ExpressionNode condition) {
      super(condition);
    }

    @Override String name() {
      return "#elseif";
    }
  }

  static final class ElseTokenNode extends TokenNode {
    ElseTokenNode(int lineNumber) {
      super(lineNumber);
    }

    @Override String name() {
      return "#else";
    }
  }

  static final class ForEachTokenNode extends TokenNode {
    final String var;
    final ExpressionNode collection;

    ForEachTokenNode(String var, ExpressionNode collection) {
      super(collection.lineNumber);
      this.var = var;
      this.collection = collection;
    }

    @Override String name() {
      return "#foreach";
    }
  }

  static final class MacroDefinitionTokenNode extends TokenNode {
    final String name;
    final ImmutableList<String> parameterNames;

    MacroDefinitionTokenNode(int lineNumber, String name, List<String> parameterNames) {
      super(lineNumber);
      this.name = name;
      this.parameterNames = ImmutableList.copyOf(parameterNames);
    }

    @Override String name() {
      return "#macro(" + name + ")";
    }
  }
}

