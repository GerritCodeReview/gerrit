/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
/*

How to Add a Keyboard Shortcut
==============================

A keyboard shortcut is composed of the following parts:

  1. A semantic identifier (e.g. OPEN_CHANGE, NEXT_PAGE)
  2. Documentation for the keyboard shortcut help dialog
  3. A binding between key combos and the semantic identifier
  4. A binding between the semantic identifier and a listener

Parts (1) and (2) for all shortcuts are defined in this file. The semantic
identifier is declared in the Shortcut enum near the head of this script:

  const Shortcut = {
    // ...
    TOGGLE_LEFT_PANE: 'TOGGLE_LEFT_PANE',
    // ...
  };

Immediately following the Shortcut enum definition, there is a _describe
function defined which is then invoked many times to populate the help dialog.
Add a new invocation here to document the shortcut:

  _describe(Shortcut.TOGGLE_LEFT_PANE, ShortcutSection.DIFFS,
      'Hide/show left diff');

When an attached view binds one or more key combos to this shortcut, the help
dialog will display this text in the given section (in this case, "Diffs"). See
the ShortcutSection enum immediately below for the list of supported sections.

Part (3), the actual key bindings, are declared by gr-app. In the future, this
system may be expanded to allow key binding customizations by plugins or user
preferences. Key bindings are defined in the following forms:

  // Ordinary shortcut with a single binding.
  this.bindShortcut(
      Shortcut.TOGGLE_LEFT_PANE, 'shift+a');

  // Ordinary shortcut with multiple bindings.
  this.bindShortcut(
      Shortcut.CURSOR_NEXT_FILE, 'j', 'down');

  // A "go-key" keyboard shortcut, which is combined with a previously and
  // continuously pressed "go" key (the go-key is hard-coded as 'g').
  this.bindShortcut(
      Shortcut.GO_TO_OPENED_CHANGES, SPECIAL_SHORTCUT.GO_KEY, 'o');

  // A "doc-only" keyboard shortcut. This declares the key-binding for help
  // dialog purposes, but doesn't actually implement the binding. It is up
  // to some element to implement this binding using iron-a11y-keys-behavior's
  // keyBindings property.
  this.bindShortcut(
      Shortcut.EXPAND_ALL_COMMENT_THREADS, SPECIAL_SHORTCUT.DOC_ONLY, 'e');

Part (4), the listener definitions, are declared by the view or element that
implements the shortcut behavior. This is done by implementing a method named
keyboardShortcuts() in an element that mixes in this behavior, returning an
object that maps semantic identifiers (as property names) to listener method
names, like this:

  keyboardShortcuts() {
    return {
      [Shortcut.TOGGLE_LEFT_PANE]: '_handleToggleLeftPane',
    };
  },

You can implement key bindings in an element that is hosted by a view IF that
element is always attached exactly once under that view (e.g. the search bar in
gr-app). When that is not the case, you will have to define a doc-only binding
in gr-app, declare the shortcut in the view that hosts the element, and use
iron-a11y-keys-behavior's keyBindings attribute to implement the binding in the
element. An example of this is in comment threads. A diff view supports actions
on comment threads, but there may be zero or many comment threads attached at
any given point. So the shortcut is declared as doc-only by the diff view and
by gr-app, and actually implemented by gr-comment-thread.

NOTE: doc-only shortcuts will not be customizable in the same way that other
shortcuts are.
*/

import {IronA11yKeysBehavior} from '@polymer/iron-a11y-keys-behavior/iron-a11y-keys-behavior';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class';
import {property} from '@polymer/decorators';
import {PolymerElement} from '@polymer/polymer';
import {check, Constructor} from '../../utils/common-util';
import {getKeyboardEvent, isModifierPressed} from '../../utils/dom-util';
import {CustomKeyboardEvent} from '../../types/events';
import {appContext} from '../../services/app-context';
import {
  Shortcut,
  ShortcutSection,
} from '../../services/shortcuts/shortcuts-config';
import {
  ShortcutListener,
  SPECIAL_SHORTCUT,
  SectionView,
} from '../../services/shortcuts/shortcuts-service';

export {
  Shortcut,
  ShortcutSection,
  SPECIAL_SHORTCUT,
  ShortcutListener,
  SectionView,
};

// The maximum age of a keydown event to be used in a jump navigation. This
// is only for cases when the keyup event is lost.
const GO_KEY_TIMEOUT_MS = 1000;

const V_KEY_TIMEOUT_MS = 1000;

interface IronA11yKeysMixinConstructor {
  // Note: this is needed to have same interface as other mixins
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  new (...args: any[]): IronA11yKeysBehavior;
}
/**
 * @polymer
 * @mixinFunction
 */
const InternalKeyboardShortcutMixin = <
  T extends Constructor<PolymerElement> & IronA11yKeysMixinConstructor
>(
  superClass: T
) => {
  /**
   * @polymer
   * @mixinClass
   */
  class Mixin extends superClass {
    @property({type: Number})
    _shortcut_go_key_last_pressed: number | null = null;

    @property({type: Number})
    _shortcut_v_key_last_pressed: number | null = null;

    @property({type: Object})
    _shortcut_go_table: Map<string, string> = new Map<string, string>();

    @property({type: Object})
    _shortcut_v_table: Map<string, string> = new Map<string, string>();

    Shortcut = Shortcut;

    ShortcutSection = ShortcutSection;

    private _disableKeyboardShortcuts = false;

    private readonly restApiService = appContext.restApiService;

    private readonly reporting = appContext.reportingService;

    private readonly shortcuts = appContext.shortcutsService;

    /** Used to disable shortcuts when the element is not visible. */
    private observer?: IntersectionObserver;

    /**
     * Enabling shortcuts only when the element is visible (see `observer`
     * above) is a great feature, but often what you want is for the *page* to
     * be visible, not the specific child element that registers keyboard
     * shortcuts. An example is the FileList in the ChangeView. So we allow
     * a broader observer target to be specified here, and fall back to
     * `this` as the default.
     */
    @property({type: Object})
    observerTarget: Element = this;

    /** Are shortcuts currently enabled? True only when element is visible. */
    private bindingsEnabled = false;

    modifierPressed(event: CustomKeyboardEvent) {
      /* We are checking for g/v as modifiers pressed. There are cases such as
       * pressing v and then /, where we want the handler for / to be triggered.
       * TODO(dhruvsri): find a way to support that keyboard combination
       */
      const e = getKeyboardEvent(event);
      return (
        isModifierPressed(e) || !!this._inGoKeyMode() || !!this.inVKeyMode()
      );
    }

    shouldSuppressKeyboardShortcut(event: CustomKeyboardEvent) {
      if (this._disableKeyboardShortcuts) return true;
      const e = getKeyboardEvent(event);
      // TODO(TS): maybe override the EventApi, narrow it down to Element always
      const target = (dom(e) as EventApi).rootTarget as Element;
      const tagName = target.tagName;
      const type = target.getAttribute('type');
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
      for (let i = 0; e.path && i < e.path.length; i++) {
        // TODO(TS): narrow this down to Element from EventTarget first
        if ((e.path[i] as Element).tagName === 'GR-OVERLAY') {
          return true;
        }
      }

      // eg: {key: "k:keydown", ..., from: "gr-diff-view"}
      let key = `${(e as unknown as KeyboardEvent).key}:${e.type}`;
      if (this._inGoKeyMode()) key = 'g+' + key;
      if (this.inVKeyMode()) key = 'v+' + key;
      if (e.shiftKey) key = 'shift+' + key;
      if (e.ctrlKey) key = 'ctrl+' + key;
      if (e.metaKey) key = 'meta+' + key;
      if (e.altKey) key = 'alt+' + key;
      this.reporting.reportInteraction('shortcut-triggered', {
        key,
        from: this.nodeName ?? 'unknown',
      });
      return false;
    }

    // Alias for getKeyboardEvent.
    getKeyboardEvent(e: CustomKeyboardEvent) {
      return getKeyboardEvent(e);
    }

    bindShortcut(shortcut: Shortcut, ...bindings: string[]) {
      this.shortcuts.bindShortcut(shortcut, ...bindings);
    }

    _addOwnKeyBindings(shortcut: Shortcut, handler: string) {
      const bindings = this.shortcuts.getBindingsForShortcut(shortcut);
      if (!bindings) {
        return;
      }
      if (bindings[0] === SPECIAL_SHORTCUT.DOC_ONLY) {
        return;
      }
      if (bindings[0] === SPECIAL_SHORTCUT.GO_KEY) {
        bindings
          .slice(1)
          .forEach(binding => this._shortcut_go_table.set(binding, handler));
      } else if (bindings[0] === SPECIAL_SHORTCUT.V_KEY) {
        // for each binding added with the go/v key, we set the handler to be
        // handleVKeyAction. handleVKeyAction then looks up in th
        // shortcut_table to see what the relevant handler should be
        bindings
          .slice(1)
          .forEach(binding => this._shortcut_v_table.set(binding, handler));
      } else {
        this.addOwnKeyBinding(bindings.join(' '), handler);
      }
    }

    override connectedCallback() {
      super.connectedCallback();
      this.restApiService.getPreferences().then(prefs => {
        if (prefs?.disable_keyboard_shortcuts) {
          this._disableKeyboardShortcuts = true;
        }
      });
      this.createVisibilityObserver();
      this.enableBindings();
    }

    override disconnectedCallback() {
      this.destroyVisibilityObserver();
      this.disableBindings();
      super.disconnectedCallback();
    }

    /**
     * Creates an intersection observer that enables bindings when the
     * element is visible and disables them when the element is hidden.
     */
    private createVisibilityObserver() {
      if (!this.hasKeyboardShortcuts()) return;
      if (this.observer) return;
      this.observer = new IntersectionObserver(entries => {
        check(entries.length === 1, 'Expected one observer entry.');
        const isVisible = entries[0].isIntersecting;
        if (isVisible) {
          this.enableBindings();
        } else {
          this.disableBindings();
        }
      });
      this.observer.observe(this.observerTarget);
    }

    private destroyVisibilityObserver() {
      if (this.observer) this.observer.unobserve(this.observerTarget);
    }

    /**
     * Enables all the shortcuts returned by keyboardShortcuts().
     * This is a private method being called when the element becomes
     * connected or visible.
     */
    private enableBindings() {
      if (!this.hasKeyboardShortcuts()) return;
      if (this.bindingsEnabled) return;
      this.bindingsEnabled = true;

      const shortcuts = new Map<string, string>(
        Object.entries(this.keyboardShortcuts())
      );
      this.shortcuts.attachHost(this, shortcuts);

      for (const [key, value] of shortcuts.entries()) {
        this._addOwnKeyBindings(key as Shortcut, value);
      }

      // each component that uses this behaviour must be aware if go key is
      // pressed or not, since it needs to check it as a modifier
      this.addOwnKeyBinding('g:keydown', '_handleGoKeyDown');
      this.addOwnKeyBinding('g:keyup', '_handleGoKeyUp');

      // If any of the shortcuts utilized GO_KEY, then they are handled
      // directly by this behavior.
      if (this._shortcut_go_table.size > 0) {
        this._shortcut_go_table.forEach((_, key) => {
          this.addOwnKeyBinding(key, '_handleGoAction');
        });
      }

      this.addOwnKeyBinding('v:keydown', '_handleVKeyDown');
      this.addOwnKeyBinding('v:keyup', '_handleVKeyUp');
      if (this._shortcut_v_table.size > 0) {
        this._shortcut_v_table.forEach((_, key) => {
          this.addOwnKeyBinding(key, '_handleVAction');
        });
      }
    }

    /**
     * Disables all the shortcuts returned by keyboardShortcuts().
     * This is a private method being called when the element becomes
     * disconnected or invisible.
     */
    private disableBindings() {
      if (!this.bindingsEnabled) return;
      this.bindingsEnabled = false;
      if (this.shortcuts.detachHost(this)) {
        this.removeOwnKeyBindings();
      }
    }

    private hasKeyboardShortcuts() {
      return Object.entries(this.keyboardShortcuts()).length > 0;
    }

    keyboardShortcuts() {
      return {};
    }

    addKeyboardShortcutDirectoryListener(listener: ShortcutListener) {
      this.shortcuts.addListener(listener);
    }

    removeKeyboardShortcutDirectoryListener(listener: ShortcutListener) {
      this.shortcuts.removeListener(listener);
    }

    _handleVKeyDown(e: CustomKeyboardEvent) {
      if (this.shouldSuppressKeyboardShortcut(e)) return;
      this._shortcut_v_key_last_pressed = Date.now();
    }

    _handleVKeyUp() {
      setTimeout(() => {
        this._shortcut_v_key_last_pressed = null;
      }, V_KEY_TIMEOUT_MS);
    }

    private inVKeyMode() {
      return !!(
        this._shortcut_v_key_last_pressed &&
        Date.now() - this._shortcut_v_key_last_pressed <= V_KEY_TIMEOUT_MS
      );
    }

    _handleVAction(e: CustomKeyboardEvent) {
      if (
        !this.inVKeyMode() ||
        !this._shortcut_v_table.has(e.detail.key) ||
        this.shouldSuppressKeyboardShortcut(e)
      ) {
        return;
      }
      e.preventDefault();
      const handler = this._shortcut_v_table.get(e.detail.key);
      if (handler) {
        // TODO(TS): should fix this
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (this as any)[handler](e);
      }
    }

    _handleGoKeyDown(e: CustomKeyboardEvent) {
      if (this.shouldSuppressKeyboardShortcut(e)) return;
      this._shortcut_go_key_last_pressed = Date.now();
    }

    _handleGoKeyUp() {
      // Set go_key_last_pressed to null `GO_KEY_TIMEOUT_MS` after keyup event
      // so that users can trigger `g + i` by pressing g and i quickly.
      setTimeout(() => {
        this._shortcut_go_key_last_pressed = null;
      }, GO_KEY_TIMEOUT_MS);
    }

    _inGoKeyMode() {
      return !!(
        this._shortcut_go_key_last_pressed &&
        Date.now() - this._shortcut_go_key_last_pressed <= GO_KEY_TIMEOUT_MS
      );
    }

    _handleGoAction(e: CustomKeyboardEvent) {
      if (
        !this._inGoKeyMode() ||
        !this._shortcut_go_table.has(e.detail.key) ||
        this.shouldSuppressKeyboardShortcut(e)
      ) {
        return;
      }
      e.preventDefault();
      const handler = this._shortcut_go_table.get(e.detail.key);
      if (handler) {
        // TODO(TS): should fix this
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (this as any)[handler](e);
      }
    }
  }

  return Mixin as T &
    Constructor<
      KeyboardShortcutMixinInterface & KeyboardShortcutMixinInterfaceTesting
    >;
};

// The following doesn't work (IronA11yKeysBehavior crashes):
// const KeyboardShortcutMixin = superClass => {
//    class Mixin extends mixinBehaviors([IronA11yKeysBehavior], superClass) {
//    ...
//    }
//    return Mixin;
// }
// This is a workaround
export const KeyboardShortcutMixin = <T extends Constructor<PolymerElement>>(
  superClass: T
): T &
  Constructor<
    KeyboardShortcutMixinInterface & KeyboardShortcutMixinInterfaceTesting
  > =>
  InternalKeyboardShortcutMixin(
    // TODO(TS): mixinBehaviors in some lib is returning: `new () => T` instead
    // which will fail the type check due to missing IronA11yKeysBehavior interface
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    mixinBehaviors([IronA11yKeysBehavior], superClass) as any
  );

/** The interface corresponding to KeyboardShortcutMixin */
export interface KeyboardShortcutMixinInterface {
  keyboardShortcuts(): {[key: string]: string | null};
  bindShortcut(shortcut: Shortcut, ...bindings: string[]): void;
  shouldSuppressKeyboardShortcut(event: CustomKeyboardEvent): boolean;
  modifierPressed(event: CustomKeyboardEvent): boolean;
  addKeyboardShortcutDirectoryListener(listener: ShortcutListener): void;
  removeKeyboardShortcutDirectoryListener(listener: ShortcutListener): void;
}

export interface KeyboardShortcutMixinInterfaceTesting {
  _shortcut_go_key_last_pressed: number | null;
  _shortcut_v_key_last_pressed: number | null;
  _shortcut_go_table: Map<string, string>;
  _shortcut_v_table: Map<string, string>;
  _handleGoAction: (e: CustomKeyboardEvent) => void;
}
