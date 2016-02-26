package tests;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

@AutoFactory
final class CustomNullable {

  private final String string;
  private final Object object;

  CustomNullable(
      @CustomNullable.Nullable String string, @CustomNullable.Nullable @Provided Object object) {
    this.string = string;
    this.object = object;
  }

  @interface Nullable {}
}
