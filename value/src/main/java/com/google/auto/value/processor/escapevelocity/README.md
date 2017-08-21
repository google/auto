# EscapeVelocity summary

EscapeVelocity is a templating engine that can be used from Java. It is a reimplementation of a subset of
functionality from [Apache Velocity](http://velocity.apache.org/).

For a fuller explanation of Velocity's functioning, see its
[User Guide](http://velocity.apache.org/engine/releases/velocity-1.7/user-guide.html)

If EscapeVelocity successfully produces a result from a template evaluation, that result should be
the exact same string that Velocity produces. If not, that is a bug.

EscapeVelocity has no facilities for HTML escaping and it is not appropriate for producing
HTML output that might include portions of untrusted input.


## Motivation

Velocity has a convenient templating language. It is easy to read, and it has widespread support
from tools such as editors and coding websites. However, *using* Velocity can prove difficult.
Its use to generate Java code in the [AutoValue][AutoValue] annotation processor required many
[workarounds][VelocityHacks]. The way it dynamically loads classes as part of its standard operation
makes it hard to [shade](https://maven.apache.org/plugins/maven-shade-plugin/) it, which in the case
of AutoValue led to interference if Velocity was used elsewhere in a project.

EscapeVelocity has a simple API that does not involve any class-loading or other sources of
problems. It and its dependencies can be shaded with no difficulty.

## Loading a template

The entry point for EscapeVelocity is the `Template` class. To obtain an instance, use
`Template.from(Reader)`. If a template is stored in a file, that file conventionally has the
suffix `.vm` (for Velocity Macros). But since the argument is a `Reader`, you can also load
a template directly from a Java string, using `StringReader`.

Here's how you might make a `Template` instance from a template file that is packaged as a resource
in the same package as the calling class:

```java
InputStream in = getClass().getResourceAsStream("foo.vm");
if (in == null) {
  throw new IllegalArgumentException("Could not find resource foo.vm");
}
Reader reader = new BufferedReader(new InputStreamReader(in));
Template template = Template.parseFrom(reader);
```

## Expanding a template

Once you have a `Template` object, you can use it to produce a string where the variables in the
template are given the values you provide. You can do this any number of times, specifying the
same or different values each time.

Suppose you have this template:

```
The $language word for $original is $translated.
```

You might write this code:

```java
Map<String, String> vars = new HashMap<>();
vars.put("language", "French");
vars.put("original", "toe");
vars.put("translated", "orteil");
String result = template.evaluate(vars);
```

The `result` string would then be: `The French word for toe is orteil.`

## Comments

The characters `##` introduce a comment. Characters from `##` up to and including the following
newline are omitted from the template. This template has comments:

```
Line 1 ## with a comment
Line 2
```

It is the same as this template:
```
Line 1 Line 2
```

## References

EscapeVelocity supports most of the reference types described in the
[Velocity User Guide](http://velocity.apache.org/engine/releases/velocity-1.7/user-guide.html#References)

### Variables

A variable has an ASCII name that starts with a letter (a-z or A-Z) and where any other characters
are also letters or digits or hyphens (-) or underscores (_). A variable reference can be written
as `$foo` or as `${foo}`. The value of a variable can be of any Java type. If the value `v` of
variable `foo` is not a String then the result of `$foo` in a template will be `String.valueOf(v)`.
Variables must be defined before they are referenced; otherwise an `EvaluationException` will be
thrown.

Variable names are case-sensitive: `$foo` is not the same variable as `$Foo` or `$FOO`.

Initially the values of variables come from the Map that is passed to `Template.evaluate`. Those
values can be changed, and new ones defined, using the `#set` directive in the template:

```
#set ($foo = "bar")
```

Setting a variable affects later references to it in the template, but has no effect on the
`Map` that was passed in or on later template evaluations.

### Properties

If a reference looks like `$purchase.Total` then the value of the `$purchase` variable must be a
Java object that has a public method `getTotal()` or `gettotal()`, or a method called `isTotal()` or
`istotal()` that returns `boolean`. The result of `$purchase.Total` is then the result of calling
that method on the `$purchase` object.

If you want to have a period (`.`) after a variable reference *without* it being a property
reference, you can use braces like this: `${purchase}.Total`. If, after a property reference, you
have a further period, you can put braces around the reference like this:
`${purchase.Total}.nonProperty`.

### Methods

If a reference looks like `$purchase.addItem("scones", 23)` then the value of the `$purchase`
variable must be a Java object that has a public method `addItem` with two parameters that match
the given values. Unlike Velocity, EscapeVelocity requires that there be exactly one such method.
It is OK if there are other `addItem` methods provided they are not compatible with the
arguments provided.

Properties are in fact a special case of methods: instead of writing `$purchase.Total` you could
write `$purchase.getTotal()`. Braces can be used to make the method invocation explicit
(`${purchase.getTotal()}`) or to prevent method invocation (`${purchase}.getTotal()`).

### Indexing

If a reference looks like `$indexme[$i]` then the value of the `$indexme` variable must be a Java
object that has a public `get` method that takes one argument that is compatible with the index.
For example, `$indexme` might be a `List` and `$i` might be an integer. Then the reference would
be the result of `List.get(int)` for that list and that integer. Or, `$indexme` might be a `Map`,
and the reference would be the result of `Map.get(Object)` for the object `$i`. In general,
`$indexme[$i]` is equivalent to `$indexme.get($i)`.

Unlike Velocity, EscapeVelocity does not allow `$indexme` to be a Java array.

### Undefined references

If a variable has not been given a value, either by being in the initial Map argument or by being
set in the template, then referencing it will provoke an `EvaluationException`. There is
a special case for `#if`: if you write `#if ($var)` then it is allowed for `$var` not to be defined,
and it is treated as false.

### Setting properties and indexes: not supported

Unlke Velocity, EscapeVelocity does not allow `#set` assignments with properties or indexes:

```
#set ($data.User = "jon")        ## Allowed in Velocity but not in EscapeVelocity
#set ($map["apple"] = "orange")  ## Allowed in Velocity but not in EscapeVelocity
```

## Expressions

In certain contexts, such as the `#set` directive we have just seen or certain other directives,
EscapeVelocity can evaluate expressions. An expression can be any of these:

* A reference, of the kind we have just seen. The value is the value of the reference.
* A string literal enclosed in double quotes, like `"this"`. A string literal must appear on
  one line. EscapeVelocity does not support the characters `$` or `\\` in a string literal.
* An integer literal such as `23` or `-100`. EscapeVelocity does not support floating-point
  literals.
* A Boolean literal, `true` or `false`.
* Simpler expressions joined together with operators that have the same meaning as in Java:
  `!`, `==`, `!=`, `<`, `<=`, `>`, `>=`, `&&`, `||`, `+`, `-`, `*`, `/`, `%`. The operators have the
  same precedence as in Java.
* A simpler expression in parentheses, for example `(2 + 3)`.

Velocity supports string literals with single quotes, like `'this`' and also references within
strings, like `"a $reference in a string"`, but EscapeVelocity does not.

## Directives

A directive is introduced by a `#` character followed by a word. We have already seen the `#set`
directive, which sets the value of a variable. The other directives are listed below.

Directives can be spelled with or without braces, so `#set` or `#{set}`.

### `#if`/`#elseif`/`#else`

The `#if` directive selects parts of the template according as a condition is true or false.
The simplest case looks like this:

```
#if ($condition) yes #end
```

This evaluates to the string ` yes ` if the variable `$condition` is defined and has a true value,
and to the empty string otherwise. It is allowed for `$condition` not to be defined in this case,
and then it is treated as false.

The expression in `#if` (here `$condition`) is considered true if its value is not null and not
equal to the Boolean value `false`.

An `#if` directive can also have an `#else` part, for example:

```
#if ($condition) yes #else no #end
```

This evaluates to the string ` yes ` if the condition is true or the string ` no ` if it is not.

An `#if` directive can have any number of `#elseif` parts. For example:

```
#if ($i == 0) zero #elseif ($i == 1) one #elseif ($i == 2) two #else many #end
```

### `#foreach`

The `#foreach` directive repeats a part of the template once for each value in a list.

```
#foreach ($product in $allProducts)
  ${product}!
#end
```

This will produce one line for each value in the `$allProducts` variable. The value of
`$allProducts` can be a Java `Iterable`, such as a `List` or `Set`; or it can be an object array;
or it can be a Java `Map`. When it is a `Map` the `#foreach` directive loops over every *value*
in the `Map`.

If `$allProducts` is a `List` containing the strings `oranges` and `lemons` then the result of the
`#foreach` would be this:

```

  oranges!


  lemons!

```

When the `#foreach` completes, the loop variable (`$product` in the example) goes back to whatever
value it had before, or to being undefined if it was undefined before.

Within the `#foreach`, a special variable `$foreach` is defined, such that you can write
`$foreach.hasNext`, which will be true if there are more values after this one or false if this
is the last value. For example:

```
#foreach ($product in $allProducts)${product}#if ($foreach.hasNext), #end#end
```

This would produce the output `oranges, lemons` for the list above. (The example is scrunched up
to avoid introducing extraneous spaces, as described in the [section](#spaces) on spaces
below.)

Velocity gives the `$foreach` variable other properties (`index` and `count`) but EscapeVelocity
does not.

### Macros

A macro is a part of the template that can be reused in more than one place, potentially with
different parameters each time. In the simplest case, a macro has no arguments:

```
#macro (hello) bonjour #end
```

Then the macro can be referenced by writing `#hello()` and the result will be the string ` bonjour `
inserted at that point.

Macros can also have parameters:

```
#macro (greet $hello $world) $hello, $world! #end
```

Then `#greet("bonjour", "monde")` would produce ` bonjour, monde! `. The comma is optional, so
you could also write `#greet("bonjour" "monde")`.

When a macro completes, the parameters (`$hello` and `$world` in the example) go back to whatever
values they had before, or to being undefined if they were undefined before.

All macro definitions take effect before the template is evaluated, so you can use a macro at a
point in the template that is before the point where it is defined. This also means that you can't
define a macro conditionally:

```
## This doesn't work!
#if ($language == "French")
#macro (hello) bonjour #end
#else
#macro (hello) hello #end
#end
```

There is no particular reason to define the same macro more than once, but if you do it is the
first definition that is retained. In the `#if` example just above, the `bonjour` version will
always be used.

Macros can make templates hard to understand. You may prefer to put the logic in a Java method
rather than a macro, and call the method from the template using `$methods.doSomething("foo")`
or whatever.

## <a name="spaces"></a> Spaces

For the most part, spaces and newlines in the template are preserved exactly in the output.
To avoid unwanted newlines, you may end up using `##` comments. In the `#foreach` example above
we had this:

```
#foreach ($product in $allProducts)${product}#if ($foreach.hasNext), #end#end
```

That was to avoid introducing unwanted spaces and newlines. A more readable way to achieve the same
result is this:

```
#foreach ($product in $allProducts)##
${product}##
#if ($foreach.hasNext), #end##
#end
```

Spaces are ignored between the `#` of a directive and the `)` that closes it, so there is no trace
in the output of the spaces in `#foreach ($product in $allProducts)` or `#if ($foreach.hasNext)`.
Spaces are also ignored inside references, such as `$indexme[ $i ]` or `$callme( $i , $j )`.

If you are concerned about the detailed formatting of the text from the template, you may want to
post-process it. For example, if it is Java code, you could use a formatter such as
[google-java-format](https://github.com/google/google-java-format). Then you shouldn't have to
worry about extraneous spaces.

[VelocityHacks]: https://github.com/google/auto/blob/ca2384d5ad15a0c761b940384083cf5c50c6e839/value/src/main/java/com/google/auto/value/processor/TemplateVars.java#L54
[AutoValue]: https://github.com/google/auto/tree/master/value
