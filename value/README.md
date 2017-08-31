# AutoValue

*Generated immutable value classes for Java 1.6+* <br />
***Kevin Bourrillion, Éamonn McManus*** <br />
**Google, Inc.**

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

For more information, consult the
[detailed
documentation](userguide/index.md)
