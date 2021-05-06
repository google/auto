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
*   ... [have AutoValue implement a concrete or default method?](#concrete)
*   ... [have multiple **`create`** methods, or name it/them
    differently?](#create)
*   ... [**ignore** certain properties in `equals`, etc.?](#ignore)
*   ... [have AutoValue also implement abstract methods from my
    **supertypes**?](#supertypes)
*   ... [use AutoValue with a **generic** class?](#generic)
*   ... [make my class Java- or GWT\-**serializable**?](#serialize)
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
*   ... [**memoize** ("cache") derived properties?](#memoize)
*   ... [memoize the result of `hashCode` or
    `toString`?](#memoize_hash_tostring)
*   ... [make a class where only one of its properties is ever set?](#oneof)
*   ... [copy annotations from a class/method to the implemented
    class/method/field?](#copy_annotations)
*   ... [create a **pretty string** representation?](#toprettystring)

## <a name="builder"></a>... also generate a builder for my value class?

Please see [AutoValue with builders](builders.md).

## <a name="nested"></a>... use AutoValue with a nested class?

AutoValue composes the generated class name in the form
`AutoValue_`*`Outer_Middle_Inner`*.
As many of these segments will be used in the generated name as required.
Only the simple class name will appear in `toString` output.

```java
class Outer {
  static class Middle {
    @AutoValue
    abstract static class Inner {
      static Inner create(String foo) {
        return new AutoValue_Outer_Middle_Inner(foo);
      }
      ...
```

## <a name="beans"></a>... use (or not use) JavaBeans-style name prefixes?

Some developers prefer to name their accessors with a `get-` or `is-` prefix,
but would prefer that only the "bare" property name be used in `toString` and
for the generated constructor's parameter names.

AutoValue will do exactly this, but only if you are using these prefixes
*consistently*. In that case, it infers your intended property name by first
stripping the `get-` or `is-` prefix, then adjusting the case of what remains as
specified by
[Introspector.decapitalize](http://docs.oracle.com/javase/8/docs/api/java/beans/Introspector.html#decapitalize).

Note that, in keeping with the JavaBeans specification, the `is-` prefix is only
allowed on `boolean`-returning methods. `get-` is allowed on any type of
accessor.

## <a name="nullable"></a>... use nullable properties?

Ordinarily the generated constructor will reject any null values. If you want to
accept null, simply apply any annotation named `@Nullable` to the appropriate
accessor methods. This causes AutoValue to remove the null checks and generate
null-friendly code for `equals`, `hashCode` and `toString`. Example:

```java
@AutoValue
public abstract class Foo {
  public static Foo create(@Nullable Bar bar) {
    return new AutoValue_Foo(bar);
  }

  @Nullable abstract Bar bar();
}
```

This example also shows annotating the corresponding `create` parameter with
`@Nullable`. AutoValue does not actually require this annotation, only the one
on the accessor, but we recommended it as useful documentation to your caller.
Conversely, if `@Nullable` is only added to the parameter in `create` (or
similarly the setter method of [AutoValue.Builder](builders)), but not the
corresponding accessor method, it won't have any effect.

## <a name="validate"></a>... perform other validation?

Null checks are added automatically (as [above](#nullable)). For other types of
precondition checks or pre-processing, just add them to your factory method:

```java
static MyType create(String first, String second) {
  checkArgument(!first.isEmpty());
  return new AutoValue_MyType(first, second.trim());
}
```

## <a name="mutable_property"></a>... use a property of a mutable type?

AutoValue classes are meant and expected to be immutable. But sometimes you
would want to take a mutable type and use it as a property. In these cases:

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
    // Replace `MutablePropertyType.copyOf()` below with the right copying code for this type
    return new AutoValue_MutableExample(MutablePropertyType.copyOf(ouch));
  }

  /**
   * Returns the ouch associated with this object; <b>do not mutate</b> the
   * returned object.
   */
  public abstract MutablePropertyType ouch();
}
```

Warning: this is an ugly workaround, not a perfectly sensible practice! Callers
can trivially break the invariants of the immutable class by mutating the
accessor's return value. An example where something can go wrong: AutoValue
objects can be used as keys in Maps.

## <a name="custom"></a>... use a custom implementation of `equals`, etc.?

Simply write your custom implementation; AutoValue will notice this and will
skip generating its own. Your hand-written logic will thus be inherited on the
concrete implementation class. We call this *underriding* the method.

Remember when doing this that you are losing AutoValue's protections. Be careful
to follow the basic rules of hash codes: equal objects must have equal hash
codes *always*, and equal hash codes should imply equal objects *almost always*.
You should now test your class more thoroughly, ideally using
[`EqualsTester`](http://static.javadoc.io/com.google.guava/guava-testlib/19.0/com/google/common/testing/EqualsTester.html)
from [guava-testlib](http://github.com/google/guava).

Best practice: mark your underriding methods `final` to make it clear to future
readers that these methods aren't overridden by AutoValue.

## <a name="concrete"></a>... have AutoValue implement a concrete or default method?

If a parent class defines a concrete (non-abstract) method that you would like
AutoValue to implement, you can *redeclare* it as abstract. This applies to
`Object` methods like `toString()`, but also to property methods that you would
like to have AutoValue implement. It also applies to default methods in
interfaces.

```java
@AutoValue
class PleaseOverrideExample extends SuperclassThatDefinesToString {
  ...

  // cause AutoValue to generate this even though the superclass has it
  @Override public abstract String toString();
}
```

```java
@AutoValue
class PleaseReimplementDefaultMethod implements InterfaceWithDefaultMethod {
  ...

  // cause AutoValue to implement this even though the interface has a default
  // implementation
  @Override public abstract int numberOfLegs();
}
```

## <a name="create"></a>... have multiple `create` methods, or name it/them differently?

Just do it! AutoValue doesn't actually care. This
[best practice item](practices.md#one_reference) may be relevant.

## <a name="ignore"></a>... ignore certain properties in `equals`, etc.?

Suppose your value class has an extra field that shouldn't be included in
`equals` or `hashCode` computations.

If this is because it is a derived value based on other properties, see [How do
I memoize derived properties?](#memoize).

Otherwise, first make certain that you really want to do this. It is often, but
not always, a mistake. Remember that libraries will treat two equal instances as
absolutely *interchangeable* with each other. Whatever information is present in
this extra field could essentially "disappear" when you aren't expecting it, for
example when your value is stored and retrieved from certain collections.

If you're sure, here is how to do it:

```java
@AutoValue
abstract class IgnoreExample {
  static IgnoreExample create(String normalProperty, String ignoredProperty) {
    IgnoreExample ie = new AutoValue_IgnoreExample(normalProperty);
    ie.ignoredProperty.set(ignoredProperty);
    return ie;
  }

  abstract String normalProperty();

  private final AtomicReference<String> ignoredProperty = new AtomicReference<>();

  final String ignoredProperty() {
    return ignoredProperty.get();
  }
}
```

Note that this means the field is also ignored by `toString`; to AutoValue
it simply doesn't exist.

Note that we use `AtomicReference<String>` to ensure that other threads will
correctly see the value that was written. You could also make the field
`volatile`, or use `synchronized` (`synchronized (ie)` around the assignment and
`synchronized` on the `ignoredProperty()` method).

## <a name="supertypes"></a>... have AutoValue also implement abstract methods from my supertypes?

AutoValue will recognize every abstract accessor method whether it is defined
directly in your own hand-written class or in a supertype.

These abstract methods can come from more than one place, for example from an
interface and from the superclass. It may not then be obvious what order they
are in, even though you need to know this order if you want to call the
generated `AutoValue_Foo` constructor. You might find it clearer to use a
[builder](builders.md) instead. But the order is deterministic: within a class
or interface, methods are in the order they appear in the source code; methods
in ancestors come before methods in descendants; methods in interfaces come
before methods in classes; and in a class or interface that has more than one
superinterface, the interfaces are in the order of their appearance in
`implements` or `extends`.

## <a name="generic"></a>... use AutoValue with a generic class?

There's nothing to it: just add type parameters to your class and to your call
to the generated constructor.

## <a name="serialize"></a>... make my class Java- or GWT\-serializable?

Just add `implements Serializable` or the `@GwtCompatible(serializable = true)`
annotation (respectively) to your hand-written class; it (as well as any
`serialVersionUID`) will be duplicated on the generated class, and you'll be
good to go.

## <a name="annotation"></a>... use AutoValue to implement an annotation type?

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

For more details, see the [`AutoAnnotation`
javadoc](http://github.com/google/auto/blob/master/value/src/main/java/com/google/auto/value/AutoAnnotation.java#L24).

## <a name="setters"></a>... also include setter (mutator) methods?

You can't; AutoValue only generates immutable value classes.

Note that giving value semantics to a mutable type is widely considered a
questionable practice in the first place. Equal instances of a value class are
treated as *interchangeable*, but they can't truly be interchangeable if one
might be mutated and the other not.

## <a name="compareTo"></a>... also generate `compareTo`?

AutoValue intentionally does not provide this feature. It is better for you to
roll your own comparison logic using the new methods added to
[`Comparator`](https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html)
in Java 8, or
[`ComparisonChain`](https://guava.dev/releases/snapshot/api/docs/com/google/common/collect/ComparisonChain.html)
from [Guava](http://github.com/google/guava).

Since these mechanisms are easy to use, require very little code, and give you
the flexibility you need, there's really no way for AutoValue to improve on
them!

## <a name="primitive_array"></a>... use a primitive array for a property value?

Go right ahead! AutoValue will generate code that acts on the *values* stored
the array, not the object identity of the array itself, which is (with virtual
certainty) what you want. Heed the warnings given above about [mutable
properties](#mutable_property).

## <a name="object_array"></a>... use an object array for a property value?

This is not allowed. Object arrays are very badly-behaved and unlike primitive
arrays, they can be replaced with a proper `List` implementation for very little
added cost.

If it's important to accept an object array at construction time, refer to the
*first* example shown [here](#mutable_property).

## <a name="inherit"></a>... have one `@AutoValue` class extend another?

This ability is intentionally not supported, because there is no way to do it
correctly. See *Effective Java, 2nd Edition* Item 8: "Obey the general contract
when overriding equals".

## <a name="private_accessors"></a>... keep my accessor methods private?

We're sorry. This is one of the rare and unfortunate restrictions AutoValue's
approach places on your API. Your accessor methods don't have to be *public*,
but they must be at least package-visible.

## <a name="public_constructor"></a>... expose a constructor, not factory method, as my public creation API?

We're sorry. This is one of the rare restrictions AutoValue's approach places on
your API. However, note that static factory methods are recommended over public
constructors by *Effective Java*, Item 1.

## <a name="interface"></a>... use AutoValue on an interface, not abstract class?

AutoValue classes can certainly implement an interface, however an interface may
not be used in lieu of an abstract class. The only advantage of interfaces we're
aware of is that you can omit `public abstract` from the methods. That's not
much. On the other hand, you would lose the immutability guarantee, and you'd
also invite more of the kind of bad behavior described in
[this best-practices item](practices.md#simple). On balance, we don't think it's
worth it.

## <a name="memoize"></a>... memoize ("cache") derived properties?

Sometimes your class has properties that are derived from the ones that
AutoValue implements. You'd typically implement them with a concrete method that
uses the other properties:

```java
@AutoValue
abstract class Foo {
  abstract Bar barProperty();

  String derivedProperty() {
    return someFunctionOf(barProperty());
  }
}
```

But what if `someFunctionOf(Bar)` is expensive? You'd like to calculate it only
one time, then cache and reuse that value for all future calls. Normally,
thread-safe lazy initialization involves a lot of tricky boilerplate.

Instead, just write the derived-property accessor method as above, and
annotate it with [`@Memoized`]. Then AutoValue will override that method to
return a stored value after the first call:

```java
@AutoValue
abstract class Foo {
  abstract Bar barProperty();

  @Memoized
  String derivedProperty() {
    return someFunctionOf(barProperty());
  }
}
```

Then your method will be called at most once, even if multiple threads attempt
to access the property concurrently.

The annotated method must have the usual form of an accessor method, and may not
be `abstract`, `final`, or `private`.

The stored value will not be used in the implementation of `equals`, `hashCode`,
or `toString`.

If a `@Memoized` method is also annotated with `@Nullable`, then `null` values
will be stored; if not, then the overriding method throws `NullPointerException`
when the annotated method returns `null`.

[`@Memoized`]: https://github.com/google/auto/blob/master/value/src/main/java/com/google/auto/value/extension/memoized/Memoized.java

## <a name="memoize_hash_tostring"></a>... memoize the result of `hashCode` or `toString`?

You can also make your class remember and reuse the result of `hashCode`,
`toString`, or both, like this:

```java
@AutoValue
abstract class Foo {
  abstract Bar barProperty();

  @Memoized
  @Override
  public abstract int hashCode();

  @Memoized
  @Override
  public abstract String toString();
}
```

## <a name="oneof"></a>... make a class where only one of its properties is ever set?

Often, the best way to do this is using inheritance. Although one
`@AutoValue` class can't inherit from another, two `@AutoValue` classes can
inherit from a common parent.

```java
public abstract class StringOrInteger {
  public abstract String representation();

  public static StringOrInteger ofString(String s) {
    return new AutoValue_StringOrInteger_StringValue(s);
  }

  public static StringOrInteger ofInteger(int i) {
    return new AutoValue_StringOrInteger_IntegerValue(i);
  }

  @AutoValue
  abstract static class StringValue extends StringOrInteger {
    abstract String string();

    @Override
    public String representation() {
      return '"' + string() + '"';
    }
  }

  @AutoValue
  abstract static class IntegerValue extends StringOrInteger {
    abstract int integer();

    @Override
    public String representation() {
      return Integer.toString(integer());
    }
  }
}
```

So any `StringOrInteger` instance is actually either a `StringValue` or an
`IntegerValue`. Clients only care about the `representation()` method, so they
don't need to know which it is.

But if clients of your class may want to take different actions depending on
which property is set, there is an alternative to `@AutoValue` called
`@AutoOneOf`. This effectively creates a
[*tagged union*](https://en.wikipedia.org/wiki/Tagged_union).
Here is `StringOrInteger` written using `@AutoOneOf`, with the
`representation()` method moved to a separate client class:

```java
@AutoOneOf(StringOrInteger.Kind.class)
public abstract class StringOrInteger {
  public enum Kind {STRING, INTEGER}
  public abstract Kind getKind();

  public abstract String string();

  public abstract int integer();

  public static StringOrInteger ofString(String s) {
    return AutoOneOf_StringOrInteger.string(s);
  }

  public static StringOrInteger ofInteger(int i) {
    return AutoOneOf_StringOrInteger.integer(i);
  }
}

public class Client {
  public String representation(StringOrInteger stringOrInteger) {
    switch (stringOrInteger.getKind()) {
      case STRING:
        return '"' + stringOrInteger.string() + '"';
      case INTEGER:
        return Integer.toString(stringOrInteger.integer());
    }
    throw new AssertionError(stringOrInteger.getKind());
  }
}
```

Switching on an enum like this can lead to more robust code than using
`instanceof` checks, especially if a tool like [Error
Prone](https://errorprone.info/bugpattern/MissingCasesInEnumSwitch) can alert you
if you add a new variant without updating all your switches. (On the other hand,
if nothing outside your class references `getKind()`, you should consider if a
solution using inheritance might be better.)

There must be an enum such as `Kind`, though it doesn't have to be called `Kind`
and it doesn't have to be nested inside the `@AutoOneOf` class. There must be an
abstract method returning the enum, though it doesn't have to be called
`getKind()`. For every value of the enum, there must be an abstract method with
the same name (ignoring case and underscores). An `@AutoOneOf` class called
`Foo` will then get a generated class called `AutoOneOf_Foo` that has a static
factory method for each property, with the same name. In the example, the
`STRING` value in the enum corresponds to the `string()` property and to the
`AutoOneOf_StringOrInteger.string` factory method.

Properties in an `@AutoOneOf` class can be `void` to indicate that the
corresponding variant has no data. In that case, the factory method for that
variant has no parameters:

```java
@AutoOneOf(Transform.Kind.class)
public abstract class Transform {
  public enum Kind {NONE, CIRCLE_CROP, BLUR}
  public abstract Kind getKind();

  abstract void none();

  abstract void circleCrop();

  public abstract BlurTransformParameters blur();

  public static Transform ofNone() {
    return AutoOneOf_Transform.none();
  }

  public static Transform ofCircleCrop() {
    return AutoOneOf_Transform.circleCrop();
  }

  public static Transform ofBlur(BlurTransformParmeters params) {
    return AutoOneOf_Transform.blur(params);
  }
}
```

Here, the `NONE` and `CIRCLE_CROP` variants have no associated data but are
distinct from each other. The `BLUR` variant does have data. The `none()`
and `circleCrop()` methods are package-private; they must exist to configure
`@AutoOneOf`, but calling them is not very useful. (It does nothing if the
instance is of the correct variant, or throws an exception otherwise.)

The `AutoOneOf_Transform.none()` and `AutoOneOf_Transform.circleCrop()` methods
return the same instance every time they are called.

If one of the `void` variants means "none", consider using an `Optional<Transform>` or
a `@Nullable Transform` instead of that variant.

Properties in an `@AutoOneOf` class cannot be null. Instead of a
`StringOrInteger` with a `@Nullable String`, you probably want a
`@Nullable StringOrInteger` or an `Optional<StringOrInteger>`, or an empty
variant as just described.

## <a name="copy_annotations"></a>... copy annotations from a class/method to the implemented class/method/field?

### Copying to the generated class

If you want to copy annotations from your `@AutoValue`-annotated class to the
generated `AutoValue_...` implemention, annotate your class with
[`@AutoValue.CopyAnnotations`].

For example, if `Example.java` is:

```java
@AutoValue
@AutoValue.CopyAnnotations
@SuppressWarnings("Immutable") // justification ...
abstract class Example {
  // details ...
}
```

Then `@AutoValue` will generate `AutoValue_Example.java`:

```java
@SuppressWarnings("Immutable")
final class AutoValue_Example extends Example {
  // implementation ...
}
```

Applying `@AutoValue.CopyAnnotations` to an `@AutoValue.Builder` class like
`Foo.Builder` similarly causes annotations on that class to be copied to the
generated subclass `AutoValue_Foo.Builder`.

### Copying to the generated method

For historical reasons, annotations on methods of an `@AutoValue`-annotated
class are copied to the generated implementation class's methods. However, if
you want to exclude some annotations from being copied, you can use
[`@AutoValue.CopyAnnotations`]'s `exclude` method to stop this behavior.

### Copying to the generated field

If you want to copy annotations from your `@AutoValue`-annotated class's methods
to the generated fields in the `AutoValue_...` implementation, annotate your
method with [`@AutoValue.CopyAnnotations`].

For example, if `Example.java` is:

```java
@Immutable
@AutoValue
abstract class Example {
  @CopyAnnotations
  @SuppressWarnings("Immutable") // justification ...
  abstract Object getObject();

  // other details ...
}
```

Then `@AutoValue` will generate `AutoValue_Example.java`:

```java
final class AutoValue_Example extends Example {
  @SuppressWarnings("Immutable")
  private final Object object;

  @SuppressWarnings("Immutable")
  @Override
  Object getObject() {
    return object;
  }

  // other details ...
}
```

[`@AutoValue.CopyAnnotations`]: http://static.javadoc.io/com.google.auto.value/auto-value/1.6/com/google/auto/value/AutoValue.CopyAnnotations.html

## <a name="toprettystring"></a>... create a pretty string representation?

If you have a value class with a long `toString()` representation, annotate a
method with [`@ToPrettyString`] and AutoValue will generate an implementation that
returns a pretty String rendering of the instance. For example:

```java
@AutoValue
abstract class Song {
  abstract String lyrics();
  abstract List<Artist> artists();

  @ToPrettyString
  abstract String toPrettyString();
}
```

Below is a sample rendering of the result of calling `toPrettyString()`.

```
Song {
  lyrics = I'm off the deep end, watch as I dive in
    I'll never meet the ground
    Crash through the surface, where they can't hurt us
    We're far from the shallow now.,
  artists = [
    Artist {
      name = Lady Gaga,
    },
    Artist {
      name = Bradley Cooper,
    }
  ],
}
```

`@ToPrettyString` can be used on the default `toString()` to override the
default AutoValue-generated `toString()` implementation, or on another
user-defined method.

[`@ToPrettyString`]: https://github.com/google/auto/blob/master/value/src/main/java/com/google/auto/value/extension/toprettystring/ToPrettyString.java
