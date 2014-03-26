AutoValue
=========
*Immutable value-type code generation for Java 1.6+* <br />
***Kevin Bourrillion, Éamonn McManus*** <br />
**Google, Inc.**

> "AutoValue may sound like a cheesy newspaper for 
> unloading your jalopy on an unsuspecting stranger, 
> but it's actually a great tool for eliminating the 
> drudgery of writing mundane value classes in Java. 
> It encapsulates much of the advice in Effective 
> Java Chapter 2, and frees you to concentrate on 
> the more interesting aspects of your program. 
> The resulting program is likely to be shorter,
> clearer, and freer of bugs. Two thumbs up."
>  
> – Joshua Bloch, author, Effective Java


Contents
------------------
<!-- generated with http://doctoc.herokuapp.com/ -->

- [Background](#background)
- [Design goals](#design-goals)
- [How to use AutoValue](#how-to-use-autovalue)
  - [In Example.java](#in-examplejava)
  - [In ExampleTest.java](#in-exampletestjava)
  - [In pom.xml](#in-pomxml)
  - [What’s going on here?](#whats-going-on-here)
- [Optional "features"](#optional-features)
  - [Data hiding](#data-hiding)
  - [Multiple creation paths](#multiple-creation-paths)
  - [Nullability](#nullability)
  - [Other preconditions or preprocessing](#other-preconditions-or-preprocessing)
  - [Custom implementations](#custom-implementations)
  - [Nesting](#nesting)
  - [Derived fields](#derived-fields)
  - [Serialization](#serialization)
- [Warnings](#warnings)
- [Restrictions and non-features](#restrictions-and-non-features)
- [Best practices](#best-practices)
- [Alternatives](#alternatives)
- [Troubleshooting](#troubleshooting)


Background
------------------

Classes with value semantics are extremely common in Java.
These are classes for which object identity is irrelevant,
and instances are considered interchangeable based only on
the equality of their field values. We will refer to these
classes as value types in this document.

To implement a value type safely and properly requires
implementing `equals`, `hashCode` and `toString` in a bloated,
repetitive, formulaic yet error-prone fashion. These methods
are not especially time-consuming to write, especially with
the aid of IDE templates and a few Guava helpers, but they
are a continual burden to reviewers, editors and future
readers. Their wide expanses of boilerplate sharply decrease
the signal-to-noise ratio of your code, and constitute
probably the single greatest violation of the DRY (Don’t
Repeat Yourself) principle we are ever forced to commit.

AutoValue provides an easier way to create immutable value
types, with less code and less room for error, while **not
restricting your freedom** to code any aspect of your class
just the way you want it.


Design goals
------------------

AutoValue is the only solution to the value types problem in
Java (that we are aware of) having all of the following
characteristics:

  * Usage is **API-invisible** (callers cannot become
    dependent on your choice to use it, or generally
    even tell the difference)
  * No required runtime dependencies
  * No performance penalty vs. hand-written code.
  * Virtually no limitations on what you can do with your
    class (private accessors, implementing interfaces, 
    custom `hashCode()`, etc. See the "optional features" 
    section.)
  * Minimal extralinguistic magic


How to use AutoValue
------------------

The way to use AutoValue is a little surprising at first!
Create your value type as an abstract class, containing
an abstract accessor method for each desired field. This
accessor can be any non-void, parameterless method. Add
the @AutoValue annotation to your class, and AutoValue will
automatically generate an implementing class.

### In `Example.java`

```java
    import com.google.auto.value.AutoValue;
    
    /** Javadoc. (In real life, it would be on the methods too.) */
    @AutoValue
    public abstract class Example {
      public static Example create(String name, int integer) {
        return new AutoValue_Example(name, integer);
      }
      public abstract String name();
      public abstract int integer();
    }
```

The central idea behind AutoValue is: if you can create
an abstract class that has one "obvious" reasonable
implementation, a tool ought to be able to write that
implementation for you. And now it does.

### In `ExampleTest.java`

To a consumer, this looks and functions like any other object.
The simple test below illustrates that behavior. Note that in
real life, you would write tests that actually **do something
interesting** with the object, instead of just checking field
values going in and out.

```java
    public void testExample() {
      Example ex = Example.create("happy", 23);
      assertEquals(“happy”, ex.name());
      assertEquals(23, ex.integer());
    
      // You really don't need to write tests like these; just illustrating.
      
      assertTrue(Example.create("happy", 23).equals(ex));
      assertFalse(Example.create("sad", 23).equals(ex));
      assertFalse(Example.create("happy", 24).equals(ex));
      
      assertEquals("Example{name=happy, integer=23}", ex.toString());
    }
```

### In `pom.xml`

add the following to your Maven configuration:

```xml
    <dependency>
      <groupId>com.google.auto.value</groupId>
      <artifactId>auto-value</artifactId>
      <version>1.0-rc1</version>
      <scope>provided</scope>
    </dependency>
```

Of course, please upgrade to the final 1.0 release once it is 
available (likely in April 2014).

### What’s going on here?

AutoValue runs as a standard annotation processor in javac.
It generates source code, in your package, for a *package-private*
implementation class called **`AutoValue_Example`**. The generated
class contains a field for each abstract accessor method, and
the generated constructor sets these fields. It implements the
accessor methods to simply return those references/values. It
implements equals to compare these values in the standard
fashion, and implements an appropriate corresponding `hashCode`.
It implements `toString` to return an unspecified string
representation of the class.

Consumers of your value type don’t need to know any of this.
They just invoke your provided factory method and get a
well-behaved instance back.


Optional "features"
------------------
Many of the following are not even features of AutoValue itself,
just features of the Java language which (unlike some other 
solutions to the value types problem) AutoValue doesn't get in
the way of.

### Data hiding
Your accessors don't have to be public! They do have to be at 
least package-private. The fields themselves cannot be directly 
accessed.

### Multiple creation paths
You can offer as many static creation methods as you need, named
descriptively, to cover different combinations of parameters.
They do not need to be named `create` as in the example.

### Nullability
By default, AutoValue inserts a not-null check for each non-primitive
parameter passed the generated constructor. If your class has a
property that is allowed to be null, apply `@Nullable` to the
corresponding accessor method and factory parameter. This has two
effects: AutoValue will skip the null check, and generate null-friendly
code for `equals` and `hashCode`. The `@Nullable` annotation can be
`javax.annotation.Nullable` or a `Nullable` annotation defined in
any other package.

### Other preconditions or preprocessing
If you need to check preconditions or perform any other preparatory
steps, insert the code to do so into your static factory method before
invoking the generated constructor. Remember that null checks are
already present in the generated constructor.

### Custom implementations
Don't like the `equals`, `hashCode` or `toString` method AutoValue
generates? You can underride it! Just write your own, directly in your
abstract class; AutoValue will see this and skip generating its own.

### Nesting
For a nested abstract value type called `Foo.Bar.Qux`, the generated
implementation class is named `AutoValue_Foo_Bar_Qux`.

### Derived fields
If a field exists to cache a derived value, and should be ignored in
`equals`, `hashCode` and `toString`, define it directly in your class
instead of providing an abstract accessor for it. AutoValue will know
nothing of it.

You may have fields you wish to ignore in equals for other reasons.
We're sorry: AutoValue doesn't work for these cases, since there’s no
way to pass the extra parameter "through" the generated class
constructor.

###Serialization
As you might expect, the generated class will be serializable if your
abstract class implements `Serializable`. (Ordinarily, abstract types
should not implement `Serializable`, but in this case it's harmless,
since we truly expect no other implementations to ever exist.) There
is no way to mark individual fields as transient or customize the
serialization behavior.


Warnings
------------------

Use of AutoValue has one serious **negative consequence**: certain 
formerly safe refactorings could now break your code, and be caught
only by your tests.

You must ensure that parameters are passed to the auto-generated
constructor in the ***same order*** the corresponding accessor
methods are defined in the file. **Your tests must be sufficient**
to catch any field ordering problem. In most cases this should
be the natural outcome from testing whatever actual purpose this
value type was created for! In other cases a very simple test
like the one shown above is enough.

We reserve the right to **change the `hashCode` implementation**
at any time. Do not depend on the order your objects appear in
hash maps (use `ImmutableMap` or `LinkedHashMap`!), and never
persist these hash codes.


Restrictions and non-features
------------------

* Using AutoValue limits your public creation API to static 
  factory methods, not constructors. See *Effective Java* Item 1
  for several reasons this is usually a good idea anyway.

* AutoValue does not and will not support creating *mutable* 
  value types. (We may consider adding support for 
  `withField`-style methods, which return a new immutable 
  copy of the original instance with one field value changed.)

* One `@AutoValue` class may not extend another. As explained in
  *Effective Java*, inheritance of value types simply doesn't work.

* Object arrays are not and will not be supported, including
  multidimensional primitive array types. Use a `Collection` type
  instead, such as `ImmutableList`.

* Your accessor methods may not be `private` -- but they may be 
  package-private. The same is true for your `@AutoValue` class 
  itself (if it is a nested class).

* We don’t generate `compareTo`, because we feel you need the
  expressiveness that [ComparisonChain][1] provides, and we can't beat
  it at its own game. If we find later that a large minority of 
  AutoValue classes are implementing compareTo by the exact same 
  formula, we will reconsider this feature.

* Many users have asked for AutoValue to generate a builder
  class. We explored this idea deeply. It is much more complex
  than it seems, especially because field values often need 
  validation. We also feel that, unlike the simple value
  objects themselves, there is a lot of natural variation in
  how builders for different types should be written, and it
  would be a disservice for us to start coercing everything
  into the same mold.
* AutoValue currently doesn’t inspect the new `AutoValue_Foo`
  line to issue warnings on parameter order. (There are
  certain technical issues with doing so.) As stated above
  in Warnings, you had better have some test somewhere that
  will catch such problems.


Best practices
------------------

You should add a **package-private constructor** to your abstract
class, although we have omitted it for brevity in our examples.
This accomplishes two things: it prevents an undocumented public
constructor (that no one can even call) from appearing in your
javadoc, and it prevents users outside your package from creating
their own subclasses (and potentially subverting your
immutability).

Other code in the same package will be able to directly access
the generated class, *but should not*. It’s best if each generated
class has **one and only one reference** from your source code.
If you have multiple creation methods, have them all call through
to the same point, so there is still one call to the generated file,
and one place to insert preconditions, etc.

**Avoid mutable field types**, especially if you make your
accessor methods `public`. The generated accessors won't copy the
field value on its way out, so you'd be exposing your internal
state. This doesn’t mean your factory method can't *accept*
mutable types as input parameters. Example:

```java
    @AutoValue
    public abstract class ListExample {
      abstract ImmutableList<String> names();
    
      public static ListExample create(List<String> mutableNames) {
        return new AutoValue_ListExample(ImmutableList.copyOf(mutableNames));
      }
    }
```

Primitive arrays are arguably an exception to this, as in this
case only AutoValue does return a copy of the internal array
from the generated accessor. It does not automatically copy the
data on its way in, however, so your static factory method should
pass `array.clone()` in to the generated constructor instead of
the input `array` itself.

Finally, if you choose to provide an explicit `equals`, `hashCode`
or `toString` implementation, please make it **`final`**, so readers
don't have to wonder whether AutoValue is overriding it.


Alternatives
------------------
[This slide presentation][2]
walks through several competing solutions to this problem and shows
why we considered them insufficient.

[1]: http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/collect/ComparisonChain.html
[2]: https://docs.google.com/presentation/d/14u_h-lMn7f1rXE1nDiLX0azS3IkgjGl5uxp5jGJ75RE/edit


Troubleshooting
------------------

If you get an error `error: cannot find symbol` related to the AutoValue
generated class, and you are using nested classes, make sure you are
using the correct generated name that includes the nesting classes. See
the [Nesting](#Nesting) section.
