# AutoBuilder


AutoBuilder makes it easy to create a generalized builder, with setter methods
that accumulate values, and a build method that calls a constructor or static
method with those values as parameters. Callers don't need to know the order of
those parameters. Parameters can also have default values. There can be
validation before the constructor or method call.

If you are familiar with [AutoValue builders](builders.md) then AutoBuilder
should also be familiar. Where an `@AutoValue.Builder` has setter methods
corresponding to the getter methods in the `@AutoValue` class, an `@AutoBuilder`
has setter methods corresponding to the parameters of a constructor or static
method. Apart from that, the two are very similar.

## Example: calling a constructor

Here is a simple example:

```java
@AutoBuilder(ofClass = Person.class)
abstract class PersonBuilder {
  static PersonBuilder personBuilder() {
    return new AutoBuilder_PersonBuilder();
  }

  abstract PersonBuilder setName(String name);
  abstract PersonBuilder setId(int id);
  abstract Person build();
}
```

It might be used like this:

```java
Person p = PersonBuilder.personBuilder().setName("Priz").setId(6).build();
```

That would have the same effect as this:

```java
Person p = new Person("Priz", 6);
```

But it doesn't require you to know what order the constructor parameters are in.

Here, `setName` and `setId` are _setter methods_. Calling
`builder.setName("Priz")` records the value `"Priz"` for the parameter `name`,
and likewise with `setId`.

There is also a `build()` method. Calling that method invokes the `Person`
constructor with the parameters that were previously set.

## <a name="kotlin"></a> Example: calling a Kotlin constructor

Kotlin has named arguments and default arguments for constructors and functions,
which means there is not much need for anything like AutoBuilder there. But if
you are constructing an instance of a Kotlin data class from Java code,
AutoBuilder can help.

Given this trivial Kotlin data class:

```kotlin
class KotlinData(val level: Int, val name: String?, val id: Long = -1L)
```

You might make a builder for it like this:

```java
@AutoBuilder(ofClass = KotlinData.class)
public abstract class KotlinDataBuilder {
  public static KotlinDataBuilder kotlinDataBuilder() {
    return new AutoBuilder_KotlinDataBuilder();
  }

  public abstract KotlinDataBuilder setLevel(int x);
  public abstract KotlinDataBuilder setName(@Nullable String x);
  public abstract KotlinDataBuilder setId(long x);
  public abstract KotlinData build();
}
```

The Kotlin type `String?` corresponds to `@Nullable String` in the AutoBuilder
class, where `@Nullable` is any annotation with that name, such as
`org.jetbrains.annotations.Nullable`.

The `id` parameter has a default value of `-1L`, which means that if `setId` is
not called then the `id` field of the built `KotlinData` will be `-1L`.

If you are using [kapt](https://kotlinlang.org/docs/kapt.html) then you can also
define the builder in the data class itself:

```kotlin
class KotlinData(val level: Int, val name: String?, val id: Long = -1L) {
  @AutoBuilder // we don't need ofClass: by default it is the containing class
  interface Builder {
    fun setLevel(x: Int): Builder
    fun setName(x: String?): Builder
    fun setId(x: Long): Builder
    fun build(): KotlinData
  }

  fun toBuilder(): Builder = AutoBuilder_KotlinData_Builder(this)

  companion object {
    @JvmStatic fun builder(): Builder = AutoBuilder_KotlinData_Builder()
  }
}
```

This example uses an interface rather than an abstract class for the builder,
but both are possible. Java code would then construct instances like this:

```java
KotlinData k = KotlinData.builder().setLevel(23).build();
```

The example also implements a `toBuilder()` method to get a builder that starts
out with values from the given instance. See [below](#to_builder) for more
details on that.

## The generated subclass

Like `@AutoValue.Builder`, compiling an `@AutoBuilder` class will generate a
concrete subclass. In the example above, this will be `class
AutoBuilder_PersonBuilder extends PersonBuilder`. It is common to have a static
`builder()` method, as in the example, which calls `new AutoBuilder_...()`. That
will typically be the only reference to the generated class.

If the `@AutoBuilder` type is nested then the name of the generated class
reflects that nesting. For example:

```java
class Outer {
  static class Inner {
    @AutoBuilder
    abstract static class Builder {...}
  }
  static Inner.Builder builder() {
    return new AutoBuilder_Outer_Inner_Builder();
  }
}
```

## `@AutoBuilder` annotation parameters

`@AutoBuilder` has two annotation parameters, `ofClass` and `callMethod`.

If `ofClass` is specified, then `build()` will call a constructor or static
method of that class. Otherwise it will call a constructor or static method of
the class _containing_ the `@AutoBuilder` class.

If `callMethod` is specified, then `build()` will call a static method with that
name. Otherwise `build()` will call a constructor.

The following examples illustrate the various possibilities. These examples use
an interface for the `@AutoBuilder` type. You can also use an abstract class; if
it is nested then it must be static.

### Both `callMethod` and `ofClass`

```java
@AutoBuilder(callMethod = "of", ofClass = LocalTime.class)
interface LocalTimeBuilder {
  ...
  LocalTime build(); // calls: LocalTime.of(...)
}
```

### Only `ofClass`

```java
@AutoBuilder(ofClass = Thread.class)
interface ThreadBuilder {
  ...
  Thread build(); // calls: new Thread(...)
}
```

### Only `callMethod`

```java
class Foo {
  static String concat(String first, String middle, String last) {...}

  @AutoBuilder(callMethod = "concat")
  interface ConcatBuilder {
    ...
    String build(); // calls: Foo.concat(first, middle, last)
  }
}
```

Notice in this example that the static method returns `String`. The implicit
`ofClass` is `Foo`, but the static method can return any type.

### Neither `callMethod` nor `ofClass`

```java
class Person {
  Person(String name, int id) {...}

  @AutoBuilder
  interface Builder {
    ...
    Person build(); // calls: new Person(name, id)
  }
}
```

## The build method

The build method must have a certain return type. If it calls a constructor then
its return type must be the type of the constructed class. If it calls a static
method then its return type must be the return type of the static method.

The build method is often called `build()` but it does not have to be. The only
requirement is that there must be exactly one no-arg abstract method that has
the return type just described and that does not correspond to a parameter name.

The following example uses the name `call()` since that more accurately reflects
what it does:

```java
public class LogUtil {
  public static void log(Level severity, String message, Object... params) {...}

  @AutoBuilder(callMethod = "log")
  public interface Caller {
    Caller setSeverity(Level level);
    Caller setMessage(String message);
    Caller setParams(Object... params);
    void call(); // calls: LogUtil.log(severity, message, params)
  }
```

## <a name="to_builder"></a> Making a builder from a built instance

It is not always possible to map back from the result of a constructor or method
call to a builder that might have produced it. But in one important case, it
*is* possible. That's when every parameter in the constructor or method
corresponds to a "getter method" in the built type. This will always be true
when building a Java record or a Kotlin data class (provided its getters are
visible to the builder). In this case, the generated builder class will have a
second constructor that takes an object of the built type as a parameter and
produces a builder that starts out with values from that object. That can then
be used to produce a new object that may differ from the first one in just one
or two properties. (This is very similar to AutoValue's
[`toBuilder()`](builders-howto.md#to_builder) feature.)

If the constructor or method has a parameter `String bar` then the built type
must have a visible method `String bar()` or `String getBar()`. (Java records
have the first and Kotlin data classes have the second.) If there is a
similar corresponding method for every parameter then the second constructor is
generated.

If you are able to change the built type, the most convenient way to use this is
to add a `toBuilder()` instance method that calls `new AutoBuilder_Foo(this)`.
We saw this in the [Kotlin example](#kotlin) earlier. Otherwise, you can have
a second static `builder` method, like this:

```java
@AutoBuilder(ofClass = Person.class)
abstract class PersonBuilder {
  static PersonBuilder personBuilder() {
    return new AutoBuilder_PersonBuilder();
  }
  static PersonBuilder personBuilder(Person person) {
    return new AutoBuilder_PersonBuilder(person);
  }
  ...
}
```

## Overloaded constructors or methods

There might be more than one constructor or static method that matches the
`callMethod` and `ofClass`. AutoBuilder will ignore any that are not visible to
the generated class, meaning private, or package-private and in a different
package. Of the others, it will pick the one whose parameter names match the
`@AutoBuilder` setter methods. It is a compilation error if there is not exactly
one such method or constructor.

## Generics

If the builder calls the constructor of a generic type, then it must have the
same type parameters as that type, as in this example:

```java
class NumberPair<T extends Number> {
  NumberPair(T first, T second) {...}

  @AutoBuilder
  interface Builder<T extends Number> {
    Builder<T> setFirst(T x);
    Builder<T> setSecond(T x);
    NumberPair<T> build();
  }
}
```

If the builder calls a static method with type parameters, then it must have the
same type parameters, as in this example:

```java
class Utils {
  static <K extends Number, V> Map<K, V> singletonNumberMap(K key, V value) {...}

  @AutoBuilder(callMethod = "singletonNumberMap")
  interface Builder<K extends Number, V> {
    Builder<K, V> setKey(K x);
    Builder<K, V> setValue(V x);
    Map<K, V> build();
  }
}
```

Although it's unusual, a Java constructor can have its own type parameters,
separately from any that its containing class might have. A builder that calls a
constructor like that must have the type parameters of the class followed by the
type parameters of the constructor:

```java
class CheckedSet<E> implements Set<E> {
  <T extends E> CheckedSet(Class<T> type) {...}

  @AutoBuilder
  interface Builder<E, T extends E> {
    Builder<E, T> setType(Class<T> type);
    CheckedSet<E> build();
  }
}
```

## Required, optional, and nullable parameters

Parameters that are annotated `@Nullable` are null by default. Parameters of
type `Optional`, `OptionalInt`, `OptionalLong`, and `OptionalDouble` are empty
by default. Kotlin constructor parameters with default values get those values
by default. Every other parameter is _required_, meaning that the build method
will throw `IllegalStateException` if any are omitted.

To establish default values for parameters, set them in the `builder()` method
before returning the builder.

```java
class Foo {
  Foo(String bar, @Nullable String baz, String buh) {...}

  static Builder builder() {
    return new AutoBuilder_Foo_Builder()
        .setBar(DEFAULT_BAR);
  }

  @AutoBuilder
  interface Builder {
    Builder setBar(String x);
    Builder setBaz(String x);
    Builder setBuh(String x);
    Foo build();
  }

  {
     builder().build(); // IllegalStateException, buh is not set
     builder().setBuh("buh").build(); // OK, bar=DEFAULT_BAR and baz=null
     builder().setBaz(null).setBuh("buh").build(); // OK
     builder().setBar(null); // NullPointerException, bar is not @Nullable
  }
}
```

Trying to set a parameter that is _not_ annotated `@Nullable` to `null` will
produce a `NullPointerException`.

`@Nullable` here is any annotation with that name, such as
`javax.annotation.Nullable` or
`org.checkerframework.checker.nullness.qual.Nullable`.

## Getters

The `@AutoBuilder` class or interface can also have _getter_ methods. A getter
method returns the value that has been set for a certain parameter. Its return
type can be either the same as the parameter type, or an `Optional` wrapping
that type. Calling the getter before any value has been set will throw an
exception in the first case or return an empty `Optional` in the second.

In this example, the `nickname` parameter defaults to the same value as the
`name` parameter but can also be set to a different value:

```java
public class Named {
  Named(String name, String nickname) {...}

  @AutoBuilder
  public abstract static class Builder {
    public abstract Builder setName(String x);
    public abstract Builder setNickname(String x);
    abstract String getName();
    abstract Optional<String> getNickname();
    abstract Named autoBuild();

    public Named build() {
      if (!getNickname().isPresent()) {
        setNickname(getName());
      }
      return autoBuild();
    }
  }
}
```

The example illustrates having a package-private `autoBuild()` method that
AutoBuilder implements. The public `build()` method calls it after adjusting the
nickname if necessary.

The builder in the example is an abstract class rather than an interface. An
abstract class allows us to distinguish between public methods for users of the
builder to call, and package-private methods that the builder's own logic uses.

## Building annotation instances

AutoBuilder can build instances of annotation interfaces. When the annotation
has no elements (methods in the annotation), or only one, then AutoAnnotation is
simpler to use. But when there are several elements, a builder is helpful. See
[here](howto.md#annotation) for examples of both.

## Naming conventions

A setter method for the parameter `foo` can be called either `setFoo` or `foo`.
A getter method can be called either `getFoo` or `foo`, and for a `boolean`
parameter it can also be called `isFoo`. The choice for getters and setters is
independent. For example your getter might be `foo()` while your setter is
`setFoo(T)`.

By convention, the parameter name of a setter method either echoes the parameter
being set:<br>
`Builder setName(String name);`<br>
or it is just `x`:<br>
`Builder setName(String x);`<br>

If class `Foo` has a nested `@AutoBuilder` that builds instances of `Foo`, then
conventionally that type is called `Builder`, and instances of it are obtained
by calling a static `Foo.builder()` method:

```java
Foo foo1 = Foo.builder().setBar(bar).setBaz(baz).build();
Foo.Builder fooBuilder = Foo.builder();
```

If an `@AutoBuilder` for `Foo` is its own top-level class then that class will
typically be called `FooBuilder` and it will have a static `fooBuilder()` method
that returns an instance of `FooBuilder`. That way callers can statically import
`FooBuilder.fooBuilder` and just write `fooBuilder()` in their code.

```java
@AutoBuilder(ofClass = Foo.class)
public abstract class FooBuilder {
  public static FooBuilder fooBuilder() {
    return new AutoBuilder_FooBuilder();
  }
  ...
  public abstract Foo build();
}
```

If an `@AutoBuilder` is designed to call a static method that is not a factory
method, the word "call" is better than "build" in the name of the type
(`FooCaller`), the static method (`fooCaller()`), and the "build" method (`call()`).

```java
@AutoBuilder(callMethod = "log", ofClass = MyLogger.class)
public abstract class LogCaller {
  public static LogCaller logCaller() {
    return new AutoBuilder_LogCaller();
  }
  ...
  public abstract void call();
}

// used as:
logCaller().setLevel(Level.INFO).setMessage("oops").call();
```

## Other builder features

There are a number of other builder features that have not been detailed here
because they are the same as for `@AutoValue.Builder`. They include:

*   [Special treatment of collections](builders-howto.md#collection)
*   [Handling of nested builders](builders-howto.md#nested_builders)

## When parameter names are unavailable

AutoBuilder depends on knowing the names of parameters. But parameter names are
not always available in Java. They _are_ available in these cases, at least:

*   In code that is being compiled at the same time as the `@AutoBuilder` class
    or interface.
*   In _records_ (from Java 16 onwards).
*   In the constructors of Kotlin data classes.
*   In code that was compiled with the [`-parameters`] option.

A Java compiler bug means that parameter names are not available to AutoBuilder
when compiling with JDK versions before 11, in any of these cases except the
first. We recommend building with a recent JDK, using the `--release` option if
necessary to produce code that can run on earlier versions.

If parameter names are unavailable, you always have the option of introducing a
static method in the same class as the `@AutoBuilder` type, and having it call
the method you want. Since it is compiled at the same time, its parameter names
are available.

Here's an example of fixing a problem this way. The code here typically will not
compile, since parameter names of JDK methods are not available:

```java
import java.time.LocalTime;

public class TimeUtils {
  // Does not work, since parameter names from LocalTime.of are unavailable.
  @AutoBuilder(callMethod = "of", ofClass = LocalTime.class)
  public interface TimeBuilder {
    TimeBuilder setHour(int x);
    TimeBuilder setMinute(int x);
    TimeBuilder setSecond(int x);
    LocalTime build();
  }
}
```

It will produce an error message like this:

```
error: [AutoBuilderNoMatch] Property names do not correspond to the parameter names of any static method named "of":
  public interface TimeBuilder {
  ^
    of(int arg0, int arg1)
    of(int arg0, int arg1, int arg2)
    of(int arg0, int arg1, int arg2, int arg3)
```

The names `arg0`, `arg1`, etc are concocted by the compiler because it doesn't
have the real names.

Introducing a static method fixes the problem:

```java
import java.time.LocalTime;

public class TimeUtils {
  static LocalTime localTimeOf(int hour, int minute, int second) {
    return LocalTime.of(hour, minute, second);
  }

  @AutoBuilder(callMethod = "localTimeOf")
  public interface TimeBuilder {
    TimeBuilder setHour(int x);
    TimeBuilder setMinute(int x);
    TimeBuilder setSecond(int x);
    LocalTime build();
  }
}
```

[`-parameters`]: https://docs.oracle.com/en/java/javase/16/docs/specs/man/javac.html#option-parameters
