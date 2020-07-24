/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

/**
 * Get computed style value.
 *
 * If ShadyCSS is provided, use ShadyCSS api.
 * If `getComputedStyleValue` is provided on the element, use it.
 * Otherwise fallback to native method (in polymer 2).
 *
 */
export function getComputedStyleValue(name, el) {
  let style;
  if (window.ShadyCSS) {
    style = ShadyCSS.getComputedStyleValue(el, name);
  } else if (el.getComputedStyleValue) {
    style = el.getComputedStyleValue(name);
  } else {
    style = getComputedStyle(el).getPropertyValue(name);
  }
  return style;
}

/**
 * Query selector on a dom element.
 *
 * This is shadow DOM compatible, but only works when selector is within
 * one shadow host, won't work if your selector is crossing
 * multiple shadow hosts.
 *
 */
export function querySelector(el, selector) {
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
}

/**
 * Query selector all dom elements matching with certain selector.
 *
 * This is shadow DOM compatible, but only works when selector is within
 * one shadow host, won't work if your selector is crossing
 * multiple shadow hosts.
 *
 * Note: this can be very expensive, only use when have to.
 */
export function querySelectorAll(el, selector) {
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
}

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
export function getEventPath(e) {
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
}

/**
 * Are any ancestors of the element (or the element itself) members of the
 * given class.
 *
 * @param {!Element} element
 * @param {string} className
 * @param {Element=} opt_stopElement If provided, stop traversing the
 *     ancestry when the stop element is reached. The stop element's class
 *     is not checked.
 * @return {boolean}
 */
export function descendedFromClass(element, className, opt_stopElement) {
  let isDescendant = element.classList.contains(className);
  while (!isDescendant && element.parentElement &&
        (!opt_stopElement || element.parentElement !== opt_stopElement)) {
    isDescendant = element.classList.contains(className);
    element = element.parentElement;
  }
  return isDescendant;
}

/**
 * Convert any string into a valid class name.
 *
 * For class names, naming rules:
 * Must begin with a letter A-Z or a-z
 * Can be followed by: letters (A-Za-z), digits (0-9), hyphens ("-"), and underscores ("_")
 *
 * @param {string} str
 * @param {string} prefix
 */
export function strToClassName(str = '', prefix = 'generated_') {
  return `${prefix}${str.replace(/[^a-zA-Z0-9-_]/g, '_')}`;
}

/**
 * Find and rewrite the href for the favicon link in <head>.
 *
 * @param {string} new_path The new href path to point to.
 */
export function setFavicon(new_path) {
    var link = document.querySelector("link[rel*='icon']") || document.createElement('link');
    link.type = 'image/x-icon';
    link.rel = 'shortcut icon';
    link.href = new_path;
    document.getElementsByTagName('head')[0].appendChild(link);
}

// shared API element
let _sharedApiEl;

export function getSharedApiEl() {
  if (!_sharedApiEl) {
    _sharedApiEl = document.createElement('gr-js-api-interface');
  }
  return _sharedApiEl;
}
