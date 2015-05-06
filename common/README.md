Auto Common Utilities
========

## Overview

The Auto project has a set of common utilities to help ease use of the annotation processing
environment.

## Utility classes of note

  * MoreTypes - utilities and Equivalence wrappers for TypeMirror and related subtypes
  * MoreElements - utilities for Element and related subtypes
  * SuperficialValidation - very simple scanner to ensure an Element is valid and free from
    distortion from upstream compilation errors
  * Visibility - utilities for working with Elements' visibility levels (public, protected, etc.)
  * BasicAnnotationProcessor/ProcessingStep - simple types that
    - implement a validating annotation processor
    - defer invalid elements until later
    - break processor actions into multiple steps (which may each handle different annotations)

## Usage/Setup

Auto common utilities have a standard [Maven](http://maven.apache.org) setup which can also be
used from Gradle, Ivy, Ant, or other systems which consume binary artifacts from the central Maven
binary artifact repositories.

```xml
<dependency>
  <groupId>com.google.auto</groupId>
  <artifactId>auto-common</artifactId>
  <version>1.0-SNAPSHOT</version> <!-- or use a known release version -->
</dependency>
```

## Processor Resilience

Auto Common Utilities is used by a variety of annotation processors in Google and new versions
may have breaking changes.  Users of auto-common are urged to use
[shade](https://maven.apache.org/plugins/maven-shade-plugin/) or
[jarjar](https://code.google.com/p/jarjar/) (or something similar) in packaging their processors
so that conflicting versions of this library do not adversely interact with each other.

For example, in a Maven build you can repackage `com.google.auto.common` into
`your.processor.shaded.auto.common` like this:

```xml
<project>
  <!-- your other config -->
  <build>
    <plugins>
      <plugin>
        <artifactId>maven-shade-plugin</artifactId>
        <executions>
          <execution>
            <phase>package</phase>
            <goals>
              <goal>shade</goal>
            </goals>
            <configuration>
              <artifactSet>
                <excludes>
                  <!-- exclude dependencies you don't want to bundle in your processor -->
                </excludes>
              </artifactSet>
              <relocations>
                <relocation>
                  <pattern>com.google.auto.common</pattern>
                  <shadedPattern>your.processor.shaded.auto.common</shadedPattern>
                </relocation>
              </relocations>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

