# Serializable AutoValue Extension


An [`AutoValue`] extension that enables `@AutoValue` classes with
un-serializable properties to be serializable.

## Usage

To use the [`SerializableAutoValueExtension`] with your `AutoValue` class, the
`AutoValue` class must:

1.  Implement `java.io.Serializable`.
2.  Be annotated with `@SerializableAutoValue`.

## Example

The following `AutoValue` class is un-serializable:

```java
@AutoValue
public abstract class Foo implements Serializable {

  public static Foo create(Optional<String> x) {
    return new AutoValue_Foo(x);
  }

  // java.util.Optional is not serializable.
  abstract Optional<String> x;
}
```

This is because `java.util.Optional` is un-serializable. We can make `Foo`
serializable by using the [`SerializableAutoValueExtension`].

```java
@SerializableAutoValue  // This annotation activates the extension.
@AutoValue
public abstract class Foo implements Serializable {
  ...
}
```

## Details

For the example class `Foo` above, `SerializableAutoValueExtension` will
generate the following code:

```java
@Generated("SerializableAutoValueExtension")
final class AutoValue_Foo extends $AutoValue_Foo {

  // Instead of serializing AutoValue_Foo, we delegate serialization to a
  // proxy object.
  Object writeReplace() throws ObjectStreamException {
    return new Proxy$(this.x);
  }

  // When serializing, AutoValue_Foo's values are written to Proxy$.
  // When de-serializing, Proxy$'s values used to create a new instance of
  // AutoValue_Foo
  static class Proxy$ implements Serializable {

    private String x;

    // During serialization, un-wrap the Optional field.
    Proxy$(Optional<String> x) {
      this.x = x.orElse(null);
    }

    // During de-serialization, re-create AutoValue_Foo.
    Object readResolve() throws ObjectStreamException {
      return new AutoValue_Foo(Optional.ofNullable(x));
    }
  }
}
```

`SerializableAutoValueExtension` delegates the serialization of `Foo` to a proxy
object `Proxy$` where `Foo`'s data is unwrapped.

## Supported Types

`SerializableAutoValueExtension` currently supports the following types:

*   `java.util.Optional`
*   `com.google.common.collect.ImmutableList`
    *   Enables `ImmutableList<T>`, where `T` is an un-serializable but
        supported type, to be serializable.
*   `com.google.common.collect.ImmutableMap`
    *   Enables `ImmutableMap<K, V>`, where `K` and/or `V` are un-serializable
        but supported types, to be serializable.

### Extensions

`SerializableAutoValueExtension` can be extended to support additional
un-serializable types with [SerializerExtensions].

[`AutoValue`]: https://github.com/google/auto/tree/main/value
[`SerializableAutoValue`]: https://github.com/google/auto/blob/main/value/src/main/java/com/google/auto/value/extension/serializable/SerializableAutoValue.java
[`SerializableAutoValueExtension`]: https://github.com/google/auto/blob/main/value/src/main/java/com/google/auto/value/extension/serializable/extension/SerializableAutoValueExtension.java
[SerializerExtensions]: serializer-extension
