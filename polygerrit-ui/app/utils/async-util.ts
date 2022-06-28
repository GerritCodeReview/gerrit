/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable} from 'rxjs';
import {filter, take} from 'rxjs/operators';

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

export interface DelayedPromise<T> extends Promise<T> {
  cancel: (reason?: any) => void;
  flush: () => Promise<void>;
  delegate: (promise: Promise<T>) => void;
}

export class CancelationError extends Error {}

function delayedPromise<T>(
  callback: () => Promise<T>,
  waitMs = 0
): DelayedPromise<T> {
  let resolve: (value: PromiseLike<T> | T) => void;
  let reject: (reason?: any) => void;
  let timer: number | undefined;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  }) as DelayedPromise<T>;
  const stop = () => {
    if (timer === undefined) return false;
    window.clearTimeout(timer);
    timer = undefined;
    return true;
  };
  promise.cancel = (reason?: any) => {
    if (stop()) return;
    reject(reason);
  };
  promise.flush = async () => {
    if (!stop()) return;
    try {
      resolve(await callback());
    } catch (e) {
      reject(e);
    }
  };
  promise.delegate = (other: Promise<T>) => {
    if (!stop()) return;
    other.then((value: T) => resolve(value));
    other.catch((reason?: any) => reject(reason));
  }
  timer = window.setTimeout(async () => { await promise.flush(); }, waitMs);
  return promise;
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
  const promise = delayedPromise<T>(callback, waitMs);
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
