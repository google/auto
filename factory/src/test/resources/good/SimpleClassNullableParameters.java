package tests;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import javax.annotation.Nullable;

@AutoFactory
@SuppressWarnings("unused")
final class SimpleClassNullableParameters {
  @Nullable private final String nullable;
  @Nullable private final String qualifiedNullable;
  @Nullable private final String providedNullable;
  @Nullable private final String providedQualifiedNullable;

  // TODO(ronshapiro): with Java 8, test Provider<@Nullable String> parameters and provider fields
  SimpleClassNullableParameters(
      @Nullable String nullable,
      @Nullable @AQualifier String qualifiedNullable,
      @Nullable @Provided String providedNullable,
      @Nullable @Provided @BQualifier String providedQualifiedNullable) {
    this.nullable = nullable;
    this.qualifiedNullable = qualifiedNullable;
    this.providedNullable = providedNullable;
    this.providedQualifiedNullable = providedQualifiedNullable;
  }
}
