#!/bin/bash
set -e
cd "$(dirname "$0")"

VERSION=$(grep 'versionName' app/build.gradle.kts | head -1 | cut -d'"' -f2)
TAG="v${VERSION}"

if [ -z "$VERSION" ]; then
  printf 'versionName not found\n'
  exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
  printf 'tag %s already exists\n' "$TAG"
  exit 1
fi

git tag "$TAG"
git push origin "$TAG"
printf 'pushed %s\n' "$TAG"
