#!/bin/bash

set -ex

npm_bin=$(which npm)
if [[ -z "$npm_bin" ]]; then
    echo "NPM must be on the path."
    exit 1
fi

polylint_bin="node_modules/polylint/lib/polylint-bin.js"
if [[ -z "$polylint_bin" ]]; then
    echo "You must install polylint and its dependencies from NPM."
    echo "> npm install -g polylint"
    exit 1
fi

unzip -o polygerrit-ui/polygerrit_components.bower_components.zip -d polygerrit-ui/app

${polylint_bin} --root polygerrit-ui/app --input elements/gr-app.html --b 'bower_components' --verbose
