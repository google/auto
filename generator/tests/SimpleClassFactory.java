package tests;

import javax.annotation.Generated;
import javax.inject.Inject;

@Generated(value = "auto-factory")
final class SimpleClassFactory {
  @Inject SimpleClassFactory() {}
  
  SimpleClass create() {
    return new SimpleClass();
  }
}
