# AutoValue with Builders


The [introduction](index.md) of this User Guide covers the basic usage of
AutoValue using a static factory method as your public creation API. But in many
circumstances (such as those laid out in *Effective Java, 2nd Edition* Item 2),
you may prefer to let your callers use a *builder* instead.

Fortunately, AutoValue can generate builder classes too! This page explains how.
Note that we recommend reading and understanding the basic usage shown in the
[introduction](index.md) first.

## How to use AutoValue with Builders <a name="howto"></a>

As explained in the introduction, the AutoValue concept is that **you write an
abstract value class, and AutoValue implements it**. Builder generation works in
the exact same way: you also create an abstract builder class, nesting it inside
your abstract value class, and AutoValue generates implementations for both.

### In `Animal.java` <a name="example_java"></a>

```java
import com.google.auto.value.AutoValue;

@AutoValue
abstract class Animal {
  abstract String name();
  abstract int numberOfLegs();

  static Builder builder() {
    // The naming here will be different if you are using a nested class
    // e.g. `return new AutoValue_OuterClass_InnerClass.Builder();`
    return new AutoValue_Animal.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setName(String value);
    abstract Builder setNumberOfLegs(int value);
    abstract Animal build();
  }
}
```

Note that in real life, some classes and methods would presumably be public and
have **Javadoc**. We're leaving these off in the User Guide only to keep the
examples clean and short.

### Usage <a name="usage"></a>

```java
public void testAnimal() {
  Animal dog = Animal.builder().setName("dog").setNumberOfLegs(4).build();
  assertEquals("dog", dog.name());
  assertEquals(4, dog.numberOfLegs());

  // You probably don't need to write assertions like these; just illustrating.
  assertTrue(
      Animal.builder().setName("dog").setNumberOfLegs(4).build().equals(dog));
  assertFalse(
      Animal.builder().setName("cat").setNumberOfLegs(4).build().equals(dog));
  assertFalse(
      Animal.builder().setName("dog").setNumberOfLegs(2).build().equals(dog));

  assertEquals("Animal{name=dog, numberOfLegs=4}", dog.toString());
}
```

### What does AutoValue generate? <a name="generated"></a>

For the `Animal` example shown above, here is [typical code AutoValue might
generate](generated-builder-example.md).

## Warnings <a name="warnings"></a>

Be sure to put the static `builder()` method directly in your value class (e.g.,
`Animal`) and not the nested abstract `Builder` class. That ensures that the
`Animal` class is always initialized before `Builder`. Otherwise you may be
exposing yourself to initialization-order problems.

## <a name="howto"></a>How do I...

*   ... [use (or not use) `set` **prefixes**?](builders-howto.md#beans)
*   ... [use different **names** besides
    `builder()`/`Builder`/`build()`?](builders-howto.md#build_names)
*   ... [specify a **default** value for a property?](builders-howto.md#default)
*   ... [initialize a builder to the same property values as an **existing**
    value instance](builders-howto.md#to_builder)
*   ... [include `with-` methods on my value class for creating slightly
    **altered** instances?](builders-howto.md#withers)
*   ... [**validate** property values?](builders-howto.md#validate)
*   ... [**normalize** (modify) a property value at `build`
    time?](builders-howto.md#normalize)
*   ... [expose **both** a builder and a factory
    method?](builders-howto.md#both)
*   ... [handle `Optional` properties?](builders-howto.md#optional)
*   ... [use a **collection**-valued property?](builders-howto.md#collection)
    *   ... [let my builder **accumulate** values for a collection-valued
        property (not require them all at once)?](builders-howto.md#accumulate)
    *   ... [accumulate values for a collection-valued property, without
        **"breaking the chain"**?](builders-howto.md#add)
    *   ... [offer **both** accumulation and set-at-once methods for the same
        collection-valued property?](builders-howto.md#collection_both)
*   ... [access nested builders while
    building?](builders-howto.md#nested_builders)
*   ... [create a "step builder"?](builders-howto.md#step)
*   ... [create a builder for something other than an
    `@AutoValue`?](builders-howto.md#autobuilder)
*   ... [use a different build method for a
    property?](builders-howto.md#build_method)
