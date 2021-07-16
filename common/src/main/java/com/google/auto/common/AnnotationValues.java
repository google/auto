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
package com.google.auto.common;

import static com.google.auto.common.MoreStreams.toImmutableList;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Function;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

/**
 * A utility class for working with {@link AnnotationValue} instances.
 *
 * @author Christian Gruber
 */
public final class AnnotationValues {
  private static final Equivalence<AnnotationValue> ANNOTATION_VALUE_EQUIVALENCE =
      new Equivalence<AnnotationValue>() {
        @Override
        protected boolean doEquivalent(AnnotationValue left, AnnotationValue right) {
          return left.accept(
              new SimpleAnnotationValueVisitor8<Boolean, AnnotationValue>() {
                // LHS is not an annotation or array of annotation values, so just test equality.
                @Override
                protected Boolean defaultAction(Object left, AnnotationValue right) {
                  return left.equals(
                      right.accept(
                          new SimpleAnnotationValueVisitor8<Object, Void>() {
                            @Override
                            protected Object defaultAction(Object object, Void unused) {
                              return object;
                            }
                          },
                          null));
                }

                // LHS is an annotation mirror so test equivalence for RHS annotation mirrors
                // and false for other types.
                @Override
                public Boolean visitAnnotation(AnnotationMirror left, AnnotationValue right) {
                  return right.accept(
                      new SimpleAnnotationValueVisitor8<Boolean, AnnotationMirror>() {
                        @Override
                        protected Boolean defaultAction(Object right, AnnotationMirror left) {
                          return false; // Not an annotation mirror, so can't be equal to such.
                        }

                        @Override
                        public Boolean visitAnnotation(
                            AnnotationMirror right, AnnotationMirror left) {
                          return AnnotationMirrors.equivalence().equivalent(left, right);
                        }
                      },
                      left);
                }

                // LHS is a list of annotation values have to collect-test equivalences, or false
                // for any other types.
                @Override
                public Boolean visitArray(
                    List<? extends AnnotationValue> left, AnnotationValue right) {
                  return right.accept(
                      new SimpleAnnotationValueVisitor8<
                          Boolean, List<? extends AnnotationValue>>() {
                        @Override
                        protected Boolean defaultAction(
                            Object ignored, List<? extends AnnotationValue> alsoIgnored) {
                          return false; // Not an array, so can't be equal to such.
                        }

                        @SuppressWarnings("unchecked") // safe covariant cast
                        @Override
                        public Boolean visitArray(
                            List<? extends AnnotationValue> right,
                            List<? extends AnnotationValue> left) {
                          return AnnotationValues.equivalence()
                              .pairwise()
                              .equivalent(
                                  (List<AnnotationValue>) left, (List<AnnotationValue>) right);
                        }
                      },
                      left);
                }

                @Override
                public Boolean visitType(TypeMirror left, AnnotationValue right) {
                  return right.accept(
                      new SimpleAnnotationValueVisitor8<Boolean, TypeMirror>() {
                        @Override
                        protected Boolean defaultAction(Object ignored, TypeMirror alsoIgnored) {
                          return false; // Not an annotation mirror, so can't be equal to such.
                        }

                        @Override
                        public Boolean visitType(TypeMirror right, TypeMirror left) {
                          return MoreTypes.equivalence().equivalent(left, right);
                        }
                      },
                      left);
                }
              },
              right);
        }

        @Override
        protected int doHash(AnnotationValue value) {
          return value.accept(
              new SimpleAnnotationValueVisitor8<Integer, Void>() {
                @Override
                public Integer visitAnnotation(AnnotationMirror value, Void ignore) {
                  return AnnotationMirrors.equivalence().hash(value);
                }

                @SuppressWarnings("unchecked") // safe covariant cast
                @Override
                public Integer visitArray(List<? extends AnnotationValue> values, Void ignore) {
                  return AnnotationValues.equivalence()
                      .pairwise()
                      .hash((List<AnnotationValue>) values);
                }

                @Override
                public Integer visitType(TypeMirror value, Void ignore) {
                  return MoreTypes.equivalence().hash(value);
                }

                @Override
                protected Integer defaultAction(Object value, Void ignored) {
                  return value.hashCode();
                }
              },
              null);
        }
      };

  /**
   * Returns an {@link Equivalence} for {@link AnnotationValue} as annotation values may
   * contain {@link AnnotationMirror} instances some of whose implementations delegate
   * equality tests to {@link Object#equals} whereas the documentation explicitly states
   * that instance/reference equality is not the proper test.
   *
   * @see AnnotationMirrors#equivalence()
   */
  public static Equivalence<AnnotationValue> equivalence() {
    return ANNOTATION_VALUE_EQUIVALENCE;
  }

  private static class DefaultVisitor<T> extends SimpleAnnotationValueVisitor8<T, Void> {
    final Class<T> clazz;

    DefaultVisitor(Class<T> clazz) {
      this.clazz = checkNotNull(clazz);
    }

    @Override
    public T defaultAction(Object o, Void unused) {
      throw new IllegalArgumentException(
          "Expected a " + clazz.getSimpleName() + ", got instead: " + o);
    }
  }

  private static final class TypeMirrorVisitor extends DefaultVisitor<DeclaredType> {
    static final TypeMirrorVisitor INSTANCE = new TypeMirrorVisitor();

    TypeMirrorVisitor() {
      super(DeclaredType.class);
    }

    @Override
    public DeclaredType visitType(TypeMirror value, Void unused) {
      return MoreTypes.asDeclared(value);
    }
  }
  ;

  /**
   * Returns the value as a class.
   *
   * @throws IllegalArgumentException if the value is not a class.
   */
  public static DeclaredType getTypeMirror(AnnotationValue value) {
    return TypeMirrorVisitor.INSTANCE.visit(value);
  }

  private static final class AnnotationMirrorVisitor extends DefaultVisitor<AnnotationMirror> {
    static final AnnotationMirrorVisitor INSTANCE = new AnnotationMirrorVisitor();

    AnnotationMirrorVisitor() {
      super(AnnotationMirror.class);
    }

    @Override
    public AnnotationMirror visitAnnotation(AnnotationMirror value, Void unused) {
      return value;
    }
  }
  ;

  /**
   * Returns the value as an AnnotationMirror.
   *
   * @throws IllegalArgumentException if the value is not an annotation.
   */
  public static AnnotationMirror getAnnotationMirror(AnnotationValue value) {
    return AnnotationMirrorVisitor.INSTANCE.visit(value);
  }

  private static final class EnumVisitor extends DefaultVisitor<VariableElement> {
    static final EnumVisitor INSTANCE = new EnumVisitor();

    EnumVisitor() {
      super(VariableElement.class);
    }

    @Override
    public VariableElement visitEnumConstant(VariableElement value, Void unused) {
      return value;
    }
  }

  /**
   * Returns the value as a VariableElement.
   *
   * @throws IllegalArgumentException if the value is not an enum.
   */
  public static VariableElement getEnum(AnnotationValue value) {
    return EnumVisitor.INSTANCE.visit(value);
  }

  private static <T> T valueOfType(AnnotationValue annotationValue, Class<T> type) {
    Object value = annotationValue.getValue();
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException(
          "Expected " + type.getSimpleName() + ", got instead: " + value);
    }
    return type.cast(value);
  }

  /**
   * Returns the value as a string.
   *
   * @throws IllegalArgumentException if the value is not a string.
   */
  public static String getString(AnnotationValue value) {
    return valueOfType(value, String.class);
  }

  /**
   * Returns the value as an int.
   *
   * @throws IllegalArgumentException if the value is not an int.
   */
  public static int getInt(AnnotationValue value) {
    return valueOfType(value, Integer.class);
  }

  /**
   * Returns the value as a long.
   *
   * @throws IllegalArgumentException if the value is not a long.
   */
  public static long getLong(AnnotationValue value) {
    return valueOfType(value, Long.class);
  }

  /**
   * Returns the value as a byte.
   *
   * @throws IllegalArgumentException if the value is not a byte.
   */
  public static byte getByte(AnnotationValue value) {
    return valueOfType(value, Byte.class);
  }

  /**
   * Returns the value as a short.
   *
   * @throws IllegalArgumentException if the value is not a short.
   */
  public static short getShort(AnnotationValue value) {
    return valueOfType(value, Short.class);
  }

  /**
   * Returns the value as a float.
   *
   * @throws IllegalArgumentException if the value is not a float.
   */
  public static float getFloat(AnnotationValue value) {
    return valueOfType(value, Float.class);
  }

  /**
   * Returns the value as a double.
   *
   * @throws IllegalArgumentException if the value is not a double.
   */
  public static double getDouble(AnnotationValue value) {
    return valueOfType(value, Double.class);
  }

  /**
   * Returns the value as a boolean.
   *
   * @throws IllegalArgumentException if the value is not a boolean.
   */
  public static boolean getBoolean(AnnotationValue value) {
    return valueOfType(value, Boolean.class);
  }

  /**
   * Returns the value as a char.
   *
   * @throws IllegalArgumentException if the value is not a char.
   */
  public static char getChar(AnnotationValue value) {
    return valueOfType(value, Character.class);
  }

  private static final class ArrayVisitor<T>
      extends SimpleAnnotationValueVisitor8<ImmutableList<T>, Void> {
    final Function<AnnotationValue, T> visitT;

    ArrayVisitor(Function<AnnotationValue, T> visitT) {
      this.visitT = checkNotNull(visitT);
    }

    @Override
    public ImmutableList<T> defaultAction(Object o, Void unused) {
      throw new IllegalStateException("Expected an array, got instead: " + o);
    }

    @Override
    public ImmutableList<T> visitArray(List<? extends AnnotationValue> values, Void unused) {
      return values.stream().map(visitT).collect(toImmutableList());
    }
  }

  private static final ArrayVisitor<DeclaredType> TYPE_MIRRORS_VISITOR =
      new ArrayVisitor<>(AnnotationValues::getTypeMirror);

  /**
   * Returns the value as a list of classes.
   *
   * @throws IllegalArgumentException if the value is not an array of classes.
   */
  public static ImmutableList<DeclaredType> getTypeMirrors(AnnotationValue value) {
    return TYPE_MIRRORS_VISITOR.visit(value);
  }

  private static final ArrayVisitor<AnnotationMirror> ANNOTATION_MIRRORS_VISITOR =
      new ArrayVisitor<>(AnnotationValues::getAnnotationMirror);

  /**
   * Returns the value as a list of annotations.
   *
   * @throws IllegalArgumentException if the value if not an array of annotations.
   */
  public static ImmutableList<AnnotationMirror> getAnnotationMirrors(AnnotationValue value) {
    return ANNOTATION_MIRRORS_VISITOR.visit(value);
  }

  private static final ArrayVisitor<VariableElement> ENUMS_VISITOR =
      new ArrayVisitor<>(AnnotationValues::getEnum);

  /**
   * Returns the value as a list of enums.
   *
   * @throws IllegalArgumentException if the value is not an array of enums.
   */
  public static ImmutableList<VariableElement> getEnums(AnnotationValue value) {
    return ENUMS_VISITOR.visit(value);
  }

  private static final ArrayVisitor<String> STRINGS_VISITOR =
      new ArrayVisitor<>(AnnotationValues::getString);

  /**
   * Returns the value as a list of strings.
   *
   * @throws IllegalArgumentException if the value is not an array of strings.
   */
  public static ImmutableList<String> getStrings(AnnotationValue value) {
    return STRINGS_VISITOR.visit(value);
  }

  private static final ArrayVisitor<Integer> INTS_VISITOR =
      new ArrayVisitor<>(AnnotationValues::getInt);

  /**
   * Returns the value as a list of integers.
   *
   * @throws IllegalArgumentException if the value is not an array of ints.
   */
  public static ImmutableList<Integer> getInts(AnnotationValue value) {
    return INTS_VISITOR.visit(value);
  }

  private static final ArrayVisitor<Long> LONGS_VISITOR =
      new ArrayVisitor<>(AnnotationValues::getLong);

  /**
   * Returns the value as a list of longs.
   *
   * @throws IllegalArgumentException if the value is not an array of longs.
   */
  public static ImmutableList<Long> getLongs(AnnotationValue value) {
    return LONGS_VISITOR.visit(value);
  }

  private static final ArrayVisitor<Byte> BYTES_VISITOR =
      new ArrayVisitor<>(AnnotationValues::getByte);

  /**
   * Returns the value as a list of bytes.
   *
   * @throws IllegalArgumentException if the value is not an array of bytes.
   */
  public static ImmutableList<Byte> getBytes(AnnotationValue value) {
    return BYTES_VISITOR.visit(value);
  }

  private static final ArrayVisitor<Short> SHORTS_VISITOR =
      new ArrayVisitor<>(AnnotationValues::getShort);
  /**
   * Returns the value as a list of shorts.
   *
   * @throws IllegalArgumentException if the value is not an array of shorts.
   */
  public static ImmutableList<Short> getShorts(AnnotationValue value) {
    return SHORTS_VISITOR.visit(value);
  }

  private static final ArrayVisitor<Float> FLOATS_VISITOR =
      new ArrayVisitor<>(AnnotationValues::getFloat);

  /**
   * Returns the value as a list of floats.
   *
   * @throws IllegalArgumentException if the value is not an array of floats.
   */
  public static ImmutableList<Float> getFloats(AnnotationValue value) {
    return FLOATS_VISITOR.visit(value);
  }

  private static final ArrayVisitor<Double> DOUBLES_VISITOR =
      new ArrayVisitor<>(AnnotationValues::getDouble);

  /**
   * Returns the value as a list of doubles.
   *
   * @throws IllegalArgumentException if the value is not an array of doubles.
   */
  public static ImmutableList<Double> getDoubles(AnnotationValue value) {
    return DOUBLES_VISITOR.visit(value);
  }

  private static final ArrayVisitor<Boolean> BOOLEANS_VISITOR =
      new ArrayVisitor<>(AnnotationValues::getBoolean);

  /**
   * Returns the value as a list of booleans.
   *
   * @throws IllegalArgumentException if the value is not an array of booleans.
   */
  public static ImmutableList<Boolean> getBooleans(AnnotationValue value) {
    return BOOLEANS_VISITOR.visit(value);
  }

  private static final ArrayVisitor<Character> CHARS_VISITOR =
      new ArrayVisitor<>(AnnotationValues::getChar);

  /**
   * Returns the value as a list of characters.
   *
   * @throws IllegalArgumentException if the value is not an array of chars.
   */
  public static ImmutableList<Character> getChars(AnnotationValue value) {
    return CHARS_VISITOR.visit(value);
  }

  private static final ArrayVisitor<AnnotationValue> ANNOTATION_VALUES_VISITOR =
      new ArrayVisitor<>(x -> x);

  /**
   * Returns the value as a list of {@link AnnotationValue}s.
   *
   * @throws IllegalArgumentException if the value is not an array.
   */
  public static ImmutableList<AnnotationValue> getAnnotationValues(AnnotationValue value) {
    return ANNOTATION_VALUES_VISITOR.visit(value);
  }

  /**
   * Returns a string representation of the given annotation value, suitable for inclusion in a Java
   * source file as part of an annotation. For example, if {@code annotationValue} represents the
   * string {@code unchecked} in the annotation {@code @SuppressWarnings("unchecked")}, this method
   * will return the string {@code "unchecked"}, which you can then use as part of an annotation
   * being generated.
   *
   * <p>For all annotation values other than nested annotations, the returned string can also be
   * used to initialize a variable of the appropriate type.
   *
   * <p>Fully qualified names are used for types in annotations, class literals, and enum constants,
   * ensuring that the source form will compile without requiring additional imports.
   */
  public static String toString(AnnotationValue annotationValue) {
    return AnnotationOutput.toString(annotationValue);
  }

  private AnnotationValues() {}
}
