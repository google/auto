# Auto

A collection of source code generators for [Java][java].

## Auto‽

[Java][java] is full of code that is mechanical, repetitive, typically untested
and sometimes the source of subtle bugs. _Sounds like a job for robots!_

The Auto subprojects are a collection of code generators that automate those
types of tasks. They create the code you would have written, but without
the bugs.

Save time.  Save code.  Save sanity.

## Subprojects

  * [AutoFactory] - JSR-330-compatible factories

    Latest version: `0.1-beta3`

  * [AutoService] - Provider-configuration files for [`ServiceLoader`]

    Latest version: `1.0-rc2`

  * [AutoValue] - Immutable [value-type] code generation for Java 1.6+.

    Latest version: `1.3`

  * [Common] - Helper utilities for writing annotation processors.

    Latest version: `0.6`

## License

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

[AutoFactory]: https://github.com/google/auto/tree/master/factory
[AutoService]: https://github.com/google/auto/tree/master/service
[AutoValue]: https://github.com/google/auto/tree/master/value
[Common]: https://github.com/google/auto/tree/master/common

[java]: https://en.wikipedia.org/wiki/Java_(programming_language)
[value-type]: http://en.wikipedia.org/wiki/Value_object
[`ServiceLoader`]: http://docs.oracle.com/javase/7/docs/api/java/util/ServiceLoader.html
