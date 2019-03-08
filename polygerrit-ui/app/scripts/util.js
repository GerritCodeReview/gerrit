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
(function(window) {
  'use strict';

  const util = window.util || {};

  util.parseDate = function(dateStr) {
    // Timestamps are given in UTC and have the format
    // "'yyyy-mm-dd hh:mm:ss.fffffffff'" where "'ffffffffff'" represents
    // nanoseconds.
    // Munge the date into an ISO 8061 format and parse that.
    return new Date(dateStr.replace(' ', 'T') + 'Z');
  };

  util.getCookie = function(name) {
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
  };

  /**
   * Make the promise cancelable.
   *
   * Returns a promise with a `cancel()` method wrapped around `promise`.
   * Calling `cancel()` will reject the returned promise with
   * {isCancelled: true} synchronously. If the inner promise for a cancelled
   * promise resolves or rejects this is ignored.
   */
  util.makeCancelable = promise => {
    let wasCanceled = false;

    let rejectPromise;

    const wrappedPromise = new Promise((resolve, reject) => {
      rejectPromise = reject;
      promise.then(val => {
        if (!wasCanceled) resolve(val);
      });
      promise.catch(error => {
        if (!wasCanceled) reject(error);
      });
    });

    wrappedPromise.cancel = () => {
      if (wasCanceled) return;
      rejectPromise({isCanceled: true});
      wasCanceled = true;
    };
    return wrappedPromise;
  };

  window.util = util;
})(window);
