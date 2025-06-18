/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../types/globals';
import {getAppContext} from '../services/app-context';
import {RestApiService} from '../services/gr-rest-api/gr-rest-api';
import {SinonSpy, SinonStub} from 'sinon';
import {ReportingService} from '../services/gr-reporting/gr-reporting';
import {query, queryAndAssert} from '../utils/common-util';
import {FlagsService} from '../services/flags/flags';
import {Key, Modifier, whenVisible} from '../utils/dom-util';
import {Observable} from 'rxjs';
import {filter, take, timeout} from 'rxjs/operators';
import {assert} from '@open-wc/testing';
import {Route, ViewState} from '../models/views/base';
import {PageContext} from '../elements/core/gr-router/gr-page';
import {waitUntil} from '../utils/async-util';
import {JSON_PREFIX} from '../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {
  applyTheme as applyDarkTheme,
  removeTheme as removeDarkTheme,
} from '../styles/themes/dark-theme';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {LitElement} from 'lit';
export {query, queryAll, queryAndAssert} from '../utils/common-util';
export {mockPromise, waitUntil} from '../utils/async-util';
export type {MockPromise} from '../utils/async-util';

export function isHidden(el: Element | undefined | null) {
  if (!el) return true;
  return getComputedStyle(el).display === 'none';
}

export function isVisible(el: Element) {
  assert.ok(el);
  return getComputedStyle(el).getPropertyValue('display') !== 'none';
}

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

export function stubReporting<K extends keyof ReportingService>(method: K) {
  return sinon.stub(getAppContext().reportingService, method);
}

export function stubFlags<K extends keyof FlagsService>(method: K) {
  return sinon.stub(getAppContext().flagsService, method);
}

export function stubElement<
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

export type SinonSpyMember<F extends (...args: any) => any> = SinonSpy<
  Parameters<F>,
  ReturnType<F>
>;

export function removeThemeStyles() {
  // Do not remove the light theme, because it is only added once statically,
  // not once per gr-app instantiation.
  // document.head.querySelector('#light-theme')?.remove();
  document.head.querySelector('#dark-theme')?.remove();
}

function getActiveElement() {
  return document.activeElement;
}

export function isFocusInsideElement(element: Element) {
  // In Polymer 2 focused element either <paper-input> or nested
  // native input <input> element depending on the current focus
  // in browser window.
  // For example, the focus is changed if the developer console
  // get a focus.
  let activeElement = getActiveElement();
  while (activeElement) {
    if (activeElement === element) {
      return true;
    }
    if (activeElement.parentElement) {
      activeElement = activeElement.parentElement;
    } else {
      activeElement = (activeElement.getRootNode() as ShadowRoot).host;
    }
  }
  return false;
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

export async function waitUntilVisible(element: Element): Promise<void> {
  return new Promise(resolve => {
    whenVisible(element, () => resolve());
  });
}

export function waitUntilCalled(stub: SinonStub | SinonSpy, name: string) {
  return waitUntil(() => stub.called, `${name} was not called`);
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
 * sinon.useFakeTimers() overwrites window.setTimeout with a controlled,
 * synchronous version for tests to use. Keep the original one for use in
 * waitEventLoop
 */
const nativeSetTimeout = window.setTimeout;
/**
 * Wait for the current event loop's tasks to complete by scheduling a promise
 * to resolve during the next loop. Prefer other wait methods over this one to
 * wait for specific work to be done or for specific states to exist.
 */
export function waitEventLoop(): Promise<void> {
  return new Promise(resolve => nativeSetTimeout(resolve, 0));
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
    cancelable: true,
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

export function assertFails<T = unknown>(promise: Promise<unknown>, error?: T) {
  return promise
    .then((_v: unknown) => {
      assert.fail('Promise resolved but should have failed');
    })
    .catch((e: T) => {
      if (error) {
        assert.equal(e, error);
      }
      return e;
    });
}

export function logProxy<T extends object>(obj: T, name?: string): T {
  const handler = {
    get(target: object, prop: PropertyKey, receiver: any) {
      const result = Reflect.get(target, prop, receiver);
      if (result instanceof Function) {
        return (...rest: unknown[]) => {
          console.error(`${name}.${String(prop)}(${rest})`);
          return result.apply(target, rest);
        };
      }
      return result;
    },
  };
  return new Proxy(obj, handler) as unknown as T;
}

export function assertRouteState<T extends ViewState>(
  route: Route<T>,
  path: string,
  state: T,
  createUrl: (state: T) => string
) {
  const {urlPattern, createState} = route;
  const ctx = new PageContext(path);
  const matches = ctx.match(urlPattern);
  assert.isTrue(matches);
  assert.deepEqual(createState(ctx), state);
  assert.equal(path, createUrl(state));
}

export function assertRouteFalse<T extends ViewState>(
  route: Route<T>,
  path: string
) {
  const ctx = new PageContext(path);
  const matches = ctx.match(route.urlPattern);
  assert.isFalse(matches);
}

export function makePrefixedJSON(obj: any) {
  return JSON_PREFIX + JSON.stringify(obj);
}

export async function visualDiffDarkTheme(
  element: LitElement | HTMLElement,
  name: string
) {
  applyDarkTheme();
  document.documentElement.classList.add('darkTheme');
  await visualDiff(element, `${name}-dark`);
  removeDarkTheme();
  document.documentElement.classList.remove('darkTheme');
}
