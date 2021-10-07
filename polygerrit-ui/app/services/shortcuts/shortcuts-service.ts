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

/** Enum for all special shortcuts */
export enum SPECIAL_SHORTCUT {
  DOC_ONLY = 'DOC_ONLY',
  GO_KEY = 'GO_KEY',
  V_KEY = 'V_KEY',
}
export type SectionView = Array<{binding: string[][]; text: string}>;

/**
 * The interface for listener for shortcut events.
 */
export type ShortcutListener = (
  viewMap?: Map<ShortcutSection, SectionView>
) => void;

/**
 * Shortcuts service, holds all hosts, bindings and listeners.
 */
export class ShortcutsService {
  private readonly activeHosts = new Map<unknown, Map<string, string>>();

  private readonly bindings = new Map<Shortcut, string[]>();

  public _testOnly_getBindings() {
    return this.bindings;
  }

  public _testOnly_isEmpty() {
    return this.activeHosts.size === 0 && this.listeners.size === 0;
  }

  private readonly listeners = new Set<ShortcutListener>();

  createTitle(shortcutName: Shortcut, section: ShortcutSection) {
    const desc = this.getDescription(section, shortcutName);
    const shortcut = this.getShortcut(shortcutName);
    return desc && shortcut ? `${desc} (shortcut: ${shortcut})` : '';
  }

  bindShortcut(shortcut: Shortcut, ...bindings: string[]) {
    this.bindings.set(shortcut, bindings);
  }

  getBindingsForShortcut(shortcut: Shortcut) {
    return this.bindings.get(shortcut);
  }

  attachHost(host: unknown, shortcuts: Map<string, string>) {
    this.activeHosts.set(host, shortcuts);
    this.notifyListeners();
  }

  detachHost(host: unknown) {
    if (!this.activeHosts.delete(host)) return false;
    this.notifyListeners();
    return true;
  }

  addListener(listener: ShortcutListener) {
    this.listeners.add(listener);
    listener(this.directoryView());
  }

  removeListener(listener: ShortcutListener) {
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
      .map(binding => this.describeBinding(binding).join('+'))
      .join(',');
  }

  activeShortcutsBySection() {
    const activeShortcuts = new Set<string>();
    this.activeHosts.forEach(shortcuts => {
      shortcuts.forEach((_, shortcut) => activeShortcuts.add(shortcut));
    });

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
          // From previous condition, the `get(section)`
          // should always return a valid result
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
    if (!bindings) {
      return null;
    }
    if (bindings[0] === SPECIAL_SHORTCUT.GO_KEY) {
      return bindings
        .slice(1)
        .map(binding => this._describeKey(binding))
        .map(binding => ['g'].concat(binding));
    }
    if (bindings[0] === SPECIAL_SHORTCUT.V_KEY) {
      return bindings
        .slice(1)
        .map(binding => this._describeKey(binding))
        .map(binding => ['v'].concat(binding));
    }

    return bindings
      .filter(binding => binding !== SPECIAL_SHORTCUT.DOC_ONLY)
      .map(binding => this.describeBinding(binding));
  }

  _describeKey(key: string) {
    switch (key) {
      case 'shift':
        return 'Shift';
      case 'meta':
        return 'Meta';
      case 'ctrl':
        return 'Ctrl';
      case 'enter':
        return 'Enter';
      case 'up':
        return '\u2191'; // ↑
      case 'down':
        return '\u2193'; // ↓
      case 'left':
        return '\u2190'; // ←
      case 'right':
        return '\u2192'; // →
      default:
        return key;
    }
  }

  describeBinding(binding: string) {
    // single key bindings
    if (binding.length === 1) {
      return [binding];
    }
    return binding
      .split(':')[0]
      .split('+')
      .map(part => this._describeKey(part));
  }

  notifyListeners() {
    const view = this.directoryView();
    this.listeners.forEach(listener => listener(view));
  }
}
