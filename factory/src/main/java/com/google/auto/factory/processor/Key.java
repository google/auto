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

import com.google.common.base.Objects;
import com.google.common.base.Optional;

import javax.lang.model.element.AnnotationMirror;

/**
 * A value object for types and qualifiers.
 *
 * @author Gregory Kick
 */
final class Key {
  private final String type;
  private final Optional<AnnotationMirror> qualifier;

  Key(Optional<AnnotationMirror> qualifier, String type) {
    this.qualifier = qualifier;
    this.type = type;
  }

  Optional<AnnotationMirror> getQualifier() {
    return qualifier;
  }

  String getType() {
    return type;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof Key) {
      Key that = (Key) obj;
      return this.type.equals(that.type)
          && this.qualifier.toString().equals(that.qualifier.toString());
    }
    return super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(qualifier.toString(), type);
  }

  @Override
  public String toString() {
    return qualifier.isPresent()
        ? qualifier.get().toString() + '/' + type
        : type;
  }
}
