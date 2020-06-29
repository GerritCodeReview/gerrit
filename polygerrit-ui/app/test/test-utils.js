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
import {KeyboardShortcutBinder, _testOnly_getShortcutManagerInstance} from '../behaviors/keyboard-shortcut-behavior/keyboard-shortcut-behavior.js';

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
    return KeyboardShortcutBinder;
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
