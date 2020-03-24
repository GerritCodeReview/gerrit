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

function getPathFromNode(el) {
  if (!el.tagName || el.tagName === 'GR-APP'
      || el instanceof DocumentFragment
      || el instanceof HTMLSlotElement) {
    return '';
  }
  let path = el.tagName.toLowerCase();
  if (el.id) path += `#${el.id}`;
  if (el.className) path += `.${el.className.replace(/ /g, '.')}`;
  return path;
}

export const util = {
  parseDate(dateStr) {
    // Timestamps are given in UTC and have the format
    // "'yyyy-mm-dd hh:mm:ss.fffffffff'" where "'ffffffffff'" represents
    // nanoseconds.
    // Munge the date into an ISO 8061 format and parse that.
    return new Date(dateStr.replace(' ', 'T') + 'Z');
  },

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

  /**
   * Get computed style value.
   *
   * If ShadyCSS is provided, use ShadyCSS api.
   * If `getComputedStyleValue` is provided on the elment, use it.
   * Otherwise fallback to native method (in polymer 2).
   *
   */
  getComputedStyleValue: (name, el) => {
    let style;
    if (window.ShadyCSS) {
      style = ShadyCSS.getComputedStyleValue(el, name);
    } else if (el.getComputedStyleValue) {
      style = el.getComputedStyleValue(name);
    } else {
      style = getComputedStyle(el).getPropertyValue(name);
    }
    return style;
  },

  /**
   * Query selector on a dom element.
   *
   * This is shadow DOM compatible, but only works when selector is within
   * one shadow host, won't work if your selector is crossing
   * multiple shadow hosts.
   *
   */
  querySelector: (el, selector) => {
    let nodes = [el];
    let result = null;
    while (nodes.length) {
      const node = nodes.pop();

      // Skip if it's an invalid node.
      if (!node || !node.querySelector) continue;

      // Try find it with native querySelector directly
      result = node.querySelector(selector);

      if (result) {
        break;
      }

      // Add all nodes with shadowRoot and loop through
      const allShadowNodes = [...node.querySelectorAll('*')]
          .filter(child => !!child.shadowRoot)
          .map(child => child.shadowRoot);
      nodes = nodes.concat(allShadowNodes);

      // Add shadowRoot of current node if has one
      // as its not included in node.querySelectorAll('*')
      if (node.shadowRoot) {
        nodes.push(node.shadowRoot);
      }
    }
    return result;
  },

  /**
   * Query selector all dom elements matching with certain selector.
   *
   * This is shadow DOM compatible, but only works when selector is within
   * one shadow host, won't work if your selector is crossing
   * multiple shadow hosts.
   *
   * Note: this can be very expensive, only use when have to.
   */
  querySelectorAll: (el, selector) => {
    let nodes = [el];
    const results = new Set();
    while (nodes.length) {
      const node = nodes.pop();

      if (!node || !node.querySelectorAll) continue;

      // Try find all from regular children
      [...node.querySelectorAll(selector)]
          .forEach(el => results.add(el));

      // Add all nodes with shadowRoot and loop through
      const allShadowNodes = [...node.querySelectorAll('*')]
          .filter(child => !!child.shadowRoot)
          .map(child => child.shadowRoot);
      nodes = nodes.concat(allShadowNodes);

      // Add shadowRoot of current node if has one
      // as its not included in node.querySelectorAll('*')
      if (node.shadowRoot) {
        nodes.push(node.shadowRoot);
      }
    }
    return [...results];
  },

  /**
   * Retrieves the dom path of the current event.
   *
   * If the event object contains a `path` property, then use it,
   * otherwise, construct the dom path based on the event target.
   *
   * @param {!Event} e
   * @return {string}
   * @example
   *
   * domNode.onclick = e => {
   *  getEventPath(e); // eg: div.class1>p#pid.class2
   * }
   */
  getEventPath: e => {
    if (!e) return '';

    let path = e.path;
    if (!path || !path.length) {
      path = [];
      let el = e.target;
      while (el) {
        path.push(el);
        el = el.parentNode || el.host;
      }
    }

    return path.reduce((domPath, curEl) => {
      const pathForEl = getPathFromNode(curEl);
      if (!pathForEl) return domPath;
      return domPath ? `${pathForEl}>${domPath}` : pathForEl;
    }, '');
  },
};
