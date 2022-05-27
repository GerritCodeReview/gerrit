/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
// This should be the first import to install handler before any other code
import './source-map-support-install';
// TODO(dmfilippov): remove bundled-polymer.js imports when the following issue
// https://github.com/Polymer/polymer-resin/issues/9 is resolved.
import '../scripts/bundled-polymer';
import '@polymer/iron-test-helpers/iron-test-helpers';
import './test-router';
import {AppContext, injectAppContext} from '../services/app-context';
import {Finalizable} from '../services/registry';
import {
  createTestAppContext,
  createTestDependencies,
  Creator,
} from './test-app-context-init';
import {_testOnly_resetPluginLoader} from '../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {_testOnlyResetGrRestApiSharedObjects} from '../services/gr-rest-api/gr-rest-api-impl';
import {
  cleanupTestUtils,
  getCleanupsCount,
  registerTestCleanup,
  addIronOverlayBackdropStyleEl,
  removeIronOverlayBackdropStyleEl,
  removeThemeStyles,
} from './test-utils';
import {safeTypesBridge} from '../utils/safe-types-util';
import {initGlobalVariables} from '../elements/gr-app-global-var-init';
import 'chai/chai';
import {chaiDomDiff} from '@open-wc/semantic-dom-diff';
import {fixtureCleanup} from '@open-wc/testing-helpers';
import {
  _testOnly_defaultResinReportHandler,
  installPolymerResin,
} from '../scripts/polymer-resin-install';
import {_testOnly_allTasks} from '../utils/async-util';
import {cleanUpStorage} from '../services/storage/gr-storage_mock';
import {
  DependencyRequestEvent,
  DependencyError,
  DependencyToken,
  Provider,
} from '../models/dependency';

declare global {
  interface Window {
    assert: typeof chai.assert;
    expect: typeof chai.expect;
    fixture: typeof fixtureImpl;
    stub: typeof stubImpl;
    sinon: typeof sinon;
    chai: typeof chai;
  }
  let assert: typeof chai.assert;
  let expect: typeof chai.expect;
  let stub: typeof stubImpl;
  let sinon: typeof sinon;
}
window.assert = chai.assert;
window.expect = chai.expect;
window.chai.use(chaiDomDiff);

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
let appContext: AppContext & Finalizable;

const injectedDependencies: Map<
  DependencyToken<unknown>,
  Provider<unknown>
> = new Map();

const finalizers: Finalizable[] = [];

function injectDependency<T>(
  dependency: DependencyToken<T>,
  creator: Creator<T>
) {
  let service: (T & Finalizable) | undefined = undefined;
  injectedDependencies.set(dependency, () => {
    if (service) return service;
    service = creator();
    finalizers.push(service);
    return service;
  });
}

export function testResolver<T>(token: DependencyToken<T>): T {
  const provider = injectedDependencies.get(token);
  if (provider) {
    return provider() as T;
  } else {
    throw new DependencyError(token, 'Forgot to set up dependency for tests');
  }
}

function resolveDependency(evt: DependencyRequestEvent<unknown>) {
  evt.callback(testResolver(evt.dependency));
}

setup(() => {
  testSetupTimestampMs = new Date().getTime();
  addIronOverlayBackdropStyleEl();

  // If the following asserts fails - then window.stub is
  // overwritten by some other code.
  assert.equal(getCleanupsCount(), 0);
  appContext = createTestAppContext();
  injectAppContext(appContext);
  finalizers.push(appContext);
  const dependencies = createTestDependencies(appContext, testResolver);
  for (const [token, provider] of dependencies) {
    injectDependency(token, provider);
  }
  document.addEventListener('request-dependency', resolveDependency);
  // The following calls is nessecary to avoid influence of previously executed
  // tests.
  initGlobalVariables(appContext);

  const shortcuts = appContext.shortcutsService;
  assert.isTrue(shortcuts._testOnly_isEmpty());
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
function stubImpl<
  T extends keyof HTMLElementTagNameMap,
  K extends keyof HTMLElementTagNameMap[T]
>(tagName: T, method: K) {
  // This method is inspired by web-component-tester method
  const proto = document.createElement(tagName).constructor
    .prototype as HTMLElementTagNameMap[T];
  const stub = sinon.stub(proto, method);
  registerTestCleanup(() => {
    stub.restore();
  });
  return stub;
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

function cancelAllTasks() {
  for (const task of _testOnly_allTasks.values()) {
    console.warn('ATTENTION! A task was still active at the end of the test!');
    task.cancel();
  }
}

teardown(() => {
  sinon.restore();
  fixtureCleanup();
  cleanupTestUtils();
  checkGlobalSpace();
  removeIronOverlayBackdropStyleEl();
  removeThemeStyles();
  cancelAllTasks();
  cleanUpStorage();
  document.removeEventListener('request-dependency', resolveDependency);
  injectedDependencies.clear();
  // Reset state
  for (const f of finalizers) {
    f.finalize();
  }
  const testTeardownTimestampMs = new Date().getTime();
  const elapsedMs = testTeardownTimestampMs - testSetupTimestampMs;
  if (elapsedMs > 1000) {
    console.warn(`ATTENTION! Test took longer than 1 second: ${elapsedMs} ms`);
  }
});
