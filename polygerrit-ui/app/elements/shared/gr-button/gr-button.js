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
(function() {
  'use strict';

  /**
   * @appliesMixin Gerrit.KeyboardShortcutMixin
   * @appliesMixin Gerrit.TooltipMixin
   * @extends Polymer.Element
   */
  class GrButton extends Polymer.mixinBehaviors( [
    Gerrit.KeyboardShortcutBehavior,
    Gerrit.TooltipBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-button'; }

    static get properties() {
      return {
        tooltip: String,
        downArrow: {
          type: Boolean,
          reflectToAttribute: true,
        },
        link: {
          type: Boolean,
          value: false,
          reflectToAttribute: true,
        },
        loading: {
          type: Boolean,
          value: false,
          reflectToAttribute: true,
        },
        disabled: {
          type: Boolean,
          observer: '_disabledChanged',
          reflectToAttribute: true,
        },
        noUppercase: {
          type: Boolean,
          value: false,
        },
        _enabledTabindex: {
          type: String,
          value: '0',
        },
      };
    }

    static get observers() {
      return [
        '_computeDisabled(disabled, loading)',
      ];
    }

    /** @override */
    created() {
      super.created();
      this.addEventListener('click',
          e => this._handleAction(e));
      this.addEventListener('keydown',
          e => this._handleKeydown(e));
    }

    /** @override */
    ready() {
      super.ready();
      this._ensureAttribute('role', 'button');
      this._ensureAttribute('tabindex', '0');
    }

    _handleAction(e) {
      if (this.disabled) {
        e.preventDefault();
        e.stopImmediatePropagation();
      }
    }

    _disabledChanged(disabled) {
      if (disabled) {
        this._enabledTabindex = this.getAttribute('tabindex') || '0';
      }
      this.setAttribute('tabindex', disabled ? '-1' : this._enabledTabindex);
      this.updateStyles();
    }

    _computeDisabled(disabled, loading) {
      return disabled || loading;
    }

    _handleKeydown(e) {
      if (this.modifierPressed(e)) { return; }
      e = this.getKeyboardEvent(e);
      // Handle `enter`, `space`.
      if (e.keyCode === 13 || e.keyCode === 32) {
        e.preventDefault();
        e.stopPropagation();
        this.click();
      }
    }
  }

  customElements.define(GrButton.is, GrButton);
})();
