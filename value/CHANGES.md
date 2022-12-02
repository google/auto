# AutoValue Changes

**This document is obsolete.** For details of changes in releases since 1.5,
see the [releases page](https://github.com/google/auto/releases) for the Auto
project.

## 1.4 → 1.5

### Functional changes

* A workaround for older Eclipse versions has been removed. If you need to use
  an Eclipse version older than 4.5, you will need to stay on AutoValue 1.4.

* The [retention](https://docs.oracle.com/javase/8/docs/api/java/lang/annotation/Retention.html)
  of the `@AutoValue` annotation has changed from `SOURCE` to `CLASS`. This
  means that it is possible for code-analysis tools to tell whether a class is
  an `@AutoValue`. AutoValue itself uses this to enforce the check that one
  `@AutoValue` class cannot extend another, even if the classes are compiled
  separately.

* It is now an error if `@Memoized` is applied to a method not inside an
  `@AutoValue` class.

* Type annotations are now handled more consistently. If `@Nullable` is a type
  annotation, a property of type `@Nullable Integer` will have that type used
  everywhere in the generated code. Associated bugs with nested type
  annotations, like `Outer.@Inner`, have been fixed.

### Bugs fixed since 1.4.1

* `@Memoized` methods can now throw checked exceptions. Previously this failed
  because the exceptions were not copied into the `throws` clause of the
  generated override, so the call to `super.foo()` did not compile.

* The generated `hashCode()` method uses `h = (int) (h ^ longProperty)` rather
  than `h ^= longProperty` to avoid warnings about loss of precision.

* Annotations are not copied from an abstract method to its implementation if
  they are not visible from the latter. This can happen if the `@AutoValue`
  inherits the abstract method from a class or interface in a different package.

## 1.3 → 1.4

*This is the last AutoValue version that compiles and runs on Java 6.* Future
versions will require at least Java 8 to run. We will continue to generate code
that is compatible with Java 7, so AutoValue can be used with `javac -source 7
-target 7 -bootclasspath <rt.jar-from-jdk7>`, but using the `javac` from jdk8 or
later.

### Functional changes

* Builder setters now reject a null parameter immediately unless the
  corresponding property is `@Nullable`. Previously this check happened at
  `build()` time, and in some cases didn't happen at all. This is the change
  that is most likely to affect existing code.

* Added `@Memoized`. A `@Memoized` method will be overridden in the generated
  `AutoValue_Foo` class to save the value returned the first time it was called
  and reuse that every other time.

* Generalized support for property builders. Now, in addition to being able to
  say `immutableListBuilder()` for a property of type `ImmutableList<T>`, you
  can say `fooBuilder()` for a property of an arbitrary type that has a builder
  following certain conventions. In particular, you can do this if the type of
  `foo()` is itself an `@AutoValue` class with a builder. The default value of
  `foo()`, if `fooBuilder()` is never called, is `fooBuilder().build()`.

* If a property `foo()` or `getFoo()` has a builder method `fooBuilder()` then
  the property can not now be `@Nullable`. An `ImmutableList`, for example,
  starts off empty, not null, so `@Nullable` was misleading.

* When an `@AutoValue` class `Foo` has a builder, the generated
  `AutoValue_Foo.Builder` has a constructor `AutoValue_Foo.Builder(Foo)`. That
  constructor was never documented and is now private. If you want to make a
  `Foo.Builder` from a `Foo`, `Foo` should have an abstract method `Builder
  toBuilder()`.

  This change was necessary so that generalized property-builder support could
  know whether or not the built class needs to be convertible back into its
  builder. That's only necessary if there is a `toBuilder()`.

* The Extension API is now a committed API, meaning we no longer warn that it is
  likely to change incompatibly. A
  [guide](https://github.com/google/auto/blob/main/value/userguide/extensions.md)
  gives tips on writing extensions.

* Extensions can now return null rather than generated code. In that case the
  extension does not generate a class in the AutoValue hierarchy, but it can
  still do other things like error checking or generating side files.

* Access modifiers like `protected` are copied from builder methods to their
  implementations, instead of the implementations always being public.
  Change by @torquestomp.

* AutoAnnotation now precomputes parts of the `hashCode` that are constant
  because they come from defaulted methods. This avoids warnings about integer
  overflow from tools that check that.

* If a property is called `oAuth()`, its setter can be called
  `setOAuth(x)`. Previously it had to be `setoAuth(x)`, which is still allowed.

## Bugs fixed

* AutoAnnotation now correctly handles types like `Class<? extends
  Annotation>[]`. Previously it would try to create a generic array, which Java
  doesn't allow. Change by @lukesandberg.

* We guard against spurious exceptions due to a JDK bug in reading resources
  from jars. (#365)

* We don't propagate an exception if a corrupt jar is found in extension
  loading.

* AutoValue is ready for Java 9, where public classes are not necessarily
  accessible, and javax.annotation.Generated is not necessarily present.

* AutoValue now works correctly even if the version of AutoValue in the
  `-classpath` is older than the one in the `-processorpath`.

* Builders now behave correctly when there is a non-optional property called
  `missing`. Previously a variable-hiding problem meant that we didn't detect
  when it wasn't set.

* If `@AutoValue class Foo` has a builder, we always generated two constructors,
  `Builder()` and `Builder(Foo)`, but we only used the second one if `Foo` had a
  `toBuilder()` method. Now we only generate that constructor if it is
  needed. That avoids warnings about unused code.

* `@AutoAnnotation` now works when the annotation and the factory method are in
  the default (unnamed) package.

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
