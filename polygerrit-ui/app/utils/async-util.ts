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

export enum ResolvedDelayedTaskStatus {
  CALLBACK_EXECUTED = 'CALLBACK_EXECUTED',
  TASK_CANCELLED = 'TASK_CANCELLED',
}

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
  private timerId?: number;

  /**
   * Promise that is resolved after the callback is run or the task is
   * cancelled.
   *
   * If callback returns a Promise this resolves after the promise is settled.
   */
  public readonly promise: Promise<ResolvedDelayedTaskStatus>;

  private resolvePromise?: (
    value: ResolvedDelayedTaskStatus | PromiseLike<ResolvedDelayedTaskStatus>
  ) => void;

  private callCallbackAndResolveOnCompletion() {
    let callbackResult;
    if (this.callback) callbackResult = this.callback();
    if (callbackResult instanceof Promise) {
      callbackResult.finally(() => {
        this.resolvePromise!(ResolvedDelayedTaskStatus.CALLBACK_EXECUTED);
      });
    } else {
      this.resolvePromise!(ResolvedDelayedTaskStatus.CALLBACK_EXECUTED);
    }
  }

  constructor(
    private readonly callback: () => void | Promise<void>,
    waitMs = 0
  ) {
    this.promise = new Promise(resolve => {
      this.resolvePromise = resolve;
      this.timerId = window.setTimeout(() => {
        if (this.timerId) _testOnly_allTasks.delete(this.timerId);
        this.timerId = undefined;
        this.callCallbackAndResolveOnCompletion();
      }, waitMs);
      _testOnly_allTasks.set(this.timerId, this);
    });
  }

  private cancelTimer() {
    window.clearTimeout(this.timerId);
    if (this.timerId) _testOnly_allTasks.delete(this.timerId);
    this.timerId = undefined;
  }

  cancel() {
    if (this.isActive()) {
      this.cancelTimer();
      this.resolvePromise?.(ResolvedDelayedTaskStatus.TASK_CANCELLED);
    }
  }

  flush() {
    if (this.isActive()) {
      this.cancelTimer();
      this.callCallbackAndResolveOnCompletion();
    }
  }

  isActive() {
    return this.timerId !== undefined;
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

export const DELAYED_CANCELLATION = Symbol('Delayed Cancellation');

export class DelayedPromise<T> extends Promise<T> {
  private resolve: (value: PromiseLike<T> | T) => void;

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private reject: (reason?: any) => void;

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
    this.reject(reason ?? DELAYED_CANCELLATION);
  }

  delegate(other: Promise<T>) {
    if (!this.stop()) return;
    other
      .then((value: T) => this.resolve(value))
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      .catch((reason?: any) => this.reject(reason));
  }

  // From ECMAScript specification:
  // https://tc39.es/ecma262/#sec-get-promise-@@species
  //    Promise prototype methods normally use their this value's constructor to
  //    create a derived object. However, a subclass constructor may over-ride
  //    that default behaviour by redefining its @@species property.
  // NOTE: This is required otherwise .then and .catch on a DelayedPromise
  // will try to instantiate a DelayedPromise with 'resolve, reject' arguments.
  static override get [Symbol.species]() {
    return Promise;
  }

  override get [Symbol.toStringTag]() {
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

/**
 * Ensure only one call is made within THROTTLE_INTERVAL_MS and any call within
 * this interval is ignored
 */
export function throttleWrap<T>(fn: (e?: T) => void, throttleInterval = 500) {
  let lastCall: number | undefined;
  return (e?: T) => {
    if (lastCall !== undefined && Date.now() - lastCall < throttleInterval) {
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

/**
 * Noop function that can be used to suppress the tsetse must-use-promises rule.
 *
 * Example Usage:
 *   async function x() {
 *     await doA();
 *     noAwait(doB());
 *   }
 */
export function noAwait(_: {then: Function} | null | undefined) {}

export interface CancelablePromise<T> extends Promise<T> {
  cancel(): void;
}

/**
 * Make the promise cancelable.
 *
 * Returns a promise with a `cancel()` method wrapped around `promise`.
 * Calling `cancel()` will reject the returned promise with
 * {isCancelled: true} synchronously. If the inner promise for a cancelled
 * promise resolves or rejects this is ignored.
 */
export function makeCancelable<T>(promise: Promise<T>) {
  // True if the promise is either resolved or reject (possibly cancelled)
  let isDone = false;

  let rejectPromise: (reason?: unknown) => void;

  const wrappedPromise: CancelablePromise<T> = new Promise(
    (resolve, reject) => {
      rejectPromise = reject;
      promise.then(
        val => {
          if (!isDone) resolve(val);
          isDone = true;
        },
        error => {
          if (!isDone) reject(error);
          isDone = true;
        }
      );
    }
  ) as CancelablePromise<T>;

  wrappedPromise.cancel = () => {
    if (isDone) return;
    rejectPromise({isCanceled: true});
    isDone = true;
  };
  return wrappedPromise;
}

export interface MockPromise<T> extends Promise<T> {
  resolve: (value?: T) => void;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  reject: (reason?: any) => void;
}

export function mockPromise<T = unknown>(): MockPromise<T> {
  let res: (value?: T) => void;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
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

// MockPromise is the established name in tests, and we don't want to rename
// that in 50 files. But "Mock" is a bit misleading and definitely not a great
// fit for non-test code. So let's also export under a different name.
export type InteractivePromise<T> = MockPromise<T>;
export const interactivePromise = mockPromise;

export function timeoutPromise(timeoutMs: number): Promise<void> {
  return new Promise<void>(resolve => {
    setTimeout(resolve, timeoutMs);
  });
}

export async function waitUntil(
  predicate: (() => boolean) | (() => Promise<boolean>),
  message = 'The waitUntil() predicate is still false after 1000 ms.',
  timeout_ms = 1000
): Promise<void> {
  if (await predicate()) return Promise.resolve();
  const start = Date.now();
  let sleep = 10;
  const error = new Error(message);
  return new Promise((resolve, reject) => {
    const waiter = async () => {
      if (await predicate()) {
        resolve();
        return;
      }
      if (Date.now() - start >= timeout_ms) {
        reject(error);
        return;
      }
      setTimeout(waiter, sleep);
      sleep *= 2;
    };
    waiter();
  });
}
