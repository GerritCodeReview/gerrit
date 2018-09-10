/**
@license
Copyright (C) 2017 The Android Open Source Project

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

import '../../../../@polymer/iron-dropdown/iron-dropdown.js';
import '../../../../@polymer/paper-item/paper-item.js';
import '../../../../@polymer/paper-listbox/paper-listbox.js';
import '../../../styles/shared-styles.js';
import '../gr-button/gr-button.js';
import '../gr-date-formatter/gr-date-formatter.js';
import '../gr-select/gr-select.js';

/**
 * fired when the selected value of the dropdown changes
 *
 * @event {change}
 */

const Defs = {};

/**
 * Requred values are text and value. mobileText and triggerText will
 * fall back to text if not provided.
 *
 * If bottomText is not provided, nothing will display on the second
 * line.
 *
 * If date is not provided, nothing will be displayed in its place.
 *
 * @typedef {{
 *    text: string,
 *    value: (string|number),
 *    bottomText: (string|undefined),
 *    triggerText: (string|undefined),
 *    mobileText: (string|undefined),
 *    date: (!Date|undefined),
 * }}
 */
Defs.item;

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: inline-block;
      }
      #triggerText {
        -moz-user-select: text;
        -ms-user-select: text;
        -webkit-user-select: text;
        user-select: text;
      }
      .dropdown-trigger {
        cursor: pointer;
        padding: 0;
      }
      .dropdown-content {
        background-color: var(--dropdown-background-color);
        box-shadow: 0 1px 5px rgba(0, 0, 0, .3);
        max-height: 70vh;
        margin-top: 2em;
        min-width: 266px;
        @apply --dropdown-content-style
      }
      paper-listbox {
        --paper-listbox: {
          padding: 0;
        }
      }
      paper-item {
        cursor: pointer;
        flex-direction: column;
        font-size: var(--font-size-normal);
        --paper-item: {
          min-height: 0;
          padding: 10px 16px;
        }
        --paper-item-focused-before: {
          background-color: var(--selection-background-color);
        }
        --paper-item-focused: {
          background-color: var(--selection-background-color);
        }
      }
      paper-item:hover {
        background-color: var(--hover-background-color);
      }
      paper-item:not(:last-of-type) {
        border-bottom: 1px solid var(--border-color);
      }
      .bottomContent {
        color: var(--deemphasized-text-color);
        /*
         * Should be 16px when the base font size is 13px (browser default of
         * 16px.
         */
        line-height: 1.37rem;
      }
      .bottomContent,
      .topContent {
        display: flex;
        /*
         * Should be 16px when the base font size is 13px (browser default of
         * 16px.
         */
        line-height: 1.37rem;
        justify-content: space-between;
        flex-direction: row;
        width: 100%;
      }
       gr-button {
        --gr-button: {
          @apply --trigger-style;
        }
      }
      gr-date-formatter {
        color: var(--deemphasized-text-color);
        margin-left: 2em;
        white-space: nowrap;
      }
      gr-select {
        display: none;
      }
      /* Because the iron dropdown 'area' includes the trigger, and the entire
       width of the dropdown, we want to treat tapping the area above the
       dropdown content as if it is tapping whatever content is underneath it.
       The next two styles allow this to happen. */
      iron-dropdown {
        max-width: none;
        pointer-events: none;
      }
      paper-listbox {
        pointer-events: auto;
      }
      @media only screen and (max-width: 50em) {
        gr-select {
          display: inline;
          @apply --gr-select-style;
        }
        gr-button,
        iron-dropdown {
          display: none;
        }
        select {
          @apply --native-select-style;
        }
      }
    </style>
    <gr-button down-arrow="" link="" id="trigger" class="dropdown-trigger" on-tap="_showDropdownTapHandler" slot="dropdown-trigger">
      <span id="triggerText">[[text]]</span>
    </gr-button>
    <iron-dropdown id="dropdown" vertical-align="top" allow-outside-scroll="true" on-tap="_handleDropdownTap">
      <paper-listbox class="dropdown-content" slot="dropdown-content" attr-for-selected="value" selected="{{value}}" on-tap="_handleDropdownTap">
        <template is="dom-repeat" items="[[items]]" initial-count="[[initialCount]]">
          <paper-item disabled="[[item.disabled]]" value="[[item.value]]">
            <div class="topContent">
              <div>[[item.text]]</div>
              <template is="dom-if" if="[[item.date]]">
                  <gr-date-formatter date-str="[[item.date]]"></gr-date-formatter>
              </template>
            </div>
            <template is="dom-if" if="[[item.bottomText]]">
              <div class="bottomContent">
                <div>[[item.bottomText]]</div>
              </div>
            </template>
          </paper-item>
        </template>
      </paper-listbox>
    </iron-dropdown>
    <gr-select bind-value="{{value}}">
      <select>
        <template is="dom-repeat" items="[[items]]">
          <option disabled\$="[[item.disabled]]" value="[[item.value]]">
            [[_computeMobileText(item)]]
          </option>
        </template>
      </select>
    </gr-select>
`,

  is: 'gr-dropdown-list',

  /**
   * Fired when the selected value changes
   *
   * @event value-change
   *
   * @property {string|number} value
   */

  properties: {
    initialCount: Number,
    /** @type {!Array<!Defs.item>} */
    items: Object,
    text: String,
    value: {
      type: String,
      notify: true,
    },
  },

  observers: [
    '_handleValueChange(value, items)',
  ],

  /**
   * Handle a click on the iron-dropdown element.
   * @param {!Event} e
   */
  _handleDropdownTap(e) {
    // async is needed so that that the click event is fired before the
    // dropdown closes (This was a bug for touch devices).
    this.async(() => {
      this.$.dropdown.close();
    }, 1);
  },

  /**
   * Handle a click on the button to open the dropdown.
   * @param {!Event} e
   */
  _showDropdownTapHandler(e) {
    this._open();
  },

  /**
   * Open the dropdown.
   */
  _open() {
    this.$.dropdown.open();
  },

  _computeMobileText(item) {
    return item.mobileText ? item.mobileText : item.text;
  },

  _handleValueChange(value, items) {
    if (!value) { return; }
    const selectedObj = items.find(item => {
      return item.value + '' === value + '';
    });
    if (!selectedObj) { return; }
    this.text = selectedObj.triggerText? selectedObj.triggerText :
        selectedObj.text;
    this.dispatchEvent(new CustomEvent('value-change', {
      detail: {value},
      bubbles: false,
    }));
  }
});
