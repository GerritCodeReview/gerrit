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
import '@polymer/iron-input/iron-input';
import '@polymer/paper-toggle-button/paper-toggle-button';
import '../../../styles/gr-form-styles';
import '../../../styles/gr-subpage-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../gr-plugin-config-array-editor/gr-plugin-config-array-editor';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-repo-plugin-config_html';
import {customElement, property} from '@polymer/decorators';
import {ConfigParameterInfoType} from '../../../constants/constants';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {
  ConfigParameterInfo,
  PluginParameterToConfigParameterInfoMap,
} from '../../../types/common';
import {PaperToggleButtonElement} from '@polymer/paper-toggle-button/paper-toggle-button';
import {IronInputElement} from '@polymer/iron-input/iron-input';
import {
  PluginConfigOptionsChangedEventDetail,
  PluginOption,
} from './gr-repo-plugin-config-types';

const PLUGIN_CONFIG_CHANGED_EVENT_NAME = 'plugin-config-changed';

export interface ConfigChangeInfo {
  _key: string; // parameterName of PluginParameterToConfigParameterInfoMap
  info: ConfigParameterInfo;
  notifyPath: string;
}

export interface PluginData {
  name: string; // parameterName of PluginParameterToConfigParameterInfoMap
  config: PluginParameterToConfigParameterInfoMap;
}

export interface PluginConfigChangeDetail {
  name: string; // parameterName of PluginParameterToConfigParameterInfoMap
  config: PluginParameterToConfigParameterInfoMap;
  notifyPath: string;
}

@customElement('gr-repo-plugin-config')
class GrRepoPluginConfig extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the plugin config changes.
   *
   * @event plugin-config-changed
   */

  @property({type: Object})
  pluginData?: PluginData;

  @property({
    type: Array,
    computed: '_computePluginConfigOptions(pluginData.*)',
  })
  _pluginConfigOptions!: PluginOption[]; // _computePluginConfigOptions never returns null

  @property({type: Boolean, reflectToAttribute: true})
  disabled = false;

  _computePluginConfigOptions(
    dataRecord: PolymerDeepPropertyChange<PluginData, PluginData>
  ): PluginOption[] {
    if (!dataRecord || !dataRecord.base || !dataRecord.base.config) {
      return [];
    }
    const config = dataRecord.base.config;
    return Object.keys(config).map(_key => {
      return {_key, info: config[_key]};
    });
  }

  _isArray(type: ConfigParameterInfoType) {
    return type === ConfigParameterInfoType.ARRAY;
  }

  _isBoolean(type: ConfigParameterInfoType) {
    return type === ConfigParameterInfoType.BOOLEAN;
  }

  _isList(type: ConfigParameterInfoType) {
    return type === ConfigParameterInfoType.LIST;
  }

  _isString(type: ConfigParameterInfoType) {
    // Treat numbers like strings for simplicity.
    return (
      type === ConfigParameterInfoType.STRING ||
      type === ConfigParameterInfoType.INT ||
      type === ConfigParameterInfoType.LONG
    );
  }

  _computeDisabled(disabled: boolean, editable: boolean) {
    return disabled || !editable;
  }

  _computeChecked(value = 'false') {
    return JSON.parse(value) as boolean;
  }

  _handleStringChange(e: Event) {
    const el = (dom(e) as EventApi).localTarget as IronInputElement;
    // In the template, the data-option-key is assigned to each editor
    const _key = el.getAttribute('data-option-key')!;
    const configChangeInfo = this._buildConfigChangeInfo(el.value, _key);
    this._handleChange(configChangeInfo);
  }

  _handleListChange(e: Event) {
    const el = (dom(e) as EventApi).localTarget as HTMLOptionElement;
    // In the template, the data-option-key is assigned to each editor
    const _key = el.getAttribute('data-option-key')!;
    const configChangeInfo = this._buildConfigChangeInfo(el.value, _key);
    this._handleChange(configChangeInfo);
  }

  _handleBooleanChange(e: Event) {
    const el = (dom(e) as EventApi).localTarget as PaperToggleButtonElement;
    // In the template, the data-option-key is assigned to each editor
    const _key = el.getAttribute('data-option-key')!;
    const configChangeInfo = this._buildConfigChangeInfo(
      JSON.stringify(el.checked),
      _key
    );
    this._handleChange(configChangeInfo);
  }

  _buildConfigChangeInfo(
    value: string | null | undefined,
    _key: string
  ): ConfigChangeInfo {
    // If pluginData is not set, editors are not created and this method
    // can't be called
    const info = this.pluginData!.config[_key];
    info.value = value !== null ? value : undefined;
    return {
      _key,
      info,
      notifyPath: `${_key}.value`,
    };
  }

  _handleArrayChange(e: CustomEvent<PluginConfigOptionsChangedEventDetail>) {
    this._handleChange(e.detail);
  }

  _handleChange({_key, info, notifyPath}: ConfigChangeInfo) {
    // If pluginData is not set, editors are not created and this method
    // can't be called
    const {name, config} = this.pluginData!;

    /** @type {Object} */
    const detail: PluginConfigChangeDetail = {
      name,
      config: {...config, [_key]: info},
      notifyPath: `${name}.${notifyPath}`,
    };

    this.dispatchEvent(
      new CustomEvent(PLUGIN_CONFIG_CHANGED_EVENT_NAME, {
        detail,
        bubbles: true,
        composed: true,
      })
    );
  }

  /**
   * Work around a issue on iOS when clicking turns into double tap
   */
  _onTapPluginBoolean(e: Event) {
    e.preventDefault();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-plugin-config': GrRepoPluginConfig;
  }
}
