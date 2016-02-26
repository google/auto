package tests;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import javax.annotation.Nullable;

@AutoFactory
final class MultipleProvidedParamsSameKey {
  private final String one;
  private final String two;
  private final String three;

  public MultipleProvidedParamsSameKey(
      @Provided String one,
      @Provided String two,
      @Nullable @Provided String three) {
    this.one = one;
    this.two = two;
    this.three = three;
  }
}
