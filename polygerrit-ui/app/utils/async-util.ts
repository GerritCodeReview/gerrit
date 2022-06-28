/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable} from 'rxjs';
import {filter, take} from 'rxjs/operators';
import {assertIsDefined} from './common-util';

/**
 * @param fn An iteratee function to be passed each element of
 *     the array in order. Must return a promise, and the following
 *     iteration will not begin until resolution of the promise returned by
 *     the previous iteration.
 *
 *     An optional second argument to fn is a callback that will halt the
 *     loop if called.
 */
export function asyncForeach<T>(
  array: T[],
  fn: (item: T, stopCallback: () => void) => Promise<unknown>
): Promise<T | void> {
  if (!array.length) {
    return Promise.resolve();
  }
  let stop = false;
  const stopCallback = () => {
    stop = true;
  };
  return fn(array[0], stopCallback).then(() => {
    if (stop) {
      return Promise.resolve();
    }
    return asyncForeach(array.slice(1), fn);
  });
}

export const _testOnly_allTasks = new Map<number, DelayedTask>();

/**
 * This is just a very simple and small wrapper around setTimeout(). Instead of
 * the usual:
 *
 * const timer = window.setTimeout(() => {...do stuff...}, 123);
 * window.clearTimeout(timer);
 *
 * With this class you can do:
 *
 * const task = new Task(() => {...do stuff...}, 123);
 * task.cancel();
 *
 * It is just nicer to have an object for this instead of a number as a handle.
 */
export class DelayedTask {
  private timer?: number;

  constructor(private callback: () => void, waitMs = 0) {
    this.timer = window.setTimeout(() => {
      if (this.timer) _testOnly_allTasks.delete(this.timer);
      this.timer = undefined;
      if (this.callback) this.callback();
    }, waitMs);
    _testOnly_allTasks.set(this.timer, this);
  }

  cancel() {
    if (this.isActive()) {
      window.clearTimeout(this.timer);
      if (this.timer) _testOnly_allTasks.delete(this.timer);
      this.timer = undefined;
    }
  }

  flush() {
    if (this.isActive()) {
      this.cancel();
      if (this.callback) this.callback();
    }
  }

  isActive() {
    return this.timer !== undefined;
  }
}

/**
 * The usage pattern is:
 *
 * this.myDebouncedTask = debounce(this.myDebouncedTask, () => {...}, 123);
 *
 * It is identical to:
 *
 * this.myTask = new DelayedTask(() => {...}, 123);
 *
 * But it would cancel a potentially scheduled task beforehand.
 */
export function debounce(
  existingTask: DelayedTask | undefined,
  callback: () => void,
  waitMs = 0
) {
  existingTask?.cancel();
  return new DelayedTask(callback, waitMs);
}

export class DelayedCancelation {}

export class DelayedPromise<T> extends Promise<T> {
  private readonly resolve: (value: PromiseLike<T> | T) => void;

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private readonly reject: (reason?: any) => void;

  private timer: number | undefined;

  constructor(private readonly callback: () => Promise<T>, waitMs = 0) {
    let resolve: ((value: PromiseLike<T> | T) => void) | undefined;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let reject: ((reason?: any) => void) | undefined;
    super((res, rej) => {
      resolve = res;
      reject = rej;
    });
    assertIsDefined(resolve);
    assertIsDefined(reject);
    this.resolve = resolve;
    this.reject = reject;
    this.timer = window.setTimeout(async () => {
      await this.flush();
    }, waitMs);
  }

  private stop() {
    if (this.timer === undefined) return false;
    window.clearTimeout(this.timer);
    this.timer = undefined;
    return true;
  }

  async flush() {
    if (!this.stop()) return;
    try {
      this.resolve(await this.callback());
    } catch (e) {
      this.reject(e);
    }
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  cancel(reason?: any) {
    if (!this.stop()) return;
    this.reject(reason ?? new DelayedCancelation());
  }

  delegate(other: Promise<T>) {
    if (!this.stop()) return;
    other.then((value: T) => this.resolve(value));
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    other.catch((reason?: any) => this.reject(reason));
  }

  // From ECMAScript specification:
  // https://tc39.es/ecma262/#sec-get-promise-@@species
  //    Promise prototype methods normally use their this value's constructor to
  //    create a derived object. However, a subclass constructor may over-ride
  //    that default behaviour by redefining its @@species property.
  // NOTE: This is required otherwise .then and .catch on a DelayedPromise
  // will try to instantiate a DelayedPromise with 'resolve, reject' arguments.
  static get [Symbol.species]() {
    return Promise;
  }

  get [Symbol.toStringTag]() {
    return 'DelayedPromise';
  }
}

/**
 * The usage pattern is
 * this.aDebouncedPromise = debounceP(this.aDebouncedPromise, () => {...}, 123)
 */
export function debounceP<T>(
  existingPromise: DelayedPromise<T> | undefined,
  callback: () => Promise<T>,
  waitMs = 0
): DelayedPromise<T> {
  const promise = new DelayedPromise<T>(callback, waitMs);
  if (existingPromise) existingPromise.delegate(promise);
  return promise;
}
const THROTTLE_INTERVAL_MS = 500;

/**
 * Ensure only one call is made within THROTTLE_INTERVAL_MS and any call within
 * this interval is ignored
 */
export function throttleWrap<T>(fn: (e: T) => void) {
  let lastCall: number | undefined;
  return (e: T) => {
    if (
      lastCall !== undefined &&
      Date.now() - lastCall < THROTTLE_INTERVAL_MS
    ) {
      return;
    }
    lastCall = Date.now();
    fn(e);
  };
}

/**
 * Let's you wait for an Observable to become true.
 */
export function until<T>(obs$: Observable<T>, predicate: (t: T) => boolean) {
  return new Promise<void>(resolve => {
    obs$.pipe(filter(predicate), take(1)).subscribe(() => {
      resolve();
    });
  });
}

export const isFalse = (b: boolean) => b === false;

export type PromiseResult<T> =
  | {status: 'fulfilled'; value: T}
  | {status: 'rejected'; reason: string};
export function isFulfilled<T>(
  promiseResult?: PromiseResult<T>
): promiseResult is PromiseResult<T> & {status: 'fulfilled'} {
  return promiseResult?.status === 'fulfilled';
}

// An equivalent to Promise.allSettled from ES2020.
// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Promise/allSettled
// TODO: Migrate our tooling to ES2020 and remove this method.
export function allSettled<T>(
  promises: Promise<T>[]
): Promise<PromiseResult<T>[]> {
  return Promise.all(
    promises.map(promise =>
      promise
        .then(value => ({status: 'fulfilled', value} as const))
        .catch(reason => ({status: 'rejected', reason} as const))
    )
  );
}
