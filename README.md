AutoFactory
======

A source code generator for JSR-330-compatible factories.

What is this?
-------------

[Java][java] is full of [factories](http://en.wikipedia.org/wiki/Factory_method_pattern).  They're mechanical, repetitive, typically untested and sometimes the source of subtle bugs. _Sounds like a job for robots!_

AutoFactory generates factories that can be used on their own or with [JSR-330](http://jcp.org/en/jsr/detail?id=330)-compatible [dependency injectors](http://en.wikipedia.org/wiki/Dependency_injection) from a simple annotation.

Save time.  Save code.  Save sanity.

Example
-------

Say you have:

```java
@AutoFactory
final class SomeClass {
  private final String providedDepA;
  private final String depB;

  SomeClass(@Provided @AQualifier String providedDepA, String depB) {
    this.providedDepA = providedDepA;
    this.depB = depB;
  }

  …
}
```

AutoFactory will generate:

```java
import javax.annotation.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(value = "com.google.autofactory.AutoFactoryProcessor")
final class SimpleClassMixedDepsFactory {
  private final Provider<String> providedDepAProvider;
  
  @Inject SimpleClassMixedDepsFactory(
      @AQualifier Provider<String> providedDepAProvider) {
    this.providedDepAProvider = providedDepAProvider;
  }
  
  SimpleClassMixedDeps create(String depB) {
    return new SimpleClassMixedDeps(providedDepAProvider.get(), depB);
  }
}
```

Download
--------

In order to activate code generation you will need to
include `autofactory-${autofactory.version}.jar` in your build at 
compile time.

In a Maven project, one would include the `autofactory-generator` 
artifact as an "optional" dependency:

```xml
<dependencies>
  <dependency>
    <groupId>com.google.autofactory</groupId>
    <artifactId>autofactory-generator</artifactId>
    <version>${autofactory.version}</version>
    <optional>true</optional>
  </dependency>
</dependencies>
```

You can also find downloadable .jars on the [GitHub download page](http://github.com/google/autofactory/downloads).



License
-------

    Copyright 2013 Google, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[java]: https://en.wikipedia.org/wiki/Java_(programming_language)

