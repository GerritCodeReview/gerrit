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

// TODO(dmfilippov): remove bundled-polymer.js imports when the following issue
// https://github.com/Polymer/polymer-resin/issues/9 is resolved.
import '../scripts/bundled-polymer.js';
import './test-app-context-init.js';
import 'polymer-resin/standalone/polymer-resin.js';
import '@polymer/iron-test-helpers/iron-test-helpers.js';
import './test-router.js';
import {SafeTypes} from '../behaviors/safe-types-behavior/safe-types-behavior.js';
import {_testOnly_resetPluginLoader} from '../elements/shared/gr-js-api-interface/gr-plugin-loader.js';
import {_testOnlyResetRestApi} from '../elements/shared/gr-js-api-interface/gr-plugin-rest-api.js';
import {_testOnlyResetGrRestApiSharedObjects} from '../elements/shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {TestKeyboardShortcutBinder} from './test-utils';
import {flushDebouncers} from '@polymer/polymer/lib/utils/debounce';

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
  safeTypesBridge: SafeTypes.safeTypesBridge,
});

const cleanups = [];

// For karma always set our implementation
// (karma doesn't provide the fixture method)
window.fixture = function(fixtureId, model) {
  // This method is inspired by web-component-tester method
  cleanups.push(() => document.getElementById(fixtureId).restore());
  return document.getElementById(fixtureId).create(model);
};

setup(() => {
  // If the following asserts fails - then window.stub is
  // overwritten by some other code.
  assert.equal(cleanups.length, 0);
  // The following calls is nessecary to avoid influence of previously executed
  // tests.
  TestKeyboardShortcutBinder.push();
  document.getSelection().removeAllRanges();
  _testOnly_resetPluginLoader();
  _testOnlyResetGrRestApiSharedObjects();
  _testOnlyResetRestApi();
});

// For karma always set our implementation
// (karma doesn't provide the stub method)
window.stub = function(tagName, implementation) {
  // This method is inspired by web-component-tester method
  const proto = document.createElement(tagName).constructor.prototype;
  const stubs = Object.keys(implementation)
      .map(key => sinon.stub(proto, key, implementation[key]));
  cleanups.push(() => {
    stubs.forEach(stub => {
      stub.restore();
    });
  });
};

// Very simple function to catch unexpected elements in documents body.
// It can't catch everything, but in most cases it is enough.
function checkChildAllowed(element) {
  const allowedTags = ['SCRIPT', 'IRON-A11Y-ANNOUNCER'];
  if (allowedTags.includes(element.tagName)) {
    return;
  }
  if (element.tagName === 'TEST-FIXTURE') {
    if (element.children.length == 0 ||
        (element.children.length == 1 &&
        element.children[0].tagName === 'TEMPLATE')) {
      return;
    }
    assert.fail(`Test fixture
        ${element.outerHTML}` +
        `isn't resotred after the test is finished. Please ensure that ` +
        `restore() method is called for this test-fixture. Usually the call` +
        `happens automatically.`);
    return;
  }
  if (element.tagName === 'DIV' && element.id === 'gr-hovercard-container' &&
      element.childNodes.length === 0) {
    return;
  }
  assert.fail(
      `The following node remains in document after the test:
      ${element.tagName}
      Outer HTML:
      ${element.outerHTML},
      Stack trace:
      ${element.stackTrace}`);
}
function checkGlobalSpace() {
  for (const child of document.body.children) {
    checkChildAllowed(child);
  }
}

teardown(() => {
  cleanups.forEach(cleanup => cleanup());
  cleanups.splice(0);
  TestKeyboardShortcutBinder.pop();
  checkGlobalSpace();
  // Clean Polymer debouncer queue, so next tests will not be affected.
  flushDebouncers();
});
