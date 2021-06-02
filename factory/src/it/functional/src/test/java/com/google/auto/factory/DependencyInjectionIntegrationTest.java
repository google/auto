package com.google.auto.factory;

import static com.google.common.truth.Truth.assertThat;

import com.google.auto.factory.GenericFoo.DepE;
import com.google.auto.factory.GenericFoo.IntAndStringAccessor;
import com.google.auto.factory.otherpackage.OtherPackage;
import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DependencyInjectionIntegrationTest {

  private static final IntAndStringAccessor INT_AND_STRING_ACCESSOR = new IntAndStringAccessor() {};

  @Test
  public void daggerInjectedFactory() {
    FooFactory fooFactory = DaggerFactoryComponent.create().factory();
    Foo one = fooFactory.create("A");
    Foo two = fooFactory.create("B");
    assertThat(one.name()).isEqualTo("A");
    assertThat(one.dependency()).isNotNull();
    assertThat(one.dependencyProvider()).isNotNull();
    assertThat(one.dependencyProvider().get()).isInstanceOf(QualifiedDependencyImpl.class);
    assertThat(one.primitive()).isEqualTo(1);
    assertThat(one.qualifiedPrimitive()).isEqualTo(2);
    assertThat(two.name()).isEqualTo("B");
    assertThat(two.dependency()).isNotNull();
    assertThat(two.dependency()).isNotEqualTo(one.dependency());
    assertThat(two.dependencyProvider()).isNotNull();
    assertThat(two.dependencyProvider().get()).isInstanceOf(QualifiedDependencyImpl.class);
    assertThat(two.primitive()).isEqualTo(1);
    assertThat(two.qualifiedPrimitive()).isEqualTo(2);
  }

  @Test
  public void daggerInjectedGenericFactory() {
    GenericFooFactory<Number> genericFooFactory =
        DaggerFactoryComponent.create().generatedFactory();
    GenericFoo<Number, ImmutableList<Long>, String, DepE> three =
        genericFooFactory.create(ImmutableList.of(3L), INT_AND_STRING_ACCESSOR, DepE.VALUE_1);
    ArrayList<Double> intAndStringAccessorArrayList = new ArrayList<>();
    intAndStringAccessorArrayList.add(4.0);
    GenericFoo<Number, ArrayList<Double>, Long, DepE> four =
        genericFooFactory.create(
            intAndStringAccessorArrayList, INT_AND_STRING_ACCESSOR, DepE.VALUE_2);
    assertThat(three.getDepA()).isEqualTo(3);
    ImmutableList<Long> unusedLongList = three.getDepB();
    assertThat(three.getDepB()).containsExactly(3L);
    assertThat(three.getDepDIntAccessor()).isEqualTo(INT_AND_STRING_ACCESSOR);
    assertThat(three.getDepDStringAccessor()).isEqualTo(INT_AND_STRING_ACCESSOR);
    assertThat(three.passThrough("value")).isEqualTo("value");
    assertThat(three.getDepE()).isEqualTo(DepE.VALUE_1);
    assertThat(four.getDepA()).isEqualTo(3);
    ArrayList<Double> unusedDoubleList = four.getDepB();
    assertThat(four.getDepB()).isInstanceOf(ArrayList.class);
    assertThat(four.getDepB()).containsExactly(4.0);
    assertThat(four.getDepDIntAccessor()).isEqualTo(INT_AND_STRING_ACCESSOR);
    assertThat(four.getDepDStringAccessor()).isEqualTo(INT_AND_STRING_ACCESSOR);
    assertThat(four.passThrough(5L)).isEqualTo(5L);
    assertThat(four.getDepE()).isEqualTo(DepE.VALUE_2);
  }

  @Test
  public void daggerInjectedPackageSpanningFactory() {
    FactoryComponent component = DaggerFactoryComponent.create();
    ReferencePackageFactory referencePackageFactory = component.referencePackageFactory();
    ReferencePackage referencePackage = referencePackageFactory.create(5);
    OtherPackage otherPackage = referencePackage.otherPackage();
    assertThat(otherPackage.referencePackageFactory()).isNotSameInstanceAs(referencePackageFactory);
    assertThat(otherPackage.random()).isEqualTo(5);
  }

  @Test
  public void guiceInjectedFactory() {
    FooFactory fooFactory = Guice.createInjector(new GuiceModule()).getInstance(FooFactory.class);
    Foo one = fooFactory.create("A");
    Foo two = fooFactory.create("B");
    assertThat(one.name()).isEqualTo("A");
    assertThat(one.dependency()).isNotNull();
    assertThat(one.dependencyProvider()).isNotNull();
    assertThat(one.dependencyProvider().get()).isInstanceOf(QualifiedDependencyImpl.class);
    assertThat(one.primitive()).isEqualTo(1);
    assertThat(one.qualifiedPrimitive()).isEqualTo(2);
    assertThat(two.name()).isEqualTo("B");
    assertThat(two.dependency()).isNotNull();
    assertThat(two.dependency()).isNotEqualTo(one.dependency());
    assertThat(two.dependencyProvider()).isNotNull();
    assertThat(two.dependencyProvider().get()).isInstanceOf(QualifiedDependencyImpl.class);
    assertThat(two.primitive()).isEqualTo(1);
    assertThat(two.qualifiedPrimitive()).isEqualTo(2);
  }

  @Test
  public void guiceInjectedGenericFactory() {
    GenericFooFactory<Number> genericFooFactory =
        Guice.createInjector(new GuiceModule())
            .getInstance(Key.get(new TypeLiteral<GenericFooFactory<Number>>() {}));
    GenericFoo<Number, ImmutableList<Long>, String, DepE> three =
        genericFooFactory.create(ImmutableList.of(3L), INT_AND_STRING_ACCESSOR, DepE.VALUE_1);
    ArrayList<Double> intAndStringAccessorArrayList = new ArrayList<>();
    intAndStringAccessorArrayList.add(4.0);
    GenericFoo<Number, ArrayList<Double>, Long, DepE> four =
        genericFooFactory.create(
            intAndStringAccessorArrayList, INT_AND_STRING_ACCESSOR, DepE.VALUE_2);
    assertThat(three.getDepA()).isEqualTo(3);
    ImmutableList<Long> unusedLongList = three.getDepB();
    assertThat(three.getDepB()).containsExactly(3L);
    assertThat(three.getDepDIntAccessor()).isEqualTo(INT_AND_STRING_ACCESSOR);
    assertThat(three.getDepDStringAccessor()).isEqualTo(INT_AND_STRING_ACCESSOR);
    assertThat(three.passThrough("value")).isEqualTo("value");
    assertThat(three.getDepE()).isEqualTo(DepE.VALUE_1);
    assertThat(four.getDepA()).isEqualTo(3);
    ArrayList<Double> unusedDoubleList = four.getDepB();
    assertThat(four.getDepB()).containsExactly(4.0);
    assertThat(four.getDepDIntAccessor()).isEqualTo(INT_AND_STRING_ACCESSOR);
    assertThat(four.getDepDStringAccessor()).isEqualTo(INT_AND_STRING_ACCESSOR);
    assertThat(four.passThrough(5L)).isEqualTo(5L);
    assertThat(four.getDepE()).isEqualTo(DepE.VALUE_2);
  }

  @Test
  public void guiceInjectedPackageSpanningFactory() {
    ReferencePackageFactory referencePackageFactory =
        Guice.createInjector(new GuiceModule()).getInstance(ReferencePackageFactory.class);
    ReferencePackage referencePackage = referencePackageFactory.create(5);
    OtherPackage otherPackage = referencePackage.otherPackage();
    assertThat(otherPackage.referencePackageFactory()).isNotSameInstanceAs(referencePackageFactory);
    assertThat(otherPackage.random()).isEqualTo(5);
  }
}
