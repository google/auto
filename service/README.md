# AutoService

A configuration/metadata generator for java.util.ServiceLoader-style service
providers

## AutoWhat‽

[Java][java] annotation processors and other systems use
[java.util.ServiceLoader][sl] to register implementations of well-known types
using META-INF metadata. However, it is easy for a developer to forget to update
or correctly specify the service descriptors. \
AutoService generates this metadata for the developer, for any class annotated
with `@AutoService`, avoiding typos, providing resistance to errors from
refactoring, etc.

## Example

Say you have:

```java
package foo.bar;

import javax.annotation.processing.Processor;

@AutoService(Processor.class)
final class MyProcessor implements Processor {
  // …
}
```

AutoService will generate the file
`META-INF/services/javax.annotation.processing.Processor` in the output classes
folder. The file will contain:

```
foo.bar.MyProcessor
```

In the case of javax.annotation.processing.Processor, if this metadata file is
included in a jar, and that jar is on javac's classpath, then `javac` will
automatically load it, and include it in its normal annotation processing
environment. Other users of java.util.ServiceLoader may use the infrastructure
to different ends, but this metadata will provide auto-loading appropriately.

## Getting Started

You will need `auto-service-annotations-${version}.jar` in your compile-time
classpath, and you will need `auto-service-${version}.jar` in your
annotation-processor classpath.

In Maven, you can write:

```xml
<dependencies>
  <dependency>
    <groupId>com.google.auto.service</groupId>
    <artifactId>auto-service-annotations</artifactId>
    <version>${auto-service.version}</version>
  </dependency>
</dependencies>

...

<plugins>
  <plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
      <annotationProcessorPaths>
        <path>
          <groupId>com.google.auto.service</groupId>
          <artifactId>auto-service</artifactId>
          <version>${auto-service.version}</version>
        </path>
      </annotationProcessorPaths>
    </configuration>
  </plugin>
</plugins>
```

Alternatively, you can include the processor itself (which transitively depends
on the annotation) in your compile-time classpath. (However, note that doing so
may pull unnecessary classes into your runtime classpath.)

```xml
<dependencies>
  <dependency>
    <groupId>com.google.auto.service</groupId>
    <artifactId>auto-service</artifactId>
    <version>${version}</version>
    <optional>true</optional>
  </dependency>
</dependencies>
```

## License

    Copyright 2013 Google LLC

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
[sl]: http://docs.oracle.com/javase/6/docs/api/java/util/ServiceLoader.html
