package tests;

import javax.annotation.Generated;
import javax.inject.Inject;

@Generated(value = "auto-factory")
final class CustomNamedFactory {
  @Inject CustomNamedFactory() {}
  
  SimpleClass create() {
    return new SimpleClass();
  }
}
