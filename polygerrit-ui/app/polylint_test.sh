#!/bin/bash

set -ex

npm_bin=$(which npm)
if [[ -z "$npm_bin" ]]; then
    echo "NPM must be on the path."
    exit 1
fi

npx_bin=$(which npx)
if [[ -z "$npx_bin" ]]; then
    echo "NPX must be on the path."
    echo "> npm i -g npx"
    exit 1
fi

unzip -o polygerrit-ui/polygerrit_components.bower_components.zip -d polygerrit-ui/app

npx polylint --root polygerrit-ui/app --input elements/gr-app-p2.html --b 'bower_components' --verbose
