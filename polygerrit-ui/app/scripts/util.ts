/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

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
