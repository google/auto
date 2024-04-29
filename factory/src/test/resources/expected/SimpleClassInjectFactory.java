package tests;

import javax.annotation.processing.Generated;
import javax.inject.Inject;

@Generated(
    value = "com.google.auto.factory.processor.AutoFactoryProcessor",
    comments = "https://github.com/google/auto/tree/main/factory"
)
public final class SimpleClassInjectFactory {
  private final String twoProvider;

  @Inject
  public SimpleClassInjectFactory(String twoProvider) {
    this.twoProvider = checkNotNull(twoProvider, 1, 1);
  }

  public SimpleClassInject create(String one) {
    return new SimpleClassInject(checkNotNull(one, 1, 2), checkNotNull(twoProvider, 2, 2));
  }

  private static <T> T checkNotNull(T reference, int argumentNumber, int argumentCount) {
    if (reference == null) {
      throw new NullPointerException("@AutoFactory method argument is null but is not marked @Nullable. Argument " + argumentNumber + " of " + argumentCount);
    }
    return reference;
  }
}