# How do I...

This page answers common how-to questions that may come up when using AutoValue.
You should read and understand the [Introduction](index.md) first.

Questions specific to usage of the **builder option** are documented separately;
for this, start by reading [AutoValue with builders](builders.md).

## Contents

How do I...

*   ... [also generate a **builder** for my value class?](#builder)
*   ... [use AutoValue with a **nested** class?](#nested)
*   ... [use (or not use) JavaBeans-style name **prefixes**?](#beans)
*   ... [use **nullable** properties?](#nullable)
*   ... [perform other **validation**?](#validate)
*   ... [use a property of a **mutable** type?](#mutable_property)
*   ... [use a **custom** implementation of `equals`, etc.?](#custom)
*   ... [**ignore** certain properties in `equals`, etc.?](#ignore)
*   ... [have multiple **create** methods, or name it/them differently?]
    (#create)
*   ... [have AutoValue also implement abstract methods from my **supertypes**?]
    (#supertypes)
*   ... [use AutoValue with a **generic** class?](#generic)
*   ... [make my class Java- or GWT- **serializable**?](#serialize)
*   ... [apply an **annotation** to a generated **field**?](#annotate_field)
*   ... [use AutoValue to **implement** an **annotation** type?](#annotation)
*   ... [also include **setter** (mutator) methods?](#setters)
*   ... [also generate **`compareTo`**?](#compareTo)
*   ... [use a **primitive array** for a property value?](#primitive_array)
*   ... [use an **object array** for a property value?](#object_array)
*   ... [have one `@AutoValue` class **extend** another?](#inherit)
*   ... [keep my accessor methods **private**?](#private_accessors)
*   ... [expose a **constructor**, not factory method, as my public creation
    API?](#public_constructor)
*   ... [use AutoValue on an **interface**, not abstract class?](#interface)

## ... also generate a builder for my value class? {#builder}

Please see [AutoValue with builders](builders.md).

## ... use AutoValue with a nested class? {#nested}

AutoValue composes the generated class name in the form
*`AutoValue_Outer_Middle_Inner`*; use as many of these underscore-separated
segments as required. Only the simple class name will appear in `toString`
output.

## ... use (or not use) JavaBeans-style name prefixes? {#beans}

Some developers prefer to name their accessors with a `get-` or `is-` prefix,
but would prefer that only the "bare" property name be used in `toString` and
for the generated constructor's parameter names.

AutoValue will do exactly this, but only if you are using these prefixes
*consistently*. In that case, it infers your intended property name by first
stripping the `get-` or `is-` prefix, then adjusting the case of what remains as
specified by [Introspector.decapitalize]
(http://docs.oracle.com/javase/8/docs/api/java/beans/Introspector.html#decapitalize).

Note that, in keeping with the JavaBeans specification, the `is-` prefix is only
allowed on `boolean`-returning methods. `get-` is allowed on any type of
accessor.

## ... use nullable properties? {#nullable}

Ordinarily the generated constructor will reject any null values. If you want to
accept null, simply apply any annotation named `@Nullable` to the appropriate
accessor methods. This causes AutoValue to remove the null checks and generate
null-friendly code for `equals`, `hashCode` and `toString`.

You may also wish to add `@Nullable` to the appropriate parameters of your
public factory method, but doing this doesn't affect AutoValue.

## ... perform other validation? {#validate}

Null checks are added automatically (as [above](#nullable)). For other types of
precondition checks or pre-processing, just add them to your factory method:

```java
static MyType create(String first, String second) {
  checkArgument(!first.isEmpty());
  return new AutoValue_MyType(first, second.trim());
}
```

## ... use a property of a mutable type? {#mutable_property}

First, check if the mutable type has a corresponding immutable cousin. For
example, the types `List<String>` and `String[]` have the immutable counterpart
`ImmutableList<String>` in [Guava](http://github.com/google/guava). If so, use
the immutable type for your property, and only accept the mutable type during
construction:

```java
@AutoValue
public abstract class ListExample {
  public static ListExample create(String[] mutableNames) {
    return new AutoValue_ListExample(ImmutableList.copyOf(mutableNames));
  }

  public abstract ImmutableList<String> names();
}
```

Note: this is a perfectly sensible practice, not an ugly workaround!

If there is no suitable immutable type to use, you'll need to proceed with
caution. Your static factory method should pass a *clone* of the passed object
to the generated constructor. Your accessor method should document a very loud
warning never to mutate the object returned.

```java
@AutoValue
public abstract class MutableExample {
  public static MutableExample create(MutablePropertyType ouch) {
    // Replace `.clone` below with the right copying code for this type
    return new AutoValue_MutableExample(ouch.clone());
  }

  /**
   * Returns the ouch associated with this object; <b>do not mutate</b> the
   * returned object.
   */
  public abstract MutablePropertyType ouch();
}
```

Note: this is a perfectly ugly workaround, not a sensible practice!

## ... use a custom implementation of `equals`, etc.? {#custom}

Simply write your custom implementation; AutoValue will notice this and will
skip generating its own. Your hand-written logic will thus be inherited on the
concrete implementation class. We call this *underriding* the method.

Remember when doing this that you are losing AutoValue's protections. Be careful
to follow the basic rules of hash codes: equal objects must have equal hash
codes *always*, and equal hash codes should imply equal objects *almost always*.
You should now test your class more thoroughly, ideally using [`EqualsTester`]
(http://static.javadoc.io/com.google.guava/guava-testlib/18.0/com/google/common/testing/EqualsTester.html)
from [guava-testlib](http://github.com/google/guava).

Best practice: mark your underriding methods `final` to make it clear to future
readers that these methods aren't overridden by AutoValue.

Note that this also works if the underriding method was defined in one of your
abstract class's supertypes. If this is the case and you *want* AutoValue to
override it, you can "re-abstract" the method in your own class:

```java
@AutoValue
class PleaseOverrideExample extends SuperclassThatDefinesToString {
  ...

  // cause AutoValue to generate this even though the superclass has it
  @Override public abstract String toString();
}
```

<!-- TODO(kevinb): should the latter part have been split out? -->

## ... have multiple `create` methods, or name it/them differently? {#create}

Just do it! AutoValue doesn't actually care. This [best practice item]
(practices.md#one_reference) may be relevant.

## ... ignore certain properties in `equals`, etc.? {#ignore}

Suppose your value class has an extra field that shouldn't be included in
`equals` or `hashCode` computations. One common reason is that it is a "cached"
or derived value based on other properties. In this case, simply define the
field in your abstract class directly; AutoValue will have nothing to do with
it:

```java
@AutoValue
abstract class DerivedExample {
  static DerivedExample create(String realProperty) {
    return new AutoValue_DerivedExample(realProperty);
  }

  abstract String realProperty();

  private String derivedProperty;

  String derivedProperty() {
    // non-thread-safe example
    if (derivedProperty == null) {
      derivedProperty = realProperty().toLowerCase();
    }
  }
}
```

On the other hand, if the value is user-specified, not derived, the solution is
slightly more elaborate (but still reasonable):

```java
@AutoValue
abstract class IgnoreExample {
  static IgnoreExample create(String normalProperty, String ignoredProperty) {
    IgnoreExample ie = new AutoValue_IgnoreExample(normalProperty);
    ie.ignoredProperty = ignoredProperty;
    return ie;
  }

  abstract String normalProperty();

  private String ignoredProperty; // sadly, it can't be `final`

  String ignoredProperty() {
    return ignoredProperty;
  }
}
```

Note that in both cases the field ignored for `equals` and `hashCode` is also
ignored by `toString`; to AutoValue it simply doesn't exist.

## ... have AutoValue also implement abstract methods from my supertypes? {#supertypes}

AutoValue will recognize every abstract accessor method whether it is defined
directly in your own hand-written class or in a supertype.

<!-- TODO(kevinb): what about the order? -->

## ... use AutoValue with a generic class? {#generic}

There's nothing to it: just add type parameters to your class and to your call
to the generated constructor.

## ... make my class Java- or GWT-serializable? {#serialize}

Just add `implements Serializable` or the `@GwtCompatible(serializable = true)`
annotation (respectively) to your hand-written class; it (as well as any
`serialVersionUID`) will be duplicated on the generated class, and you'll be
good to go.

## ... apply an annotation to a generated field? {#annotate_field}

Your only option is to put the same annotation on your hand-written abstract
accessor method, if possible.

## ... use AutoValue to implement an annotation type? {#annotation}

Most users should never have the need to programmatically create "fake"
annotation instances. But if you do, using `@AutoValue` in the usual way will
fail because the `Annotation.hashCode` specification is incompatible with
AutoValue's behavior.

However, we've got you covered anyway! Suppose this annotation definition:

```java
public @interface Named {
  String value();
}
```

All you need is this:

```java
public class Names {
  @AutoAnnotation public static Named named(String value) {
    return new AutoAnnotation_Names_named(value);
  }
}
```

For more details, see the [`AutoAnnotation` javadoc]
(http://github.com/google/auto/blob/master/value/src/main/java/com/google/auto/value/AutoAnnotation.java#L24).

## ... also include setter (mutator) methods? {#setters}

You can't; AutoValue only generates immutable value classes.

Note that giving value semantics to a mutable type is widely considered a
questionable practice in the first place. Equal instances of a value class are
treated as *interchangeable*, but they can't truly be interchangeable if one
might be mutated and the other not.

## ... also generate `compareTo`? {#compareTo}

AutoValue intentionally does not provide this feature. It is better for you to
roll your own comparison logic using the new methods added to [`Comparator`]
(https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html) in Java 8,
or [`ComparisonChain`]
(http://google.github.io/guava/releases/snapshot/api/docs/com/google/common/collect/ComparisonChain.html)
from [Guava](http://github.com/google/guava).

Since these mechanisms are easy to use, require very little code, and give you
the flexibility you need, there's really no way for AutoValue to improve on
them!

## ... use a primitive array for a property value? {#primitive_array}

Go right ahead! AutoValue will generate code that acts on the *values* stored
the array, not the object identity of the array itself, which is (with virtual
certainty) what you want. Heed the warnings given above about [mutable
properties](#mutable_property).

## ... use an object array for a property value? {#object_array}

This is not allowed. Object arrays are very badly-behaved and unlike primitive
arrays, they can be replaced with a proper `List` implementation for very little
added cost.


If it's important to accept an object array at construction time, refer to the
*first* example shown [here](#mutable_property).

## ... have one `@AutoValue` class extend another? {#inherit}

This ability is intentionally not supported, because there is no way to do it
correctly. See *Effective Java, 2nd Edition* Item 8.

## ... keep my accessor methods private? {#private_accessors}

We're sorry. This is one of the rare and unfortunate restrictions AutoValue's
approach places on your API. Your accessor methods don't have to be *public*,
but they must be at least package-visible.

## ... expose a constructor, not factory method, as my public creation API? {#public_constructor}

We're sorry. This is one of the rare restrictions AutoValue's approach places on
your API. However, note that static factory methods are recommended over public
constructors by *Effective Java*, Item 1.

## ... use AutoValue on an interface, not abstract class? {#interface}

Interfaces are not allowed. The only advantage of interfaces we're aware of is
that you can omit `public abstract` from the methods. That's not much. On the
other hand, you would lose the immutability guarantee, and you'd also invite
more of the kind of bad behavior described in [this best-practices item]
(practices.md#simple). On balance, we don't think it's worth it.

