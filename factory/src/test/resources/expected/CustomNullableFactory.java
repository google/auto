package tests;

import javax.annotation.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(
  value = "com.google.auto.factory.processor.AutoFactoryProcessor",
  comments = "https://github.com/google/auto/tree/master/factory"
)
final class CustomNullableFactory {

  private final Provider<Object> objectProvider;

  @Inject
  CustomNullableFactory(Provider<Object> objectProvider) {
    this.objectProvider = objectProvider;
  }

  // TODO(ronshapiro): fully-qualified String is fixed in JavaPoet 1.6
  CustomNullable create(@CustomNullable.Nullable java.lang.String string) {
    return new CustomNullable(string, objectProvider.get());
  }
}
