package foo.qux;

import com.google.auto.factory.AutoFactory;

import foo.Foo;
import foo.FooFactory;

@AutoFactory(implementing = FooFactory.class)
public class Qux implements Foo {
}
