# AutoValue

*Immutable value-type code generation for Java 1.6+* <br />
***Kevin Bourrillion, Éamonn McManus*** <br />
**Google, Inc.**

> "AutoValue may sound like a cheesy newspaper for unloading your jalopy on an
> unsuspecting stranger, but it's actually a great tool for eliminating the
> drudgery of writing mundane value classes in Java. It encapsulates much of the
> advice in Effective Java Chapter 2, and frees you to concentrate on the more
> interesting aspects of your program. The resulting program is likely to be
> shorter, clearer, and freer of bugs. Two thumbs up."
>
> -- *Joshua Bloch, author, Effective Java*

## Contents

<!-- generated with http://doctoc.herokuapp.com/ -->

-   [Background](#background)
-   [Design goals](#design-goals)
-   [How to use AutoValue](#how-to-use-autovalue)
    -   [In Example.java](#in-examplejava)
    -   [In ExampleTest.java](#in-exampletestjava)
    -   [In pom.xml](#in-pomxml)
    -   [What's going on here?](#whats-going-on-here)
    -   [Builders](#builders)
-   [Optional "features"](#optional-features)
    -   [Data hiding](#data-hiding)
    -   [Multiple creation paths](#multiple-creation-paths)
    -   [Default values with builders](#default-values-with-builders)
    -   [Nullability](#nullability)
    -   [JavaBeans-style prefixes are optional]
        (#javabeans-style-prefixes-are-optional)
    -   [Other preconditions or preprocessing]
        (#other-preconditions-or-preprocessing)
    -   [Custom implementations](#custom-implementations)
    -   [Nesting](#nesting)
    -   [Derived fields](#derived-fields)
    -   [Generics](#generics)
    -   [Converting back to a builder](#converting-back-to-a-builder)
    -   [Serialization](#serialization)
-   [Warnings](#warnings)
-   [Restrictions and non-features](#restrictions-and-non-features)
-   [Best practices](#best-practices)
-   [Alternatives](#alternatives)

## Background

Classes with value semantics are extremely common in Java. These are classes for
which object identity is irrelevant, and instances are considered
interchangeable based only on the equality of their field values. We will refer
to these classes as value types in this document.

To implement a value type safely and properly requires implementing `equals`,
`hashCode` and `toString` in a bloated, repetitive, formulaic yet error-prone
fashion. These methods are not especially time-consuming to write, especially
with the aid of IDE templates and a few Guava helpers, but they are a continual
burden to reviewers, editors and future readers. Their wide expanses of
boilerplate sharply decrease the signal-to-noise ratio of your code, and
constitute probably the single greatest violation of the DRY (Don't Repeat
Yourself) principle we are ever forced to commit.

AutoValue provides an easier way to create immutable value types, with less code
and less room for error, while **not restricting your freedom** to code any
aspect of your class just the way you want it.

## Design goals

AutoValue is the only solution to the value types problem in Java (that we are
aware of) having all of the following characteristics:

*   Usage is **API-invisible** (callers cannot become dependent on your choice
    to use it, or generally even tell the difference)
*   No required runtime dependencies
*   No performance penalty vs. hand-written code.
*   Virtually no limitations on what you can do with your class (private
    accessors, implementing interfaces, custom `hashCode()`, etc. See the
    "optional features" section.)
*   Minimal extralinguistic magic

## How to use AutoValue

The way to use AutoValue is a little surprising at first! Create your value type
as an abstract class, containing an abstract accessor method for each desired
field. This accessor can be any non-void, parameterless method. Add the
@AutoValue annotation to your class, and AutoValue will automatically generate
an implementing class.

### In `Example.java`

```java
import com.google.auto.value.AutoValue;

class Example {
  @AutoValue
  abstract static class Animal {
    static Animal create(String name, int numberOfLegs) {
      return new AutoValue_Example_Animal(name, numberOfLegs);
      // (or just AutoValue_Animal if this is not nested)
    }

    abstract String name();
    abstract int numberOfLegs();
  }
}
```

That's it! (In real life, some classes and methods would presumably be public
and have Javadoc.)

The central idea behind AutoValue is: if you can create an abstract class that
has one "obvious" reasonable implementation, a tool ought to be able to write
that implementation for you. And now it does.

### In `ExampleTest.java`

To a consumer, this looks and functions like any other object. The simple test
below illustrates that behavior. Note that in real life, you would write tests
that actually **do something interesting** with the object, instead of just
checking field values going in and out.

```java
public void testAnimal() {
  Animal dog = Animal.create("dog", 4);
  assertEquals("dog", dog.name());
  assertEquals(4, dog.numberOfLegs());

  // You really don't need to write tests like these; just illustrating.

  assertTrue(Animal.create("dog", 4).equals(dog));
  assertFalse(Animal.create("cat", 4).equals(dog));
  assertFalse(Animal.create("dog", 2).equals(dog));

  assertEquals("Animal{name=dog, numberOfLegs=4}", dog.toString());
}
```


### In `pom.xml`

For Maven users, add the following to your Maven configuration:

```xml
<dependency>
  <groupId>com.google.auto.value</groupId>
  <artifactId>auto-value</artifactId>
  <version>1.1</version>
  <scope>provided</scope>
</dependency>
```

### What's going on here?

AutoValue runs as a standard annotation processor in javac. It generates source
code, in your package, for a *package-private* implementation class called
**`AutoValue_Example_Animal`**. The generated class contains a field for each
abstract accessor method, and the generated constructor sets these fields. It
implements the accessor methods to simply return those references/values. It
implements equals to compare these values in the standard fashion, and
implements an appropriate corresponding `hashCode`. It implements `toString` to
return an unspecified string representation of the class.

Consumers of your value type don't need to know any of this. They just invoke
your provided factory method and get a well-behaved instance back.

### Builders

*(since 1.1)*

You may prefer to construct some objects through _builders_. If there is a
nested interface or abstract class that is annotated with `@AutoValue.Builder`,
then AutoValue will implement it to make it a builder class. The
`@AutoValue.Builder` interface or class is conventionally called `Builder`. It
must have method for every property to set the value of the property, and a
build method. Here is the example above written using builders.

```java
import com.google.auto.value.AutoValue;

class Example {
  @AutoValue
  abstract static class Animal {
    static Builder builder() {
      return new AutoValue_Example_Animal.Builder();
    }

    abstract String name();
    abstract int numberOfLegs();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder name(String s);
      abstract Builder numberOfLegs(int n);
      abstract Animal build();
    }
  }
}
```

Now client code can look something like this:

```java
Animal dog = Animal.builder().name("dog").numberOfLegs(4).build();
```

## Optional "features"

Many of the following are not even features of AutoValue itself, just features
of the Java language which (unlike some other solutions to the value types
problem) AutoValue doesn't get in the way of.

### Data hiding

Your accessors don't have to be public! They do have to be at least
package-private. The fields themselves cannot be directly accessed.

### Multiple creation paths

You can offer as many static creation methods as you need, named descriptively,
to cover different combinations of parameters. They do not need to be named
`create` as in the example.

### Default values with builders

Generated builders require every property to be set, except `@Nullable`
properties. If you want to define a default value for a property, set it in the
`builder()` method before returning. Here's how to define that animals have 4
legs by default:

```java
class Example {
  @AutoValue
  abstract static class Animal {
    static Builder builder() {
      return new AutoValue_Example_Animal.Builder()
          .numberOfLegs(4);
    }
    // ...remainder as before...
  }
}
```

### Nullability

By default, AutoValue inserts a not-null check for each non-primitive parameter
passed to the generated constructor. If your class has a property that is
allowed to be null, apply `@Nullable` to the corresponding accessor method and
factory parameter. This has two effects: AutoValue will skip the null check, and
generate null-friendly code for `equals` and `hashCode`. The `@Nullable`
annotation can be `javax.annotation.Nullable` or a `Nullable` annotation defined
in any other package.

### JavaBeans-style prefixes are optional

In the example above, we used the `name()` and `numberOfLegs()` methods to
define the properties of the object. If you prefer, you can use JavaBeans-style
method names, like `getName()` and `getNumberOfLegs()` to achieve the same
effect. The property names will still be `name` and `numberOfLegs`, for example
in the result of `toString()`. This applies only if every abstract method looks
like `getX()` or `boolean isX()` for some non-empty string X.

Similarly, in a builder you can use methods like `name(String s)` and
`numberOfLegs(int n)` or methods like `setName(String s)` and
`setNumberOfLegs(int n)`. Again, every abstract method must follow the same one
of the two conventions.

### Other preconditions or preprocessing

If you need to check preconditions or perform any other preparatory steps,
insert the code to do so into your static factory method before invoking the
generated constructor. Remember that null checks are already present in the
generated constructor.

When using builders, you can validate by implementing your own build() method
that calls the generated build method, conventionally called autoBuild(). You
can construct an object provisionally and inspect its properties before
returning it. Here's how our example might be validated:

```java
class Example {
  @AutoValue
  abstract static class Animal {
    ...
    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder name(String s);
      abstract Builder numberOfLegs(int n);

      abstract Animal autoBuild();
      Animal build() {
        Animal animal = autoBuild();
        if (animal.numberOfLegs() < 0) {
          throw new IllegalStateException("Negative legs");
        }
        return animal;
      }
    }
  }
}
```

If the Builder class is public, typically `build()` will be too but
`autoBuild()` will be package-private.

Sometimes it may be more convenient to consult the value in the builder before
calling `autoBuild()`. If the `Builder` class has an abstract method with the
same name and type as one of the methods in the `@AutoValue` class, it will be
implemented to return the value set on the builder, or throw an exception if no
value has been set. Like `autoBuild()`, these getter methods will typically not
be public.

```java
class Example {
  @AutoValue
  abstract static class Animal {
    abstract String name();
    abstract int numberOfLegs();

    ...

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder name(String s);
      abstract Builder numberOfLegs(int n);
      abstract int numberOfLegs();

      abstract Animal autoBuild();
      Animal build() {
        if (numberOfLegs() < 0) {
          throw new IllegalStateException("Negative legs");
        }
        return autoBuild();
      }
    }
  }
}
```

### Custom implementations

Don't like the `equals`, `hashCode` or `toString` method AutoValue generates?
You can underride it! Just write your own, directly in your abstract class;
AutoValue will see this and skip generating its own.

### Nesting

Your hand-written abstract value type can be nested at any level. The generated
implementation class is named `AutoValue_` plus each component of the class
named separated by underscores. For example, for the abstract class
`Foo.Bar.Qux`, the generated implementation class is `AutoValue_Foo_Bar_Qux`, in
the same package.

### Derived fields

If a field exists to cache a derived value, and should be ignored in `equals`,
`hashCode` and `toString`, define it directly in your class instead of providing
an abstract accessor for it. AutoValue will know nothing of it.

You may have fields you wish to ignore in equals for other reasons. We're sorry:
AutoValue doesn't work for these cases, since there's no way to pass the extra
parameter "through" the generated class constructor.

### Generics

An `@AutoValue` class can have type parameters, as illustrated in these
examples:

```java
@AutoValue
abstract class MapEntry<K extends Comparable<K>, V> implements Map.Entry<K, V> {
  static <K extends Comparable<K>, V> MapEntry<K, V> create(K key, V value) {
    return new AutoValue_MapEntry<K, V>(key, value);
  }
  ...
}
```

or

```java
@AutoValue
abstract class MapEntry<K extends Comparable<K>, V> implements Map.Entry<K, V> {
  static <K extends Comparable<K>, V> Builder<K, V> builder() {
    return new AutoValue_MapEntry.Builder<K, V>();
  }

  abstract static class Builder<K extends Comparable<K>, V> {
    abstract Builder setKey(K key);
    abstract Builder setValue(V value);
    abstract MapEntry<K, V> build();
  }
  ...
}
```

### Converting back to a builder

An `@AutoValue` class with a builder can optionally have an abstract method that
returns the builder type. It will be implemented by initializing the builder
with the values of the properties. This can be exposed directly to clients, or
used to implement "wither" methods that return a new object that is the same as
the original one except for one changed property. Here is an example:

```java
class Example {
  @AutoValue
  abstract static class Animal {
    static Builder builder() {
      return AutoValue_Example_Animal.Builder();
    }

    abstract Builder toBuilder();

    Animal withNumberOfLegs(int n) {
      return toBuilder().numberOfLegs(n).build();
    }
    // ...remainder as before...
  }
}
```

### Serialization

The generated class will be serializable if your abstract class implements
`Serializable`. It will be GWT-serializable if your abstract class is annotated
with `@GwtCompatible(serializable = true)` (any annotation with that name and
field will do, such as the one included in [Guava]).

(Ordinarily, abstract types should not be serializable, but in this case it's
harmless, since we truly expect no other implementations to ever exist.)

There is no way to mark individual fields as transient or customize the
serialization behavior.

[Guava]: https://github.com/google/guava

## Warnings

Use of AutoValue has one serious **negative consequence**: certain formerly safe
refactorings could now break your code, and be caught only by your tests.

If you are not using builders, you must ensure that parameters are passed to the
auto-generated constructor in the ***same order*** the corresponding accessor
methods are defined in the file. **Your tests must be sufficient** to catch any
field ordering problem. In most cases this should be the natural outcome from
testing whatever actual purpose this value type was created for! In other cases
a very simple test like the one shown above is enough.

We reserve the right to **change the `hashCode` implementation** at any time. Do
not depend on the order your objects appear in hash maps (use `ImmutableMap` or
`LinkedHashMap`!), and never persist these hash codes.


## Restrictions and non-features

*   Using AutoValue limits your public creation API to static factory methods,
    not constructors. See *Effective Java* Item 1 for several reasons this is
    usually a good idea anyway.

*   AutoValue does not and will not support creating *mutable* value types. (We
    may consider adding support for `withField`-style methods, which return a
    new immutable copy of the original instance with one field value changed.)

*   One `@AutoValue` class may not extend another. As explained in *Effective
    Java*, inheritance of value types simply doesn't work.

*   Object arrays are not and will not be supported, including multidimensional
    primitive array types. Use a `Collection` type instead, such as
    `ImmutableList`.

*   Your accessor methods may not be `private` -- but they may be
    package-private. The same is true for your `@AutoValue` class itself.

*   We don't generate `compareTo`, because we feel you need the expressiveness
    that [`ComparisonChain`] provides, and we can't beat it at its own game. If
    we find later that a large minority of AutoValue classes are implementing
    compareTo by the exact same formula, we will reconsider this feature.

*   AutoValue currently doesn't inspect the `new AutoValue_Foo` line to issue
    warnings on parameter order. (There are certain technical issues with doing
    so.) As stated above in Warnings, you had better have some test somewhere
    that will catch such problems. Or you can use builders to avoid having a
    parameter order at all.

*   It might seem convenient to use AutoValue to implement annotation
    interfaces, which frameworks such as [Guice] occasionally require. But in
    fact such interfaces must obey the contracts for `equals` and `hashCode`
    specified by java.lang.annotation.Annotation, and the implementations of
    those methods that AutoValue would generate do not. Instead, the
    com.google.auto.value package includes another annotation `@AutoAnnotation`
    specifically for this case. See its documentation for more details.

[`ComparisonChain`]: http://google.github.io/guava/releases/snapshot/api/docs/com/google/common/collect/ComparisonChain.html
[Guice]: https://github.com/google/guice/wiki/BindingAnnotations#user-content-binding-annotations-with-attributes

## Best practices

You should add a **package-private constructor** to your abstract class,
although we have omitted it for brevity in our examples. This accomplishes two
things: it prevents an undocumented public constructor (that no one can even
call) from appearing in your javadoc, and it prevents users outside your package
from creating their own subclasses (and potentially subverting your
immutability).

In fact, **you should virtually never need an alternative implementation of your
hand-written abstract class**, whether hand-written or generated by a mocking
framework. Your class can and should contain simple intrinsic behavior, but if
that behavior has enough complexity or enough dependencies that it actually
needs to be mocked or faked, split it into a separate type that is *not* a value
type. Otherwise it permits an instance with "real" behavior and one with
"mock/fake" behavior to be `equals`, which does not make sense. Keep your value
types straightforward.

Other code in the same package will be able to directly access the generated
class, *but should not*. It's best if each generated class has **one and only
one reference** from your source code. If you have multiple creation methods,
have them all call through to the same point, so there is still one call to the
generated file, and one place to insert preconditions, etc.

**Avoid mutable field types**, such as arrays, especially if you make your
accessor methods `public`. The generated accessors won't copy the field value on
its way out, so you'd be exposing your internal state. This doesn't mean your
factory method can't *accept* mutable types as input parameters. Example:

```java
@AutoValue
public abstract class ListExample {
  abstract ImmutableList<String> names();

  public static ListExample create(List<String> mutableNames) {
    return new AutoValue_ListExample(ImmutableList.copyOf(mutableNames));
  }
}
```

Finally, if you choose to provide an explicit `equals`, `hashCode` or `toString`
implementation, please make it **`final`**, so readers don't have to wonder
whether AutoValue is overriding it.

## Alternatives

This [slide presentation] walks through several competing solutions to this
problem and shows why we considered them insufficient.

[slide presentation]: https://docs.google.com/presentation/d/14u_h-lMn7f1rXE1nDiLX0azS3IkgjGl5uxp5jGJ75RE/edit
