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
import {_testOnly_resetEndpoints} from '../elements/shared/gr-js-api-interface/gr-plugin-endpoints';
import {
  _testOnly_getShortcutManagerInstance,
  Shortcut,
} from '../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {appContext} from '../services/app-context';
import {RestApiService} from '../services/gr-rest-api/gr-rest-api';
import {SinonSpy} from 'sinon/pkg/sinon-esm';

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

export function isHidden(el: Element | undefined | null) {
  if (!el) return true;
  return getComputedStyle(el).display === 'none';
}

export function queryAll<E extends Element = Element>(
  el: Element | undefined,
  selector: string
): NodeListOf<E> {
  if (!el) assert.fail(`element not defined`);
  const root = el.shadowRoot ?? el;
  return root.querySelectorAll<E>(selector);
}

export function query<E extends Element = Element>(
  el: Element | undefined,
  selector: string
): E | undefined {
  if (!el) return undefined;
  const root = el.shadowRoot ?? el;
  return root.querySelector<E>(selector) ?? undefined;
}

export function queryAndAssert<E extends Element = Element>(
  el: Element | undefined,
  selector: string
): E {
  const found = query<E>(el, selector);
  if (!found) assert.fail(`selector '${selector}' did not match anything'`);
  return found;
}

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

export function addListenerForTest(
  el: EventTarget,
  type: string,
  listener: EventListenerOrEventListenerObject
) {
  el.addEventListener(type, listener);
  registerListenerCleanup(el, type, listener);
}

export function registerListenerCleanup(
  el: EventTarget,
  type: string,
  listener: EventListenerOrEventListenerObject
) {
  registerTestCleanup(() => {
    el.removeEventListener(type, listener);
  });
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

export function stubRestApi<K extends keyof RestApiService>(method: K) {
  return sinon.stub(appContext.restApiService, method);
}

export function spyRestApi<K extends keyof RestApiService>(method: K) {
  return sinon.spy(appContext.restApiService, method);
}

export type SinonSpyMember<F extends (...args: any) => any> = SinonSpy<
  Parameters<F>,
  ReturnType<F>
>;

/**
 * Forcing an opacity of 0 onto the ironOverlayBackdrop is required, because
 * otherwise the backdrop stays around in the DOM for too long waiting for
 * an animation to finish.
 */
export function addIronOverlayBackdropStyleEl() {
  const el = document.createElement('style');
  el.setAttribute('id', 'backdrop-style');
  document.head.appendChild(el);
  el.sheet!.insertRule('body { --iron-overlay-backdrop-opacity: 0; }');
}

export function removeIronOverlayBackdropStyleEl() {
  const el = document.getElementById('backdrop-style');
  if (!el?.parentNode) throw new Error('Backdrop style element not found.');
  el.parentNode?.removeChild(el);
}

/**
 * Promisify an event callback to simplify async...await tests.
 *
 * Use like this:
 *   await listenOnce(el, 'render');
 *   ...
 */
export function listenOnce(el: EventTarget, eventType: string) {
  return new Promise<void>(resolve => {
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
