package com.google.autofactory;

import static com.google.common.base.Preconditions.checkState;

import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

final class FactoryMethodDescriptor {
  private final String factoryName;
  private final String name;
  private final String returnType;
  private final ImmutableSet<Parameter> passedParameters;
  private final ImmutableSet<Parameter> providedParameters;
  private final ImmutableSet<Parameter> creationParameters;

  private FactoryMethodDescriptor(Builder builder) {
    this.factoryName = builder.factoryName.get();
    this.name = builder.name.get();
    this.returnType = builder.returnType.get();
    this.passedParameters = ImmutableSet.copyOf(builder.passedParameters);
    this.providedParameters = ImmutableSet.copyOf(builder.providedParameters);
    this.creationParameters = ImmutableSet.copyOf(builder.creationParameters);
    checkState(creationParameters.equals(Sets.union(passedParameters, providedParameters)));
  }

  String factoryName() {
    return factoryName;
  }

  String name() {
    return name;
  }

  String returnType() {
    return returnType;
  }

  ImmutableSet<Parameter> passedParameters() {
    return passedParameters;
  }

  ImmutableSet<Parameter> providedParameters() {
    return providedParameters;
  }

  ImmutableSet<Parameter> creationParameters() {
    return creationParameters;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("factoryName", factoryName)
        .add("name", name)
        .add("returnType", returnType)
        .add("passed", passedParameters)
        .add("provided", providedParameters)
        .toString();
  }

  static final class Builder {
    private Optional<String> factoryName = Optional.absent();
    private Optional<String> name = Optional.absent();
    private Optional<String> returnType = Optional.absent();
    private final Set<Parameter> passedParameters = Sets.newLinkedHashSet();
    private final Set<Parameter> providedParameters = Sets.newLinkedHashSet();
    private final Set<Parameter> creationParameters = Sets.newLinkedHashSet();

    Builder factoryName(String factoryName) {
      this.factoryName = Optional.of(factoryName);
      return this;
    }

    Builder name(String name) {
      this.name = Optional.of(name);
      return this;
    }

    Builder returnType(String returnType) {
      this.returnType = Optional.of(returnType);
      return this;
    }

    Builder passedParameters(Iterable<Parameter> passedParameters) {
      Iterables.addAll(this.passedParameters, passedParameters);
      return this;
    }

    Builder providedParameters(Iterable<Parameter> providedParameters) {
      Iterables.addAll(this.providedParameters, providedParameters);
      return this;
    }

    Builder creationParameters(Iterable<Parameter> creationParameters) {
      Iterables.addAll(this.creationParameters, creationParameters);
      return this;
    }

    FactoryMethodDescriptor build() {
      return new FactoryMethodDescriptor(this);
    }
  }
}
