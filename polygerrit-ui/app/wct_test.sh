#!/bin/sh

set -ex
t=$(mktemp -d || mktemp -d -t wct-XXXXXXXXXX)

#t=$TEST_SRCDIR/gerrit/polygerrit-ui/app
#echo $t
#ls -l $TEST_SRCDIR/ui_npm
cp -r $TEST_SRCDIR/ui_npm/node_modules $t/
ln -s $t/node_modules/@polymer/test-fixture $t/node_modules/test-fixture
ls -l $t

# ls -l $TEST_SRCDIR
# ls -l $TEST_SRCDIR/gerrit/polygerrit-ui
# ls -l $TEST_SRCDIR/gerrit/polygerrit-ui/app
# ls -l $TEST_SRCDIR/gerrit/polygerrit-ui/app/node_modules
# ls -l $TEST_SRCDIR/gerrit/polygerrit-ui/app/node_modules/web-component-tester
cp -r $TEST_SRCDIR/gerrit/polygerrit-ui/app/* $t/
ls -l $t/

#mkdir -p $t/test
#cp $TEST_SRCDIR/gerrit/polygerrit-ui/app/test/index.html $t/test/

if [ "${WCT_HEADLESS_MODE:-0}" != "0" ]; then
    CHROME_OPTIONS=[\'start-maximized\',\'headless\',\'disable-gpu\',\'no-sandbox\']
    FIREFOX_OPTIONS=[\'-headless\']
else
    CHROME_OPTIONS=[\'start-maximized\']
    FIREFOX_OPTIONS=[\'\']
fi

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
          {'/components/bower_components': 'node_modules'},
          {'/components/test-fixture': 'polymer_legacy_support/test-fixture'}
        ]
      },
      'npm': true,
      'moduleResolution': 'node',
      'plugins': {
        'local': {
          'skipSeleniumInstall': true,
          'browserOptions': {
            'chrome': ${CHROME_OPTIONS},
            'firefox': ${FIREFOX_OPTIONS}
          }
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

export PATH="$(dirname $NPM):$PATH"

cd $t
test -n "${WCT}"
# ls -l
# ls -l node_modules/web-component-tester
${WCT} ${WCT_ARGS}
