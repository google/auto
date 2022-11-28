# AutoValue


*Generated immutable value classes for Java 7+* <br />
***Éamonn McManus, Kevin Bourrillion*** <br />
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

**Note**: If you are using Kotlin then its
[data classes](https://kotlinlang.org/docs/data-classes.html) are usually more
appropriate than AutoValue. Likewise, if you are using a version of Java that
has [records](https://docs.oracle.com/en/java/javase/17/language/records.html),
then those are usually more appropriate. For a detailed comparison of AutoValue
and records, including information on how to migrate from one to the other, see
[here](records.md).<br>
You can still use [AutoBuilder](autobuilder.md) to make builders for data
classes or records.

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
    return new AutoValue_Animal(name, numberOfLegs);
  }

  abstract String name();
  abstract int numberOfLegs();
}
```

The constructor parameters correspond, in order, to the abstract accessor
methods.

**For a nested class**, see ["How do I use AutoValue with a nested class"](howto.md#nested).

Note that in real life, some classes and methods would presumably be public and
have Javadoc. We're leaving these off in the User Guide only to keep the
examples short and simple.

### With Maven

You will need `auto-value-annotations-${auto-value.version}.jar` in your
compile-time classpath, and you will need `auto-value-${auto-value.version}.jar`
in your annotation-processor classpath.

For `auto-value-annotations`, you can write this in `pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>com.google.auto.value</groupId>
    <artifactId>auto-value-annotations</artifactId>
    <version>${auto-value.version}</version>
  </dependency>
</dependencies>
```

Some AutoValue annotations have CLASS retention. This is mostly of use for
compile-time tools, such as AutoValue itself. If you are creating
a library, the end user rarely needs to know the original AutoValue annotations.
In that case, you can set the scope to `provided`, so that the user of your
library does not have `auto-value-annotations` as a transitive dependency.

```xml
<dependencies>
  <dependency>
    <groupId>com.google.auto.value</groupId>
    <artifactId>auto-value-annotations</artifactId>
    <version>${auto-value.version}</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

For `auto-value` (the annotation processor), you can write this:

```xml
<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <annotationProcessorPaths>
          <path>
            <groupId>com.google.auto.value</groupId>
            <artifactId>auto-value</artifactId>
            <version>${auto-value.version}</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>
```

Alternatively, you can include the processor itself in your compile-time
classpath. Doing so may pull unnecessary classes into your runtime classpath.

```xml
<dependencies>
  <dependency>
    <groupId>com.google.auto.value</groupId>
    <artifactId>auto-value</artifactId>
    <version>${auto-value.version}</version>
    <optional>true</optional>
  </dependency>
</dependencies>
```

### With Gradle

Gradle users can declare the dependencies in their `build.gradle` script:

```groovy
dependencies {
  compileOnly         "com.google.auto.value:auto-value-annotations:${autoValueVersion}"
  annotationProcessor "com.google.auto.value:auto-value:${autoValueVersion}"
}
```

Note: For java-library projects, use `compileOnlyApi` (or `api` for Gradle
versions prior to 6.7) instead of `compileOnly`. For Android projects, use `api`
instead of `compileOnly`. If you are using a version prior to 4.6, you must
apply an annotation processing plugin
[as described in these instructions][tbroyer-apt].

[tbroyer-apt]: https://plugins.gradle.org/plugin/net.ltgt.apt

### <a name="usage"></a>Usage

Your choice to use AutoValue is essentially *API-invisible*. This means that, to
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
generates source code, in your package, of a concrete implementation class which
extends your abstract class, having:

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

## <a name="versions"></a>What Java versions does it work with?

AutoValue requires that your compiler be at least Java 8. However, the code that
it generates is compatible with Java 7. That means that you can use it with
`-source 7 -target 7` or (for Java 9+) `--release 7`.

## <a name="more_howto"></a>How do I...

How do I...

*   ... [also generate a **builder** for my value class?](howto.md#builder)
*   ... [use AutoValue with a **nested** class?](howto.md#nested)
*   ... [use (or not use) JavaBeans-style name **prefixes**?](howto.md#beans)
*   ... [use **nullable** properties?](howto.md#nullable)
*   ... [perform other **validation**?](howto.md#validate)
*   ... [use a property of a **mutable** type?](howto.md#mutable_property)
*   ... [use a **custom** implementation of `equals`, etc.?](howto.md#custom)
*   ... [have AutoValue implement a concrete or default
    method?](howto.md#concrete)
*   ... [have multiple **`create`** methods, or name it/them
    differently?](howto.md#create)
*   ... [**ignore** certain properties in `equals`, etc.?](howto.md#ignore)
*   ... [have AutoValue also implement abstract methods from my
    **supertypes**?](howto.md#supertypes)
*   ... [use AutoValue with a **generic** class?](howto.md#generic)
*   ... [make my class Java- or GWT\-**serializable**?](howto.md#serialize)
*   ... [use AutoValue to **implement** an **annotation**
    type?](howto.md#annotation)
*   ... [also include **setter** (mutator) methods?](howto.md#setters)
*   ... [also generate **`compareTo`**?](howto.md#compareTo)
*   ... [use a **primitive array** for a property
    value?](howto.md#primitive_array)
*   ... [use an **object array** for a property value?](howto.md#object_array)
*   ... [have one `@AutoValue` class **extend** another?](howto.md#inherit)
*   ... [keep my accessor methods **private**?](howto.md#private_accessors)
*   ... [expose a **constructor**, not factory method, as my public creation
    API?](howto.md#public_constructor)
*   ... [use AutoValue on an **interface**, not abstract
    class?](howto.md#interface)
*   ... [**memoize** ("cache") derived properties?](howto.md#memoize)
*   ... [memoize the result of `hashCode` or
    `toString`?](howto.md#memoize_hash_tostring)
*   ... [make a class where only one of its properties is ever
    set?](howto.md#oneof)
*   ... [copy annotations from a class/method to the implemented
    class/method/field?](howto.md#copy_annotations)
*   ... [create a **pretty string** representation?](howto.md#toprettystring)

<!-- TODO(kevinb): should the above be only a selected subset? -->
