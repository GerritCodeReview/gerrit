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

import '../gr-button/gr-button.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        color: var(--primary-text-color);
        display: block;
        max-height: 90vh;
      }
      .container {
        display: flex;
        flex-direction: column;
        max-height: 90vh;
      }
      header {
        border-bottom: 1px solid var(--border-color);
        flex-shrink: 0;
        font-family: var(--font-family-bold);
      }
      main {
        display: flex;
        flex-shrink: 1;
        width: 100%;
      }
      header,
      main,
      footer {
        padding: .5em 1.5em;
      }
      gr-button {
        margin-left: 1em;
      }
      footer {
        display: flex;
        flex-shrink: 0;
        justify-content: flex-end;
      }
      .hidden {
        display: none;
      }
    </style>
    <div class="container" on-keydown="_handleKeydown">
      <header><slot name="header"></slot></header>
      <main><slot name="main"></slot></main>
      <footer>
        <gr-button id="cancel" class\$="[[_computeCancelClass(cancelLabel)]]" link="" on-tap="_handleCancelTap">
          [[cancelLabel]]
        </gr-button>
        <gr-button id="confirm" link="" primary="" on-tap="_handleConfirm" disabled="[[disabled]]">
          [[confirmLabel]]
        </gr-button>
      </footer>
    </div>
`,

  is: 'gr-dialog',

  /**
   * Fired when the confirm button is pressed.
   *
   * @event confirm
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  properties: {
    confirmLabel: {
      type: String,
      value: 'Confirm',
    },
    // Supplying an empty cancel label will hide the button completely.
    cancelLabel: {
      type: String,
      value: 'Cancel',
    },
    disabled: {
      type: Boolean,
      value: false,
    },
    confirmOnEnter: {
      type: Boolean,
      value: false,
    },
  },

  hostAttributes: {
    role: 'dialog',
  },

  _handleConfirm(e) {
    if (this.disabled) { return; }

    e.preventDefault();
    this.fire('confirm', null, {bubbles: false});
  },

  _handleCancelTap(e) {
    e.preventDefault();
    this.fire('cancel', null, {bubbles: false});
  },

  _handleKeydown(e) {
    if (this.confirmOnEnter && e.keyCode === 13) { this._handleConfirm(e); }
  },

  resetFocus() {
    this.$.confirm.focus();
  },

  _computeCancelClass(cancelLabel) {
    return cancelLabel.length ? '' : 'hidden';
  }
});
