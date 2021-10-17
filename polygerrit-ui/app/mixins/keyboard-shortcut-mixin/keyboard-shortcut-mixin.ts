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
import {IronKeyboardEvent} from '../../types/events';
import {appContext} from '../../services/app-context';
import {
  Shortcut,
  ShortcutSection,
  SPECIAL_SHORTCUT,
} from '../../services/shortcuts/shortcuts-config';
import {
  ComboKey,
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

      // If any of the shortcuts utilized GO_KEY, then they are handled
      // directly by this behavior.
      if (this._shortcut_go_table.size > 0) {
        this._shortcut_go_table.forEach((_, key) => {
          this.addOwnKeyBinding(key, '_handleGoAction');
        });
      }

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

    _handleVAction(e: IronKeyboardEvent) {
      if (
        !this.shortcuts.isInSpecificComboKeyMode(ComboKey.V) ||
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

    _handleGoAction(e: IronKeyboardEvent) {
      if (
        !this.shortcuts.isInSpecificComboKeyMode(ComboKey.G) ||
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

  return Mixin as T & Constructor<KeyboardShortcutMixinInterface>;
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
): T & Constructor<KeyboardShortcutMixinInterface> =>
  InternalKeyboardShortcutMixin(
    // TODO(TS): mixinBehaviors in some lib is returning: `new () => T` instead
    // which will fail the type check due to missing IronA11yKeysBehavior interface
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    mixinBehaviors([IronA11yKeysBehavior], superClass) as any
  );

/** The interface corresponding to KeyboardShortcutMixin */
export interface KeyboardShortcutMixinInterface {
  keyboardShortcuts(): {[key: string]: string | null};
}
