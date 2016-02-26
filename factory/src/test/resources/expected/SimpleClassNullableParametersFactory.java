package tests;

import javax.annotation.Generated;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(
  value = "com.google.auto.factory.processor.AutoFactoryProcessor",
  comments = "https://github.com/google/auto/tree/master/factory"
)
final class SimpleClassNullableParametersFactory {
  private final Provider<String> providedNullableProvider;

  private final Provider<String> providedQualifiedNullableProvider;

  @Inject
  SimpleClassNullableParametersFactory(
      Provider<String> providedNullableProvider,
      @BQualifier Provider<String> providedQualifiedNullableProvider) {
    this.providedNullableProvider = providedNullableProvider;
    this.providedQualifiedNullableProvider = providedQualifiedNullableProvider;
  }

  SimpleClassNullableParameters create(
      @Nullable java.lang.String nullable,
      @Nullable @AQualifier java.lang.String qualifiedNullable) {
    return new SimpleClassNullableParameters(
        nullable,
        qualifiedNullable,
        providedNullableProvider.get(),
        providedQualifiedNullableProvider.get());
  }
}
