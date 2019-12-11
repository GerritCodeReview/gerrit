#!/bin/sh

set -ex

eslint_bin=$(which npm)
if [ -z "$eslint_bin" ]; then
    echo "NPM must be on the path."
    exit 1
fi

eslint_bin=$(which eslint)
eslint_config=$(npm list -g | grep -c eslint-config-google)
eslint_plugin=$(npm list -g | grep -c eslint-plugin-html)
if [ -z "$eslint_bin" ] || [ "$eslint_config" -eq "0" ] || [ "$eslint_plugin" -eq "0" ]; then
    echo "You must install ESLint and its dependencies from NPM."
    echo "> npm install -g eslint eslint-config-google eslint-plugin-html"
    echo "For more information, view the README:"
    echo "https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/#Style-guide"
    exit 1
fi

${eslint_bin} --ext .html,.js .
