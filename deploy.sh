#!/bin/bash
set -e
cd "$(dirname "$0")"

TOKEN=$(git config --global github.token)
USER=Sekiguchi-Takashi
REPO=LauncherApp
MSG=${1:-update}

curl -s -o /dev/null -X POST \
  -H "Authorization: token ${TOKEN}" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"${REPO}\",\"private\":true}" || true

if [ ! -d .git ]; then
  git init -b main
fi

git remote remove origin 2>/dev/null || true
git remote add origin "https://${USER}:${TOKEN}@github.com/${USER}/${REPO}.git"

git rm --cached ci/appathy.keystore --quiet 2>/dev/null || true

git add -A
git commit -m "${MSG}" || true

git fetch origin main --quiet 2>/dev/null || true
git pull --rebase origin main || true

git push -u origin main
