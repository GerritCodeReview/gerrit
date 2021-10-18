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
import {appContext} from '../services/app-context';
import {RestApiService} from '../services/gr-rest-api/gr-rest-api';
import {SinonSpy} from 'sinon';
import {StorageService} from '../services/storage/gr-storage';
import {AuthService} from '../services/gr-auth/gr-auth';
import {ReportingService} from '../services/gr-reporting/gr-reporting';
import {CommentsService} from '../services/comments/comments-service';
import {UserService} from '../services/user/user-service';
import {ShortcutsService} from '../services/shortcuts/shortcuts-service';
export {query, queryAll, queryAndAssert} from '../utils/common-util';

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
  return sinon.stub(appContext.restApiService, method);
}

export function spyRestApi<K extends keyof RestApiService>(method: K) {
  return sinon.spy(appContext.restApiService, method);
}

export function stubComments<K extends keyof CommentsService>(method: K) {
  return sinon.stub(appContext.commentsService, method);
}

export function stubUsers<K extends keyof UserService>(method: K) {
  return sinon.stub(appContext.userService, method);
}

export function stubShortcuts<K extends keyof ShortcutsService>(method: K) {
  return sinon.stub(appContext.shortcutsService, method);
}

export function stubStorage<K extends keyof StorageService>(method: K) {
  return sinon.stub(appContext.storageService, method);
}

export function spyStorage<K extends keyof StorageService>(method: K) {
  return sinon.spy(appContext.storageService, method);
}

export function stubAuth<K extends keyof AuthService>(method: K) {
  return sinon.stub(appContext.authService, method);
}

export function stubReporting<K extends keyof ReportingService>(method: K) {
  return sinon.stub(appContext.reportingService, method);
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

export function waitUntil(
  predicate: () => boolean,
  maxMillis = 100
): Promise<void> {
  const start = Date.now();
  let sleep = 1;
  return new Promise((resolve, reject) => {
    const waiter = () => {
      if (predicate()) {
        return resolve();
      }
      if (Date.now() - start >= maxMillis) {
        return reject(new Error('Took to long to waitUntil'));
      }
      setTimeout(waiter, sleep);
      sleep *= 2;
    };
    waiter();
  });
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
