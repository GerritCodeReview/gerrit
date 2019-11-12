#!/bin/bash

set -ex

npm_bin=$(which npm)
if [[ -z "$npm_bin" ]]; then
    echo "NPM must be on the path."
    exit 1
fi

node_bin=$(which node)
if [[ -z "$node_bin" ]]; then
    echo "node must be on the path."
    exit 1
fi

polymer_bin=$(which polymer)
if [[ -z "$polymer_bin" ]]; then
  polymer_bin=$(abs_path ./node_modules/polymer-cli/bin/polymer.js);
fi
if [[ -z "$polymer_bin" ]]; then
    echo "polymer must be set or polymer-cli locally installed (npm install polymer-cli)."
    exit 1
fi

unzip -o polygerrit-ui/polygerrit_components.bower_components.zip -d polygerrit-ui/app

$polymer_bin lint --root polygerrit-ui/app --entrypoint polygerrit-ui/app/elements/gr-app.html --component-dir 'polygerrit-ui/app/bower_components' --verbose --rules polymer-2-hybrid --sources "polygerrit-ui/app/**/*"
