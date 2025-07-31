# Auto Common Utilities

## Overview

The Auto project has a set of common utilities to help ease use of the
annotation processing environment.

## Utility classes of note

`MoreTypes`
:   Utilities and `Equivalence` wrappers for `TypeMirror` and related subtypes

`MoreElements`
:   Utilities for `Element` and related subtypes

`SuperficialValidation`
:   Very simple scanner to ensure an `Element` is valid and free from distortion
    from upstream compilation errors

`Visibility`
:   Utilities for working with `Element`s' visibility levels (public, protected,
    etc.)

`BasicAnnotationProcessor`/`Step`
:   Simple types that
    -   implement a validating annotation processor
    -   defer invalid elements until later
    -   break processor actions into multiple steps (which may each handle
        different annotations)

## Usage/Setup

Auto common utilities have a standard [Maven](http://maven.apache.org) setup
which can also be used from Gradle, Ivy, Ant, or other systems which consume
binary artifacts from the central Maven binary artifact repositories.

```xml
<dependency>
  <groupId>com.google.auto</groupId>
  <artifactId>auto-common</artifactId>
  <version>1.0-SNAPSHOT</version> <!-- or use a known release version -->
</dependency>
```
