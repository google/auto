/*
 * Copyright 2022 Google LLC
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

import static com.google.auto.common.MoreStreams.toImmutableList;
import static com.google.auto.common.MoreStreams.toImmutableMap;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.processor.AutoValueishProcessor.Property;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Code generation to track which properties have been set in a builder.
 *
 * <p>Every property in an {@code @AutoValue} or {@code @AutoBuilder} builder must be set before the
 * {@code build()} method is called, with a few exceptions like {@code @Nullable} and {@code
 * Optional} properties. That means we must keep track of which ones have in fact been set. We do
 * that in two ways: for reference (non-primitive) types, we use {@code null} to indicate that the
 * value has not been set, while for primitive types we use a bitmask where each bit indicates
 * whether a certain primitive property has been set.
 *
 * <p>The public methods in this class are accessed reflectively from the {@code builder.vm}
 * template. In that template, {@code $builderRequiredProperties} references an instance of this
 * class corresponding to the builder being generated. A reference like {@code
 * $builderRequiredProperties.markAsSet($p)} calls the method {@link #markAsSet} with the given
 * parameter. A reference like {@code $builderRequiredProperties.properties} is shorthand for {@link
 * #getProperties() $builderRequiredProperties.getProperties()}.
 */
public final class BuilderRequiredProperties {
  static final BuilderRequiredProperties EMPTY = new BuilderRequiredProperties(ImmutableSet.of());

  /** All required properties. */
  private final ImmutableSet<Property> properties;

  /**
   * The bit index for each primitive property. Primitive properties are numbered consecutively from
   * 0. Non-primitive properties do not appear in this map.
   */
  private final ImmutableMap<Property, Integer> primitivePropertyToIndex;

  /**
   * The integer fields that store the bitmask. In the usual case, where there are ≤32 primitive
   * properties, we can pack the bitmask into one integer field. Its type is the smallest one that
   * fits the required number of bits, for example {@code byte} if there are ≤8 primitive
   * properties.
   *
   * <p>If there are {@literal >32} primitive properties, we will pack them into as few integer
   * fields as possible. For example if there are 75 primitive properties (this can happen) then we
   * will put numbers 0 to 31 in an {@code int}, 32 to 63 in a second {@code int}, and 64 to 75 in a
   * {@code short}.
   *
   * <p>When there are {@literal >32} primitive properties, we could potentially pack them better if
   * we used {@code long}. But sometimes AutoValue code gets translated into JavaScript, which
   * doesn't handle long values natively. By the time you have that many properties you are probably
   * not going to notice the difference between 5 ints or 2 longs plus an int.
   */
  private final ImmutableList<BitmaskField> bitmaskFields;

  /**
   * Represents a field in which we will record which primitive properties from a certain set have
   * been given a value.
   */
  private static class BitmaskField {
    final Class<?> type;
    final String name;
    /**
     * The source representation of the value this field has when all properties have been given a
     * value.
     */
    final String allOnes;

    BitmaskField(Class<?> type, String name, String allOnes) {
      this.type = type;
      this.name = name;
      this.allOnes = allOnes;
    }
  }

  BuilderRequiredProperties(ImmutableSet<Property> properties) {
    this.properties = properties;

    ImmutableList<Property> primitiveProperties =
        properties.stream().filter(p -> p.getKind().isPrimitive()).collect(toImmutableList());
    int primitiveCount = primitiveProperties.size();
    this.primitivePropertyToIndex =
        IntStream.range(0, primitiveCount)
            .boxed()
            .collect(toImmutableMap(primitiveProperties::get, i -> i));

    this.bitmaskFields =
        IntStream.range(0, (primitiveCount + 31) / 32)
            .mapToObj(
                i -> {
                  int remain = primitiveCount - i * 32;
                  Class<?> type = classForBits(remain);
                  String name = "set$" + i;
                  // We have to be a bit careful with sign-extension. If we're using a byte and the
                  // mask is 0xff, then we'll write -1 instead. The comparison set$0 == 0xff would
                  // always fail since the byte value gets sign-extended to 0xffff_ffff. We should
                  // also write -1 if this is not the last field.
                  boolean minusOne = remain >= 32 || remain == 16 || remain == 8;
                  String allOnes = minusOne ? "-1" : hex((1 << remain) - 1);
                  return new BitmaskField(type, name, allOnes);
                })
            .collect(toImmutableList());
  }

  public ImmutableSet<Property> getProperties() {
    return properties;
  }

  /**
   * Returns code to declare any fields needed to track which properties have been set. Each line in
   * the returned list should appear on a line of its own.
   */
  public ImmutableList<String> getFieldDeclarations() {
    return bitmaskFields.stream()
        .map(field -> "private " + field.type + " " + field.name + ";")
        .collect(toImmutableList());
  }

  /**
   * Returns code to indicate that all primitive properties have received a value. This is needed in
   * the {@code toBuilder()} constructor, since it assigns to the corresponding fields directly
   * without going through their setters.
   */
  public ImmutableList<String> getInitToAllSet() {
    return bitmaskFields.stream()
        .map(field -> field.name + " = " + cast(field.type, field.allOnes) + ";")
        .collect(toImmutableList());
  }

  /**
   * Returns code to indicate that the given property has been set, if assigning to the property
   * field is not enough. For reference (non-primitive) properties, assignment <i>is</i> enough, but
   * for primitive properties we also need to set a bit in the bitmask.
   */
  public String markAsSet(Property p) {
    Integer index = primitivePropertyToIndex.get(p);
    if (index == null) {
      return "";
    }
    BitmaskField field = bitmaskFields.get(index / 32);
    // This use-case is why Java reduces int shift amounts mod 32. :-)
    return field.name + " |= " + hex(1 << index) + ";";
  }

  /**
   * Returns an expression that is true if the given property is required but has not been set.
   * Returns null if the property is not required.
   */
  public String missingRequiredProperty(Property p) {
    if (!properties.contains(p)) {
      return null;
    }
    Integer index = primitivePropertyToIndex.get(p);
    if (index == null) {
      return "this." + p + " == null";
    }
    BitmaskField field = bitmaskFields.get(index / 32);
    return "(" + field.name + " & " + hex(1 << index) + ") == 0";
  }

  /**
   * Returns an expression that is true if any required properties have not been set. Should not be
   * called if there are no required properties.
   */
  public String getAnyMissing() {
    Stream<String> primitiveConditions =
        bitmaskFields.stream().map(field -> field.name + " != " + field.allOnes);
    Stream<String> nonPrimitiveConditions =
        properties.stream()
            .filter(p -> !primitivePropertyToIndex.containsKey(p))
            .map(this::missingRequiredProperty);
    return Stream.concat(primitiveConditions, nonPrimitiveConditions).collect(joining("\n|| "));
  }

  /**
   * The smallest primitive integer type that has at least this many bits, or {@code int} if the
   * number of bits is more than 32.
   */
  private static Class<?> classForBits(int bits) {
    return bits <= 8
        ? byte.class
        : bits <= 16
            ? short.class : int.class;
  }

  private static String cast(Class<?> type, String number) {
    return (type == int.class) ? number : ("(" + type + ") " + number);
  }

  @VisibleForTesting
  static String hex(int number) {
    if (number >= 0) {
      if (number < 10) {
        return Integer.toHexString(number);
      }
      if (number <= 0xffff) {
        return "0x" + Integer.toHexString(number);
      }
    }
    // It's harder to tell 0x7fffffff from 0x7ffffff than to tell 0x7fff_ffff from 0x7ff_ffff.
    String lowNybble = Integer.toHexString(number & 0xffff);
    String pad = "000".substring(lowNybble.length() - 1);
    return "0x" + Integer.toHexString(number >>> 16) + "_" + pad + lowNybble;
  }
}
