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
import {IronA11yKeysBehavior} from '@polymer/iron-a11y-keys-behavior/iron-a11y-keys-behavior';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class';
import {property} from '@polymer/decorators';
import {PolymerElement} from '@polymer/polymer';
import {check, Constructor} from '../../utils/common-util';
import {isModifierPressed} from '../../utils/dom-util';
import {IronKeyboardEvent} from '../../types/events';
import {appContext} from '../../services/app-context';
import {
  Shortcut,
  ShortcutSection,
  SPECIAL_SHORTCUT,
} from '../../services/shortcuts/shortcuts-config';
import {
  ShortcutListener,
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

    modifierPressed(e: IronKeyboardEvent) {
      /* We are checking for g/v as modifiers pressed. There are cases such as
       * pressing v and then /, where we want the handler for / to be triggered.
       * TODO(dhruvsri): find a way to support that keyboard combination
       */
      return (
        isModifierPressed(e) || !!this._inGoKeyMode() || !!this.inVKeyMode()
      );
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

    _handleVKeyDown(e: IronKeyboardEvent) {
      if (this.shortcuts.shouldSuppress(e)) return;
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

    _handleVAction(e: IronKeyboardEvent) {
      if (
        !this.inVKeyMode() ||
        !this._shortcut_v_table.has(e.detail.key) ||
        this.shortcuts.shouldSuppress(e)
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

    _handleGoKeyDown(e: IronKeyboardEvent) {
      if (this.shortcuts.shouldSuppress(e)) return;
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

    _handleGoAction(e: IronKeyboardEvent) {
      if (
        !this._inGoKeyMode() ||
        !this._shortcut_go_table.has(e.detail.key) ||
        this.shortcuts.shouldSuppress(e)
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
  modifierPressed(event: IronKeyboardEvent): boolean;
}

export interface KeyboardShortcutMixinInterfaceTesting {
  _shortcut_go_key_last_pressed: number | null;
  _shortcut_v_key_last_pressed: number | null;
  _shortcut_go_table: Map<string, string>;
  _shortcut_v_table: Map<string, string>;
  _handleGoAction: (e: IronKeyboardEvent) => void;
}
