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
import {property} from '@polymer/decorators';
import {PolymerElement} from '@polymer/polymer';
import {check, Constructor} from '../../utils/common-util';
import {appContext} from '../../services/app-context';
import {
  Shortcut,
  ShortcutSection,
  SPECIAL_SHORTCUT,
} from '../../services/shortcuts/shortcuts-config';
import {
  SectionView,
  ShortcutListener,
} from '../../services/shortcuts/shortcuts-service';

export {
  Shortcut,
  ShortcutSection,
  SPECIAL_SHORTCUT,
  ShortcutListener,
  SectionView,
};

export const KeyboardShortcutMixin = <T extends Constructor<PolymerElement>>(
  superClass: T
) => {
  /**
   * @polymer
   * @mixinClass
   */
  class Mixin extends superClass {
    // This enables `Shortcut` to be used in the html template.
    Shortcut = Shortcut;

    // This enables `ShortcutSection` to be used in the html template.
    ShortcutSection = ShortcutSection;

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

    override connectedCallback() {
      super.connectedCallback();
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

      this.shortcuts.attachHost(this, this.keyboardShortcuts());
    }

    /**
     * Disables all the shortcuts returned by keyboardShortcuts().
     * This is a private method being called when the element becomes
     * disconnected or invisible.
     */
    private disableBindings() {
      if (!this.bindingsEnabled) return;
      this.bindingsEnabled = false;
      this.shortcuts.detachHost(this);
    }

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

      modifierPressed(event: CustomKeyboardEvent) {
        /* We are checking for g/v as modifiers pressed. There are cases such as
         * pressing v and then /, where we want the handler for / to be triggered.
         * TODO(dhruvsri): find a way to support that keyboard combination
         */
        const e = getKeyboardEvent(event);
        return (
          e.altKey ||
          e.ctrlKey ||
          e.metaKey ||
          e.shiftKey ||
          !!this._inGoKeyMode() ||
          !!this.inVKeyMode()
        );
      }

      isModifierPressed(e: CustomKeyboardEvent, modifier: Modifier) {
        return getKeyboardEvent(e)[modifier];
      }

      shouldSuppressKeyboardShortcut(event: CustomKeyboardEvent) {
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
          // and target is an anchor or button.
          (e.keyCode === 13 && (tagName === 'A' || tagName === 'BUTTON'))
        ) {
          return true;
        }
        const path = e.composedPath();
        for (let i = 0; path && i < path.length; i++) {
          // TODO(TS): narrow this down to Element from EventTarget first
          if ((path[i] as Element).tagName === 'GR-OVERLAY') {
            return true;
          }
        }
        const detail: ShortcutTriggeredEventDetail = {
          event: e,
          goKey: this._inGoKeyMode(),
          vKey: this.inVKeyMode(),
        };
        this.dispatchEvent(
          new CustomEvent('shortcut-triggered', {
            detail,
            composed: true,
            bubbles: true,
          })
        );
        return false;
      }

      // Alias for getKeyboardEvent.
      getKeyboardEvent(e: CustomKeyboardEvent) {
        return getKeyboardEvent(e);
      }

      // TODO(TS): maybe remove, no reference in the code base
      getRootTarget(e: CustomKeyboardEvent) {
        // TODO(TS): worth checking if we can limit this to EventApi only
        // dom currently returns DomNativeApi|EventApi
        return (dom(getKeyboardEvent(e)) as EventApi).rootTarget;
      }

      bindShortcut(shortcut: Shortcut, ...bindings: string[]) {
        shortcutManager.bindShortcut(shortcut, ...bindings);
      }

      createTitle(shortcutName: Shortcut, section: ShortcutSection) {
        const desc = shortcutManager.getDescription(section, shortcutName);
        const shortcut = shortcutManager.getShortcut(shortcutName);
        return desc && shortcut ? `${desc} (shortcut: ${shortcut})` : '';
      }

      _throttleWrap(fn: (e: Event) => void) {
        let lastCall: number | undefined;
        return (e: Event) => {
          if (
            lastCall !== undefined &&
            Date.now() - lastCall < THROTTLE_INTERVAL_MS
          ) {
            return;
          }
          lastCall = Date.now();
          fn(e);
        };
      }

      _addOwnKeyBindings(shortcut: Shortcut, handler: string) {
        const bindings = shortcutManager.getBindingsForShortcut(shortcut);
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

      /** @override */
      connectedCallback() {
        super.connectedCallback();
        const shortcuts = shortcutManager.attachHost(this);
        if (!shortcuts) {
          return;
        }

        for (const key of Object.keys(shortcuts)) {
          // TODO(TS): not needed if convert shortcuts to Map
          this._addOwnKeyBindings(key as Shortcut, shortcuts[key]);
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

      /** @override */
      disconnectedCallback() {
        if (shortcutManager.detachHost(this)) {
          this.removeOwnKeyBindings();
        }
        super.disconnectedCallback();
      }

      keyboardShortcuts() {
        return {};
      }

      addKeyboardShortcutDirectoryListener(listener: ShortcutListener) {
        shortcutManager.addListener(listener);
      }

      removeKeyboardShortcutDirectoryListener(listener: ShortcutListener) {
        shortcutManager.removeListener(listener);
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

    private hasKeyboardShortcuts() {
      return this.keyboardShortcuts().length > 0;
    }

    keyboardShortcuts(): ShortcutListener[] {
      return [];
    }
  }

  return Mixin as T & Constructor<KeyboardShortcutMixinInterface>;
};

/** The interface corresponding to KeyboardShortcutMixin */
export interface KeyboardShortcutMixinInterface {
  keyboardShortcuts(): ShortcutListener[];
}
