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

import {EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';

/**
 * Event emitted from polymer elements.
 */
export interface PolymerEvent extends EventApi, Event {}

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
 */
export function getComputedStyleValue(name: string, el: Element) {
  return getComputedStyle(el).getPropertyValue(name).trim();
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
    const allShadowNodes = [...node.querySelectorAll('*')]
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
    [...node.querySelectorAll(selector)].forEach(el => results.add(el));

    // Add all nodes with shadowRoot and loop through
    const allShadowNodes = [...node.querySelectorAll('*')]
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
export function getEventPath<T extends PolymerEvent>(e?: T) {
  if (!e) return '';

  let path = e.composedPath();
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
  stopElement?: Element
) {
  let isDescendant = element.classList.contains(className);
  while (
    !isDescendant &&
    element.parentElement &&
    (!stopElement || element.parentElement !== stopElement)
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
 */
export function strToClassName(str = '', prefix = 'generated_') {
  return `${prefix}${str.replace(/[^a-zA-Z0-9-_]/g, '_')}`;
}

// document.activeElement is not enough, because it's not getting activeElement
// without looking inside of shadow roots. This will find best activeElement.
export function findActiveElement(
  root: DocumentOrShadowRoot | null,
  ignoreDialogs?: boolean
): HTMLElement | null {
  if (root === null) {
    return null;
  }
  if (
    ignoreDialogs &&
    root.activeElement &&
    root.activeElement.nodeName.toUpperCase().includes('DIALOG')
  ) {
    return null;
  }
  if (root.activeElement?.shadowRoot?.activeElement) {
    return findActiveElement(root.activeElement.shadowRoot);
  }
  if (!root.activeElement) {
    return null;
  }
  // We block some elements
  if ('BODY' === root.activeElement.nodeName.toUpperCase()) {
    return null;
  }
  return root.activeElement as HTMLElement;
}
