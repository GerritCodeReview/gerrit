/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import {
  config,
  Shortcut,
  ShortcutHelpItem,
  ShortcutSection,
} from './shortcuts-config';
import {disableShortcuts$} from '../user/user-model';
import {
  ComboKey,
  eventMatchesShortcut,
  isElementTarget,
  Key,
  Modifier,
  Binding,
} from '../../utils/dom-util';
import {ReportingService} from '../gr-reporting/gr-reporting';

export type SectionView = Array<{binding: string[][]; text: string}>;

export interface ShortcutListener {
  shortcut: Shortcut;
  listener: (e: KeyboardEvent) => void;
}

export function listen(
  shortcut: Shortcut,
  listener: (e: KeyboardEvent) => void
): ShortcutListener {
  return {shortcut, listener};
}

/**
 * The interface for listener for shortcut events.
 */
export type ShortcutViewListener = (
  viewMap?: Map<ShortcutSection, SectionView>
) => void;

function isComboKey(key: string): key is ComboKey {
  return Object.values(ComboKey).includes(key as ComboKey);
}

export const COMBO_TIMEOUT_MS = 1000;

/**
 * Shortcuts service, holds all hosts, bindings and listeners.
 */
export class ShortcutsService {
  /**
   * Keeps track of the components that are currently active such that we can
   * show a shortcut help dialog that only shows the shortcuts that are
   * currently relevant.
   */
  private readonly activeShortcuts = new Map<HTMLElement, Shortcut[]>();

  /**
   * Keeps track of cleanup callbacks (which remove keyboard listeners) that
   * have to be invoked when a component unregisters itself.
   */
  private readonly cleanupsPerHost = new Map<HTMLElement, (() => void)[]>();

  /** Static map built in the constructor by iterating over the config. */
  private readonly bindings = new Map<Shortcut, Binding[]>();

  private readonly listeners = new Set<ShortcutViewListener>();

  /**
   * Stores the timestamp of the last combo key being pressed.
   * This enabled key combinations like 'g+o' where we can check whether 'g' was
   * pressed recently when 'o' is processed. Keys of this map must be items of
   * COMBO_KEYS. Values are Date timestamps in milliseconds.
   */
  private comboKeyLastPressed: {key?: ComboKey; timestampMs?: number} = {};

  /** Keeps track of the corresponding user preference. */
  private shortcutsDisabled = false;

  constructor(readonly reporting?: ReportingService) {
    for (const section of config.keys()) {
      const items = config.get(section) ?? [];
      for (const item of items) {
        this.bindings.set(item.shortcut, item.bindings);
      }
    }
    disableShortcuts$.subscribe(x => (this.shortcutsDisabled = x));
    document.addEventListener('keydown', (e: KeyboardEvent) => {
      if (!isComboKey(e.key)) return;
      if (this.shouldSuppress(e)) return;
      this.comboKeyLastPressed = {key: e.key, timestampMs: Date.now()};
    });
  }

  public _testOnly_isEmpty() {
    return this.activeShortcuts.size === 0 && this.listeners.size === 0;
  }

  isInComboKeyMode() {
    return Object.values(ComboKey).some(key =>
      this.isInSpecificComboKeyMode(key)
    );
  }

  isInSpecificComboKeyMode(comboKey: ComboKey) {
    const {key, timestampMs} = this.comboKeyLastPressed;
    return (
      key === comboKey &&
      timestampMs &&
      Date.now() - timestampMs < COMBO_TIMEOUT_MS
    );
  }

  /**
   * TODO(brohlfs): Reconcile with the addShortcut() function in dom-util.
   * Most likely we will just keep this one here, but that is something for a
   * follow-up change.
   */
  addShortcut(
    element: HTMLElement,
    shortcut: Binding,
    listener: (e: KeyboardEvent) => void
  ) {
    const wrappedListener = (e: KeyboardEvent) => {
      if (e.repeat) return;
      if (!eventMatchesShortcut(e, shortcut)) return;
      if (shortcut.combo) {
        if (!this.isInSpecificComboKeyMode(shortcut.combo)) return;
      } else {
        if (this.isInComboKeyMode()) return;
      }
      if (this.shouldSuppress(e)) return;
      e.preventDefault();
      e.stopPropagation();
      listener(e);
    };
    element.addEventListener('keydown', wrappedListener);
    return () => element.removeEventListener('keydown', wrappedListener);
  }

  shouldSuppress(e: KeyboardEvent) {
    if (this.shortcutsDisabled) return true;

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
        (tagName === 'A' || tagName === 'BUTTON' || tagName === 'PAPER-TAB'))
    ) {
      return true;
    }
    const path: EventTarget[] = e.composedPath() ?? [];
    for (const el of path) {
      if (!isElementTarget(el)) continue;
      if (el.tagName === 'GR-OVERLAY') return true;
    }
    // eg: {key: "k:keydown", ..., from: "gr-diff-view"}
    let key = `${e.key}:${e.type}`;
    if (this.isInSpecificComboKeyMode(ComboKey.G)) key = 'g+' + key;
    if (this.isInSpecificComboKeyMode(ComboKey.V)) key = 'v+' + key;
    if (e.shiftKey) key = 'shift+' + key;
    if (e.ctrlKey) key = 'ctrl+' + key;
    if (e.metaKey) key = 'meta+' + key;
    if (e.altKey) key = 'alt+' + key;
    let from = 'unknown';
    if (isElementTarget(e.currentTarget)) {
      from = e.currentTarget.tagName;
    }
    this.reporting?.reportInteraction('shortcut-triggered', {key, from});
    return false;
  }

  createTitle(shortcutName: Shortcut, section: ShortcutSection) {
    const desc = this.getDescription(section, shortcutName);
    const shortcut = this.getShortcut(shortcutName);
    return desc && shortcut ? `${desc} (shortcut: ${shortcut})` : '';
  }

  getBindingsForShortcut(shortcut: Shortcut) {
    return this.bindings.get(shortcut);
  }

  attachHost(host: HTMLElement, shortcuts: ShortcutListener[]) {
    this.activeShortcuts.set(
      host,
      shortcuts.map(s => s.shortcut)
    );
    const cleanups: (() => void)[] = [];
    for (const s of shortcuts) {
      const bindings = this.getBindingsForShortcut(s.shortcut);
      for (const binding of bindings ?? []) {
        if (binding.docOnly) continue;
        cleanups.push(this.addShortcut(document.body, binding, s.listener));
      }
    }
    this.cleanupsPerHost.set(host, cleanups);
    this.notifyViewListeners();
  }

  detachHost(host: HTMLElement) {
    this.activeShortcuts.delete(host);
    const cleanups = this.cleanupsPerHost.get(host);
    for (const cleanup of cleanups ?? []) cleanup();
    this.notifyViewListeners();
    return true;
  }

  addListener(listener: ShortcutViewListener) {
    this.listeners.add(listener);
    listener(this.directoryView());
  }

  removeListener(listener: ShortcutViewListener) {
    return this.listeners.delete(listener);
  }

  getDescription(section: ShortcutSection, shortcutName: Shortcut) {
    const bindings = config.get(section);
    if (!bindings) return '';
    const binding = bindings.find(binding => binding.shortcut === shortcutName);
    return binding?.text ?? '';
  }

  getShortcut(shortcutName: Shortcut) {
    const bindings = this.bindings.get(shortcutName);
    if (!bindings) return '';
    return bindings
      .map(binding => describeBinding(binding).join('+'))
      .join(',');
  }

  activeShortcutsBySection() {
    const activeShortcuts = new Set<Shortcut>();
    for (const shortcuts of this.activeShortcuts.values()) {
      for (const shortcut of shortcuts) {
        activeShortcuts.add(shortcut);
      }
    }

    const activeShortcutsBySection = new Map<
      ShortcutSection,
      ShortcutHelpItem[]
    >();
    config.forEach((shortcutList, section) => {
      shortcutList.forEach(shortcutHelp => {
        if (activeShortcuts.has(shortcutHelp.shortcut)) {
          if (!activeShortcutsBySection.has(section)) {
            activeShortcutsBySection.set(section, []);
          }
          activeShortcutsBySection.get(section)!.push(shortcutHelp);
        }
      });
    });
    return activeShortcutsBySection;
  }

  directoryView() {
    const view = new Map<ShortcutSection, SectionView>();
    this.activeShortcutsBySection().forEach((shortcutHelps, section) => {
      const sectionView: SectionView = [];
      shortcutHelps.forEach(shortcutHelp => {
        const bindingDesc = this.describeBindings(shortcutHelp.shortcut);
        if (!bindingDesc) {
          return;
        }
        this.distributeBindingDesc(bindingDesc).forEach(bindingDesc => {
          sectionView.push({
            binding: bindingDesc,
            text: shortcutHelp.text,
          });
        });
      });
      view.set(section, sectionView);
    });
    return view;
  }

  distributeBindingDesc(bindingDesc: string[][]): string[][][] {
    if (
      bindingDesc.length === 1 ||
      this.comboSetDisplayWidth(bindingDesc) < 21
    ) {
      return [bindingDesc];
    }
    // Find the largest prefix of bindings that is under the
    // size threshold.
    const head = [bindingDesc[0]];
    for (let i = 1; i < bindingDesc.length; i++) {
      head.push(bindingDesc[i]);
      if (this.comboSetDisplayWidth(head) >= 21) {
        head.pop();
        return [head].concat(this.distributeBindingDesc(bindingDesc.slice(i)));
      }
    }
    return [];
  }

  comboSetDisplayWidth(bindingDesc: string[][]) {
    const bindingSizer = (binding: string[]) =>
      binding.reduce((acc, key) => acc + key.length, 0);
    // Width is the sum of strings + (n-1) * 2 to account for the word
    // "or" joining them.
    return (
      bindingDesc.reduce((acc, binding) => acc + bindingSizer(binding), 0) +
      2 * (bindingDesc.length - 1)
    );
  }

  describeBindings(shortcut: Shortcut): string[][] | null {
    const bindings = this.bindings.get(shortcut);
    if (!bindings) return null;
    return bindings
      .filter(binding => !binding.docOnly)
      .map(binding => describeBinding(binding));
  }

  notifyViewListeners() {
    const view = this.directoryView();
    this.listeners.forEach(listener => listener(view));
  }
}

function describeKey(key: string | Key) {
  switch (key) {
    case Key.UP:
      return '\u2191'; // ↑
    case Key.DOWN:
      return '\u2193'; // ↓
    case Key.LEFT:
      return '\u2190'; // ←
    case Key.RIGHT:
      return '\u2192'; // →
    default:
      return key;
  }
}

export function describeBinding(binding: Binding): string[] {
  const description: string[] = [];
  if (binding.combo === ComboKey.G) {
    description.push('g');
  }
  if (binding.combo === ComboKey.V) {
    description.push('v');
  }
  if (binding.modifiers?.includes(Modifier.SHIFT_KEY)) {
    description.push('Shift');
  }
  if (binding.modifiers?.includes(Modifier.ALT_KEY)) {
    description.push('Alt');
  }
  if (binding.modifiers?.includes(Modifier.CTRL_KEY)) {
    description.push('Ctrl');
  }
  if (binding.modifiers?.includes(Modifier.META_KEY)) {
    description.push('Meta/Cmd');
  }
  description.push(describeKey(binding.key));
  return description;
}
