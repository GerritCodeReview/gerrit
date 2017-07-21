#!/bin/sh

set -ex

npm_bin=$(which npm)
if [[ -z "$npm_bin" ]]; then
    echo "NPM must be on the path."
    exit 1
fi

polymer_bin=$(which polymer)
if [[ -z "$polymer_bin" ]]; then
    echo "You must install polymer-cli and its dependencies from NPM."
    echo "> npm install -g polymer-cli"
    exit 1
fi

cd polygerrit-ui/app

echo $(pwd)
unzip -o test_components.zip
polymer lint
