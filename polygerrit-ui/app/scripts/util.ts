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

// TODO (dmfilippov): Each function must be exported separately. According to
// the code style guide, a namespacing is not allowed.
export const util = {
  getCookie(name) {
    const key = name + '=';
    const cookies = document.cookie.split(';');
    for (let i = 0; i < cookies.length; i++) {
      let c = cookies[i];
      while (c.charAt(0) === ' ') {
        c = c.substring(1);
      }
      if (c.startsWith(key)) {
        return c.substring(key.length, c.length);
      }
    }
    return '';
  },

  /**
   * Make the promise cancelable.
   *
   * Returns a promise with a `cancel()` method wrapped around `promise`.
   * Calling `cancel()` will reject the returned promise with
   * {isCancelled: true} synchronously. If the inner promise for a cancelled
   * promise resolves or rejects this is ignored.
   */
  makeCancelable: promise => {
    // True if the promise is either resolved or reject (possibly cancelled)
    let isDone = false;

    let rejectPromise;

    const wrappedPromise = new Promise((resolve, reject) => {
      rejectPromise = reject;
      promise.then(val => {
        if (!isDone) resolve(val);
        isDone = true;
      }, error => {
        if (!isDone) reject(error);
        isDone = true;
      });
    });

    wrappedPromise.cancel = () => {
      if (isDone) return;
      rejectPromise({isCanceled: true});
      isDone = true;
    };
    return wrappedPromise;
  },
};
