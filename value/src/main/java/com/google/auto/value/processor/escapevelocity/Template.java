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

import com.google.auto.value.processor.escapevelocity.EvaluationContext.PlainEvaluationContext;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;

/**
 * A template expressed in EscapeVelocity, a subset of the Velocity Template Language (VTL) from
 * Apache. The intent of this implementation is that if a template is accepted and successfully
 * produces output, that output will be identical to what Velocity would have produced for the same
 * template and input variables.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
// TODO(emcmanus): spell out exactly what Velocity features are unsupported.
public class Template {
  private final Node root;

  /**
   * Parse a VTL template from the given {@code Reader}.
   */
  public static Template parseFrom(Reader reader) throws IOException {
    return new Parser(reader).parse();
  }

  Template(Node root) {
    this.root = root;
  }

  /**
   * Evaluate the given template with the given initial set of variables.
   *
   * @param vars a map where the keys are variable names and the values are the corresponding
   *     variable values. For example, if {@code "x"} maps to 23, then {@code $x} in the template
   *     will expand to 23.
   *
   * @return the string result of evaluating the template.
   */
  public String evaluate(Map<String, ?> vars) {
    EvaluationContext evaluationContext = new PlainEvaluationContext(vars);
    return String.valueOf(root.evaluate(evaluationContext));
  }
}
