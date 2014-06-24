/*
 * Copyright (C) 2014 Google, Inc.
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
package com.google.auto.common;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.EXECUTABLE;
import static javax.lang.model.type.TypeKind.TYPEVAR;
import static javax.lang.model.type.TypeKind.WILDCARD;

import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

/**
 * Utilities related to {@link TypeMirror} instances.
 *
 * @author Gregory Kick
 * @since 2.0
 */
public final class MoreTypes {
  private static final Equivalence<TypeMirror> TYPE_EQUIVALENCE = new Equivalence<TypeMirror>() {
    @Override
    protected boolean doEquivalent(TypeMirror a, TypeMirror b) {
      return MoreTypes.equal(a, b, ImmutableSet.<ComparedElements>of());
    }

    @Override
    protected int doHash(TypeMirror t) {
      return MoreTypes.hash(t, ImmutableSet.<Element>of());
    }
  };

  public static Equivalence<TypeMirror> equivalence() {
    return TYPE_EQUIVALENCE;
  }

  // So EQUAL_VISITOR can be a singleton, we maintain visiting state, in particular which types
  // have been seen already, in this object.
  private static final class EqualVisitorParam {
    TypeMirror type;
    Set<ComparedElements> visiting;
  }

  private static class ComparedElements {
    final Element a;
    final Element b;

    ComparedElements(Element a, Element b) {
      this.a = a;
      this.b = b;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof ComparedElements) {
        ComparedElements that = (ComparedElements) o;
        return this.a.equals(that.a) && this.b.equals(that.b);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return a.hashCode() * 31 + b.hashCode();
    }
  }

  private static final TypeVisitor<Boolean, EqualVisitorParam> EQUAL_VISITOR =
      new SimpleTypeVisitor6<Boolean, EqualVisitorParam>() {
        @Override
        protected Boolean defaultAction(TypeMirror a, EqualVisitorParam p) {
          return a.getKind().equals(p.type.getKind());
        }

        @Override
        public Boolean visitArray(ArrayType a, EqualVisitorParam p) {
          if (p.type.getKind().equals(ARRAY)) {
            ArrayType b = (ArrayType) p.type;
            return equal(a.getComponentType(), b.getComponentType(), p.visiting);
          }
          return false;
        }

        @Override
        public Boolean visitDeclared(DeclaredType a, EqualVisitorParam p) {
          if (p.type.getKind().equals(DECLARED)) {
            DeclaredType b = (DeclaredType) p.type;
            Element aElement = a.asElement();
            Element bElement = b.asElement();
            ComparedElements comparedElements = new ComparedElements(aElement, bElement);
            if (p.visiting.contains(comparedElements)) {
              // This can happen for example with Enum in Enum<E extends Enum<E>>. Return a
              // provisional true value since if the Elements are not in fact equal the original
              // visitor of Enum will discover that. We have to check both Elements being compared
              // though to avoid missing the fact that one of the types being compared
              // differs at exactly this point.
              return true;
            }
            Set<ComparedElements> newVisiting = new HashSet<ComparedElements>(p.visiting);
            newVisiting.add(comparedElements);
            return aElement.equals(bElement)
                && equal(a.getEnclosingType(), a.getEnclosingType(), newVisiting)
                && equalLists(a.getTypeArguments(), b.getTypeArguments(), newVisiting);

          }
          return false;
        }

        @Override
        public Boolean visitError(ErrorType a, EqualVisitorParam p) {
          return a.equals(p.type);
        }

        @Override
        public Boolean visitExecutable(ExecutableType a, EqualVisitorParam p) {
          if (p.type.getKind().equals(EXECUTABLE)) {
            ExecutableType b = (ExecutableType) p.type;
            return equalLists(a.getParameterTypes(), b.getParameterTypes(), p.visiting)
                && equal(a.getReturnType(), b.getReturnType(), p.visiting)
                && equalLists(a.getThrownTypes(), b.getThrownTypes(), p.visiting)
                && equalLists(a.getTypeVariables(), b.getTypeVariables(), p.visiting);
          }
          return false;
        }

        @Override
        public Boolean visitTypeVariable(TypeVariable a, EqualVisitorParam p) {
          if (p.type.getKind().equals(TYPEVAR)) {
            TypeVariable b = (TypeVariable) p.type;
            return equal(a.getUpperBound(), b.getUpperBound(), p.visiting)
                && equal(a.getLowerBound(), b.getLowerBound(), p.visiting);
          }
          return false;
        }

        @Override
        public Boolean visitWildcard(WildcardType a, EqualVisitorParam p) {
          if (p.type.getKind().equals(WILDCARD)) {
            WildcardType b = (WildcardType) p.type;
            return equal(a.getExtendsBound(), b.getExtendsBound(), p.visiting)
                && equal(a.getSuperBound(), b.getSuperBound(), p.visiting);
          }
          return false;
        }

        @Override
        public Boolean visitUnknown(TypeMirror a, EqualVisitorParam p) {
          throw new UnsupportedOperationException();
        }
      };

  private static boolean equal(TypeMirror a, TypeMirror b, Set<ComparedElements> visiting) {
    EqualVisitorParam p = new EqualVisitorParam();
    p.type = b;
    p.visiting = visiting;
    return (a == b) || (a != null && b != null && a.accept(EQUAL_VISITOR, p));
  }

  private static boolean equalLists(
      List<? extends TypeMirror> a, List<? extends TypeMirror> b,
      Set<ComparedElements> visiting) {
    int size = a.size();
    if (size != b.size()) {
      return false;
    }
    // Use iterators in case the Lists aren't RandomAccess
    Iterator<? extends TypeMirror> aIterator = a.iterator();
    Iterator<? extends TypeMirror> bIterator = b.iterator();
    while (aIterator.hasNext()) {
      if (!bIterator.hasNext()) {
        return false;
      }
      TypeMirror nextMirrorA = aIterator.next();
      TypeMirror nextMirrorB = bIterator.next();
      if (!equal(nextMirrorA, nextMirrorB, visiting)) {
        return false;
      }
    }
    return !aIterator.hasNext();
  }

  private static final int HASH_SEED = 17;
  private static final int HASH_MULTIPLIER = 31;

  private static final TypeVisitor<Integer, Set<Element>> HASH_VISITOR =
      new SimpleTypeVisitor6<Integer, Set<Element>>() {
          int hashKind(int seed, TypeMirror t) {
            int result = seed * HASH_MULTIPLIER;
            result += t.getKind().hashCode();
            return result;
          }

          @Override
          protected Integer defaultAction(TypeMirror e, Set<Element> visiting) {
            return hashKind(HASH_SEED, e);
          }

          @Override
          public Integer visitArray(ArrayType t, Set<Element> visiting) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += t.getComponentType().accept(this, visiting);
            return result;
          }

          @Override
          public Integer visitDeclared(DeclaredType t, Set<Element> visiting) {
            Element element = t.asElement();
            if (visiting.contains(element)) {
              return 0;
            }
            Set<Element> newVisiting = new HashSet<Element>(visiting);
            newVisiting.add(element);
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += t.asElement().hashCode();
            result *= HASH_MULTIPLIER;
            result += t.getEnclosingType().accept(this, newVisiting);
            result *= HASH_MULTIPLIER;
            result += hashList(t.getTypeArguments(), newVisiting);
            return result;
          }

          @Override
          public Integer visitExecutable(ExecutableType t, Set<Element> visiting) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += hashList(t.getParameterTypes(), visiting);
            result *= HASH_MULTIPLIER;
            result += t.getReturnType().accept(this, visiting);
            result *= HASH_MULTIPLIER;
            result += hashList(t.getThrownTypes(), visiting);
            result *= HASH_MULTIPLIER;
            result += hashList(t.getTypeVariables(), visiting);
            return result;
          }

          @Override
          public Integer visitTypeVariable(TypeVariable t, Set<Element> visiting) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += t.getLowerBound().accept(this, visiting);
            TypeParameterElement element = (TypeParameterElement) t.asElement();
            for (TypeMirror bound : element.getBounds()) {
              result *= HASH_MULTIPLIER;
              result += bound.accept(this, visiting);
            }
            return result;
          }

          @Override
          public Integer visitWildcard(WildcardType t, Set<Element> visiting) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result +=
                (t.getExtendsBound() == null) ? 0 : t.getExtendsBound().accept(this, visiting);
            result *= HASH_MULTIPLIER;
            result += (t.getSuperBound() == null) ? 0 : t.getSuperBound().accept(this, visiting);
            return result;
          }

          @Override
          public Integer visitUnknown(TypeMirror t, Set<Element> visiting) {
            throw new UnsupportedOperationException();
          }
      };

  private static int hashList(List<? extends TypeMirror> mirrors, Set<Element> visiting) {
    int result = HASH_SEED;
    for (TypeMirror mirror : mirrors) {
      result *= HASH_MULTIPLIER;
      result += hash(mirror, visiting);
    }
    return result;
  }

  private static int hash(TypeMirror mirror, Set<Element> visiting) {
    return mirror == null ? 0 : mirror.accept(HASH_VISITOR, visiting);
  }

  /**
   * Returns the set of {@linkplain TypeElement types} that are referenced by the given
   * {@link TypeMirror}.
   */
  public static ImmutableSet<TypeElement> referencedTypes(TypeMirror type) {
    checkNotNull(type);
    ImmutableSet.Builder<TypeElement> elements = ImmutableSet.builder();
    type.accept(new SimpleTypeVisitor6<Void, ImmutableSet.Builder<TypeElement>>() {
      @Override
      public Void visitArray(ArrayType t, Builder<TypeElement> p) {
        t.getComponentType().accept(this, p);
        return null;
      }

      @Override
      public Void visitDeclared(DeclaredType t, Builder<TypeElement> p) {
        p.add(MoreElements.asType(t.asElement()));
        for (TypeMirror typeArgument : t.getTypeArguments()) {
          typeArgument.accept(this, p);
        }
        return null;
      }

      @Override
      public Void visitTypeVariable(TypeVariable t, Builder<TypeElement> p) {
        t.getLowerBound().accept(this, p);
        t.getUpperBound().accept(this, p);
        return null;
      }

      @Override
      public Void visitWildcard(WildcardType t, Builder<TypeElement> p) {
        TypeMirror extendsBound = t.getExtendsBound();
        if (extendsBound != null) {
          extendsBound.accept(this, p);
        }
        TypeMirror superBound = t.getSuperBound();
        if (superBound != null) {
          superBound.accept(this, p);
        }
        return null;
      }
    }, elements);
    return elements.build();
  }

  public static TypeElement asTypeElement(Types types, TypeMirror mirror) {
    checkNotNull(types);
    checkNotNull(mirror);
    Element element = types.asElement(mirror);
    checkArgument(element != null);
    return element.accept(new SimpleElementVisitor6<TypeElement, Void>() {
      @Override
      protected TypeElement defaultAction(Element e, Void p) {
        throw new IllegalArgumentException();
      }

      @Override public TypeElement visitType(TypeElement e, Void p) {
        return e;
      }
    }, null);
  }

  public static ImmutableSet<TypeElement> asTypeElements(Types types,
      Iterable<? extends TypeMirror> mirrors) {
    checkNotNull(types);
    checkNotNull(mirrors);
    ImmutableSet.Builder<TypeElement> builder = ImmutableSet.builder();
    for (TypeMirror mirror : mirrors) {
      builder.add(asTypeElement(types, mirror));
    }
    return builder.build();
  }

  private MoreTypes() {}
}
