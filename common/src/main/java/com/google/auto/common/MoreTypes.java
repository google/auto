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
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

import java.lang.reflect.Method;
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
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
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

  private static final Class<?> INTERSECTION_TYPE;
  private static final Method GET_BOUNDS;
  static {
    Class<?> c;
    Method m;
    try {
      c = Class.forName("javax.lang.model.type.IntersectionType");
      m = c.getMethod("getBounds");
    } catch (Exception e) {
      c = null;
      m = null;
    }
    INTERSECTION_TYPE = c;
    GET_BOUNDS = m;
  }

  private static boolean equal(TypeMirror a, TypeMirror b, Set<ComparedElements> visiting) {
    // TypeMirror.equals is not guaranteed to return true for types that are equal, but we can
    // assume that if it does return true then the types are equal. This check also avoids getting
    // stuck in infinite recursion when Eclipse decrees that the upper bound of the second K in
    // <K extends Comparable<K>> is a distinct but equal K.
    // The javac implementation of ExecutableType, at least in some versions, does not take thrown
    // exceptions into account in its equals implementation, so avoid this optimization for
    // ExecutableType.
    if (Objects.equal(a, b) && !(a instanceof ExecutableType)) {
      return true;
    }
    EqualVisitorParam p = new EqualVisitorParam();
    p.type = b;
    p.visiting = visiting;
    if (INTERSECTION_TYPE != null && INTERSECTION_TYPE.isInstance(a)) {
      return equalIntersectionTypes(a, b, visiting);
    }
    return (a == b) || (a != null && b != null && a.accept(EQUAL_VISITOR, p));
  }

  // The representation of an intersection type, as in <T extends Number & Comparable<T>>, changed
  // between Java 7 and Java 8. In Java 7 it was modeled as a fake DeclaredType, and our logic
  // for DeclaredType does the right thing. In Java 8 it is modeled as a new type IntersectionType.
  // In order for our code to run on Java 7 (and Java 6) we can't even mention IntersectionType,
  // so we can't override visitIntersectionType(IntersectionType). Instead, we discover through
  // reflection whether IntersectionType exists, and if it does we extract the bounds of the
  // intersection ((Number, Comparable<T>) in the example) and compare them directly.
  @SuppressWarnings("unchecked")
  private static boolean equalIntersectionTypes(
      TypeMirror a, TypeMirror b, Set<ComparedElements> visiting) {
    if (!INTERSECTION_TYPE.isInstance(b)) {
      return false;
    }
    List<? extends TypeMirror> aBounds;
    List<? extends TypeMirror> bBounds;
    try {
      aBounds = (List<? extends TypeMirror>) GET_BOUNDS.invoke(a);
      bBounds = (List<? extends TypeMirror>) GET_BOUNDS.invoke(b);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
    return equalLists(aBounds, bBounds, visiting);
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

  /**
   * Returns a {@link ArrayType} if the {@link TypeMirror} represents a primitive array or
   * throws an {@link IllegalArgumentException}.
   */
  public static ArrayType asArray(TypeMirror maybeArrayType) {
    return maybeArrayType.accept(new CastingTypeVisitor<ArrayType>() {
      @Override public ArrayType visitArray(ArrayType type, String ignore) {
        return type;
      }
    }, "primitive array");
  }

  /**
   * Returns a {@link DeclaredType} if the {@link TypeMirror} represents a declared type such
   * as a class, interface, union/compound, or enum or throws an {@link IllegalArgumentException}.
   */
  public static DeclaredType asDeclared(TypeMirror maybeDeclaredType) {
    return maybeDeclaredType.accept(new CastingTypeVisitor<DeclaredType>() {
      @Override public DeclaredType visitDeclared(DeclaredType type, String ignored) {
        return type;
      }
    }, "declared type");
  }

  /**
   * Returns a {@link ExecutableType} if the {@link TypeMirror} represents an executable type such
   * as may result from missing code, or bad compiles or throws an {@link IllegalArgumentException}.
   */
  public static ErrorType asError(TypeMirror maybeErrorType) {
    return maybeErrorType.accept(new CastingTypeVisitor<ErrorType>() {
      @Override public ErrorType visitError(ErrorType type, String p) {
        return type;
      }
    }, "error type");
  }

  /**
   * Returns a {@link ExecutableType} if the {@link TypeMirror} represents an executable type such
   * as a method, constructor, or initializer or throws an {@link IllegalArgumentException}.
   */
  public static ExecutableType asExecutable(TypeMirror maybeExecutableType) {
    return maybeExecutableType.accept(new CastingTypeVisitor<ExecutableType>() {
      @Override public ExecutableType visitExecutable(ExecutableType type, String p) {
        return type;
      }
    }, "executable type");
  }

  /**
   * Returns a {@link NoType} if the {@link TypeMirror} represents an non-type such
   * as void, or package, etc. or throws an {@link IllegalArgumentException}.
   */
  public static NoType asNoType(TypeMirror maybeNoType) {
    return maybeNoType.accept(new CastingTypeVisitor<NoType>() {
      @Override public NoType visitNoType(NoType noType, String p) {
        return noType;
      }
    }, "non-type");
  }

  /**
   * Returns a {@link NullType} if the {@link TypeMirror} represents the null type
   * or throws an {@link IllegalArgumentException}.
   */
  public static NullType asNullType(TypeMirror maybeNullType) {
    return maybeNullType.accept(new CastingTypeVisitor<NullType>() {
      @Override public NullType visitNull(NullType nullType, String p) {
        return nullType;
      }
    }, "null");
  }

  /**
   * Returns a {@link PrimitiveType} if the {@link TypeMirror} represents a primitive type
   * or throws an {@link IllegalArgumentException}.
   */
  public static PrimitiveType asPrimitiveType(TypeMirror maybePrimitiveType) {
    return maybePrimitiveType.accept(new CastingTypeVisitor<PrimitiveType>() {
      @Override public PrimitiveType visitPrimitive(PrimitiveType type, String p) {
        return type;
      }
    }, "primitive type");
  }

  //
  // visitUnionType would go here, but it is a 1.7 API.
  //

  /**
   * Returns a {@link TypeVariable} if the {@link TypeMirror} represents a type variable
   * or throws an {@link IllegalArgumentException}.
   */
  public static TypeVariable asTypeVariable(TypeMirror maybeTypeVariable) {
    return maybeTypeVariable.accept(new CastingTypeVisitor<TypeVariable>() {
      @Override public TypeVariable visitTypeVariable(TypeVariable type, String p) {
        return type;
      }
    }, "type variable");
  }

  /**
   * Returns a {@link WildcardType} if the {@link TypeMirror} represents a wildcard type
   * or throws an {@link IllegalArgumentException}.
   */
  public static WildcardType asWildcard(WildcardType maybeWildcardType) {
    return maybeWildcardType.accept(new CastingTypeVisitor<WildcardType>() {
      @Override public WildcardType visitWildcard(WildcardType type, String p) {
        return type;
      }
    }, "wildcard type");
  }

  /**
   *
   * Returns true if the raw type underlying the given {@link TypeMirror} represents the
   * same raw type as the given {@link Class} and throws an IllegalArgumentException if the
   * {@link TypeMirror} does not represent a type that can be referenced by a {@link Class}
   */
  public static boolean isTypeOf(final Class<?> clazz, TypeMirror type) {
    checkNotNull(clazz);
    return type.accept(new SimpleTypeVisitor6<Boolean, Void>() {
      @Override protected Boolean defaultAction(TypeMirror type, Void ignored) {
        throw new IllegalArgumentException(type + " cannot be represented as a Class<?>.");
      }

      @Override public Boolean visitNoType(NoType noType, Void p) {
        if (noType.getKind().equals(TypeKind.VOID)) {
          return clazz.equals(Void.TYPE);
        }
        throw new IllegalArgumentException(noType + " cannot be represented as a Class<?>.");
      }

      @Override public Boolean visitPrimitive(PrimitiveType type, Void p) {
        switch (type.getKind()) {
          case BOOLEAN:
            return clazz.equals(Boolean.TYPE);
          case BYTE:
            return clazz.equals(Byte.TYPE);
          case CHAR:
            return clazz.equals(Character.TYPE);
          case DOUBLE:
            return clazz.equals(Double.TYPE);
          case FLOAT:
            return clazz.equals(Float.TYPE);
          case INT:
            return clazz.equals(Integer.TYPE);
          case LONG:
            return clazz.equals(Long.TYPE);
          case SHORT:
            return clazz.equals(Short.TYPE);
          default:
            throw new IllegalArgumentException(type + " cannot be represented as a Class<?>.");
        }
      }

      @Override public Boolean visitArray(ArrayType array, Void p) {
        return clazz.isArray()
            && isTypeOf(clazz.getComponentType(), array.getComponentType());
      }

      @Override public Boolean visitDeclared(DeclaredType type, Void ignored) {
        TypeElement typeElement;
        try {
          typeElement = MoreElements.asType(type.asElement());
        } catch (IllegalArgumentException iae) {
          throw new IllegalArgumentException(type + " does not represent a class or interface.");
        }
        return typeElement.getQualifiedName().contentEquals(clazz.getCanonicalName());
      }
    }, null);
  }

  private static class CastingTypeVisitor<T> extends SimpleTypeVisitor6<T, String> {
    @Override protected T defaultAction(TypeMirror e, String label) {
      throw new IllegalArgumentException(e + " does not represent a " + label);
    }
  }

  private MoreTypes() {}
}
