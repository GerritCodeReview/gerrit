#!/bin/sh

set -ex

t=$(mktemp -d || mktemp -d -t wct-XXXXXXXXXX)
components=$TEST_SRCDIR/gerrit/polygerrit-ui/app/test_components.zip
code=$TEST_SRCDIR/gerrit/polygerrit-ui/app/polygerrit_embed_ui.zip
index=$TEST_SRCDIR/gerrit/polygerrit-ui/app/embed/test.html
tests=$TEST_SRCDIR/gerrit/polygerrit-ui/app/embed/*_test.html

unzip -qd $t $components
unzip -qd $t $code
mkdir -p $t/test
cp $index $t/test/
cp $tests $t/test/

# For some reason wct tries to install selenium into its node_modules
# directory on first run. If you've installed into /usr/local and
# aren't running wct as root, you're screwed. Turning this option off
# through skipSeleniumInstall seems to still work, so there's that.

# Sauce tests are disabled by default in order to run local tests
# only.  Run it with (saucelabs.com account required; free for open
# source): WCT_ARGS='--plugin sauce' ./polygerrit-ui/app/embed_test.sh

if [[ -z "${POLYGERRIT_CI}" ]]; then
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
else
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
          'skipSeleniumInstall': true,
          'disable': true,
        },
        'headless': {
          'skipSeleniumInstall': true,
          'browsers': [
            'chrome'
          ],
          'browsersOptions': {
            'chrome': [
              'window-size=1920,1080',
              'headless',
              'disable-gpu',
              'no-sandbox'
            ]
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
fi

export PATH="$(dirname $WCT):$(dirname $NPM):$PATH"

cd $t
test -n "${WCT}"

$(basename ${WCT}) ${WCT_ARGS}
