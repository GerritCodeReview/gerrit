/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

  Polymer({
    is: 'gr-plugin-config-array-editor',

    /**
     * Fired when the plugin config option changes.
     *
     * @event plugin-config-option-changed
     */

    properties: {
      /** @type {?} */
      pluginOption: Object,
      /** @type {Boolean} */
      disabled: {
        type: Boolean,
        computed: '_computeDisabled(pluginOption.*)',
      },
      /** @type {?} */
      _newValue: {
        type: String,
        value: '',
      },
    },

    _computeDisabled(record) {
      return !(record && record.base && record.base.info &&
          record.base.info.editable);
    },

    _handleAddTap(e) {
      e.preventDefault();
      this._handleAdd();
    },

    _handleInputKeydown(e) {
      // Enter.
      if (e.keyCode === 13) {
        e.preventDefault();
        this._handleAdd();
      }
    },

    _handleAdd() {
      if (!this._newValue.length) { return; }
      this._dispatchChanged(
          this.pluginOption.info.values.concat([this._newValue]));
      this._newValue = '';
    },

    _handleDelete(e) {
      const value = Polymer.dom(e).localTarget.dataset.item;
      this._dispatchChanged(
          this.pluginOption.info.values.filter(str => str !== value));
    },

    _dispatchChanged(values) {
      const {_key, info} = this.pluginOption;
      const detail = {
        _key,
        info: Object.assign(info, {values}, {}),
        notifyPath: `${_key}.values`,
      };
      this.dispatchEvent(
          new CustomEvent('plugin-config-option-changed', {detail}));
    },

    _computeShowInputRow(disabled) {
      return disabled ? 'hide' : '';
    },
  });
})();
