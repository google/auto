package com.google.auto.factory;

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.Guice;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DependencyInjectionIntegrationTest {
  @Test public void daggerInjectedFactory() {
    FactoryGeneratedFactory factoryGeneratedFactory = DaggerFactoryComponent.create().factory();
    FactoryGenerated one = factoryGeneratedFactory.create("A");
    FactoryGenerated two = factoryGeneratedFactory.create("B");
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

  @Test public void guiceInjectedFactory() {
    FactoryGeneratedFactory factoryGeneratedFactory =
        Guice.createInjector(new GuiceModule())
            .getInstance(FactoryGeneratedFactory.class);
    FactoryGenerated one = factoryGeneratedFactory.create("A");
    FactoryGenerated two = factoryGeneratedFactory.create("B");
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
}
