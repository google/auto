#!/bin/bash

# Run by GitHub Actions (see .github/workflows/ci.yml)

set -e

echo -e "Publishing javadoc...\n"

mvn -f build-pom.xml javadoc:aggregate
TARGET="$(pwd)/target"

cd $HOME
git clone --quiet --branch=gh-pages "https://x-access-token:${GITHUB_TOKEN}@github.com/google/auto" gh-pages > /dev/null

cd gh-pages
git config --global user.name "$GITHUB_ACTOR"
git config --global user.email "$GITHUB_ACTOR@users.noreply.github.com"
git rm -rf api/latest
mkdir -p api # Just to make mv work if the directory is missing
mv ${TARGET}/site/apidocs api/latest
git add -A -f api/latest
git commit -m "Latest javadoc on successful CI build auto-pushed to gh-pages"
git push -fq origin gh-pages > /dev/null

echo -e "Published Javadoc to gh-pages.\n"
