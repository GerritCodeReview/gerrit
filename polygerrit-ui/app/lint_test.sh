#!/bin/sh

set -ex

npm_bin=$(which npm) && true
if [ -z "$npm_bin" ]; then
    echo "NPM must be on the path."
    exit 1
fi

eslint_bin=$(which eslint) && true
eslint_config=$(npm list -g | grep -c eslint-config-google) && true
eslint_plugin=$(npm list -g | grep -c eslint-plugin-html) && true
if [ -z "$eslint_bin" ] || [ "$eslint_config" -eq "0" ] || [ "$eslint_plugin" -eq "0" ]; then
    echo "You must install ESLint and its dependencies from NPM."
    echo "> npm install -g eslint eslint-config-google eslint-plugin-html"
    echo "For more information, view the README:"
    echo "https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/#Style-guide"
    exit 1
fi

# get absolute path to lint_test.sh path
SCRIPT=$(readlink -f "$0")
UI_PATH=$(dirname "$SCRIPT")

# To make sure npm link happens in the right place
cd ${UI_PATH}

# Linking global eslint packages to the local project. Required to use eslint plugins with a global
# eslint installation.
npm link eslint eslint-config-google eslint-plugin-html

${eslint_bin} -c ${UI_PATH}/.eslintrc.json --ignore-pattern 'node_modules/' --ignore-pattern 'bower_components/' --ignore-pattern 'gr-linked-text' --ignore-pattern 'scripts/vendor' --ext .html,.js ${UI_PATH}
