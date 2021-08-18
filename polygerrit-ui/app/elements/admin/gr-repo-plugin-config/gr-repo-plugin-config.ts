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
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {subpageStyles} from '../../../styles/gr-subpage-styles';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../gr-plugin-config-array-editor/gr-plugin-config-array-editor';
import {GrLitElement} from '../../lit/gr-lit-element';
import {css, customElement, html, property} from 'lit-element';
import {ConfigParameterInfoType} from '../../../constants/constants';
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
export class GrRepoPluginConfig extends GrLitElement {

  /**
   * Fired when the plugin config changes.
   *
   * @event plugin-config-changed
   */

  @property({type: Object})
  pluginData?: PluginData;

  static get styles() {
    return [
      sharedStyles,
      formStyles,
      subpageStyles,
      css`
        .inherited {
          color: var(--deemphasized-text-color);
          margin-left: var(--spacing-m);
        }
        section.section:not(.ARRAY) .title {
          align-items: center;
          display: flex;
        }
        section.section.ARRAY .title {
          padding-top: var(--spacing-m);
        }
      `
    ]
  }

  _renderOptionDetail(option: PluginOption) {
    if (this._isArray(option.info.type)) {
      return html`
        <gr-plugin-config-array-editor
          on-plugin-config-option-changed=${this._handleArrayChange}
          plugin-option="${option}"
        ></gr-plugin-config-array-editor>
      `;
    } else if (this._isBoolean(option.info.type)) {
      return html`
          <paper-toggle-button
            checked=${this._computeChecked(option.info.value)}
            on-change=${this._handleBooleanChange}
            data-option-key$=${option._key}
            ?disabled=${option.info.editable}
            on-click=${this._onTapPluginBoolean}
          ></paper-toggle-button>
      `;
    } else if (this._isList(option.info.type)) {
      return html`
          <gr-select
            bind-value$="${option.info.value}"
            on-change=${this._handleListChange}
          >
            <select
              data-option-key$=${option._key}
              ?disabled=${option.info.editable}
            >
            ${
              (option.info.permitted_values || []).map(
                (value) => html`<option value$="${value}">${value}</option>`
              )
            }
            </select>
          </gr-select>
      `;
    } else if (this._isString(option.info.type)) {
      return html`
      <iron-input
        bind-value="${option.info.value}"
        on-input=${this._handleStringChange}
        data-option-key$="${option._key}"
        ?disabled=${option.info.editable}
      >
        <input
          is="iron-input"
          value="${option.info.value}"
          on-input=${this._handleStringChange}
          data-option-key$="${option._key}"
          ?disabled=${option.info.editable}
        />
    </iron-input>
    `;
    } else if (option.info.inherited_value) {
      return html`
        <span class="inherited">
          (Inherited: ${option.info.inherited_value})
        </span>
      `;
    } else {
      return html``;
    }
  }
  _renderOption(option: PluginOption) {
    return html`
        <section class="section ${option.info.type}">
          <span class="title">
            <gr-tooltip-content
              has-tooltip="${option.info.description}"
              show-icon="${option.info.description}"
              title="${option.info.description}"
            >
              <span>${option.info.display_name}</span>
            </gr-tooltip-content>
          </span>
          <span class="value">${this._renderOptionDetail(option)}</span>
        </section>
    `;
  }

  render() {
    // Render can be called prior to pluginData being updated.
    const pluginConfigOptions = this.pluginData ?
      this._computePluginConfigOptions(this.pluginData) : [];
    const items = pluginConfigOptions.map(
      option => this._renderOption(option));

    return html`
      <div class="gr-form-styles">
        <fieldset>
          <h4>${this.pluginData?.name || ''}</h4>
          ${items}
        </fieldset>
      </div>
    `;
  }

  _computePluginConfigOptions(pluginData: PluginData) {
    const config = pluginData.config;
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

  _computeChecked(value = 'false') {
    return JSON.parse(value) as boolean;
  }

  _handleStringChange(e: Event) {
    const el = e.target as IronInputElement;
    // In the template, the data-option-key is assigned to each editor
    const _key = el.getAttribute('data-option-key')!;
    const configChangeInfo = this._buildConfigChangeInfo(el.value, _key);
    this._handleChange(configChangeInfo);
  }

  _handleListChange(e: Event) {
    const el = e.target as HTMLOptionElement;
    // In the template, the data-option-key is assigned to each editor
    const _key = el.getAttribute('data-option-key')!;
    const configChangeInfo = this._buildConfigChangeInfo(el.value, _key);
    this._handleChange(configChangeInfo);
  }

  _handleBooleanChange(e: Event) {
    const el = e.target as PaperToggleButtonElement;
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
