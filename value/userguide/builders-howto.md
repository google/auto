# How do I... (Builder edition)


This page answers common how-to questions that may come up when using AutoValue
**with the builder option**. You should read and understand [AutoValue with
builders](builders.md) first.

If you are not using a builder, see [Introduction](index.md) and
[How do I...](howto.md) instead.

## Contents

How do I...

*   ... [use (or not use) `set` **prefixes**?](#beans)
*   ... [use different **names** besides
    `builder()`/`Builder`/`build()`?](#build_names)
*   ... [specify a **default** value for a property?](#default)
*   ... [initialize a builder to the same property values as an **existing**
    value instance](#to_builder)
*   ... [include `with-` methods on my value class for creating slightly
    **altered** instances?](#withers)
*   ... [**validate** property values?](#validate)
*   ... [**normalize** (modify) a property value at `build` time?](#normalize)
*   ... [expose **both** a builder and a factory method?](#both)
*   ... [handle `Optional` properties?](#optional)
*   ... [use a **collection**-valued property?](#collection)
    *   ... [let my builder **accumulate** values for a collection-valued
        property (not require them all at once)?](#accumulate)
    *   ... [accumulate values for a collection-valued property, without
        **"breaking the chain"**?](#add)
    *   ... [offer **both** accumulation and set-at-once methods for the same
        collection-valued property?](#collection_both)
*   ... [access nested builders while building?](#nested_builders)
*   ... [create a "step builder"?](#step)
*   ... [create a builder for something other than an `@AutoValue`?](#autobuilder)

## <a name="beans"></a>... use (or not use) `set` prefixes?

Just as you can choose whether to use JavaBeans-style names for property getters
(`getFoo()` or just `foo()`) in your value class, you have the same choice for
setters in builders too (`setFoo(value)` or just `foo(value)`). As with getters,
you must use these prefixes consistently or not at all.

Using `get`/`is` prefixes for getters and using the `set` prefix for setters are
independent choices. For example, it is fine to use the `set` prefixes on all
your builder methods, but omit the `get`/`is` prefixes from all your accessors.

Here is the `Animal` example using `get` prefixes but not `set` prefixes:

```java
@AutoValue
abstract class Animal {
  abstract String getName();
  abstract int getNumberOfLegs();

  static Builder builder() {
    return new AutoValue_Animal.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder name(String value);
    abstract Builder numberOfLegs(int value);
    abstract Animal build();
  }
}
```

## <a name="build_names"></a>... use different names besides `builder()`/`Builder`/`build()`?

Use whichever names you like; AutoValue doesn't actually care.

(We would gently recommend these names as conventional.)

## <a name="default"></a>... specify a default value for a property?

What should happen when a caller does not supply a value for a property before
calling `build()`? If the property in question is [nullable](howto.md#nullable),
it will simply default to `null` as you would expect. And if it is
[Optional](#optional) it will default to an empty `Optional` as you might also
expect. But if it isn't either of those things (including if it is a
primitive-valued property, which *can't* be null), then `build()` will throw an
unchecked exception. This includes collection properties, which must be given a
value. They don't default to empty unless there is a
[collection builder](#accumulate).

But this requirement to supply a value presents a problem, since one of the main
*advantages* of a builder in the first place is that callers can specify only
the properties they care about!

The solution is to provide a default value for such properties. Fortunately this
is easy: just set it on the newly-constructed builder instance before returning
it from the `builder()` method.

Here is the `Animal` example with the default number of legs being 4:

```java
@AutoValue
abstract class Animal {
  abstract String name();
  abstract int numberOfLegs();

  static Builder builder() {
    return new AutoValue_Animal.Builder()
        .setNumberOfLegs(4);
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setName(String value);
    abstract Builder setNumberOfLegs(int value);
    abstract Animal build();
  }
}
```

Occasionally you may want to supply a default value, but only if the property is
not set explicitly. This is covered in the section on
[normalization](#normalize).

## <a name="to_builder"></a>... initialize a builder to the same property values as an existing value instance

Suppose your caller has an existing instance of your value class, and wants to
change only one or two of its properties. Of course, it's immutable, but it
would be convenient if they could easily get a `Builder` instance representing
the same property values, which they could then modify and use to create a new
value instance.

To give them this ability, just add an abstract `toBuilder` method, returning
your abstract builder type, to your value class. AutoValue will implement it.

```java
  public abstract Builder toBuilder();
```

## <a name="withers"></a>... include `with-` methods on my value class for creating slightly altered instances?

This is a somewhat common pattern among immutable classes. You can't have
setters, but you can have methods that act similarly to setters by returning a
new immutable instance that has one property changed.

If you're already using the builder option, you can add these methods by hand:

```java
@AutoValue
public abstract class Animal {
  public abstract String name();
  public abstract int numberOfLegs();

  public static Builder builder() {
    return new AutoValue_Animal.Builder();
  }

  abstract Builder toBuilder();

  public Animal withName(String name) {
    return toBuilder().setName(name).build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String value);
    public abstract Builder setNumberOfLegs(int value);
    public abstract Animal build();
  }
}
```

Note that it's your free choice what to make public (`toBuilder`, `withName`,
neither, or both).

## <a name="validate"></a>... validate property values?

Validating properties is a little less straightforward than it is in the
[non-builder case](howto.md#validate).

What you need to do is *split* your "build" method into two methods:

*   the non-visible, abstract method that AutoValue implements
*   and the visible, *concrete* method you provide, which calls the generated
    method and performs validation.

We recommend naming these methods `autoBuild` and `build`, but any names will
work. It ends up looking like this:

```java
@AutoValue
public abstract class Animal {
  public abstract String name();
  public abstract int numberOfLegs();

  public static Builder builder() {
    return new AutoValue_Animal.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String value);
    public abstract Builder setNumberOfLegs(int value);

    abstract Animal autoBuild();  // not public

    public Animal build() {
      Animal animal = autoBuild();
      Preconditions.checkState(animal.numberOfLegs() >= 0, "Negative legs");
      return animal;
    }
  }
}
```

## <a name="normalize"></a>... normalize (modify) a property value at `build` time?

Suppose you want to convert the animal's name to lower case.

You'll need to add a *getter* to your builder, as shown:

```java
@AutoValue
public abstract class Animal {
  public abstract String name();
  public abstract int numberOfLegs();

  public static Builder builder() {
    return new AutoValue_Animal.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String value);
    public abstract Builder setNumberOfLegs(int value);

    abstract String name(); // must match method name in Animal

    abstract Animal autoBuild(); // not public

    public Animal build() {
      setName(name().toLowerCase());
      return autoBuild();
    }
  }
}
```

The getter in your builder must have the same signature as the abstract property
accessor method in the value class. It will return the value that has been set
on the `Builder`. If no value has been set for a
non-[nullable](howto.md#nullable) property, `IllegalStateException` is thrown.

Getters should generally only be used within the `Builder` as shown, so they are
not public.

As an alternative to returning the same type as the property accessor method,
the builder getter can return an Optional wrapping of that type. This can be
used if you want to supply a default, but only if the property has not been set.
(The [usual way](#default) of supplying defaults means that the property always
appears to have been set.) For example, suppose you wanted the default name of
your Animal to be something like "4-legged creature", where 4 is the
`numberOfLegs()` property. You might write this:

```java
@AutoValue
public abstract class Animal {
  public abstract String name();
  public abstract int numberOfLegs();

  public static Builder builder() {
    return new AutoValue_Animal.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String value);
    public abstract Builder setNumberOfLegs(int value);

    abstract Optional<String> name();
    abstract int numberOfLegs();

    abstract Animal autoBuild(); // not public

    public Animal build() {
      if (!name().isPresent()) {
        setName(numberOfLegs() + "-legged creature");
      }
      return autoBuild();
    }
  }
}
```

Notice that this will throw `IllegalStateException` if the `numberOfLegs`
property hasn't been set either.

The Optional wrapping can be any of the Optional types mentioned in the
[section](#optional) on `Optional` properties. If your property has type `int`
it can be wrapped as either `Optional<Integer>` or `OptionalInt`, and likewise
for `long` and `double`.

## <a name="both"></a>... expose *both* a builder *and* a factory method?

If you use the builder option, AutoValue will not generate a visible constructor
for the generated concrete value class. If it's important to offer your caller
the choice of a factory method as well as the builder, then your factory method
implementation will have to invoke the builder itself.

## <a name="optional"></a>... handle `Optional` properties?

Properties of type `Optional` benefit from special treatment. If you have a
property of type `Optional<String>`, say, then it will default to an empty
`Optional` without needing to [specify](#default) a default explicitly. And,
instead of or as well as the normal `setFoo(Optional<String>)` method, you can
have `setFoo(String)`. Then `setFoo(s)` is equivalent to
`setFoo(Optional.of(s))`. (If it is `setFoo(@Nullable String)`, then `setFoo(s)`
is equivalent to `setFoo(Optional.ofNullable(s))`.)

Here, `Optional` means either [`java.util.Optional`] from Java (Java 8 or
later), or [`com.google.common.base.Optional`] from Guava. Java 8 also
introduced related classes in `java.util` called [`OptionalInt`],
[`OptionalLong`], and [`OptionalDouble`]. You can use those in the same way. For
example a property of type `OptionalInt` will default to `OptionalInt.empty()`
and you can set it with either `setFoo(OptionalInt)` or `setFoo(int)`.

```java
@AutoValue
public abstract class Animal {
  public abstract Optional<String> name();

  public static Builder builder() {
    return new AutoValue_Animal.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    // You can have either or both of these two methods:
    public abstract Builder setName(Optional<String> value);
    public abstract Builder setName(String value);
    public abstract Animal build();
  }
}
```

[`java.util.Optional`]: https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html
[`com.google.common.base.Optional`]: https://guava.dev/releases/snapshot/api/docs/com/google/common/base/Optional.html
[`OptionalDouble`]: https://docs.oracle.com/javase/8/docs/api/java/util/OptionalDouble.html
[`OptionalInt`]: https://docs.oracle.com/javase/8/docs/api/java/util/OptionalInt.html
[`OptionalLong`]: https://docs.oracle.com/javase/8/docs/api/java/util/OptionalLong.html

## <a name="collection"></a>... use a collection-valued property?

Value objects should be immutable, so if a property of one is a collection then
that collection should be immutable too. We recommend using Guava's [immutable
collections] to make that explicit. AutoValue's builder support includes a few
special arrangements to make this more convenient.

In the examples here we use `ImmutableSet`, but the same principles apply to all
of Guava's immutable collection types, like `ImmutableList`,
`ImmutableMultimap`, and so on.

We recommend using the immutable type (like `ImmutableSet<String>`) as your
actual property type. However, it can be a pain for callers to always have to
construct `ImmutableSet` instances to pass into your builder. So AutoValue
allows your builder method to accept an argument of any type that
`ImmutableSet.copyOf` accepts.

If our `Animal` acquires an `ImmutableSet<String>` that is the countries it
lives in, that can be set from a `Set<String>` or a `Collection<String>` or an
`Iterable<String>` or a `String[]` or any other compatible type. You can even
offer multiple choices, as in this example:

```java
@AutoValue
public abstract class Animal {
  public abstract String name();
  public abstract int numberOfLegs();
  public abstract ImmutableSet<String> countries();

  public static Builder builder() {
    return new AutoValue_Animal.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String value);
    public abstract Builder setNumberOfLegs(int value);
    public abstract Builder setCountries(Set<String> value);
    public abstract Builder setCountries(String... value);
    public abstract Animal build();
  }
}
```

[immutable collections]: https://github.com/google/guava/wiki/ImmutableCollectionsExplained

### <a name="accumulate"></a>... let my builder *accumulate* values for a collection-valued property (not require them all at once)?

Instead of defining a setter for an immutable collection `foos`, you can define
a method `foosBuilder()` that returns the associated builder type for that
collection. In this example, we have an `ImmutableSet<String>` which can be
built using the `countriesBuilder()` method:

```java
@AutoValue
public abstract class Animal {
  public abstract String name();
  public abstract int numberOfLegs();
  public abstract ImmutableSet<String> countries();

  public static Builder builder() {
    return new AutoValue_Animal.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String value);
    public abstract Builder setNumberOfLegs(int value);
    public abstract ImmutableSet.Builder<String> countriesBuilder();
    public abstract Animal build();
  }
}
```

The name of this method must be exactly the property name (`countries` here)
followed by the string `Builder`. Even if the properties follow the
`getCountries()` convention, the builder method must be `countriesBuilder()`
and not `getCountriesBuilder()`.

It's also possible to have a method like `countriesBuilder` with a single
argument, provided that the `Builder` class has a public constructor or a
static `builder` method, with one parameter that the argument can be assigned
to. For example, if `countries()` were an `ImmutableSortedSet<String>` and you
wanted to supply a `Comparator` to `ImmutableSortedSet.Builder`, you could
write:

```java
    public abstract ImmutableSortedSet.Builder<String>
        countriesBuilder(Comparator<String> comparator);
```

That works because `ImmutableSortedSet.Builder` has a constructor that
accepts a `Comparator` parameter.

You may notice a small problem with these examples: the caller can no longer
create their instance in a single chained statement:

```java
  // This DOES NOT work!
  Animal dog = Animal.builder()
      .setName("dog")
      .setNumberOfLegs(4)
      .countriesBuilder()
          .add("Guam")
          .add("Laos")
      .build();
```

Instead they are forced to hold the builder itself in a temporary variable:

```java
  // This DOES work... but we have to "break the chain"!
  Animal.Builder builder = Animal.builder()
      .setName("dog")
      .setNumberOfLegs(4);
  builder.countriesBuilder()
      .add("Guam")
      .add("Laos");
  Animal dog = builder.build();
```

One solution for this problem is just below.

### <a name="add"></a>... accumulate values for a collection-valued property, without "breaking the chain"?

Another option is to keep `countriesBuilder()` itself non-public, and only use
it to implement a public `addCountry` method:

```java
@AutoValue
public abstract class Animal {
  public abstract String name();
  public abstract int numberOfLegs();
  public abstract ImmutableSet<String> countries();

  public static Builder builder() {
    return new AutoValue_Animal.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String value);
    public abstract Builder setNumberOfLegs(int value);

    abstract ImmutableSet.Builder<String> countriesBuilder();
    public Builder addCountry(String value) {
      countriesBuilder().add(value);
      return this;
    }

    public abstract Animal build();
  }
}
```

Now the caller can do this:

```java
  // This DOES work!
  Animal dog = Animal.builder()
      .setName("dog")
      .setNumberOfLegs(4)
      .addCountry("Guam")
      .addCountry("Laos") // however many times needed
      .build();
```

### <a name="collection_both"></a>... offer both accumulation and set-at-once methods for the same collection-valued property?

Yes, you can provide both methods, letting your caller choose the style they
prefer.

The same caller can mix the two styles only in limited ways; once `foosBuilder`
has been called, any subsequent call to `setFoos` will throw an unchecked
exception. On the other hand, calling `setFoos` first is okay; a later call to
`foosBuilder` will return a builder already populated with the
previously-supplied elements.

## <a name="nested_builders"></a>... access nested builders while building?

Often a property of an `@AutoValue` class is itself an immutable class,
perhaps another `@AutoValue`. In such cases your builder can expose a builder
for that nested class. This is very similar to exposing a builder for a
collection property, as described [earlier](#accumulate).

Suppose the `Animal` class has a property of type `Species`:

```java
@AutoValue
public abstract class Animal {
  public abstract String name();
  public abstract Species species();

  public static Builder builder() {
    return new AutoValue_Animal.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String name);
    public abstract Species.Builder speciesBuilder();
    public abstract Animal build();
  }
}

@AutoValue
public abstract class Species {
  public abstract String genus();
  public abstract String epithet();

  public static Builder builder() {
    return new AutoValue_Species.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setGenus(String genus);
    public abstract Builder setEpithet(String epithet);
    public abstract Species build();
  }
}
```

Now you can access the builder of the nested `Species` while you are building
the `Animal`:

```java
  Animal.Builder catBuilder = Animal.builder()
      .setName("cat");
  catBuilder.speciesBuilder()
      .setGenus("Felis")
      .setEpithet("catus");
  Animal cat = catBuilder.build();
```

Although the nested class in the example (`Species`) is also an `@AutoValue`
class, it does not have to be. For example, it could be a [protobuf]. The
requirements are:

* The nested class must have a way to make a new builder. This can be
  `new Species.Builder()`, or `Species.builder()`, or `Species.newBuilder()`.

* There must be a way to build an instance from the builder: `Species.Builder`
  must have a method `Species build()`.

* If there is a need to convert `Species` back into its builder, then `Species`
  must have a method `Species.Builder toBuilder()`.

  In the example, if `Animal` has an abstract [`toBuilder()`](#to_builder)
  method then `Species` must also have a `toBuilder()` method. That also applies
  if there is an abstract `setSpecies` method in addition to the
  `speciesBuilder` method.

  As an alternative to having a method `Species.Builder toBuilder()` in
  `Species`, `Species.Builder` can have a method called `addAll` or `putAll`
  that accepts an argument of type `Species`. This is how AutoValue handles
  `ImmutableSet` for example. `ImmutableSet` does not have a `toBuilder()`
  method, but `ImmutableSet.Builder` does have an `addAll` method that accepts
  an `ImmutableSet`. So given `ImmutableSet<String> strings`, we can achieve the
  effect of `strings.toBuilder()` by doing:

  ```
  ImmutableSet.Builder<String> builder = ImmutableSet.builder();
  builder.addAll(strings);
  ```

There are no requirements on the name of the builder class. Instead of
`Species.Builder`, it could be `Species.Factory` or `SpeciesBuilder`.

If `speciesBuilder()` is never called then the final `species()` property will
be set as if by `speciesBuilder().build()`. In the example, that would result
in an exception because the required properties of `Species` have not been set.

## <a name="step"></a>... create a "step builder"?

A [_step builder_](http://rdafbn.blogspot.com/2012/07/step-builder-pattern_28.html)
is a collection of builder interfaces that take you step by step through the
setting of each of a list of required properties. We think that these are a nice
idea in principle but not necessarily in practice. Regardless, if you want to
use AutoValue to implement a step builder,
[this example](https://github.com/google/auto/issues/1000#issuecomment-792875738)
shows you how.

## <a name="autobuilder"></a> ... create a builder for something other than an `@AutoValue`?

Sometimes you want to make a builder like the kind described here, but have it
build something other than an `@AutoValue` class, or even call a static method.
In that case you can use `@AutoBuilder`. See
[its documentation](autobuilder.md).

[protobuf]: https://developers.google.com/protocol-buffers/docs/reference/java-generated#builders
