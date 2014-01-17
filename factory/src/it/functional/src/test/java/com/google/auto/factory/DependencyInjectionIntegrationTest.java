package com.google.auto.factory;

import static org.truth0.Truth.ASSERT;

import org.junit.Test;

import com.google.inject.Guice;

import dagger.ObjectGraph;

public class DependencyInjectionIntegrationTest {
  @Test public void daggerInjectedFactory() {
    FactoryGeneratedFactory factoryGeneratedFactory =
        ObjectGraph.create(DaggerModule.class).get(FactoryGeneratedFactory.class);
    FactoryGenerated one = factoryGeneratedFactory.create("A");
    FactoryGenerated two = factoryGeneratedFactory.create("B");
    ASSERT.that(one.name()).isEqualTo("A");
    ASSERT.that(one.dependency()).isNotNull();
    ASSERT.that(two.name()).isEqualTo("B");
    ASSERT.that(two.dependency()).isNotNull();
    ASSERT.that(one.dependency()).isNotEqualTo(two.dependency());
  }

  @Test public void guiceInjectedFactory() {
    FactoryGeneratedFactory factoryGeneratedFactory =
        Guice.createInjector(new GuiceModule())
            .getInstance(FactoryGeneratedFactory.class);
    FactoryGenerated one = factoryGeneratedFactory.create("A");
    FactoryGenerated two = factoryGeneratedFactory.create("B");
    ASSERT.that(one.name()).isEqualTo("A");
    ASSERT.that(one.dependency()).isNotNull();
    ASSERT.that(two.name()).isEqualTo("B");
    ASSERT.that(two.dependency()).isNotNull();
    ASSERT.that(one.dependency()).isNotEqualTo(two.dependency());
  }
}
