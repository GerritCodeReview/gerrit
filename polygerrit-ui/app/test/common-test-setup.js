/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import '../scripts/bundled-polymer.js';
import './test-fixture.js';
import 'chai/chai.js';
import 'lodash/lodash.js';
self.assert = chai.assert;

import 'polymer-resin/standalone/polymer-resin.js';
import '../behaviors/safe-types-behavior/safe-types-behavior.js';
import '@polymer/iron-test-helpers/iron-test-helpers.js';
import './test-router.js';
import moment from 'moment/src/moment.js';
self.moment = moment;
security.polymer_resin.install({
  allowedIdentifierPrefixes: [''],
  reportHandler(isViolation, fmt, ...args) {
    const log = security.polymer_resin.CONSOLE_LOGGING_REPORT_HANDLER;
    log(isViolation, fmt, ...args);
    if (isViolation) {
      // This will cause the test to fail if there is a data binding
      // violation.
      throw new Error(
          'polymer-resin violation: ' + fmt +
        JSON.stringify(args));
    }
  },
  safeTypesBridge: Gerrit.SafeTypes.safeTypesBridge,
});
self.mockPromise = () => {
  let res;
  const promise = new Promise(resolve => {
    res = resolve;
  });
  promise.resolve = res;
  return promise;
};
self.isHidden = el => getComputedStyle(el).display === 'none';
setup(() => {
  if (!window.Gerrit) { return; }
  if (Gerrit._testOnly_resetPlugins) {
    Gerrit._testOnly_resetPlugins();
  }
});

/**
 * Triggers a flush of any pending events, observations, etc and calls you back
 * after they have been processed.
 *
 * @param {function()} callback
 */
function flush(callback) {
  // Ideally, this function would be a call to Polymer.dom.flush, but that
  // doesn't support a callback yet
  // (https://github.com/Polymer/polymer-dev/issues/851),
  // ...and there's cross-browser flakiness to deal with.
  // Make sure that we're invoking the callback with no arguments so that the
  // caller can pass Mocha callbacks, etc.
  var done = function done() {
    callback();
  };
  window.Polymer.dom.flush();
  window.setTimeout(done, 0);
}

self.flush = flush;
