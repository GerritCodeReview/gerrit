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

import {_testOnly_resetPluginLoader} from '../elements/shared/gr-js-api-interface/gr-plugin-loader.js';
import {testOnly_resetInternalState} from '../elements/shared/gr-js-api-interface/gr-api-utils.js';
import {_testOnly_resetEndpoints} from '../elements/shared/gr-js-api-interface/gr-plugin-endpoints.js';
import {_testOnly_getShortcutManagerInstance} from '../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';

export const mockPromise = () => {
  let res;
  const promise = new Promise(resolve => {
    res = resolve;
  });
  promise.resolve = res;
  return promise;
};
export const isHidden = el => getComputedStyle(el).display === 'none';

// Some tests/elements can define its own binding. We want to restore bindings
// at the end of the test. The TestKeyboardShortcutBinder store bindings in
// stack, so it is possible to override bindings in nested suites.
export class TestKeyboardShortcutBinder {
  static push() {
    if (!this.stack) {
      this.stack = [];
    }
    const testBinder = new TestKeyboardShortcutBinder();
    this.stack.push(testBinder);
    return _testOnly_getShortcutManagerInstance();
  }

  static pop() {
    this.stack.pop()._restoreShortcuts();
  }

  constructor() {
    this._originalBinding = new Map(
        _testOnly_getShortcutManagerInstance().bindings);
  }

  _restoreShortcuts() {
    const bindings = _testOnly_getShortcutManagerInstance().bindings;
    bindings.clear();
    this._originalBinding.forEach((value, key) => {
      bindings.set(key, value);
    });
  }
}

// Provide reset plugins function to clear installed plugins between tests.
// No gr-app found (running tests)
export const resetPlugins = () => {
  testOnly_resetInternalState();
  _testOnly_resetEndpoints();
  const pl = _testOnly_resetPluginLoader();
  pl.loadPlugins([]);
};

const cleanups = [];

function registerTestCleanup(cleanupCallback) {
  cleanups.push(cleanupCallback);
}

export function cleanupTestUtils() {
  cleanups.forEach(cleanup => cleanup());
  cleanups.splice(0);
}

export function stubBaseUrl(newUrl) {
  const originalCanonicalPath = window.CANONICAL_PATH;
  window.CANONICAL_PATH = newUrl;
  registerTestCleanup(() => window.CANONICAL_PATH = originalCanonicalPath);
}

export function generateChange(options) {
  const change = {
    _number: 42,
    project: 'testRepo',
  };
  const revisionIdStart = 1;
  const messageIdStart = 1000;
  // We want to distinguish between empty arrays/objects and undefined
  // If an option is not set - the appropriate property is not set
  // If an options is set - the property always set
  if (options && typeof options.revisionsCount !== 'undefined') {
    const revisions = {};
    for (let i = 0; i < options.revisionsCount; i++) {
      const revisionId = (i + revisionIdStart).toString(16);
      revisions[revisionId] = {
        _number: i+1,
        commit: {parents: []},
      };
    }
    change.revisions = revisions;
  }
  if (options && typeof options.messagesCount !== 'undefined') {
    const messages = [];
    for (let i = 0; i < options.messagesCount; i++) {
      messages.push({
        id: (i + messageIdStart).toString(16),
        date: new Date(2020, 1, 1),
        message: `This is a message N${i + 1}`,
      });
    }
    change.messages = messages;
  }
  if (options && options.status) {
    change.status = options.status;
  }
  return change;
}

/**
 * Forcing an opacity of 0 onto the ironOverlayBackdrop is required, because
 * otherwise the backdrop stays around in the DOM for too long waiting for
 * an animation to finish. This could be considered to be moved to a
 * common-test-setup file.
 */
export function createIronOverlayBackdropStyleEl() {
  const ironOverlayBackdropStyleEl = document.createElement('style');
  document.head.appendChild(ironOverlayBackdropStyleEl);
  ironOverlayBackdropStyleEl.sheet.insertRule(
      'body { --iron-overlay-backdrop-opacity: 0; }');
  return ironOverlayBackdropStyleEl;
}
