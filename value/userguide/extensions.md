# Extensions


AutoValue can be extended to implement new features for classes annotated with
`@AutoValue`.

## Using extensions

Each extension is a class. If that class is on the `processorpath` when you
compile your `@AutoValue` class, the extension can run.

Some extensions are triggered by their own annotations, which you add to your
class; others may be triggered in other ways. Consult the extension's
documentation for usage instructions.

## Writing an extension

To add a feature, write a class that extends [`AutoValueExtension`], and put
that class on the `processorpath` along with `AutoValueProcessor`.

`AutoValueExtension` uses the [`ServiceLoader`] mechanism, which means:

*   Your class must be public and have a public no-argument constructor.
*   Its fully-qualified name must appear in a file called
    `META-INF/services/com.google.auto.value.extension.AutoValueExtension` in a
    JAR that is on the compiler's `classpath` or `processorpath`.

You can use [AutoService] to make implementing the `ServiceLoader` pattern easy.

Without extensions, AutoValue generates a subclass of the `@AutoValue` class.
Extensions can work by generating a chain of subclasses, each of which alters
behavior by overriding or implementing new methods.

## TODO

*   How to distribute extensions.
*   List of known extensions.

[AutoService]: https://github.com/google/auto/tree/main/service
[`AutoValueExtension`]: https://github.com/google/auto/blob/main/value/src/main/java/com/google/auto/value/extension/AutoValueExtension.java
[`ServiceLoader`]: http://docs.oracle.com/javase/7/docs/api/java/util/ServiceLoader.html
