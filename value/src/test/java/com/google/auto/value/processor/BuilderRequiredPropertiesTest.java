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
import static com.google.auto.common.MoreStreams.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.auto.value.processor.AutoValueishProcessor.Property;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.testing.compile.CompilationRule;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BuilderRequiredProperties}. */
@RunWith(JUnit4.class)
public final class BuilderRequiredPropertiesTest {
  @ClassRule public static final CompilationRule compilationRule = new CompilationRule();

  private TypeMirror intType;
  private TypeMirror stringType;

  @Before
  public void initTypes() {
    intType = compilationRule.getTypes().getPrimitiveType(TypeKind.INT);
    stringType = compilationRule.getElements().getTypeElement("java.lang.String").asType();
  }

  @Test
  public void fieldDeclarations() {
    assertThat(fieldDeclarations(0)).isEmpty();
    assertThat(fieldDeclarations(1)).containsExactly("private byte set$0;");
    assertThat(fieldDeclarations(8)).containsExactly("private byte set$0;");
    assertThat(fieldDeclarations(9)).containsExactly("private short set$0;");
    assertThat(fieldDeclarations(16)).containsExactly("private short set$0;");
    assertThat(fieldDeclarations(17)).containsExactly("private int set$0;");
    assertThat(fieldDeclarations(32)).containsExactly("private int set$0;");
    assertThat(fieldDeclarations(33)).containsExactly("private int set$0;", "private byte set$1;");
    assertThat(fieldDeclarations(40)).containsExactly("private int set$0;", "private byte set$1;");
    assertThat(fieldDeclarations(41)).containsExactly("private int set$0;", "private short set$1;");
    assertThat(fieldDeclarations(48)).containsExactly("private int set$0;", "private short set$1;");
    assertThat(fieldDeclarations(49)).containsExactly("private int set$0;", "private int set$1;");
    assertThat(fieldDeclarations(64)).containsExactly("private int set$0;", "private int set$1;");
    assertThat(fieldDeclarations(65))
        .containsExactly("private int set$0;", "private int set$1;", "private byte set$2;");
    assertThat(fieldDeclarations(144))
        .containsExactly(
            "private int set$0;",
            "private int set$1;",
            "private int set$2;",
            "private int set$3;",
            "private short set$4;");
  }

  private ImmutableList<String> fieldDeclarations(int size) {
    return builderRequiredProperties(size).getFieldDeclarations();
  }

  @Test
  public void initToAllSet() {
    assertThat(initToAllSet(0)).isEmpty();
    assertThat(initToAllSet(1)).containsExactly("set$0 = (byte) 1;");
    assertThat(initToAllSet(8)).containsExactly("set$0 = (byte) 0xff;");
    assertThat(initToAllSet(9)).containsExactly("set$0 = (short) 0x1ff;");
    assertThat(initToAllSet(16)).containsExactly("set$0 = (short) 0xffff;");
    assertThat(initToAllSet(17)).containsExactly("set$0 = 0x1_ffff;");
    assertThat(initToAllSet(31)).containsExactly("set$0 = 0x7fff_ffff;");
    assertThat(initToAllSet(32)).containsExactly("set$0 = -1;");
    assertThat(initToAllSet(33)).containsExactly("set$0 = -1;", "set$1 = (byte) 1;");
    assertThat(initToAllSet(63)).containsExactly("set$0 = -1;", "set$1 = 0x7fff_ffff;");
    assertThat(initToAllSet(64)).containsExactly("set$0 = -1;", "set$1 = -1;");
    assertThat(initToAllSet(144))
        .containsExactly(
            "set$0 = -1;", "set$1 = -1;", "set$2 = -1;", "set$3 = -1;", "set$4 = (short) 0xffff;");
  }

  private ImmutableList<String> initToAllSet(int size) {
    return builderRequiredProperties(size).getInitToAllSet();
  }

  @Test
  public void markAsSet_reference() {
    BuilderRequiredProperties onlyString = builderRequiredProperties(0);
    Property stringProperty = Iterables.getOnlyElement(onlyString.getRequiredProperties());
    assertThat(onlyString.markAsSet(stringProperty)).isEmpty();
  }

  @Test
  public void markAsSet_byte() {
    BuilderRequiredProperties builderRequiredProperties = builderRequiredProperties(8);
    ImmutableList<Property> primitives = requiredPrimitiveProperties(builderRequiredProperties);
    assertThat(primitives).hasSize(8);
    assertThat(builderRequiredProperties.markAsSet(primitives.get(0)))
        .isEqualTo("set$0 |= (byte) 1;");
    assertThat(builderRequiredProperties.markAsSet(primitives.get(7)))
        .isEqualTo("set$0 |= (byte) 0x80;");
  }

  @Test
  public void markAsSet_short() {
    BuilderRequiredProperties builderRequiredProperties = builderRequiredProperties(16);
    ImmutableList<Property> primitives = requiredPrimitiveProperties(builderRequiredProperties);
    assertThat(primitives).hasSize(16);
    assertThat(builderRequiredProperties.markAsSet(primitives.get(0)))
        .isEqualTo("set$0 |= (short) 1;");
    assertThat(builderRequiredProperties.markAsSet(primitives.get(7)))
        .isEqualTo("set$0 |= (short) 0x80;");
    assertThat(builderRequiredProperties.markAsSet(primitives.get(15)))
        .isEqualTo("set$0 |= (short) 0x8000;");
  }

  @Test
  public void markAsSet_int() {
    BuilderRequiredProperties builderRequiredProperties = builderRequiredProperties(32);
    ImmutableList<Property> primitives = requiredPrimitiveProperties(builderRequiredProperties);
    assertThat(primitives).hasSize(32);
    assertThat(builderRequiredProperties.markAsSet(primitives.get(0))).isEqualTo("set$0 |= 1;");
    assertThat(builderRequiredProperties.markAsSet(primitives.get(31)))
        .isEqualTo("set$0 |= 0x8000_0000;");
  }

  @Test
  public void markAsSet_intPlusByte() {
    BuilderRequiredProperties builderRequiredProperties = builderRequiredProperties(34);
    ImmutableList<Property> primitives = requiredPrimitiveProperties(builderRequiredProperties);
    assertThat(primitives).hasSize(34);
    assertThat(builderRequiredProperties.markAsSet(primitives.get(0))).isEqualTo("set$0 |= 1;");
    assertThat(builderRequiredProperties.markAsSet(primitives.get(31)))
        .isEqualTo("set$0 |= 0x8000_0000;");
    assertThat(builderRequiredProperties.markAsSet(primitives.get(32)))
        .isEqualTo("set$1 |= (byte) 1;");
    assertThat(builderRequiredProperties.markAsSet(primitives.get(33)))
        .isEqualTo("set$1 |= (byte) 2;");
  }

  @Test
  public void missingRequiredProperty_reference() {
    BuilderRequiredProperties onlyString = builderRequiredProperties(0);
    Property stringProperty = Iterables.getOnlyElement(onlyString.getRequiredProperties());
    assertThat(onlyString.missingRequiredProperty(stringProperty)).isEqualTo("this.string == null");
  }

  @Test
  public void missingRequiredProperty_byte() {
    BuilderRequiredProperties builderRequiredProperties = builderRequiredProperties(8);
    ImmutableList<Property> primitives = requiredPrimitiveProperties(builderRequiredProperties);
    assertThat(primitives).hasSize(8);
    assertThat(builderRequiredProperties.missingRequiredProperty(primitives.get(0)))
        .isEqualTo("(set$0 & 1) == 0");
    assertThat(builderRequiredProperties.missingRequiredProperty(primitives.get(7)))
        .isEqualTo("(set$0 & 0x80) == 0");
  }

  @Test
  public void missingRequiredProperty_short() {
    BuilderRequiredProperties builderRequiredProperties = builderRequiredProperties(16);
    ImmutableList<Property> primitives = requiredPrimitiveProperties(builderRequiredProperties);
    assertThat(primitives).hasSize(16);
    assertThat(builderRequiredProperties.missingRequiredProperty(primitives.get(0)))
        .isEqualTo("(set$0 & 1) == 0");
    assertThat(builderRequiredProperties.missingRequiredProperty(primitives.get(7)))
        .isEqualTo("(set$0 & 0x80) == 0");
    assertThat(builderRequiredProperties.missingRequiredProperty(primitives.get(15)))
        .isEqualTo("(set$0 & 0x8000) == 0");
  }

  @Test
  public void missingRequiredProperty_int() {
    BuilderRequiredProperties builderRequiredProperties = builderRequiredProperties(32);
    ImmutableList<Property> primitives = requiredPrimitiveProperties(builderRequiredProperties);
    assertThat(primitives).hasSize(32);
    assertThat(builderRequiredProperties.missingRequiredProperty(primitives.get(0)))
        .isEqualTo("(set$0 & 1) == 0");
    assertThat(builderRequiredProperties.missingRequiredProperty(primitives.get(31)))
        .isEqualTo("(set$0 & 0x8000_0000) == 0");
  }

  @Test
  public void missingRequiredProperty_intPlusByte() {
    BuilderRequiredProperties builderRequiredProperties = builderRequiredProperties(34);
    ImmutableList<Property> primitives = requiredPrimitiveProperties(builderRequiredProperties);
    assertThat(primitives).hasSize(34);
    assertThat(builderRequiredProperties.missingRequiredProperty(primitives.get(0)))
        .isEqualTo("(set$0 & 1) == 0");
    assertThat(builderRequiredProperties.missingRequiredProperty(primitives.get(31)))
        .isEqualTo("(set$0 & 0x8000_0000) == 0");
    assertThat(builderRequiredProperties.missingRequiredProperty(primitives.get(32)))
        .isEqualTo("(set$1 & 1) == 0");
    assertThat(builderRequiredProperties.missingRequiredProperty(primitives.get(33)))
        .isEqualTo("(set$1 & 2) == 0");
  }

  @Test
  public void noValueToGet_noDefaults() {
    BuilderRequiredProperties builderRequiredProperties = builderRequiredProperties(34);
    ImmutableList<Property> primitives = requiredPrimitiveProperties(builderRequiredProperties);
    assertThat(primitives).hasSize(34);
    for (Property property : primitives) {
      assertWithMessage("For property %s", property)
          .that(builderRequiredProperties.noValueToGet(property))
          .isEqualTo(builderRequiredProperties.missingRequiredProperty(property));
    }
  }

  @Test
  public void noValueToGet_withDefaults() {
    ImmutableSet<Property> allProperties = fakePropertiesWithDefaults(0);
    BuilderRequiredProperties builderRequiredProperties =
        BuilderRequiredProperties.of(allProperties, /* requiredProperties= */ ImmutableSet.of());
    ImmutableList<Property> allPropertiesList = allProperties.asList();
    assertThat(allPropertiesList.get(0).hasDefault()).isFalse();
    assertThat(allPropertiesList.get(1).hasDefault()).isTrue();
    assertThat(allPropertiesList.get(2).hasDefault()).isTrue();
    assertThat(builderRequiredProperties.noValueToGet(allPropertiesList.get(0))).isNull();
    assertThat(builderRequiredProperties.noValueToGet(allPropertiesList.get(1)))
        .isEqualTo("(set$0 & 2) == 0");
    assertThat(builderRequiredProperties.noValueToGet(allPropertiesList.get(2)))
        .isEqualTo("(set$0 & 4) == 0");
  }

  @Test
  public void getAnyMissing() {
    assertThat(builderRequiredProperties(0).getAnyMissing()).isEqualTo("this.string == null");
    assertThat(builderRequiredProperties(1).getAnyMissing())
        .isEqualTo("set$0 != 1\n|| this.string == null");
    assertThat(builderRequiredProperties(16).getAnyMissing())
        .isEqualTo("set$0 != -1\n|| this.string == null");
    assertThat(builderRequiredProperties(17).getAnyMissing())
        .isEqualTo("set$0 != 0x1_ffff\n|| this.string == null");
    assertThat(builderRequiredProperties(31).getAnyMissing())
        .isEqualTo("set$0 != 0x7fff_ffff\n|| this.string == null");
    assertThat(builderRequiredProperties(32).getAnyMissing())
        .isEqualTo("set$0 != -1\n|| this.string == null");
    assertThat(builderRequiredProperties(33).getAnyMissing())
        .isEqualTo("set$0 != -1\n|| set$1 != 1\n|| this.string == null");
    assertThat(builderRequiredProperties(64).getAnyMissing())
        .isEqualTo("set$0 != -1\n|| set$1 != -1\n|| this.string == null");
  }

  @Test
  public void getAnyMissing_withDefaults() {
    assertThat(builderRequiredPropertiesWithDefaults(0).getAnyMissing())
        .isEqualTo("(~set$0 & 1) != 0");
    assertThat(builderRequiredPropertiesWithDefaults(1).getAnyMissing())
        .isEqualTo("(~set$0 & 3) != 0");
    assertThat(builderRequiredPropertiesWithDefaults(15).getAnyMissing())
        .isEqualTo("(~set$0 & 0xffff) != 0");
    assertThat(builderRequiredPropertiesWithDefaults(16).getAnyMissing())
        .isEqualTo("(~set$0 & 0x1_ffff) != 0");
    assertThat(builderRequiredPropertiesWithDefaults(17).getAnyMissing())
        .isEqualTo("(~set$0 & 0x3_ffff) != 0");

    // TODO(emcmanus): remove the no-op `& 0xfff_ffff`
    assertThat(builderRequiredPropertiesWithDefaults(31).getAnyMissing())
        .isEqualTo("(~set$0 & 0xffff_ffff) != 0");
    assertThat(builderRequiredPropertiesWithDefaults(32).getAnyMissing())
        .isEqualTo("(~set$0 & 0xffff_ffff) != 0\n|| (~set$1 & 1) != 0");
    assertThat(builderRequiredPropertiesWithDefaults(33).getAnyMissing())
        .isEqualTo("(~set$0 & 0xffff_ffff) != 0\n|| (~set$1 & 3) != 0");
    assertThat(builderRequiredPropertiesWithDefaults(63).getAnyMissing())
        .isEqualTo("(~set$0 & 0xffff_ffff) != 0\n|| (~set$1 & 0xffff_ffff) != 0");
    assertThat(builderRequiredPropertiesWithDefaults(64).getAnyMissing())
        .isEqualTo(
            "(~set$0 & 0xffff_ffff) != 0\n|| (~set$1 & 0xffff_ffff) != 0\n|| (~set$2 & 1) != 0");
  }

  @Test
  public void hex() {
    assertThat(BuilderRequiredProperties.hex(0x0)).isEqualTo("0");
    assertThat(BuilderRequiredProperties.hex(0x1)).isEqualTo("1");
    assertThat(BuilderRequiredProperties.hex(0x9)).isEqualTo("9");
    assertThat(BuilderRequiredProperties.hex(0xa)).isEqualTo("0xa");
    assertThat(BuilderRequiredProperties.hex(0xffff)).isEqualTo("0xffff");
    assertThat(BuilderRequiredProperties.hex(0x1_0000)).isEqualTo("0x1_0000");
    assertThat(BuilderRequiredProperties.hex(0x7fff_ffff)).isEqualTo("0x7fff_ffff");
    assertThat(BuilderRequiredProperties.hex(0xffff_ffff)).isEqualTo("0xffff_ffff");
  }

  private ImmutableList<Property> requiredPrimitiveProperties(
      BuilderRequiredProperties builderRequiredProperties) {
    return builderRequiredProperties.getRequiredProperties().stream()
        .filter(p -> p.getTypeMirror().getKind().isPrimitive())
        .collect(toImmutableList());
  }

  private BuilderRequiredProperties builderRequiredProperties(int primitiveCount) {
    ImmutableSet<Property> properties = fakeProperties(primitiveCount);
    return BuilderRequiredProperties.of(properties, properties);
  }

  private BuilderRequiredProperties builderRequiredPropertiesWithDefaults(int primitiveCount) {
    ImmutableSet<Property> allProperties = fakePropertiesWithDefaults(primitiveCount);
    ImmutableSet<Property> requiredProperties =
        allProperties.stream().filter(p -> !p.hasDefault()).collect(toImmutableSet());
    return BuilderRequiredProperties.of(allProperties, requiredProperties);
  }

  private ImmutableSet<Property> fakeProperties(int primitiveCount) {
    return Stream.concat(
            Stream.of(fakeProperty("string", stringType, /* hasDefault= */ false)),
            IntStream.range(0, primitiveCount)
                .mapToObj(i -> fakeProperty("x" + i, intType, /* hasDefault= */ false)))
        .collect(toImmutableSet());
  }

  private ImmutableSet<Property> fakePropertiesWithDefaults(int primitiveCount) {
    ImmutableSet<Property> requiredProperties = fakeProperties(primitiveCount);
    return ImmutableSet.<Property>builder()
        .addAll(requiredProperties)
        .add(fakeProperty("stringWithDefault", stringType, /* hasDefault= */ true))
        .add(fakeProperty("intWithDefault", intType, /* hasDefault= */ true))
        .build();
  }

  private Property fakeProperty(String name, TypeMirror type, boolean hasDefault) {
    return new Property(
        /* name= */ name,
        /* identifier= */ name,
        /* type= */ type.toString(),
        /* typeMirror= */ type,
        /* nullableAnnotation= */ Optional.empty(),
        /* nullables= */ Nullables.fromMethods(null, ImmutableList.of()),
        /* getter= */ name,
        /* maybeBuilderInitializer= */ Optional.empty(),
        /* hasDefault= */ hasDefault);
  }
}
