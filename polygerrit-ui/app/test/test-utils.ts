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
import '../types/globals';
import {_testOnly_resetPluginLoader} from '../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {testOnly_resetInternalState} from '../elements/shared/gr-js-api-interface/gr-api-utils';
import {_testOnly_resetEndpoints} from '../elements/shared/gr-js-api-interface/gr-plugin-endpoints';
import {
  _testOnly_getShortcutManagerInstance,
  Shortcut,
} from '../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';

export interface MockPromise extends Promise<unknown> {
  resolve: (value?: unknown) => void;
}

export const mockPromise = () => {
  let res: (value?: unknown) => void;
  const promise: MockPromise = new Promise(resolve => {
    res = resolve;
  }) as MockPromise;
  promise.resolve = res!;
  return promise;
};
export const isHidden = (el: Element) =>
  getComputedStyle(el).display === 'none';

// Some tests/elements can define its own binding. We want to restore bindings
// at the end of the test. The TestKeyboardShortcutBinder store bindings in
// stack, so it is possible to override bindings in nested suites.
export class TestKeyboardShortcutBinder {
  private static stack: TestKeyboardShortcutBinder[] = [];

  static push() {
    const testBinder = new TestKeyboardShortcutBinder();
    this.stack.push(testBinder);
    return _testOnly_getShortcutManagerInstance();
  }

  static pop() {
    const item = this.stack.pop();
    if (!item) {
      throw new Error('stack is empty');
    }
    item._restoreShortcuts();
  }

  private readonly originalBinding: Map<Shortcut, string[]>;

  constructor() {
    this.originalBinding = new Map(
      _testOnly_getShortcutManagerInstance()._testOnly_getBindings()
    );
  }

  _restoreShortcuts() {
    const bindings = _testOnly_getShortcutManagerInstance()._testOnly_getBindings();
    bindings.clear();
    this.originalBinding.forEach((value, key) => {
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

export type CleanupCallback = () => void;

const cleanups: CleanupCallback[] = [];

export function getCleanupsCount() {
  return cleanups.length;
}

export function registerTestCleanup(cleanupCallback: CleanupCallback) {
  cleanups.push(cleanupCallback);
}

export function cleanupTestUtils() {
  cleanups.forEach(cleanup => cleanup());
  cleanups.splice(0);
}

export function stubBaseUrl(newUrl: string) {
  const originalCanonicalPath = window.CANONICAL_PATH;
  window.CANONICAL_PATH = newUrl;
  registerTestCleanup(() => (window.CANONICAL_PATH = originalCanonicalPath));
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
  ironOverlayBackdropStyleEl.sheet!.insertRule(
    'body { --iron-overlay-backdrop-opacity: 0; }'
  );
  return ironOverlayBackdropStyleEl;
}

/**
 * Promisify an event callback to simplify async...await tests.
 *
 * Use like this:
 *   await listenOnce(el, 'render');
 *   ...
 */
export function listenOnce(el, eventType) {
  return new Promise(resolve => {
    const listener = () => {
      removeEventListener();
      resolve();
    };
    el.addEventListener(eventType, listener);
    let removeEventListener = () => {
      el.removeEventListener(eventType, listener);
      removeEventListener = () => {};
    };
    registerTestCleanup(removeEventListener);
  });
}
