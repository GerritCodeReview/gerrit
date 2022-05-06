/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
