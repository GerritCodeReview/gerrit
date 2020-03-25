/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
For some reason wct tries to install selenium into its node_modules
directory on first run. If you've installed into /usr/local and
aren't running wct as root, you're screwed. Turning this option off
through skipSeleniumInstall seems to still work, so there's that.

Sauce tests are disabled by default in order to run local tests
only.  Run it with (saucelabs.com account required; free for open
source): ./polygerrit-ui/app/run_test.sh --test_arg=--plugin --test_arg=sauce
*/

const headless = 'WCT_HEADLESS_MODE' in process.env ?
  process.env['WCT_HEADLESS_MODE'] !== '0' : false;

const headlessBrowserOptions = {
  chrome: ['start-maximized', 'headless', 'disable-gpu', 'no-sandbox'],
  firefox: ['-headless'],
};

const defaultBrowserOptions = {
  chrome: ['start-maximized'],
  firefox: [],
};

module.exports = {
  suites: ['test'],
  npm: true,
  compile: 'never',
  moduleResolution: 'node',
  wctPackageName: 'wct-browser-legacy',
  testTimeout: 100000,
  plugins: {
    local: {
      skipSeleniumInstall: true,
      browserOptions: headless ? headlessBrowserOptions : defaultBrowserOptions,
    },
    sauce: {
      disabled: true,
      browsers: [
        'OS X 10.12/chrome',
        'Windows 10/chrome',
        'Linux/firefox',
        'OS X 10.12/safari',
        'Windows 10/microsoftedge',
      ],
    },
  },
};
