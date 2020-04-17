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

// Default implementations of 'fixture' and 'stub' methods in
// web-component-tester are incorrect. Default methods calls mocha teardown
// method to register cleanup actions. Each call to the teardown method adds
// additional 'afterEach' hook to a suite.
// As a result, if a suite's setup(..) method calls fixture(..) or stub(..)
// method, then additional afterEach hook is registered before each test.
// In overall, afterEach hook is called testCount^2 instead of testCount.
// When tests runs with the wct test runner, the runner adds listener for
// the 'afterEach' and tries to make some UI and log udpates. These updates
// are quite heavy, and after about 40-50 tests each test waste 0.5-1seconds.
//
// Our implementation uses global teardown to clean up everything. mocha calls
// global teardown after each test. The cleanups array stores all functions
// which must be called after a test ends.
//
// Note, that fixture(...) and stub(..) methods are registered different by
// WCT. This is why these methods implemented slightly different here.
const cleanups = [];
if (!window.fixture) {
  window.fixture = function(fixtureId, model) {
    // This method is inspired by WCT method
    cleanups.push(() => document.getElementById(fixtureId).restore());
    return document.getElementById(fixtureId).create(model);
  };
} else {
  throw new Error('window.fixture must be set before wct sets it');
}

// On the first call to the setup, WCT installs window.fixture
// and widnow.stub methods
setup(() => {
  // If the following asserts fails - then window.stub is
  // overwritten by some other code.
  assert.equal(cleanups.length, 0);

  if (!window.Gerrit) { return; }
  if (Gerrit._testOnly_resetPlugins) {
    Gerrit._testOnly_resetPlugins();
  }
});

if (window.stub) {
  window.stub = function(tagName, implementation) {
    // This method is inspired by WCT method
    const proto = document.createElement(tagName).constructor.prototype;
    const stubs = Object.keys(implementation)
        .map(key => sinon.stub(proto, key, implementation[key]));
    cleanups.push(() => {
      stubs.forEach(stub => {
        stub.restore();
      });
    });
  };
} else {
  throw new Error('window.stub must be set after wct sets it');
}

teardown(() => {
  // WCT incorrectly uses teardown method in the 'fixture' and 'stub'
  // implementations. This leads to slowdown WCT tests after each tests.
  // I.e. more tests in a file - longer it takes.
  // For example, gr-file-list_test.html takes approx 40 second without
  // a fix and 10 seconds with our implementation of fixture and stub.
  cleanups.forEach(cleanup => cleanup());
  cleanups.splice(0);
});
