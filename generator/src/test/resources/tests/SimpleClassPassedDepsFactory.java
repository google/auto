package tests;

import javax.annotation.Generated;
import javax.inject.Inject;

@Generated(value = "auto-factory")
final class SimpleClassPassedDepsFactory {
  @Inject SimpleClassPassedDepsFactory() {}
  
  SimpleClassPassedDeps create(String depA, String depB) {
    return new SimpleClassPassedDeps(depA, depB);
  }
}
