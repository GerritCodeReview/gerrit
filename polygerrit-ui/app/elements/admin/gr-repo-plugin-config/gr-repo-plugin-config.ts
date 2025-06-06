/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '@material/web/switch/switch';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {subpageStyles} from '../../../styles/gr-subpage-styles';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../gr-plugin-config-array-editor/gr-plugin-config-array-editor';
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {ConfigParameterInfoType} from '../../../constants/constants';
import {
  ConfigParameterInfo,
  PluginParameterToConfigParameterInfoMap,
} from '../../../types/common';
import {IronInputElement} from '@polymer/iron-input/iron-input';
import {
  PluginConfigOptionsChangedEventDetail,
  PluginOption,
} from './gr-repo-plugin-config-types';
import {paperStyles} from '../../../styles/gr-paper-styles';
import {fire} from '../../../utils/event-util';
import {MdSwitch} from '@material/web/switch/switch';

export interface ConfigChangeInfo {
  _key: string; // parameterName of PluginParameterToConfigParameterInfoMap
  info: ConfigParameterInfo;
}

export interface PluginData {
  name: string; // parameterName of PluginParameterToConfigParameterInfoMap
  config: PluginParameterToConfigParameterInfoMap;
}

export interface PluginConfigChangeDetail {
  name: string; // parameterName of PluginParameterToConfigParameterInfoMap
  config: PluginParameterToConfigParameterInfoMap;
}

@customElement('gr-repo-plugin-config')
export class GrRepoPluginConfig extends LitElement {
  /**
   * Fired when the plugin config changes.
   *
   * @event plugin-config-changed
   */

  @property({type: Object})
  pluginData?: PluginData;

  @property({type: Boolean, reflect: true})
  disabled = false;

  static override get styles() {
    return [
      sharedStyles,
      grFormStyles,
      paperStyles,
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
      `,
    ];
  }

  override render() {
    // Render can be called prior to pluginData being updated.
    const pluginConfigOptions = this.pluginData
      ? this.computePluginConfigOptions(this.pluginData)
      : [];

    return html`
      <div class="gr-form-styles">
        <fieldset>
          <h4>${this.pluginData?.name || ''}</h4>
          ${pluginConfigOptions.map(option => this.renderOption(option))}
        </fieldset>
      </div>
    `;
  }

  private renderInherited(option: PluginOption) {
    if (option.info.inherited_value) {
      return html`
        <span class="inherited">
          (Inherited: ${option.info.inherited_value})
        </span>
      `;
    } else {
      return html``;
    }
  }

  private renderOption(option: PluginOption) {
    return html`
      <section class="section ${option.info.type}">
        <span class="title"> ${this.renderOptionTitle(option)} </span>
        <span class="value">
          ${this.renderOptionDetail(option)} ${this.renderInherited(option)}
        </span>
      </section>
    `;
  }

  private renderOptionTitle(option: PluginOption) {
    const titleName = html`<span>${option.info.display_name}</span>`;
    if (!option.info.description) return titleName;
    return html` <gr-tooltip-content
      has-tooltip
      show-icon
      title=${option.info.description}
    >
      ${titleName}
    </gr-tooltip-content>`;
  }

  private renderOptionDetail(option: PluginOption) {
    if (option.info.type === ConfigParameterInfoType.ARRAY) {
      return html`
        <gr-plugin-config-array-editor
          @plugin-config-option-changed=${this.handleArrayChange}
          .pluginOption=${option}
          ?disabled=${this.disabled || !option.info.editable}
        ></gr-plugin-config-array-editor>
      `;
    } else if (option.info.type === ConfigParameterInfoType.BOOLEAN) {
      return html`
        <md-switch
          ?selected=${this.computeChecked(option.info.value)}
          @change=${this.handleBooleanChange}
          data-option-key=${option._key}
          ?disabled=${this.disabled || !option.info.editable}
        ></md-switch>
      `;
    } else if (option.info.type === ConfigParameterInfoType.LIST) {
      return html`
        <gr-select
          .bindValue=${option.info.value}
          @change=${this.handleListChange}
        >
          <select
            data-option-key=${option._key}
            ?disabled=${this.disabled || !option.info.editable}
          >
            ${(option.info.permitted_values || []).map(
              value => html`<option value=${value}>${value}</option>`
            )}
          </select>
        </gr-select>
      `;
    } else if (
      option.info.type === ConfigParameterInfoType.STRING ||
      option.info.type === ConfigParameterInfoType.INT ||
      option.info.type === ConfigParameterInfoType.LONG
    ) {
      return html`
        <iron-input
          .bindValue=${option.info.value ?? ''}
          @input=${this.handleStringChange}
          data-option-key=${option._key}
        >
          <input
            is="iron-input"
            .value=${option.info.value ?? ''}
            @input=${this.handleStringChange}
            data-option-key=${option._key}
            ?disabled=${this.disabled || !option.info.editable}
          />
        </iron-input>
      `;
    } else {
      return html``;
    }
  }

  // Private but used in test
  computePluginConfigOptions(pluginData: PluginData) {
    const config = pluginData.config;
    return Object.keys(config).map(_key => {
      return {_key, info: config[_key]};
    });
  }

  private computeChecked(value = 'false') {
    return JSON.parse(value) as boolean;
  }

  private handleStringChange(e: Event) {
    const el = e.target as IronInputElement;
    // In the template, the data-option-key is assigned to each editor
    const key = el.getAttribute('data-option-key')!;
    const configChangeInfo = this.buildConfigChangeInfo(el.value, key);
    this.handleChange(configChangeInfo);
  }

  private handleListChange(e: Event) {
    const el = e.target as HTMLOptionElement;
    // In the template, the data-option-key is assigned to each editor
    const key = el.getAttribute('data-option-key')!;
    const configChangeInfo = this.buildConfigChangeInfo(el.value, key);
    this.handleChange(configChangeInfo);
  }

  private handleBooleanChange(e: Event) {
    const el = e.target as MdSwitch;
    // In the template, the data-option-key is assigned to each editor
    const key = el.getAttribute('data-option-key')!;
    const configChangeInfo = this.buildConfigChangeInfo(
      JSON.stringify(el.selected),
      key
    );
    this.handleChange(configChangeInfo);
  }

  // Private but used in test
  buildConfigChangeInfo(
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
    };
  }

  // Private but used in test
  handleArrayChange(e: CustomEvent<PluginConfigOptionsChangedEventDetail>) {
    this.handleChange(e.detail);
  }

  // Private but used in test
  handleChange({_key, info}: ConfigChangeInfo) {
    // If pluginData is not set, editors are not created and this method
    // can't be called
    const {name, config} = this.pluginData!;

    /** @type {Object} */
    const detail: PluginConfigChangeDetail = {
      name,
      config: {...config, [_key]: info},
    };
    fire(this, 'plugin-config-changed', detail);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-plugin-config': GrRepoPluginConfig;
  }
  interface HTMLElementEventMap {
    'plugin-config-changed': CustomEvent<PluginConfigChangeDetail>;
  }
}
