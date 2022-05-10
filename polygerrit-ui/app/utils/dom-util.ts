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

export function isElement(node: Node): node is Element {
  return node.nodeType === 1;
}

export function isElementTarget(
  target: EventTarget | null | undefined
): target is Element {
  if (!target) return false;
  return 'nodeType' in target && isElement(target as Node);
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

export function windowLocationReload() {
  const e = new Error();
  console.info(`Calling window.location.reload(): ${e.stack}`);
  window.location.reload();
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
export function getEventPath<T extends MouseEvent>(e?: T) {
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
  root: Document | ShadowRoot | null,
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

// Whether the browser is Safari. Used for polyfilling unique browser behavior.
export function isSafari() {
  return (
    /^((?!chrome|android).)*safari/i.test(navigator.userAgent) ||
    /iPad|iPhone|iPod/.test(navigator.userAgent)
  );
}

export function whenVisible(
  element: Element,
  callback: () => void,
  marginPx = 0
) {
  const observer = new IntersectionObserver(
    (entries: IntersectionObserverEntry[]) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          observer.unobserve(entry.target);
          callback();
          return;
        }
      }
    },
    {rootMargin: `${marginPx}px`}
  );
  observer.observe(element);
}

/**
 * Toggles a CSS class on or off for an element.
 */
export function toggleClass(el: Element, className: string, bool?: boolean) {
  if (bool === undefined) {
    bool = !el.classList.contains(className);
  }
  if (bool) {
    el.classList.add(className);
  } else {
    el.classList.remove(className);
  }
}

/**
 * For matching the `key` property of KeyboardEvents. These are known to work
 * with Firefox, Safari and Chrome.
 */
export enum Key {
  ENTER = 'Enter',
  ESC = 'Escape',
  TAB = 'Tab',
  SPACE = ' ',
  LEFT = 'ArrowLeft',
  RIGHT = 'ArrowRight',
  UP = 'ArrowUp',
  DOWN = 'ArrowDown',
}

export enum Modifier {
  ALT_KEY,
  CTRL_KEY,
  META_KEY,
  SHIFT_KEY,
}

export enum ComboKey {
  G = 'g',
  V = 'v',
}

export interface Binding {
  key: string | Key;
  /** Defaults to false. */
  docOnly?: boolean;
  /** Defaults to not being a combo shortcut. */
  combo?: ComboKey;
  /** Defaults to no modifiers. */
  modifiers?: Modifier[];
  /** Defaults to false. If true, then `event.repeat === true` is allowed. */
  allowRepeat?: boolean;
}

const ALPHA_NUM = new RegExp(/^[A-Za-z0-9]$/);

/**
 * For "normal" keys we do not check that the SHIFT modifier is pressed or not,
 * because that depends on the keyboard layout. Just checking the key string is
 * sufficient.
 *
 * But for some special keys it is important whether SHIFT is pressed at the
 * same time, for example we want to distinguish Enter from Shift+Enter.
 */
function shiftMustMatch(key: string | Key) {
  return Object.values(Key).includes(key as Key);
}

/**
 * For a-zA-Z0-9 and for Enter, Tab, etc. we want to check the ALT modifier.
 *
 * But for special chars like []/? we don't care whether the user is pressing
 * the ALT modifier to produce the special char. For example on a German
 * keyboard layout you have to press ALT to produce a [.
 */
function altMustMatch(key: string | Key) {
  return ALPHA_NUM.test(key) || Object.values(Key).includes(key as Key);
}

export function eventMatchesShortcut(
  e: KeyboardEvent,
  shortcut: Binding
): boolean {
  if (e.key !== shortcut.key) return false;
  const modifiers = shortcut.modifiers ?? [];
  if (e.ctrlKey !== modifiers.includes(Modifier.CTRL_KEY)) return false;
  if (e.metaKey !== modifiers.includes(Modifier.META_KEY)) return false;
  if (
    altMustMatch(e.key) &&
    e.altKey !== modifiers.includes(Modifier.ALT_KEY)
  ) {
    return false;
  }
  if (
    shiftMustMatch(e.key) &&
    e.shiftKey !== modifiers.includes(Modifier.SHIFT_KEY)
  ) {
    return false;
  }
  return true;
}

export interface ShortcutOptions {
  /**
   * Do you want to suppress events from <input> elements and such?
   */
  shouldSuppress?: boolean;
  /**
   * Do you want to take care of calling preventDefault() and
   * stopPropagation() yourself?
   */
  doNotPrevent?: boolean;
}

export function addGlobalShortcut(
  shortcut: Binding,
  listener: (e: KeyboardEvent) => void,
  options: ShortcutOptions = {
    shouldSuppress: true,
    doNotPrevent: false,
  }
) {
  return addShortcut(document.body, shortcut, listener, options);
}

/**
 * Deprecated.
 *
 * For LitElement use the shortcut-controller.
 * For PolymerElement use the keyboard-shortcut-mixin.
 */
export function addShortcut(
  element: HTMLElement,
  shortcut: Binding,
  listener: (e: KeyboardEvent) => void,
  options: ShortcutOptions = {
    shouldSuppress: false,
    doNotPrevent: false,
  }
) {
  const wrappedListener = (e: KeyboardEvent) => {
    if (e.repeat && !shortcut.allowRepeat) return;
    if (options.shouldSuppress && shouldSuppress(e)) return;
    if (!eventMatchesShortcut(e, shortcut)) return;
    if (!options.doNotPrevent) e.preventDefault();
    if (!options.doNotPrevent) e.stopPropagation();
    listener(e);
  };
  element.addEventListener('keydown', wrappedListener);
  return () => element.removeEventListener('keydown', wrappedListener);
}

export function modifierPressed(e: KeyboardEvent) {
  return e.altKey || e.ctrlKey || e.metaKey || e.shiftKey;
}

export function shiftPressed(e: KeyboardEvent) {
  return e.shiftKey;
}

/**
 * When you listen on keyboard events, then within Gerrit's web app you may want
 * to avoid firing in certain common scenarios such as key strokes from <input>
 * elements. But this can also be undesirable, for example Ctrl-Enter from
 * <input> should trigger a save event.
 *
 * The shortcuts-service has a stateful method `shouldSuppress()` with
 * reporting functionality, which delegates to here.
 */
export function shouldSuppress(e: KeyboardEvent): boolean {
  // Note that when you listen on document, then `e.currentTarget` will be the
  // document and `e.target` will be `<gr-app>` due to shadow dom, but by
  // using the composedPath() you can actually find the true origin of the
  // event.
  const rootTarget = e.composedPath()[0];
  if (!isElementTarget(rootTarget)) return false;
  const tagName = rootTarget.tagName;
  const type = rootTarget.getAttribute('type');

  if (
    // Suppress shortcuts on <input> and <textarea>, but not on
    // checkboxes, because we want to enable workflows like 'click
    // mark-reviewed and then press ] to go to the next file'.
    (tagName === 'INPUT' && type !== 'checkbox') ||
    tagName === 'TEXTAREA' ||
    // Suppress shortcuts if the key is 'enter'
    // and target is an anchor or button or paper-tab.
    (e.keyCode === 13 &&
      (tagName === 'A' ||
        tagName === 'BUTTON' ||
        tagName === 'GR-BUTTON' ||
        tagName === 'PAPER-TAB'))
  ) {
    return true;
  }
  const path: EventTarget[] = e.composedPath() ?? [];
  for (const el of path) {
    if (!isElementTarget(el)) continue;
    if (el.tagName === 'GR-OVERLAY') return true;
  }
  return false;
}

/** Executes the given callback when the element's height is > 0. */
export function whenRendered(el: HTMLElement, callback: () => void) {
  if (el.clientHeight > 0) {
    callback();
    return;
  }
  const obs = new ResizeObserver(() => {
    if (el.clientHeight > 0) {
      callback();
      obs.unobserve(el);
    }
  });
  obs.observe(el);
}

/**
 * Mimics a Polymer utility. `requestAnimationFrame` is called before the next
 * browser paint. An additional `setTimeout` ensures that the pain has
 * actually happened.
 */
export function afterNextRender(callback: (value?: unknown) => void) {
  requestAnimationFrame(() => {
    setTimeout(callback);
  });
}
