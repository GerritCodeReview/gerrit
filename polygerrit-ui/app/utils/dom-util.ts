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

import {PolymerElement} from '@polymer/polymer';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {hasOwnProperty} from './common-util';

interface PolymerEvent extends EventApi, Event {}

function getPathFromNode(el: Node | Element) {
  let tagName = '';
  if (el instanceof Node) {
    tagName = el.nodeName;
  }
  if (el instanceof Element) {
    tagName = el.tagName;
  }
  if (
    !tagName ||
    ['GR-APP', 'BODY', 'HTML', '#document'].includes(tagName) ||
    el instanceof DocumentFragment ||
    el instanceof HTMLSlotElement
  ) {
    return '';
  }
  let path = '';
  if (tagName) {
    path += tagName.toLowerCase();
  }
  if ('id' in el && el.id) {
    path += `#${el.id}`;
  }
  if ('className' in el && el.className) {
    path += `.${el.className.replace(/ /g, '.')}`;
  }
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
export function getComputedStyleValue(
  name: string,
  el: Element | PolymerElement | LegacyElementMixin
) {
  let style;
  if (window.ShadyCSS) {
    style = window.ShadyCSS.getComputedStyleValue(el as Element, name);
    // `getComputedStyleValue` defined through LegacyElementMixin
    // TODO: It should be safe to just use `getComputedStyle`, but just to be safe
  } else if (hasOwnProperty(el, 'getComputedStyleValue')) {
    style = el.getComputedStyleValue(name);
  } else {
    style = getComputedStyle(el as Element).getPropertyValue(name);
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
export function querySelector(el: Element | ShadowRoot, selector: string) {
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
      .filter(
        (child): child is Element & Record<'shadowRoot', ShadowRoot> =>
          !!child.shadowRoot
      )
      .map(child => child.shadowRoot);
    nodes = nodes.concat(allShadowNodes);

    // Add shadowRoot of current node if has one
    // as its not included in node.querySelectorAll('*')
    if ('shadowRoot' in node && node.shadowRoot) {
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
export function querySelectorAll(el: Element | ShadowRoot, selector: string) {
  let nodes = [el];
  const results = new Set();
  while (nodes.length) {
    const node = nodes.pop();

    if (!node || !node.querySelectorAll) continue;

    // Try find all from regular children
    [...node.querySelectorAll(selector)].forEach(el => results.add(el));

    // Add all nodes with shadowRoot and loop through
    const allShadowNodes = [...node.querySelectorAll('*')]
      .filter(
        (child): child is Element & Record<'shadowRoot', ShadowRoot> =>
          !!child.shadowRoot
      )
      .map(child => child.shadowRoot);
    nodes = nodes.concat(allShadowNodes);

    // Add shadowRoot of current node if has one
    // as its not included in node.querySelectorAll('*')
    if ('shadowRoot' in node && node.shadowRoot) {
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
export function getEventPath(e: PolymerEvent) {
  if (!e) return '';

  let path = e.path as Node[];
  if (!path || !path.length) {
    path = [];
    let el = e.target as Node | null;
    while (el) {
      if (el.nodeName === 'BODY') break;
      path.push(el);
      if (el.parentNode) {
        el = el.parentNode;
      } else if (hasOwnProperty(el, 'host')) {
        el = el.host as Node;
      } else {
        el = null;
      }
    }
  }

  return path.reduce<string>((domPath: string, curEl: Node) => {
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
export function descendedFromClass(
  element: Element,
  className: string,
  opt_stopElement: Element
) {
  let isDescendant = element.classList.contains(className);
  while (
    !isDescendant &&
    element.parentElement &&
    (!opt_stopElement || element.parentElement !== opt_stopElement)
  ) {
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

// shared API element
// TODO: once gr-js-api-interface moved to ts
// use GrJsApiInterface instead
let _sharedApiEl: Element;

/**
 * Retrieves the shared API element.
 * We want to keep a single instance of API element instead of
 * creating multiple elements.
 */
export function getSharedApiEl() {
  if (!_sharedApiEl) {
    _sharedApiEl = document.createElement('gr-js-api-interface');
  }
  return _sharedApiEl;
}
