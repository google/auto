AutoFactory
======

A source code generator for JSR-330-compatible factories

For more information please see [the website][1].


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

You can also find downloadable .jars on the [GitHub download page][2].



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



 [1]: http://google.github.com/autofactory/
 [2]: http://github.com/google/autofactory/downloads
