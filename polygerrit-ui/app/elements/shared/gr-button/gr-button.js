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

import '../../../behaviors/gr-tooltip-behavior/gr-tooltip-behavior.js';
import '../../../behaviors/keyboard-shortcut-behavior/keyboard-shortcut-behavior.js';
import '../../../../@polymer/paper-button/paper-button.js';
import '../../../styles/shared-styles.js';
const $_documentContainer = document.createElement('template');

$_documentContainer.innerHTML = `<dom-module id="gr-button">
  <template strip-whitespace="">
    <style include="shared-styles">
      /* general styles for all buttons */
      :host {
        --background-color: var(--button-background-color, var(--default-button-background-color));
        --text-color: var(--default-button-text-color);
        display: inline-block;
        position: relative;
      }
      :host([hidden]) {
        display: none;
      }
      :host([no-uppercase]) paper-button {
        text-transform: none;
      }
      paper-button {
        /* paper-button sets this to anti-aliased, which appears different than
        roboto-medium elsewhere. */
        -webkit-font-smoothing: initial;
        align-items: center;
        background-color: var(--background-color);
        color: var(--text-color);
        display: flex;
        font-family: inherit;
        justify-content: center;
        margin: var(--margin, 0);
        min-width: var(--border, 0);
        padding: var(--padding, 4px 8px);
        @apply --gr-button;
      }
      paper-button:hover {
        background: linear-gradient(
          rgba(0, 0, 0, .12),
          rgba(0, 0, 0, .12)
        ), var(--background-color);
      }

      :host([primary]) {
        --background-color: var(--primary-button-background-color);
        --text-color: var(--primary-button-text-color);
      }
      :host([link][primary]) {
        --text-color: var(--primary-button-background-color);
      }
      :host([secondary]) {
        --background-color: var(--secondary-button-text-color);
        --text-color: var(--secondary-button-background-color);
      }
      :host([link][secondary]) {
        --text-color: var(--secondary-button-text-color);
      }

      /* Keep below color definition for primary so that this takes precedence
        when disabled. */
      :host([disabled]) {
        --background-color: var(--table-subheader-background-color);
        --text-color: var(--deemphasized-text-color);
        cursor: default;
      }

      /* Styles for link buttons specifically */
      :host([link]) {
        --background-color: transparent;
        --margin: 0;
        --padding: 5px 4px;
      }
      :host([disabled][link]) {
        --background-color: transparent;
      }

      /* Styles for the optional down arrow */
      :host:not([down-arrow]) .downArrow {display: none; }
      :host([down-arrow]) .downArrow {
        border-top: .36em solid #ccc;
        border-left: .36em solid transparent;
        border-right: .36em solid transparent;
        margin-bottom: .05em;
        margin-left: .5em;
        transition: border-top-color 200ms;
      }
      :host([down-arrow]) paper-button:hover .downArrow {
        border-top-color: var(--deemphasized-text-color);
      }
    </style>
    <paper-button raised="[[!link]]" disabled="[[_computeDisabled(disabled, loading)]]" tabindex="-1">
      <slot></slot>
      <i class="downArrow"></i>
    </paper-button>
  </template>
  
</dom-module>`;

document.head.appendChild($_documentContainer.content);

Polymer({
  is: 'gr-button',

  properties: {
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
  },

  listeners: {
    tap: '_handleAction',
    click: '_handleAction',
    keydown: '_handleKeydown',
  },

  observers: [
    '_computeDisabled(disabled, loading)',
  ],

  behaviors: [
    Gerrit.KeyboardShortcutBehavior,
    Gerrit.TooltipBehavior,
  ],

  hostAttributes: {
    role: 'button',
    tabindex: '0',
  },

  _handleAction(e) {
    if (this.disabled) {
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  },

  _disabledChanged(disabled) {
    if (disabled) {
      this._enabledTabindex = this.getAttribute('tabindex');
    }
    this.setAttribute('tabindex', disabled ? '-1' : this._enabledTabindex);
    this.updateStyles();
  },

  _computeDisabled(disabled, loading) {
    return disabled || loading;
  },

  _handleKeydown(e) {
    if (this.modifierPressed(e)) { return; }
    e = this.getKeyboardEvent(e);
    // Handle `enter`, `space`.
    if (e.keyCode === 13 || e.keyCode === 32) {
      e.preventDefault();
      e.stopPropagation();
      this.click();
    }
  },
});
