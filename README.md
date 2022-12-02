# Auto

[![Build Status](https://github.com/google/auto/actions/workflows/ci.yml/badge.svg)](https://github.com/google/auto/actions/workflows/ci.yml)

A collection of source code generators for [Java][java].

## Overview

[Java][java] is full of code that is mechanical, repetitive, typically untested
and sometimes the source of subtle bugs. _Sounds like a job for robots!_

The Auto subprojects are a collection of code generators that automate those
types of tasks. They create the code you would have written, but without
the bugs.

Save time.  Save code.  Save sanity.

## Subprojects

  * [AutoFactory] - JSR-330-compatible factories

    [![Maven Central](https://img.shields.io/maven-central/v/com.google.auto.factory/auto-factory.svg)](https://mvnrepository.com/artifact/com.google.auto.factory/auto-factory)

  * [AutoService] - Provider-configuration files for [`ServiceLoader`]

    [![Maven Central](https://img.shields.io/maven-central/v/com.google.auto.service/auto-service.svg)](https://mvnrepository.com/artifact/com.google.auto.service/auto-service)

  * [AutoValue] - Immutable [value-type] code generation for Java 7+.

    [![Maven Central](https://img.shields.io/maven-central/v/com.google.auto.value/auto-value.svg)](https://mvnrepository.com/artifact/com.google.auto.value/auto-value)

  * [Common] - Helper utilities for writing annotation processors.

    [![Maven Central](https://img.shields.io/maven-central/v/com.google.auto/auto-common.svg)](https://mvnrepository.com/artifact/com.google.auto/auto-common)

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

[AutoFactory]: https://github.com/google/auto/tree/main/factory
[AutoService]: https://github.com/google/auto/tree/main/service
[AutoValue]: https://github.com/google/auto/tree/main/value
[Common]: https://github.com/google/auto/tree/main/common

[java]: https://en.wikipedia.org/wiki/Java_(programming_language)
[value-type]: http://en.wikipedia.org/wiki/Value_object
[`ServiceLoader`]: http://docs.oracle.com/javase/7/docs/api/java/util/ServiceLoader.html
