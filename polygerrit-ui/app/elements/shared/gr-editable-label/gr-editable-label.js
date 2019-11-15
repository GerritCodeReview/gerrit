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

  const AWAIT_MAX_ITERS = 10;
  const AWAIT_STEP = 5;

  /**
    * @appliesMixin Gerrit.FireMixin
    * @appliesMixin Gerrit.KeyboardShortcutMixin
    */
  class GrEditableLabel extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
    Gerrit.KeyboardShortcutBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-editable-label'; }
    /**
     * Fired when the value is changed.
     *
     * @event changed
     */

    static get properties() {
      return {
        labelText: String,
        editing: {
          type: Boolean,
          value: false,
        },
        value: {
          type: String,
          notify: true,
          value: '',
          observer: '_updateTitle',
        },
        placeholder: {
          type: String,
          value: '',
        },
        readOnly: {
          type: Boolean,
          value: false,
        },
        uppercase: {
          type: Boolean,
          reflectToAttribute: true,
          value: false,
        },
        maxLength: Number,
        _inputText: String,
        // This is used to push the iron-input element up on the page, so
        // the input is placed in approximately the same position as the
        // trigger.
        _verticalOffset: {
          type: Number,
          readOnly: true,
          value: -30,
        },
      };
    }

    ready() {
      super.ready();
      this._ensureAttribute('tabindex', '0');
    }

    get keyBindings() {
      return {
        enter: '_handleEnter',
        esc: '_handleEsc',
      };
    }

    _usePlaceholder(value, placeholder) {
      return (!value || !value.length) && placeholder;
    }

    _computeLabel(value, placeholder) {
      if (this._usePlaceholder(value, placeholder)) {
        return placeholder;
      }
      return value;
    }

    _showDropdown() {
      if (this.readOnly || this.editing) { return; }
      return this._open().then(() => {
        this._nativeInput.focus();
        if (!this.$.input.value) { return; }
        this._nativeInput.setSelectionRange(0, this.$.input.value.length);
      });
    }

    open() {
      return this._open().then(() => {
        this._nativeInput.focus();
      });
    }

    _open(...args) {
      this.$.dropdown.open();
      this._inputText = this.value;
      this.editing = true;

      return new Promise(resolve => {
        Polymer.IronOverlayBehaviorImpl.open.apply(this.$.dropdown, args);
        this._awaitOpen(resolve);
      });
    }

    /**
     * NOTE: (wyatta) Slightly hacky way to listen to the overlay actually
     * opening. Eventually replace with a direct way to listen to the overlay.
     */
    _awaitOpen(fn) {
      let iters = 0;
      const step = () => {
        this.async(() => {
          if (this.$.dropdown.style.display !== 'none') {
            fn.call(this);
          } else if (iters++ < AWAIT_MAX_ITERS) {
            step.call(this);
          }
        }, AWAIT_STEP);
      };
      step.call(this);
    }

    _id() {
      return this.getAttribute('id') || 'global';
    }

    _save() {
      if (!this.editing) { return; }
      this.$.dropdown.close();
      this.value = this._inputText;
      this.editing = false;
      this.fire('changed', this.value);
    }

    _cancel() {
      if (!this.editing) { return; }
      this.$.dropdown.close();
      this.editing = false;
      this._inputText = this.value;
    }

    get _nativeInput() {
      // In Polymer 2, the namespace of nativeInput
      // changed from input to nativeInput
      return this.$.input.$.nativeInput || this.$.input.$.input;
    }

    _handleEnter(e) {
      e = this.getKeyboardEvent(e);
      const target = Polymer.dom(e).rootTarget;
      if (target === this._nativeInput) {
        e.preventDefault();
        this._save();
      }
    }

    _handleEsc(e) {
      e = this.getKeyboardEvent(e);
      const target = Polymer.dom(e).rootTarget;
      if (target === this._nativeInput) {
        e.preventDefault();
        this._cancel();
      }
    }

    _computeLabelClass(readOnly, value, placeholder) {
      const classes = [];
      if (!readOnly) { classes.push('editable'); }
      if (this._usePlaceholder(value, placeholder)) {
        classes.push('placeholder');
      }
      return classes.join(' ');
    }

    _updateTitle(value) {
      this.setAttribute('title', this._computeLabel(value, this.placeholder));
    }
  }

  customElements.define(GrEditableLabel.is, GrEditableLabel);
})();
