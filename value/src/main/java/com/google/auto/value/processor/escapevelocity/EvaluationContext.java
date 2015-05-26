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

/**
 * The context of a template evaluation. This consists of the template variables. Those variables
 * start with the values supplied by the evaluation call, and can be changed by {@code #set}
 * directives and during the execution of {@code #foreach} and macro calls.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
class EvaluationContext {
  private final Map<String, Object> vars;

  EvaluationContext(Map<String, Object> vars) {
    this.vars = vars;
  }

  Object getVar(String var) {
    return vars.get(var);
  }

  boolean varIsDefined(String var) {
    return vars.containsKey(var);
  }
}
