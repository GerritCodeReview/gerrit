/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
    is: 'gr-dropdown-list',
    _legacyUndefinedCheck: true,

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
    _handleDropdownClick(e) {
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
    },
  });
})();
