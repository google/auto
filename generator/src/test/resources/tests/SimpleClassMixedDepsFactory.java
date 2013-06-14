package tests;

import javax.annotation.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(value = "auto-factory")
final class SimpleClassMixedDepsFactory {
  private final Provider<String> providedDepAProvider;
  
  @Inject SimpleClassMixedDepsFactory(
      @AQualifier Provider<String> providedDepAProvider) {
    this.providedDepAProvider = providedDepAProvider;
  }
  
  SimpleClassMixedDeps create(String depB) {
    return new SimpleClassMixedDeps(providedDepAProvider.get(), depB);
  }
}
