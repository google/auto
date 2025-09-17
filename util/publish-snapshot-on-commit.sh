#!/bin/bash

set -e

mvn -B dependency:go-offline test clean -U --quiet --fail-never -DskipTests=true -f build-pom.xml
mvn -B -U deploy -DskipTests=true -f build-pom.xml
