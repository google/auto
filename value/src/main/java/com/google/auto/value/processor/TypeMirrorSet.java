/*
 * Copyright 2014 Google LLC
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
package com.google.auto.value.processor;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * A set of TypeMirror objects.
 *
 * @author emcmanus@google.com (Éamonn McManus)
 */
class TypeMirrorSet extends AbstractSet<TypeMirror> {
  private final Set<Equivalence.Wrapper<TypeMirror>> wrappers = new LinkedHashSet<>();

  TypeMirrorSet() {}

  TypeMirrorSet(Collection<? extends TypeMirror> types) {
    addAll(types);
  }

  static TypeMirrorSet of(TypeMirror... types) {
    return new TypeMirrorSet(ImmutableList.copyOf(types));
  }

  private Equivalence.Wrapper<TypeMirror> wrap(TypeMirror typeMirror) {
    return MoreTypes.equivalence().wrap(typeMirror);
  }

  @Override
  public boolean add(TypeMirror typeMirror) {
    return wrappers.add(wrap(typeMirror));
  }

  @Override
  public Iterator<TypeMirror> iterator() {
    final Iterator<Equivalence.Wrapper<TypeMirror>> iterator = wrappers.iterator();
    return new Iterator<TypeMirror>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public TypeMirror next() {
        return iterator.next().get();
      }

      @Override
      public void remove() {
        iterator.remove();
      }
    };
  }

  @Override
  public int size() {
    return wrappers.size();
  }

  @Override
  public boolean contains(Object o) {
    if (o instanceof TypeMirror) {
      return wrappers.contains(wrap((TypeMirror) o));
    } else {
      return false;
    }
  }

  @Override
  public boolean remove(Object o) {
    if (o instanceof TypeMirror) {
      return wrappers.remove(wrap((TypeMirror) o));
    } else {
      return false;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof TypeMirrorSet) {
      TypeMirrorSet that = (TypeMirrorSet) o;
      return wrappers.equals(that.wrappers);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return wrappers.hashCode();
  }
}
