/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
// TODO(dmfilippov): remove bundled-polymer.js imports when the following issue
// https://github.com/Polymer/polymer-resin/issues/9 is resolved.
import '../scripts/bundled-polymer';
import {AppContext} from '../services/app-context';
import {Finalizable} from '../services/registry';
import {
  createTestAppContext,
  createTestDependencies,
} from './test-app-context-init';
import {testOnlyResetGrRestApiSharedObjects} from '../services/gr-rest-api/gr-rest-api-impl';
import {
  cleanupTestUtils,
  getCleanupsCount,
  removeThemeStyles,
} from './test-utils';
import {safeTypesBridge} from '../utils/safe-types-util';
import {
  initGerrit,
  initGlobalVariables,
} from '../elements/gr-app-global-var-init';
import {assert, fixtureCleanup} from '@open-wc/testing';
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
import * as sinon from 'sinon';
import '../styles/themes/app-theme.ts';
import {Creator} from '../services/app-context-init';
import {pluginLoaderToken} from '../elements/shared/gr-js-api-interface/gr-plugin-loader';

declare global {
  interface Window {
    sinon: typeof sinon;
  }
  let sinon: typeof sinon;
}

window.sinon = sinon;

installPolymerResin(safeTypesBridge, (isViolation, fmt, ...args) => {
  const log = _testOnly_defaultResinReportHandler;
  log(isViolation, fmt, ...args);
  if (isViolation) {
    // This will cause the test to fail if there is a data binding violation.
    throw new Error('polymer-resin violation: ' + fmt + JSON.stringify(args));
  }
});

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
  evt.callback(() => testResolver(evt.dependency));
}

setup(() => {
  testSetupTimestampMs = new Date().getTime();

  // If the following asserts fails - then window.stub is
  // overwritten by some other code.
  assert.equal(getCleanupsCount(), 0);
  appContext = createTestAppContext();
  initGlobalVariables(appContext);

  finalizers.push(appContext);
  const dependencies = createTestDependencies(appContext, testResolver);
  for (const [token, provider] of dependencies) {
    injectDependency(token, provider);
  }
  document.addEventListener('request-dependency', resolveDependency);
  initGerrit(testResolver(pluginLoaderToken));

  // The following calls is necessary to avoid influence of previously executed
  // tests.
  const selection = document.getSelection();
  if (selection) {
    selection.removeAllRanges();
  }
  // For testing, always init with empty plugin list
  // Since when serve in gr-app, we always retrieve the list
  // from project config and init loading after that, all
  // `awaitPluginsLoaded` will rely on that to kick off,
  // in testing, we want to kick start this earlier.
  testResolver(pluginLoaderToken).loadPlugins([]);
  testOnlyResetGrRestApiSharedObjects(appContext.authService);
});

export function removeRequestDependencyListener() {
  document.removeEventListener('request-dependency', resolveDependency);
}

// Very simple function to catch unexpected elements in documents body.
// It can't catch everything, but in most cases it is enough.
function checkChildAllowed(element: Element) {
  const allowedTags = ['SCRIPT', 'IRON-A11Y-ANNOUNCER', 'LINK'];
  if (allowedTags.includes(element.tagName)) {
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
  removeThemeStyles();
  cancelAllTasks();
  cleanUpStorage();
  removeRequestDependencyListener();
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
