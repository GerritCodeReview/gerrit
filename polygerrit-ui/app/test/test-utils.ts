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
import {getAppContext} from '../services/app-context';
import {RestApiService} from '../services/gr-rest-api/gr-rest-api';
import {SinonSpy, SinonStub} from 'sinon';
import {StorageService} from '../services/storage/gr-storage';
import {AuthService} from '../services/gr-auth/gr-auth';
import {ReportingService} from '../services/gr-reporting/gr-reporting';
import {UserModel} from '../models/user/user-model';
import {ShortcutsService} from '../services/shortcuts/shortcuts-service';
import {queryAndAssert, query} from '../utils/common-util';
import {FlagsService} from '../services/flags/flags';
import {afterNextRender, Key, Modifier} from '../utils/dom-util';
import {Observable} from 'rxjs';
import {filter, take, timeout} from 'rxjs/operators';
import {HighlightService} from '../services/highlight/highlight-service';
export {query, queryAll, queryAndAssert} from '../utils/common-util';

export interface MockPromise<T> extends Promise<T> {
  resolve: (value?: T) => void;
  reject: (reason?: any) => void;
}

export function mockPromise<T = unknown>(): MockPromise<T> {
  let res: (value?: T) => void;
  let rej: (reason?: any) => void;
  const promise: MockPromise<T> = new Promise<T | undefined>(
    (resolve, reject) => {
      res = resolve;
      rej = reject;
    }
  ) as MockPromise<T>;
  promise.resolve = res!;
  promise.reject = rej!;
  return promise;
}

export function isHidden(el: Element | undefined | null) {
  if (!el) return true;
  return getComputedStyle(el).display === 'none';
}

export function isVisible(el: Element) {
  assert.ok(el);
  return getComputedStyle(el).getPropertyValue('display') !== 'none';
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
  return sinon.stub(getAppContext().restApiService, method);
}

export function spyRestApi<K extends keyof RestApiService>(method: K) {
  return sinon.spy(getAppContext().restApiService, method);
}

export function stubUsers<K extends keyof UserModel>(method: K) {
  return sinon.stub(getAppContext().userModel, method);
}

export function stubShortcuts<K extends keyof ShortcutsService>(method: K) {
  return sinon.stub(getAppContext().shortcutsService, method);
}

export function stubHighlightService<K extends keyof HighlightService>(
  method: K
) {
  return sinon.stub(getAppContext().highlightService, method);
}

export function stubStorage<K extends keyof StorageService>(method: K) {
  return sinon.stub(getAppContext().storageService, method);
}

export function spyStorage<K extends keyof StorageService>(method: K) {
  return sinon.spy(getAppContext().storageService, method);
}

export function stubAuth<K extends keyof AuthService>(method: K) {
  return sinon.stub(getAppContext().authService, method);
}

export function stubReporting<K extends keyof ReportingService>(method: K) {
  return sinon.stub(getAppContext().reportingService, method);
}

export function stubFlags<K extends keyof FlagsService>(method: K) {
  return sinon.stub(getAppContext().flagsService, method);
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

export function removeThemeStyles() {
  // Do not remove the light theme, because it is only added once statically,
  // not once per gr-app instantiation.
  // document.head.querySelector('#light-theme')?.remove();
  document.head.querySelector('#dark-theme')?.remove();
}

export async function waitQueryAndAssert<E extends Element = Element>(
  el: Element | null | undefined,
  selector: string
): Promise<E> {
  await waitUntil(
    () => !!query<E>(el, selector),
    `The element '${selector}' did not appear in the DOM within 1000 ms.`
  );
  return queryAndAssert<E>(el, selector);
}

export function waitUntil(
  predicate: () => boolean,
  message = 'The waitUntil() predicate is still false after 1000 ms.'
): Promise<void> {
  const start = Date.now();
  let sleep = 0;
  if (predicate()) return Promise.resolve();
  const error = new Error(message);
  return new Promise((resolve, reject) => {
    const waiter = () => {
      if (predicate()) {
        resolve();
        return;
      }
      if (Date.now() - start >= 1000) {
        reject(error);
        return;
      }
      setTimeout(waiter, sleep);
      sleep = sleep === 0 ? 1 : sleep * 4;
    };
    waiter();
  });
}

export function waitUntilCalled(stub: SinonStub | SinonSpy, name: string) {
  return waitUntil(() => stub.called, `${name} was not called`);
}

export async function nextRender() {
  return new Promise(resolve => afterNextRender(resolve));
}

/**
 * Subscribes to the observable and resolves once it emits a matching value.
 * Usage:
 *   await waitUntilObserved(
 *     myTestModel.state$,
 *     state => state.prop === expectedValue
 *   );
 */
export async function waitUntilObserved<T>(
  observable$: Observable<T>,
  predicate: (t: T) => boolean,
  message = 'The waitUntilObserved() predicate did not match after 1000 ms.'
): Promise<T> {
  return new Promise((resolve, reject) => {
    observable$.pipe(filter(predicate), take(1), timeout(1000)).subscribe({
      next: t => resolve(t),
      error: () => reject(new Error(message)),
    });
  });
}

/**
 * Promisify an event callback to simplify async...await tests.
 *
 * Use like this:
 *   await listenOnce(el, 'render');
 *   ...
 */
export function listenOnce<T extends Event>(
  el: EventTarget,
  eventType: string
) {
  return new Promise<T>(resolve => {
    const listener = (e: Event) => {
      removeEventListener();
      resolve(e as T);
    };
    let removeEventListener = () => {
      el.removeEventListener(eventType, listener);
      removeEventListener = () => {};
    };
    el.addEventListener(eventType, listener);
    registerTestCleanup(removeEventListener);
  });
}

export function dispatch<T>(element: HTMLElement, type: string, detail: T) {
  const eventOptions = {
    detail,
    bubbles: true,
    composed: true,
  };
  element.dispatchEvent(new CustomEvent<T>(type, eventOptions));
}

export function pressKey(
  element: HTMLElement,
  key: string | Key,
  ...modifiers: Modifier[]
) {
  const eventOptions = {
    key,
    bubbles: true,
    composed: true,
    altKey: modifiers.includes(Modifier.ALT_KEY),
    ctrlKey: modifiers.includes(Modifier.CTRL_KEY),
    metaKey: modifiers.includes(Modifier.META_KEY),
    shiftKey: modifiers.includes(Modifier.SHIFT_KEY),
  };
  element.dispatchEvent(new KeyboardEvent('keydown', eventOptions));
}

export function mouseDown(element: HTMLElement) {
  const rect = element.getBoundingClientRect();
  const eventOptions = {
    bubbles: true,
    composed: true,
    clientX: (rect.left + rect.right) / 2,
    clientY: (rect.top + rect.bottom) / 2,
    screenX: (rect.left + rect.right) / 2,
    screenY: (rect.top + rect.bottom) / 2,
  };
  element.dispatchEvent(new MouseEvent('mousedown', eventOptions));
}

export function assertFails(promise: Promise<unknown>, error?: unknown) {
  promise
    .then((_v: unknown) => {
      assert.fail('Promise resolved but should have failed');
    })
    .catch((e: unknown) => {
      if (error) {
        assert.equal(e, error);
      }
    });
}
