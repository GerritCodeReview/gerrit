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

import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';

interface PolymerEvent extends EventApi, Event {}

interface ElementWithShadowRoot extends Element {
  shadowRoot: ShadowRoot;
}

/**
 * Type guard for element with a shadowRoot.
 */
function isElementWithShadowRoot(
  el: Element | ShadowRoot
): el is ElementWithShadowRoot {
  return 'shadowRoot' in el;
}

// TODO: maybe should have a better name for this
function getPathFromNode(el: EventTarget) {
  let tagName = '';
  let id = '';
  let className = '';
  if (el instanceof Element) {
    tagName = el.tagName;
    id = el.id;
    className = el.className;
  }
  if (
    !tagName ||
    'GR-APP' === tagName ||
    el instanceof DocumentFragment ||
    el instanceof HTMLSlotElement
  ) {
    return '';
  }
  let path = '';
  if (tagName) {
    path += tagName.toLowerCase();
  }
  if (id) {
    path += `#${id}`;
  }
  if (className) {
    path += `.${className.replace(/ /g, '.')}`;
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
  el: Element | LegacyElementMixin
) {
  let style;
  if (window.ShadyCSS) {
    style = window.ShadyCSS.getComputedStyleValue(el as Element, name);
    // `getComputedStyleValue` defined through LegacyElementMixin
    // TODO: It should be safe to just use `getComputedStyle`, but just to be safe
  } else if ('getComputedStyleValue' in el) {
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
export function querySelector(
  el: Element | ShadowRoot,
  selector: string
): Element | null {
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
    const allShadowNodes = [...node.querySelectorAll('*').values()]
      .filter(isElementWithShadowRoot)
      .map(child => child.shadowRoot);
    nodes = nodes.concat(allShadowNodes);

    // Add shadowRoot of current node if has one
    // as its not included in node.querySelectorAll('*')
    if (isElementWithShadowRoot(node)) {
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
export function querySelectorAll(
  el: Element | ShadowRoot,
  selector: string
): Element[] {
  let nodes = [el];
  const results = new Set<Element>();
  while (nodes.length) {
    const node = nodes.pop();

    if (!node || !node.querySelectorAll) continue;

    // Try find all from regular children
    node.querySelectorAll(selector).forEach(el => results.add(el));

    // Add all nodes with shadowRoot and loop through
    const allShadowNodes = [...node.querySelectorAll('*').values()]
      .filter(isElementWithShadowRoot)
      .map(child => child.shadowRoot);
    nodes = nodes.concat(allShadowNodes);

    // Add shadowRoot of current node if has one
    // as its not included in node.querySelectorAll('*')
    if (isElementWithShadowRoot(node)) {
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
 * domNode.onclick = e => {
 *  getEventPath(e); // eg: div.class1>p#pid.class2
 * }
 */
export function getEventPath(e?: PolymerEvent) {
  if (!e) return '';

  let path = e.path;
  if (!path || !path.length) {
    path = [];
    let el = e.target;
    while (el) {
      path.push(el);
      el = (el as Node).parentNode || (el as ShadowRoot).host;
    }
  }

  return path.reduce<string>((domPath: string, curEl: EventTarget) => {
    const pathForEl = getPathFromNode(curEl);
    if (!pathForEl) return domPath;
    return domPath ? `${pathForEl}>${domPath}` : pathForEl;
  }, '');
}

/**
 * Are any ancestors of the element (or the element itself) members of the
 * given class.
 *
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
