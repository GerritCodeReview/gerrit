/**
@license
Copyright (C) 2018 The Android Open Source Project

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
import "../../../scripts/bundled-polymer.js";

import '@polymer/iron-input/iron-input.js';
import '@polymer/paper-toggle-button/paper-toggle-button.js';
import '../../../behaviors/gr-repo-plugin-config-behavior/gr-repo-plugin-config-behavior.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/gr-subpage-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../shared/gr-select/gr-select.js';
import '../../shared/gr-tooltip-content/gr-tooltip-content.js';
import '../gr-plugin-config-array-editor/gr-plugin-config-array-editor.js';
import { dom } from '@polymer/polymer/lib/legacy/polymer.dom.js';
import { mixinBehaviors } from '@polymer/polymer/lib/legacy/class.js';
import { GestureEventListeners } from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import { LegacyElementMixin } from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import { PolymerElement } from '@polymer/polymer/polymer-element.js';
import { htmlTemplate } from './gr-repo-plugin-config_html.js';

/**
 * @appliesMixin Gerrit.RepoPluginConfigMixin
 * @extends Polymer.Element
 */
class GrRepoPluginConfig extends mixinBehaviors( [
  Gerrit.RepoPluginConfig,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

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
    return Object.keys(config)
        .map(_key => { return {_key, info: config[_key]}; });
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

  /**
   * @param {string} value - fallback to 'false' if undefined
   */
  _computeChecked(value = 'false') {
    return JSON.parse(value);
  }

  _handleStringChange(e) {
    const el = dom(e).localTarget;
    const _key = el.getAttribute('data-option-key');
    const configChangeInfo =
        this._buildConfigChangeInfo(el.value, _key);
    this._handleChange(configChangeInfo);
  }

  _handleListChange(e) {
    const el = dom(e).localTarget;
    const _key = el.getAttribute('data-option-key');
    const configChangeInfo =
        this._buildConfigChangeInfo(el.value, _key);
    this._handleChange(configChangeInfo);
  }

  _handleBooleanChange(e) {
    const el = dom(e).localTarget;
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
