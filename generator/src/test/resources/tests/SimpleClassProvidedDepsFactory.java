package tests;

import javax.annotation.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(value = "auto-factory")
final class SimpleClassProvidedDepsFactory {
  private final Provider<String> providedDepAProvider;
  private final Provider<String> providedDepBProvider;
  
  @Inject SimpleClassMixedDepsFactory(
      @AQualifier Provider<String> providedDepAProvider,
      @BQualifier Provider<String> providedDepBProvider) {
    this.providedDepAProvider = providedDepAProvider;
    this.providedDepBProvider = providedDepBProvider;
  }
  
  SimpleClassProvidedDeps create() {
    return new SimpleClassProvidedDeps(providedDepAProvider.get(), providedDepBProvider.get());
  }
}
