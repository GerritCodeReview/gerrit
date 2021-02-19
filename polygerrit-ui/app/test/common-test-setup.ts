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
// This should be the first import to install handler before any other code
import './source-map-support-install';
// TODO(dmfilippov): remove bundled-polymer.js imports when the following issue
// https://github.com/Polymer/polymer-resin/issues/9 is resolved.
import '../scripts/bundled-polymer';
import '@polymer/iron-test-helpers/iron-test-helpers';
import './test-router';
import {_testOnlyInitAppContext} from './test-app-context-init';
import {_testOnly_resetPluginLoader} from '../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {_testOnlyResetGrRestApiSharedObjects} from '../elements/shared/gr-rest-api-interface/gr-rest-api-interface';
import {
  cleanupTestUtils,
  getCleanupsCount,
  registerTestCleanup,
  addIronOverlayBackdropStyleEl,
  removeIronOverlayBackdropStyleEl,
  TestKeyboardShortcutBinder,
} from './test-utils';
import {flushDebouncers} from '@polymer/polymer/lib/utils/debounce';
import {_testOnly_getShortcutManagerInstance} from '../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import sinon, {SinonSpy} from 'sinon/pkg/sinon-esm';
import {safeTypesBridge} from '../utils/safe-types-util';
import {_testOnly_initGerritPluginApi} from '../elements/shared/gr-js-api-interface/gr-gerrit';
import {initGlobalVariables} from '../elements/gr-app-global-var-init';
import 'chai/chai';
import {
  _testOnly_defaultResinReportHandler,
  installPolymerResin,
} from '../scripts/polymer-resin-install';

declare global {
  interface Window {
    assert: typeof chai.assert;
    expect: typeof chai.expect;
    fixture: typeof fixtureImpl;
    stub: typeof stubImpl;
    sinon: typeof sinon;
  }
  let assert: typeof chai.assert;
  let expect: typeof chai.expect;
  let stub: typeof stubImpl;
  let sinon: typeof sinon;
}
window.assert = chai.assert;
window.expect = chai.expect;

window.sinon = sinon;

installPolymerResin(safeTypesBridge, (isViolation, fmt, ...args) => {
  const log = _testOnly_defaultResinReportHandler;
  log(isViolation, fmt, ...args);
  if (isViolation) {
    // This will cause the test to fail if there is a data binding
    // violation.
    throw new Error('polymer-resin violation: ' + fmt + JSON.stringify(args));
  }
});

interface TestFixtureElement extends HTMLElement {
  restore(): void;
  create(model?: unknown): HTMLElement | HTMLElement[];
}

function getFixtureElementById(fixtureId: string) {
  return document.getElementById(fixtureId) as TestFixtureElement;
}

// For karma always set our implementation
// (karma doesn't provide the fixture method)
function fixtureImpl(fixtureId: string, model: unknown) {
  // This method is inspired by web-component-tester method
  registerTestCleanup(() => getFixtureElementById(fixtureId).restore());
  return getFixtureElementById(fixtureId).create(model);
}

window.fixture = fixtureImpl;
let testSetupTimestampMs = 0;

setup(() => {
  testSetupTimestampMs = new Date().getTime();
  window.Gerrit = {};
  initGlobalVariables();
  addIronOverlayBackdropStyleEl();

  // If the following asserts fails - then window.stub is
  // overwritten by some other code.
  assert.equal(getCleanupsCount(), 0);
  // The following calls is nessecary to avoid influence of previously executed
  // tests.
  TestKeyboardShortcutBinder.push();
  _testOnlyInitAppContext();
  _testOnly_initGerritPluginApi();
  const mgr = _testOnly_getShortcutManagerInstance();
  assert.isTrue(mgr._testOnly_isEmpty());
  const selection = document.getSelection();
  if (selection) {
    selection.removeAllRanges();
  }
  const pl = _testOnly_resetPluginLoader();
  // For testing, always init with empty plugin list
  // Since when serve in gr-app, we always retrieve the list
  // from project config and init loading after that, all
  // `awaitPluginsLoaded` will rely on that to kick off,
  // in testing, we want to kick start this earlier.
  // You still can manually call _testOnly_resetPluginLoader
  // to reset this behavior if you need to test something specific.
  pl.loadPlugins([]);
  _testOnlyResetGrRestApiSharedObjects();
});

// For karma always set our implementation
// (karma doesn't provide the stub method)
function stubImpl<T extends keyof HTMLElementTagNameMap>(
  tagName: T,
  implementation: Partial<HTMLElementTagNameMap[T]>
) {
  // This method is inspired by web-component-tester method
  const proto = document.createElement(tagName).constructor
    .prototype as HTMLElementTagNameMap[T];
  const stubs: SinonSpy[] = [];
  for (const [key, value] of Object.entries(implementation)) {
    stubs.push(sinon.stub(proto, key).callsFake(value));
  }
  registerTestCleanup(() => {
    stubs.forEach(stub => {
      stub.restore();
    });
  });
}

window.stub = stubImpl;

// Very simple function to catch unexpected elements in documents body.
// It can't catch everything, but in most cases it is enough.
function checkChildAllowed(element: Element) {
  const allowedTags = ['SCRIPT', 'IRON-A11Y-ANNOUNCER'];
  if (allowedTags.includes(element.tagName)) {
    return;
  }
  if (element.tagName === 'TEST-FIXTURE') {
    if (
      element.children.length === 0 ||
      (element.children.length === 1 &&
        element.children[0].tagName === 'TEMPLATE')
    ) {
      return;
    }
    assert.fail(
      `Test fixture
        ${element.outerHTML}` +
        "isn't resotred after the test is finished. Please ensure that " +
        'restore() method is called for this test-fixture. Usually the call' +
        'happens automatically.'
    );
    return;
  }
  if (
    element.tagName === 'DIV' &&
    element.id === 'gr-hovercard-container' &&
    element.childNodes.length === 0
  ) {
    return;
  }
  assert.fail(
    `The following node remains in document after the test:
      ${element.tagName}
      Outer HTML:
      ${element.outerHTML}`
  );
}
function checkGlobalSpace() {
  for (const child of document.body.children) {
    checkChildAllowed(child);
  }
}

teardown(() => {
  sinon.restore();
  cleanupTestUtils();
  TestKeyboardShortcutBinder.pop();
  checkGlobalSpace();
  removeIronOverlayBackdropStyleEl();
  // Clean Polymer debouncer queue, so next tests will not be affected.
  // WARNING! This will most likely not do what you expect. `flushDebouncers()`
  // will only flush debouncers that were added using `enqueueDebouncer()`. So
  // this will not affect "normal" debouncers that were added using
  // `this.debounce()`. For those please be careful and cancel them using
  // `this.cancelDebouncer()` in the `detached()` lifecycle hook.
  flushDebouncers();
  const testTeardownTimestampMs = new Date().getTime();
  const elapsedMs = testTeardownTimestampMs - testSetupTimestampMs;
  if (elapsedMs > 1000) {
    console.warn(`ATTENTION! Test took longer than 1 second: ${elapsedMs} ms`);
  }
});
