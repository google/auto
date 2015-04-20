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
    assertThat(two.name()).isEqualTo("B");
    assertThat(two.dependency()).isNotNull();
    assertThat(one.dependency()).isNotEqualTo(two.dependency());
  }

  @Test public void guiceInjectedFactory() {
    FactoryGeneratedFactory factoryGeneratedFactory =
        Guice.createInjector(new GuiceModule())
            .getInstance(FactoryGeneratedFactory.class);
    FactoryGenerated one = factoryGeneratedFactory.create("A");
    FactoryGenerated two = factoryGeneratedFactory.create("B");
    assertThat(one.name()).isEqualTo("A");
    assertThat(one.dependency()).isNotNull();
    assertThat(two.name()).isEqualTo("B");
    assertThat(two.dependency()).isNotNull();
    assertThat(one.dependency()).isNotEqualTo(two.dependency());
  }
}
