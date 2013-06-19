package tests;

import javax.annotation.Generated;

import com.google.common.base.Supplier;

@Generated(value = "auto-factory")
final class SimpleClassImplementingFactory implements Supplier<SimpleClassImplementing> {
  @Override public SimpleClassImplementing get() {
    return new SimpleClassImplementing();
  }
}
