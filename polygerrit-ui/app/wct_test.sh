#!/bin/sh

set -ex

t=$(mktemp -d || mktemp -d -t wct-XXXXXXXXXX)
components=$TEST_SRCDIR/gerrit/polygerrit-ui/app/test_components.zip
code=$TEST_SRCDIR/gerrit/polygerrit-ui/app/pg_code.zip

echo $t
unzip -qd $t $components
unzip -qd $t $code
mkdir -p $t/test
cp $TEST_SRCDIR/gerrit/polygerrit-ui/app/test/index.html $t/test/

# For some reason wct tries to install selenium into its node_modules
# directory on first run. If you've installed into /usr/local and
# aren't running wct as root, you're screwed. Turning this option off
# through skipSeleniumInstall seems to still work, so there's that.

# Sauce tests are disabled by default in order to run local tests
# only.  Run it with (saucelabs.com account required; free for open
# source): WCT_ARGS='--plugin sauce' ./polygerrit-ui/app/run_test.sh

cat <<EOF > $t/wct.conf.js
module.exports = {
      'suites': ['test'],
      'webserver': {
        'pathMappings': [
          {'/components/bower_components': 'bower_components'}
        ]
      },
      'plugins': {
        'local': {
          'skipSeleniumInstall': true
        },
        'sauce': {
          'disabled': true,
          'browsers': [
            'OS X 10.12/chrome',
            'Windows 10/chrome',
            'Linux/firefox',
            'OS X 10.12/safari',
            'Windows 10/microsoftedge'
          ]
        }
      }
    };
EOF

export PATH="$(dirname $WCT):$(dirname $NPM):$PATH"

cd $t
test -n "${WCT}"

$(basename ${WCT}) ${WCT_ARGS}
