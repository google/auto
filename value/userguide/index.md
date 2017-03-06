# AutoValue


*Generated immutable value classes for Java 1.6+* <br />
***Kevin Bourrillion, Éamonn McManus*** <br />
**Google, Inc.**

> "AutoValue is a great tool for eliminating the drudgery of writing mundane
> value classes in Java. It encapsulates much of the advice in Effective Java
> Chapter 2, and frees you to concentrate on the more interesting aspects of
> your program. The resulting program is likely to be shorter, clearer, and
> freer of bugs. Two thumbs up."
>
> -- *Joshua Bloch, author, Effective Java*


## <a name="background"></a>Background

**Value classes** are extremely common in Java projects. These are classes for
which you want to treat any two instances with suitably equal field values as
interchangeable. That's right: we're talking about those classes where you wind
up implementing `equals`, `hashCode` and `toString` in a bloated, repetitive,
formulaic yet error-prone fashion.

Writing these methods the first time is not too bad, with the aid of a few
helper methods and IDE templates. But once written they continue to burden
reviewers, editors and future readers. Their wide expanses of boilerplate
sharply decrease the signal-to-noise ratio of your code... and they love to
harbor hard-to-spot bugs.

AutoValue provides an easier way to create immutable value classes, with a lot
less code and less room for error, while **not restricting your freedom** to
code almost any aspect of your class exactly the way you want it.

This page will walk you through how to use AutoValue. Looking for a little more
persuasion? Please see [Why AutoValue?](why.md).

## <a name="howto"></a>How to use AutoValue

The AutoValue concept is extremely simple: **You write an abstract class, and
AutoValue implements it.** That is all there is to it; there is literally *no*
configuration.

**Note:** Below, we will illustrate an AutoValue class *without* a generated
builder class. If you're more interested in the builder support, continue
reading at [AutoValue with Builders](builders.md) instead.

### <a name="example_java"></a>In your value class

Create your value class as an *abstract* class, with an abstract accessor method
for each desired property, and bearing the `@AutoValue` annotation.

```java
import com.google.auto.value.AutoValue;

@AutoValue
abstract class Animal {
  static Animal create(String name, int numberOfLegs) {
    // See "How do I...?" below for nested classes.
    return new AutoValue_Animal(name, numberOfLegs);
  }

  abstract String name();
  abstract int numberOfLegs();
}
```

Note that in real life, some classes and methods would presumably be public and
have Javadoc. We're leaving these off in the User Guide only to keep the
examples short and simple.

### In `pom.xml`

Maven users should add the following to the project's `pom.xml` file:

```xml
<dependency>
  <groupId>com.google.auto.value</groupId>
  <artifactId>auto-value</artifactId>
  <version>1.2</version>
  <scope>provided</scope>
</dependency>
```

Gradle users should install the annotation processing plugin [as described in
these instructions][tbroyer-apt] and then use it in the `build.gradle` script:

```groovy
dependencies {
  compileOnly "com.google.auto.value:auto-value:1.2"
  apt         "com.google.auto.value:auto-value:1.2"
}
```

[tbroyer-apt]: https://plugins.gradle.org/plugin/net.ltgt.apt


### <a name="usage"></a>Usage

Your choice to use AutoValue is essentially *API-invisible*. That means that to
the consumer of your class, your class looks and functions like any other. The
simple test below illustrates that behavior. Note that in real life, you would
write tests that actually *do something interesting* with the object, instead of
only checking field values going in and out.

```java
public void testAnimal() {
  Animal dog = Animal.create("dog", 4);
  assertEquals("dog", dog.name());
  assertEquals(4, dog.numberOfLegs());

  // You probably don't need to write assertions like these; just illustrating.
  assertTrue(Animal.create("dog", 4).equals(dog));
  assertFalse(Animal.create("cat", 4).equals(dog));
  assertFalse(Animal.create("dog", 2).equals(dog));

  assertEquals("Animal{name=dog, numberOfLegs=4}", dog.toString());
}
```

### <a name="whats_going_on"></a>What's going on here?

AutoValue runs inside `javac` as a standard annotation processor. It reads your
abstract class and infers what the implementation class should look like. It
generates source code, in your package, of a concrete implementation class
which extends your abstract class, having:

*   package visibility (non-public)
*   one field for each of your abstract accessor methods
*   a constructor that sets these fields
*   a concrete implementation of each accessor method returning the associated
    field value
*   an `equals` implementation that compares these values in the usual way
*   an appropriate corresponding `hashCode`
*   a `toString` implementation returning a useful (but unspecified) string
    representation of the instance

Your hand-written code, as shown above, delegates its factory method call to the
generated constructor and voilà!

For the `Animal` example shown above, here is [typical code AutoValue might
generate](generated-example.md).

Note that *consumers* of your value class *don't need to know any of this*. They
just invoke your provided factory method and get a well-behaved instance back.

## <a name="warnings"></a>Warnings

Be careful that you don't accidentally pass parameters to the generated
constructor in the wrong order. You must ensure that **your tests are
sufficient** to catch any field ordering problem. In most cases this should be
the natural outcome from testing whatever actual purpose this value class was
created for! In other cases a very simple test like the one shown above is
enough. Consider switching to use the [builder option](builders.md) to avoid
this problem.

We reserve the right to **change the `hashCode` implementation** at any time.
Never persist the result of `hashCode` or use it for any other unintended
purpose, and be careful never to depend on the order your values appear in
unordered collections like `HashSet`.

## <a name="why"></a>Why should I use AutoValue?

See [Why AutoValue?](why.md).

## <a name="more_howto"></a>How do I...

How do I...

*   ... [also generate a **builder** for my value class?](howto.md#builder)
*   ... [use AutoValue with a **nested** class?](howto.md#nested)
*   ... [use (or not use) JavaBeans-style name **prefixes**?](howto.md#beans)
*   ... [use **nullable** properties?](howto.md#nullable)
*   ... [perform other **validation**?](howto.md#validate)
*   ... [use a property of a **mutable** type?](howto.md#mutable_property)
*   ... [use a **custom** implementation of `equals`, etc.?](howto.md#custom)
*   ... [**ignore** certain properties in `equals`, etc.?](howto.md#ignore)
*   ... [have multiple **create** methods, or name it/them differently?]
    (howto.md#create)
*   ... [have AutoValue also implement abstract methods from my **supertypes**?]
    (howto.md#supertypes)
*   ... [use AutoValue with a **generic** class?](howto.md#generic)
*   ... [make my class Java- or GWT- **serializable**?](howto.md#serialize)
*   ... [apply an **annotation** to a generated **field**?]
    (howto.md#annotate_field)
*   ... [use AutoValue to **implement** an **annotation** type?]
    (howto.md#annotation)
*   ... [also include **setter** (mutator) methods?](howto.md#setters)
*   ... [also generate **`compareTo`**?](howto.md#compareTo)
*   ... [use a **primitive array** for a property value?]
    (howto.md#primitive_array)
*   ... [use an **object array** for a property value?](howto.md#object_array)
*   ... [have one `@AutoValue` class **extend** another?](howto.md#inherit)
*   ... [keep my accessor methods **private**?](howto.md#private_accessors)
*   ... [expose a **constructor**, not factory method, as my public creation
    API?](howto.md#public_constructor)
*   ... [use AutoValue on an **interface**, not abstract class?]
    (howto.md#interface)
*   ... [**memoize** derived properties?](howto.md#memoize)

<!-- TODO(kevinb): should the above be only a selected subset? -->

## <a name="more"></a>More information

See the links in the sidebar at the top left.

<!-- TODO(kevinb): there are some tidbits of information that don't seem to
     belong anywhere yet; such as how it implements floating-point equality -->

