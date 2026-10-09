#!/bin/bash

set -e

./mvnw -B dependency:go-offline test clean -U --quiet --fail-never -DskipTests=true -f build-pom.xml
./mvnw -B -U deploy -DskipTests=true -f build-pom.xml
