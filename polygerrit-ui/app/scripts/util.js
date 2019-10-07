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

  // Astral code point as per https://mathiasbynens.be/notes/javascript-unicode
  const REGEX_ASTRAL_SYMBOL = /[\uD800-\uDBFF][\uDC00-\uDFFF]/;

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
  };

  /**
   * Get computed style value.
   *
   * If ShadyCSS is provided, use ShadyCSS api.
   * If `getComputedStyleValue` is provided on the elment, use it.
   * Otherwise fallback to native method (in polymer 2).
   *
   */
  util.getComputedStyleValue = (name, el) => {
    let style;
    if (window.ShadyCSS) {
      style = ShadyCSS.getComputedStyleValue(el, name);
    } else if (el.getComputedStyleValue) {
      style = el.getComputedStyleValue(name);
    } else {
      style = getComputedStyle(el).getPropertyValue(name);
    }
    return style;
  };

  /**
   * Query selector on a dom element.
   *
   * This is shadow DOM compatible, but only works when selector is within
   * one shadow host, won't work if your selector is crossing
   * multiple shadow hosts.
   *
   */
  util.querySelector = (el, selector) => {
    let nodes = [el];
    let element = null;
    while (nodes.length) {
      const node = nodes.pop();

      // Skip if it's an invalid node.
      if (!node || !node.querySelector) continue;

      // Try find it with native querySelector directly
      element = node.querySelector(selector);

      if (element) {
        break;
      } else if (node.shadowRoot) {
        // If shadowHost detected, add the host and its children
        nodes = nodes.concat(Array.from(node.children));
        nodes.push(node.shadowRoot);
      } else {
        nodes = nodes.concat(Array.from(node.children));
      }
    }
    return element;
  };

  /**
   * Node.prototype.splitText Unicode-valid alternative.
   *
   * DOM Api for splitText() is broken for Unicode:
   * https://mathiasbynens.be/notes/javascript-unicode
   *
   */
  util.splitTextNode = (node, offset) => {
    if (node.textContent.match(REGEX_ASTRAL_SYMBOL)) {
      const head = Array.from(node.textContent);
      const tail = head.splice(offset);
      const parent = node.parentNode;

      // Split the content of the original node.
      node.textContent = head.join('');

      const tailNode = document.createTextNode(tail.join(''));
      if (parent) {
        parent.insertBefore(tailNode, node.nextSibling);
      }
      return tailNode;
    } else {
      return node.splitText(offset);
    }
  };

  window.util = util;
})(window);
