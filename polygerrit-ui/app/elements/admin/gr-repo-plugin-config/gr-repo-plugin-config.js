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

  /**
   * @appliesMixin Gerrit.RepoPluginConfigMixin
   */
  class GrRepoPluginConfig extends Polymer.mixinBehaviors( [
    Gerrit.RepoPluginConfig,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-repo-plugin-config'; }
    /**
     * Fired when the plugin config changes.
     *
     * @event plugin-config-changed
     */

    static get properties() {
      return {
      /** @type {?} */
        pluginData: Object,
        /** @type {Array} */
        _pluginConfigOptions: {
          type: Array,
          computed: '_computePluginConfigOptions(pluginData.*)',
        },
      };
    }

    _computePluginConfigOptions(dataRecord) {
      if (!dataRecord || !dataRecord.base || !dataRecord.base.config) {
        return [];
      }
      const {config} = dataRecord.base;
      return Object.keys(config).map(_key => ({_key, info: config[_key]}));
    }

    _isArray(type) {
      return type === this.ENTRY_TYPES.ARRAY;
    }

    _isBoolean(type) {
      return type === this.ENTRY_TYPES.BOOLEAN;
    }

    _isList(type) {
      return type === this.ENTRY_TYPES.LIST;
    }

    _isString(type) {
      // Treat numbers like strings for simplicity.
      return type === this.ENTRY_TYPES.STRING ||
          type === this.ENTRY_TYPES.INT ||
          type === this.ENTRY_TYPES.LONG;
    }

    _computeDisabled(editable) {
      return editable === 'false';
    }

    _computeChecked(value) {
      return JSON.parse(value);
    }

    _handleStringChange(e) {
      const el = Polymer.dom(e).localTarget;
      const _key = el.getAttribute('data-option-key');
      const configChangeInfo =
          this._buildConfigChangeInfo(el.value, _key);
      this._handleChange(configChangeInfo);
    }

    _handleListChange(e) {
      const el = Polymer.dom(e).localTarget;
      const _key = el.getAttribute('data-option-key');
      const configChangeInfo =
          this._buildConfigChangeInfo(el.value, _key);
      this._handleChange(configChangeInfo);
    }

    _handleBooleanChange(e) {
      const el = Polymer.dom(e).localTarget;
      const _key = el.getAttribute('data-option-key');
      const configChangeInfo =
          this._buildConfigChangeInfo(JSON.stringify(el.checked), _key);
      this._handleChange(configChangeInfo);
    }

    _buildConfigChangeInfo(value, _key) {
      const info = this.pluginData.config[_key];
      info.value = value;
      return {
        _key,
        info,
        notifyPath: `${_key}.value`,
      };
    }

    _handleArrayChange({detail}) {
      this._handleChange(detail);
    }

    _handleChange({_key, info, notifyPath}) {
      const {name, config} = this.pluginData;

      /** @type {Object} */
      const detail = {
        name,
        config: Object.assign(config, {[_key]: info}, {}),
        notifyPath: `${name}.${notifyPath}`,
      };

      this.dispatchEvent(new CustomEvent(
          this.PLUGIN_CONFIG_CHANGED, {detail, bubbles: true, composed: true}));
    }
  }

  customElements.define(GrRepoPluginConfig.is, GrRepoPluginConfig);
})();
