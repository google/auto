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
import static java.lang.Math.min;
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
 * <p>Additionally, for Kotlin constructors with default parameters, we track exactly which
 * properties have been set so we can invoke the constructor thas has a bitmask indicating the
 * properties to be defaulted.
 *
 * <p>The public methods in this class are accessed reflectively from the {@code builder.vm}
 * template. In that template, {@code $builderRequiredProperties} references an instance of this
 * class corresponding to the builder being generated. A reference like {@code
 * $builderRequiredProperties.markAsSet($p)} calls the method {@link #markAsSet} with the given
 * parameter. A reference like {@code $builderRequiredProperties.requiredProperties} is shorthand
 * for {@link #getRequiredProperties() $builderRequiredProperties.getProperties()}.
 */
public abstract class BuilderRequiredProperties {
  static final BuilderRequiredProperties EMPTY = of(ImmutableSet.of(), ImmutableSet.of());

  // Bitmasks are a bit fiddly because we use them in a couple of ways. The first way is where
  // we are just using the bitmasks to track which primitive properties have been set. Then if
  // we have three primitive properties we can just check that the bitmask is (1 << 3) - 1, the
  // all-ones bitmask, to see that they have all been set. The second way is when we are also
  // handling optional Kotlin parameters. Then the bitmasks are different: we have one bit for every
  // property, primitive or not, optional or not. To check that the required primitive properties
  // have been set, we need to check specific bits. For example if properties 1 and 3 are primitive
  // then we need to check (~set$0 & ((1 << 1) | (1 << 3))) == 0. That tests that bits 1 and 3 are
  // set, since if either of them is 0 then it will be 1 in ~set$0 and will survive the AND.  We can
  // also isolate the bits representing optional Kotlin parameters similarly, and pass those to the
  // special Kotlin constructor that handles default parameters. Kotlin uses bitmasks for that too:
  // they have one bit per parameter, optional or not, but only the bits for optional parameters
  // matter. We isolate those bits with `&` operations similar to what was described for primitive
  // properties.  We also need the all-ones bitmask to implement a "copy constructor" builder, which
  // starts out with all properties set.

  /** All required properties. */
  final ImmutableSet<Property> requiredProperties;

  /**
   * The bit index for each tracked property. Properties are tracked if they are primitive, or if
   * this is a Kotlin constructor with default parameters. Non-tracked properties do not appear in
   * this map.
   */
  final ImmutableMap<Property, Integer> trackedPropertyToIndex;

  /**
   * The integer fields that store the bitmask. In the usual case, where there are ≤32 tracked
   * properties, we can pack the bitmask into one integer field. Its type is the smallest one that
   * fits the required number of bits, for example {@code byte} if there are ≤8 tracked properties.
   *
   * <p>If there are {@literal >32} tracked properties, we will pack them into as few integer fields
   * as possible. For example if there are 75 tracked properties (this can happen) then we will put
   * numbers 0 to 31 in an {@code int}, 32 to 63 in a second {@code int}, and 64 to 75 in a {@code
   * short}.
   *
   * <p>When there are {@literal >32} tracked properties, we could potentially pack them better if
   * we used {@code long}. But sometimes AutoValue code gets translated into JavaScript, which
   * doesn't handle long values natively. By the time you have that many properties you are probably
   * not going to notice the difference between 5 ints or 2 longs plus an int.
   */
  final ImmutableList<BitmaskField> bitmaskFields;

  /**
   * Represents a field in which we will record which tracked properties from a certain set have
   * been given a value.
   */
  private static class BitmaskField {
    final Class<?> type;
    final String name;

    /**
     * The source representation of the value this field has when all properties have been given a
     * value.
     */
    final String allSetBitmask;

    /**
     * The source representation of the value this field has when all required properties have been
     * given a value.
     */
    final String allRequiredBitmask;

    BitmaskField(Class<?> type, String name, String allSetBitmask, String allRequiredBitmask) {
      this.type = type;
      this.name = name;
      this.allSetBitmask = allSetBitmask;
      this.allRequiredBitmask = allRequiredBitmask;
    }
  }

  static BuilderRequiredProperties of(
      ImmutableSet<Property> allProperties, ImmutableSet<Property> requiredProperties) {
    boolean hasDefaults = allProperties.stream().anyMatch(Property::hasDefault);
    return hasDefaults
        ? new WithDefaults(allProperties, requiredProperties)
        : new NoDefaults(requiredProperties);
  }

  private BuilderRequiredProperties(
      ImmutableSet<Property> requiredProperties, ImmutableList<Property> trackedProperties) {
    this.requiredProperties = requiredProperties;

    int trackedCount = trackedProperties.size();
    this.trackedPropertyToIndex =
        IntStream.range(0, trackedCount)
            .boxed()
            .collect(toImmutableMap(trackedProperties::get, i -> i));

    this.bitmaskFields =
        IntStream.range(0, (trackedCount + 31) / 32)
            .mapToObj(
                i -> {
                  int bitBase = i * 32;
                  int remainingBits = trackedCount - bitBase;
                  Class<?> type = classForBits(remainingBits);
                  String name = "set$" + i;
                  String allSetBitmask =
                      (remainingBits >= 32) ? "-1" : hex((1 << remainingBits) - 1);
                  String allRequiredBitmask =
                      allRequiredBitmask(trackedProperties, bitBase, remainingBits);
                  return new BitmaskField(type, name, allSetBitmask, allRequiredBitmask);
                })
            .collect(toImmutableList());
  }

  abstract String allRequiredBitmask(
      ImmutableList<Property> trackedProperties, int bitBase, int remainingBits);

  public ImmutableSet<Property> getRequiredProperties() {
    return requiredProperties;
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
   * Returns code to indicate that all tracked properties have received a value. This is needed in
   * the {@code toBuilder()} constructor, since it assigns to the corresponding fields directly
   * without going through their setters.
   */
  public ImmutableList<String> getInitToAllSet() {
    return bitmaskFields.stream()
        .map(field -> field.name + " = " + cast(field.type, field.allSetBitmask) + ";")
        .collect(toImmutableList());
  }

  /**
   * Returns code to indicate that the given property has been set, if assigning to the property
   * field is not enough. For reference (non-primitive) properties, assignment <i>is</i> enough, but
   * for primitive properties we also need to set a bit in the bitmask.
   */
  public String markAsSet(Property p) {
    Integer index = trackedPropertyToIndex.get(p);
    if (index == null) {
      return "";
    }
    BitmaskField field = bitmaskFields.get(index / 32);
    // This use-case is why Java reduces int shift amounts mod 32. :-)
    return field.name + " |= " + cast(field.type, hex(1 << index)) + ";";
  }

  /**
   * Returns an expression that is true if the given property is required but has not been set.
   * Returns null if the property is not required.
   */
  public String missingRequiredProperty(Property p) {
    return requiredProperties.contains(p) ? propertyNotSet(p) : null;
  }

  /**
   * Returns an expression that is true if the given property has not been given a value. That's
   * only different from {@link #missingRequiredProperty} if the property has a Kotlin default. If
   * so, we don't require it to be set at build time (because Kotlin will supply the default), but
   * we do require it to be set if it is accessed with a getter on the builder. We don't have access
   * to Kotlin parameter defaults so we can't arrange for the builder field to have the same default
   * value. Rather than returning a bogus zero value we say the value is unset.
   */
  public String noValueToGet(Property p) {
    return (requiredProperties.contains(p) || p.hasDefault()) ? propertyNotSet(p) : null;
  }

  private String propertyNotSet(Property p) {
    Integer index = trackedPropertyToIndex.get(p);
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
  public abstract String getAnyMissing();

  /**
   * Returns additional constructor parameters to indicate what properties have been defaulted, or
   * an empty string if there are none.
   */
  public abstract String getDefaultedBitmaskParameters();

  /**
   * The smallest primitive integer type that has at least this many bits, or {@code int} if the
   * number of bits is more than 32.
   */
  private static Class<?> classForBits(int bits) {
    return bits <= 8 ? byte.class : bits <= 16 ? short.class : int.class;
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

  /** Subclass for when there are no Kotlin default properties. */
  private static final class NoDefaults extends BuilderRequiredProperties {
    NoDefaults(ImmutableSet<Property> requiredProperties) {
      super(requiredProperties, primitivePropertiesIn(requiredProperties));
    }

    private static ImmutableList<Property> primitivePropertiesIn(
        ImmutableSet<Property> properties) {
      return properties.stream().filter(p -> p.getKind().isPrimitive()).collect(toImmutableList());
    }

    @Override
    String allRequiredBitmask(
        ImmutableList<Property> trackedProperties, int bitBase, int remainingBits) {
      // We have to be a bit careful with sign-extension. If we're using a byte and
      // the mask is 0xff, then we'll write -1 instead. The comparison set$0 == 0xff
      // would always fail since the byte value gets sign-extended to 0xffff_ffff.
      // We should also write -1 if this is not the last field.
      boolean minusOne = remainingBits >= 32 || remainingBits == 16 || remainingBits == 8;
      return minusOne ? "-1" : hex((1 << remainingBits) - 1);
    }

    /**
     * {@inheritDoc}
     *
     * <p>We check the bitmask for primitive properties, and null checks for non-primitive ones.
     */
    @Override
    public String getAnyMissing() {
      Stream<String> primitiveConditions =
          bitmaskFields.stream().map(field -> field.name + " != " + field.allRequiredBitmask);
      Stream<String> nonPrimitiveConditions =
          requiredProperties.stream()
              .filter(p -> !trackedPropertyToIndex.containsKey(p))
              .map(this::missingRequiredProperty);
      return Stream.concat(primitiveConditions, nonPrimitiveConditions).collect(joining("\n|| "));
    }

    @Override
    public String getDefaultedBitmaskParameters() {
      return "";
    }
  }

  /** Subclass for when there are Kotlin default properties. */
  private static final class WithDefaults extends BuilderRequiredProperties {
    private final ImmutableList<Property> allProperties;

    WithDefaults(ImmutableSet<Property> allProperties, ImmutableSet<Property> requiredProperties) {
      super(requiredProperties, allProperties.asList());
      this.allProperties = allProperties.asList();
    }

    @Override
    String allRequiredBitmask(
        ImmutableList<Property> trackedProperties, int bitBase, int remainingBits) {
      int requiredBits = 0;
      for (int bit = 0; bit < remainingBits; bit++) {
        Property p = trackedProperties.get(bitBase + bit);
        if (requiredProperties.contains(p)) {
          requiredBits |= 1 << bit;
        }
      }
      return hex(requiredBits);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Everything can be checked with bitmask operations. If bit <i>i</i> represents a required
     * property then it must be 1 in the bitmask field. So if we invert it we must get 0, and if we
     * do that for the field as a whole and AND with a bitmask selecting only required properties we
     * should get 0.
     */
    @Override
    public String getAnyMissing() {
      return bitmaskFields.stream()
          .filter(field -> !field.allRequiredBitmask.equals("0"))
          .map(field -> "(~" + field.name + " & " + field.allRequiredBitmask + ") != 0")
          .collect(joining("\n|| "));
    }

    /**
     * {@inheritDoc}
     *
     * <p>When there are default parameters, we're calling the special constructor that has one or
     * more bitmask parameters at the end. Bit <i>i</i> is set if parameter <i>i</i> (zero-origin)
     * has its default value, and then the actual value passed for that parameter is ignored. Our
     * bitmask field has a 1 for any parameter that has been set, meaning it has a 0 for any
     * parameter that has been defaulted. So we need to invert it, and we also want to AND it with a
     * bitmask that selects just the bits for parameters with defaults. (The AND probably isn't
     * strictly necessary, since the constructor code doesn't actually look at those other bits, but
     * it seems cleaner.) If the bitmask for parameters with defaults is 0 then we can just use 0
     * for that bitmask, and if it is ~0 (all 1 bits) then we can skip the AND.
     *
     * <p>That special constructor has an additional dummy parameter of type {@code
     * DefaultConstructorMarker}. We just pass {@code null} to that parameter.
     */
    @Override
    public String getDefaultedBitmaskParameters() {
      ImmutableList.Builder<Integer> defaultedBitmasksBuilder = ImmutableList.builder();
      for (int bitBase = 0; bitBase < allProperties.size(); bitBase += 32) {
        int bitCount = min(32, allProperties.size() - bitBase);
        int defaultedBitmask = 0;
        for (int i = 0; i < bitCount; i++) {
          if (allProperties.get(bitBase + i).hasDefault()) {
            defaultedBitmask |= 1 << i;
          }
        }
        defaultedBitmasksBuilder.add(defaultedBitmask);
      }
      ImmutableList<Integer> defaultedBitmasks = defaultedBitmasksBuilder.build();
      return IntStream.range(0, bitmaskFields.size())
          .mapToObj(
              i -> {
                int defaultedBitmask = defaultedBitmasks.get(i);
                switch (defaultedBitmask) {
                  case 0:
                    return "0";
                  case ~0:
                    return "~" + bitmaskFields.get(i).name;
                  default:
                    return "~" + bitmaskFields.get(i).name + " & " + hex(defaultedBitmask);
                }
              })
          .collect(joining(",\n", ",\n", ",\nnull"));
    }
  }
}
