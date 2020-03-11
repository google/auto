/*
 * Copyright 2008 Google LLC
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
package com.google.auto.service.processor;

import java.util.regex.Pattern;

class Validator {
    private ValidationError error;

    boolean isValid(String id) {
        if (id.isEmpty()) {
            error = ValidationError.EMPTY;
            return false;
        }

        if (!id.contains(".")) {
            error = ValidationError.SINGLE_DOT;
            return false;
        }

        if (id.startsWith(".") || id.endsWith(".")) {
            error = ValidationError.START_END_DOT;
            return false;
        }

        if (id.contains("..")) {
            error = ValidationError.CONSECUTIVE_DOT;
            return false;
        }

        if (Pattern.compile("[^A-Z0-9a-z-.]").matcher(id).find()) {
            error = ValidationError.UNSUPPORTED_CHARACTERS;
            return false;
        }

        if (id.contains("org.gradle") || id.contains("com.gradleware")) {
            error = ValidationError.NOT_ALLOWED_NAMESPACE;
            return false;
        }

        return true;
    }

    ValidationError getError() {
        return error;
    }
}
