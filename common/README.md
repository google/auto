Auto Common Utilities
========

## Overview

The Auto project has a set of common utilities to help ease use of the annotation processing environment. 

## Utility classes of note

  * MoreTypes - utilities and Equivalence wrappers for TypeMirror and related subtypes
  * MoreElements - utilities for Element and related subtypes
  * SuperficialValidation - very simple scanner to ensure an Element is valid and free from distortion from upstream compilation errors
  * Visibility - utilities for working with Elements' visibility levels (public, protected, etc.)

## Usage/Setup

Auto common utilities have a standard maven setup which can be used from Gradle, Ivy, Ant, or other systems which consume binary artifacts from the central maven repositories. 

```xml
<dependency>
  <groupId>com.google.auto</groupId>
  <artifactId>auto-common</artifactId>
  <version>1.0-SNAPSHOT</version> <!-- or use a known release version -->
</dependency>
```
