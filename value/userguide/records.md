# AutoValue and Java Records


Starting with Java 16,
[records](https://docs.oracle.com/en/java/javase/17/language/records.html) are a
standard feature of the language. If records are available to you, is there any
reason to use AutoValue?

The short answer is that records are usually a better choice when they are
available. They have a very concise and readable syntax, they produce less code,
and they don't need any special configuration. They are obviously a better
choice when your class is just an aggregation of values, for example to allow a
method to return multiple values or to combine values into a map key.

If you have existing code that has AutoValue classes, you may want to migrate
some or all of those classes to be records instead. Here we will explain how to
do this and in what cases you might prefer not to.

## <a id="whynot"></a>Reasons to stick with AutoValue

While records are usually better, there are some AutoValue features that have no
simple equivalent with records. So you may prefer not to try migrating AutoValue
classes that use those features, and you may even sometimes make new AutoValue
classes even if records are available to you.

### Extensions

AutoValue has [extensions](extensions.md). Some are built in, like the
[`@Memoized`](https://javadoc.io/static/com.google.auto.value/auto-value-annotations/1.10/com/google/auto/value/extension/memoized/Memoized.html),
[`@ToPrettyString`](https://javadoc.io/static/com.google.auto.value/auto-value-annotations/1.10/com/google/auto/value/extension/toprettystring/ToPrettyString.html),
and
[`@SerializableAutoValue`](https://javadoc.io/static/com.google.auto.value/auto-value-annotations/1.10/com/google/auto/value/extension/serializable/SerializableAutoValue.html)
extensions. Most extensions will have no real equivalent with records.

### <a id="visible"></a> Non-public constructor

A record's constructor always has the same visibility as the record: a public
record has a public constructor. You can define a static factory method or a
builder for your record, but you can't straightforwardly use visibility to
require callers to use those rather than the constructor.

To discourage people from calling the constructor directly, it is probably
enough to mark it deprecated. More on this [below](#deprecating).

If you really want to make it impossible to call the constructor directly, there
are ways to achieve that, but it's probably simpler to keep using AutoValue.

### Superclass

The superclass of a record is always `java.lang.Record`. Occasionally the
superclass of an AutoValue class is something other than `Object`, for example
when two AutoValue classes share a subset of their properties. In many cases
records can use interfaces instead, though.

### Derived properties

Records can't have instance fields (other than their properties). So it is hard
to cache a derived property, for example. AutoValue makes this trivial with
[`@Memoized`](https://javadoc.io/static/com.google.auto.value/auto-value-annotations/1.10/com/google/auto/value/extension/memoized/Memoized.html).

We suggest ways to achieve the same effect with records [below](#derived), but
it may be simpler to stick with AutoValue.

### Primitive array properties

AutoValue allows properties of primitive array types such as `byte[]` or `int[]`
and it will implement `equals` and `hashCode` using the methods of
`java.util.Arrays`. Records do not have any special treatment for primitive
arrays, so by default they will use the `equals` and `hashCode` methods of the
arrays. So two distinct arrays will never compare equal even if they have the
same contents. The simplest way to avoid this problem is not to have properties
with primitive array type, perhaps using alternatives such as
[`ImmutableIntArray`](http://guava.dev/ImmutableIntArray). Alternatively you can
define custom implementations of `equals` and `hashCode` as described in the
[section](#eqhc) on that topic. But again, you may prefer to keep using
AutoValue.

(AutoValue doesn't allow properties of non-primitive array types.)

## Translating an AutoValue class into a record

Suppose you have existing AutoValue classes that you do want to translate into
records, and the [above reasons](#whynot) not to don't apply. What does the
translation look like?

One important difference is that AutoValue does not allow properties to be
`null` unless they are marked `@Nullable`. Records require explicit null checks
to achieve the same effect, typically with
[`Objects.requireNonNull`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#requireNonNull\(T\)).

This might also be a good time to start using a nullness-analysis tool on your
code; see [NullAway](https://github.com/uber/NullAway) for example.

The examples below show some before-and-after for various migration scenarios.
For brevity, we've mostly omitted the javadoc comments that good code should
have on its public classes and methods.

### Basic example with only primitive properties

Before:

```java
@AutoValue
public abstract class Point {
  public abstract int x();
  public abstract int y();

  public static Point of(int x, int y) {
    return new AutoValue_Point(x, y);
  }
}
```

After:

```java
public record Point(int x, int y) {
  /** @deprecated Call the constructor directly. */
  @Deprecated
  public static Point of(int x, int y) {
    return new Point(x, y);
  }
}
```

The static factory method `of` is retained so clients of the `Point` class don't
have to be updated. If possible, you should migrate clients to call `new
Point(...)` instead. Then the record can be as simple as this:

```java
public record Point(int x, int y) {}
```

We've omitted the static factory methods from the other examples, but the
general approach applies: keep the method initially but deprecate it and change
its body so it just calls the constructor; migrate the callers so they call the
constructor directly; delete the method. You may be able to use the
[`InlineMe`](https://errorprone.info/docs/inlineme) mechanism from the Error
Prone project to encourage this migration:

```java
package com.example.geometry;

public record Point(int x, int y) {
  /** @deprecated Call the constructor directly. */
  @Deprecated
  @InlineMe(replacement = "new Point(x, y)", imports = "com.example.geometry.Point")
  public static Point of(int x, int y) {
    return new Point(x, y);
  }
}
```

### Non-primitive properties that are not `@Nullable`

Before:

```java
@AutoValue
public abstract class Person {
  public abstract String name();
  public abstract int id();

  public static Person create(String name, int id) {
    return new AutoValue_Person(name, id);
  }
}
```

After:

```java
public record Person(String name, int id) {
  public Person {
    Objects.requireNonNull(name, "name");
  }
}
```

### Non-primitive properties that are all `@Nullable`

Before:

```java
@AutoValue
public abstract class Person {
  public abstract @Nullable String name();
  public abstract int id();

  public static Person create(@Nullable String name, int id) {
    return new AutoValue_Person(name, id);
  }
}
```

After:

```java
public record Person(@Nullable String name, int id) {}
```

### Validation

Before:

```java
@AutoValue
public abstract class Person {
  public abstract String name();
  public abstract int id();

  public static Person create(String name, int id) {
    if (id <= 0) {
      throw new IllegalArgumentException("Id must be positive: " + id);
    }
    return new AutoValue_Person(name, id);
  }
}
```

After:

```java
public record Person(String name, int id) {
  public Person {
    Objects.requireNonNull(name, "name");
    if (id <= 0) {
      throw new IllegalArgumentException("Id must be positive: " + id);
    }
  }
}
```

### Normalization

With records, you can rewrite the constructor parameters to apply normalization
or canonicalization rules.

Before:

```java
@AutoValue
public abstract class OrderedPair {
  public abstract int first();
  public abstract int second();

  public static Rational of(int first, int second) {
    if (first > second) {
      int tmp = first;
      first = second;
      second = tmp;
    }
    return new AutoValue_Rational(first, second);
  }
}
```

After:

```java
public record OrderedPair(int first, int second) {
  public OrderedPair {
    if (first > second) {
      int tmp = first;
      first = second;
      second = tmp;
    }
  }
}
```

### <a id="beans"></a> JavaBeans prefixes (`getFoo()`)

AutoValue allows you to prefix every property getter with `get`, but records
don't have any special treatment here. Imagine you have a class like this:

```java
@AutoValue
public abstract class Person {
  public abstract String getName();
  public abstract int getId();

  public static Person create(String name, int id) {
    return new AutoValue_Person(name, id);
  }
}
```

The names of the fields in `Person`, and the names in its `toString()`, don't
have the `get` prefix:

```
jshell> Person.create("Priz", 6)
$1 ==> Person{name=Priz, id=6}
jshell> $1.getName()
$2 ==> Priz
jshell> List<String> showFields(Class<?> c) {
   ...>     return Arrays.stream(c.getDeclaredFields()).map(Field::getName).toList();
   ...> }
jshell> showFields($1.getClass())
$3 ==> [name, id]
```

You can translate this directly to a record if you don't mind a slightly strange
`toString()`, and strange field names from reflection and debuggers:

```java
public record Person(String getName, int getId) {
  public Person {
    Objects.requireNonNull(getName);
  }
}
```

```
jshell> Person.create("Priz", 6)
$1 ==> Person[getName=Priz, getId=6]
jshell> $1.getName()
$2 ==> Priz
jshell> showFields($1.getClass())
$3 ==> [getName, getId]
```

Alternatively, you can alias `Person.getName()` to be `Person.name()`, etc:

```java
public record Person(String name, int id) {
  public Person {
    Objects.requireNonNull(name);
  }

  public String getName() {
    return name();
  }

  public int getId() {
    return id();
  }
}
```

So both `Person.getName()` and `Person.name()` are allowed. Then you can migrate
client code so it uses `name()` instead of `getName()`, so you can delete
`getName()`.

### <a id="derived"></a> Caching derived properties

A record has an instance field for each of its properties, but cannot have other
instance fields. That means in particular that it is not easy to cache derived
properties, as you can with AutoValue and [`@Memoized`](howto.md#memoize).

Records *can* have static fields, so one way to cache derived properties is to
map from record instances to their derived properties.

Before:

```java
@AutoValue
public abstract class Person {
  public abstract String name();
  public abstract int id();

  @Memoized
  public UUID derivedProperty() {
    return expensiveFunction(this);
  }

  public static Person create(String name, int id) {
    return new AutoValue_Person(name, id);
  }
}
```

After:

```java
public record Person(String name, int id) {
  public Person {
    Objects.requireNonNull(name);
  }

  private static final Map<Person, String> derivedProperty = new WeakHashMap<>();

  public UUID derivedProperty() {
    synchronized (derivedProperty) {
      return derivedProperty.computeIfAbsent(this, person -> expensiveFunction(person)));
    }
  }
}
```

As usual with `WeakHashMap`, you have to be sure that the values in the map
don't reference the keys. For more caching options, consider using
[Caffeine](https://github.com/ben-manes/caffeine).

You may decide that AutoValue with `@Memoized` is simpler than records for this
case, though.

### Builders

Builders are still available when using records. Instead of
`@AutoValue.Builder`, you use [`@AutoBuilder`](autobuilder.md).

Before:

```java
@AutoValue
public abstract class Person {
  public abstract String name();
  public abstract int id();

  public static Builder builder() {
    return new AutoValue_Person.Builder();
  }

  @AutoValue.Builder
  public interface Builder {
    Builder name(String name);
    Builder id(int id);
    Person build();
  }
}

Person p = Person.builder().name("Priz").id(6).build();
```

After:

```java
public record Person(String name, int id) {
  public static Builder builder() {
    return new AutoBuilder_Person();
  }

  @AutoBuilder
  public interface Builder {
    Builder name(String name);
    Builder id(int id);
    Person build();
  }
}

Person p = Person.builder().name("Priz").id(6).build();
```

#### <a id="deprecating"></a>Deprecating the constructor

As mentioned [above](#visible), the primary constructor is always visible. In
the preceding example, the builder will enforce that the `name` property is not
null (since it is not marked @Nullable), but someone calling the constructor
will bypass that check. You could deprecate the constructor to discourage this:

```java
public record Person(String name, int id) {
  /** @deprecated Obtain instances using the {@link #builder()} instead. */
  @Deprecated
  public Person {}

  public static Builder builder() {
    return new AutoBuilder_Person();
  }

  @AutoBuilder
  public interface Builder {
    Builder name(String name);
    Builder id(int id);
    Person build();
  }
}
```

### Custom `toString()`

A record can define its own `toString()` in exactly the same way as an AutoValue
class.

### <a id="eqhc"></a> Custom `equals` and `hashCode`

As with AutoValue, it's unusual to want to change the default implementations of
these methods, and if you do you run the risk of making subtle mistakes. Anyway,
the idea is the same with both AutoValue and records.

Before:

```java
@AutoValue
public abstract class Person {
  ...

  @Override public boolean equals(Object o) {
    return o instanceof Person that
        && Ascii.equalsIgnoreCase(this.name(), that.name())
        && this.id() == that.id();
  }

  @Override public int hashCode() {
    return Objects.hash(Ascii.toLowerCase(name()), id());
  }
}
```

After:

```java
public record Person(String name, int id) {
  ...

  @Override public boolean equals(Object o) {
    return o instanceof Person that
        && Ascii.equalsIgnoreCase(this.name, that.name)
        && this.id == that.id;
  }

  @Override public int hashCode() {
    return Objects.hash(Ascii.toLowerCase(name), id);
  }
}
```

With records, the methods can access fields directly or use the corresponding
methods (`this.name` or `this.name()`).
