/**
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/keyboard-shortcut-behavior/keyboard-shortcut-behavior.js';
import '../../../../@polymer/iron-dropdown/iron-dropdown.js';
import '../../../../@polymer/paper-input/paper-input.js';
import '../../../styles/shared-styles.js';

const AWAIT_MAX_ITERS = 10;
const AWAIT_STEP = 5;

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        align-items: center;
        display: inline-flex;
      }
      :host([uppercase]) label {
        text-transform: uppercase;
      }
      input,
      label {
        width: 100%;
      }
      input {
        font: inherit;
      }
      label {
        color: var(--deemphasized-text-color);
        display: inline-block;
        font-family: var(--font-family-bold);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        @apply --label-style;
      }
      label.editable {
        color: var(--link-color);
        cursor: pointer;
      }
      #dropdown {
        box-shadow: rgba(0, 0, 0, 0.3) 0 1px 3px;
      }
      .inputContainer {
        background-color: var(--dialog-background-color);
        padding: .8em;
        @apply --input-style;
      }
      .buttons {
        display: flex;
        justify-content: flex-end;
        padding-top: 1.2em;
        width: 100%;
      }
      .buttons gr-button {
        margin-left: .5em;
      }
      paper-input {
        --paper-input-container: {
          padding: 0;
          min-width: 15em;
        }
        --paper-input-container-input: {
          font-size: var(--font-size-normal);
        }
        --paper-input-container-focus-color: var(--link-color);
      }
    </style>
      <label class\$="[[_computeLabelClass(readOnly, value, placeholder)]]" title\$="[[_computeLabel(value, placeholder)]]" on-tap="_showDropdown">[[_computeLabel(value, placeholder)]]</label>
      <iron-dropdown id="dropdown" vertical-align="auto" horizontal-align="auto" vertical-offset="[[_verticalOffset]]" allow-outside-scroll="true" on-iron-overlay-canceled="_cancel">
        <div class="dropdown-content" slot="dropdown-content">
          <div class="inputContainer">
            <paper-input id="input" label="[[labelText]]" maxlength="[[maxLength]]" value="{{_inputText}}"></paper-input>
            <div class="buttons">
              <gr-button link="" id="cancelBtn" on-tap="_cancel">cancel</gr-button>
              <gr-button link="" id="saveBtn" on-tap="_save">save</gr-button>
            </div>
          </div>
        </div>
    </iron-dropdown>
`,

  is: 'gr-editable-label',

  /**
   * Fired when the value is changed.
   *
   * @event changed
   */

  properties: {
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
  },

  behaviors: [
    Gerrit.KeyboardShortcutBehavior,
  ],

  keyBindings: {
    enter: '_handleEnter',
    esc: '_handleEsc',
  },

  hostAttributes: {
    tabindex: '0',
  },

  _usePlaceholder(value, placeholder) {
    return (!value || !value.length) && placeholder;
  },

  _computeLabel(value, placeholder) {
    if (this._usePlaceholder(value, placeholder)) {
      return placeholder;
    }
    return value;
  },

  _showDropdown() {
    if (this.readOnly || this.editing) { return; }
    return this._open().then(() => {
      this.$.input.$.input.focus();
      if (!this.$.input.value) { return; }
      this.$.input.$.input.setSelectionRange(0, this.$.input.value.length);
    });
  },

  _open(...args) {
    this.$.dropdown.open();
    this._inputText = this.value;
    this.editing = true;

    return new Promise(resolve => {
      Polymer.IronOverlayBehaviorImpl.open.apply(this.$.dropdown, args);
      this._awaitOpen(resolve);
    });
  },

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
  },

  _id() {
    return this.getAttribute('id') || 'global';
  },

  _save() {
    if (!this.editing) { return; }
    this.$.dropdown.close();
    this.value = this._inputText;
    this.editing = false;
    this.fire('changed', this.value);
  },

  _cancel() {
    if (!this.editing) { return; }
    this.$.dropdown.close();
    this.editing = false;
    this._inputText = this.value;
  },

  /**
   * @suppress {checkTypes}
   * Closure doesn't think 'e' is an Event.
   * TODO(beckysiegel) figure out why.
   */
  _handleEnter(e) {
    e = this.getKeyboardEvent(e);
    const target = Polymer.dom(e).rootTarget;
    if (target === this.$.input.$.input) {
      e.preventDefault();
      this._save();
    }
  },

  /**
   * @suppress {checkTypes}
   * Closure doesn't think 'e' is an Event.
   * TODO(beckysiegel) figure out why.
   */
  _handleEsc(e) {
    e = this.getKeyboardEvent(e);
    const target = Polymer.dom(e).rootTarget;
    if (target === this.$.input.$.input) {
      e.preventDefault();
      this._cancel();
    }
  },

  _computeLabelClass(readOnly, value, placeholder) {
    const classes = [];
    if (!readOnly) { classes.push('editable'); }
    if (this._usePlaceholder(value, placeholder)) {
      classes.push('placeholder');
    }
    return classes.join(' ');
  },

  _updateTitle(value) {
    this.setAttribute('title', this._computeLabel(value, this.placeholder));
  }
});
