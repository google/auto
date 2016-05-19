# AutoValue Changes

## 1.2 → 1.3

### Functional changes

* Support for TYPE_USE `@Nullable`.
  This is https://github.com/google/auto/pull/293 by @brychcy.

* Restructured the code in AutoValueProcessor for handling extensions, to get
  rid of warnings about abstract methods when those methods are going to be
  implemented by an extension, and to fix a bug where extensions would not work
  right if there was a toBuilder() method. Some of the code in this change is
  based on https://github.com/google/auto/pull/299 by @rharter.

* Added support for "optional getters", where a getter in an AutoValue Builder
  can have type `Optional<T>` and it will return `Optional.of(x)` where `x` is
  the value that has been set in the Builder, or `Optional.empty()` if no value
  has been set.

* In AutoValue builders, added support for setting a property of type
  `Optional<T>` via a setter with an argument of type `T`.

* Added logic to AutoValue to detect the confusing case where you think you
  are using JavaBeans conventions (like getFoo()) but you aren't because at
  least one method isn't.

* Added a README.md describing EscapeVelocity.

### Bugs fixed

* Allow an `@AutoValue.Builder` to extend a parent builder using the `<B extends
  Builder<B>>` idiom.

* AutoAnnotation now factors in package names when detecting
  overloads. Previously it treated all annotations with the same SimpleName as
  being overload attempts.

* Removed an inaccurate javadoc reference, which referred to an
  artifact from an earlier draft version of the Extensions API. This is
  https://github.com/google/auto/pull/322 by @lucastsa.

## 1.1 → 1.2

### Functional changes

  * A **provisional** extension API has been introduced. This **will change**
    in a later release. If you want to use it regardless, see the
    [AutoValueExtension] class.

  * Properties of primitive array type (e.g. `byte[]`) are no longer cloned
    when read. If your `@AutoValue` class includes an array property, by default
    it will get a compiler warning, which can be suppressed with
    `@SuppressWarnings("mutable")`.

  * An `@AutoValue.Builder` type can now define both the setter and builder
    methods like so:

    ```
      ...
      abstract void setStrings(ImmutableList<String>);
      abstract ImmutableList.Builder<String> stringsBuilder();
      ...
    ```
    At runtime, if `stringsBuilder()...` is called then it is an error to call
    `setStrings(...)` afterwards.

  * The classes in the autovalue jar are now shaded with a `$` so they never
    appear in IDE autocompletion.

  * AutoValue now uses its own implementation of a subset of Apache Velocity,
    so there will no longer be problems with interference between the Velocity
    that was bundled with AutoValue and other versions that might be present.

### Bugs fixed

  * Explicit check for nested `@AutoValue` classes being private, or not being
    static. Otherwise the compiler errors could be hard to understand,
    especially in IDEs.

  * An Eclipse bug that could occasionally lead to exceptions in the IDE has
    been fixed (GitHub issue #200).

  * Fixed a bug where AutoValue generated incorrect code if a method with a
    type parameter was inherited by a class that supplies a concrete type for
    that parameter. For example `StringIterator implements Iterator<String>`,
    where the type of `next()` is String, not `T`.

  * In `AutoValueProcessor`, fixed an exception that happened if the same
    abstract method was inherited from more than one parent (Github Issue #267).

  * AutoValue now works correctly in an environment where
    `@javax.annotation.Generated` does not exist.

  * Properties marked `@Nullable` now get `@Nullable` on the corresponding
    constructor parameters in the generated class.

## 1.0 → 1.1

### Functional changes

  * Adds builders to AutoValue. Builders are nested classes annotated with
    `@AutoValue.Builder`.

  * Annotates constructor parameters with `@Nullable` if the corresponding
    property methods are `@Nullable`.

  * Changes Maven shading so org.apache.commons is shaded.

  * Copies a `@GwtCompatible` annotation from the `@AutoValue` class to its
    implementation subclass.

### Bugs fixed

  * Works around a bug in the Eclipse compiler that meant that annotations
    would be incorrectly copied from `@AutoValue` methods to their
    implementations.

## 1.0 (Initial Release)

  * Allows automatic generation of value type implementations

    See [the AutoValue User's Guide](userguide/index.md)


[AutoValueExtension]: src/main/java/com/google/auto/value/extension/AutoValueExtension.java

