/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, PropertyValues} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {ServerInfo} from '../../../types/common';

@customElement('gr-plugin-host')
export class GrPluginHost extends LitElement {
  @property({type: Object})
  config?: ServerInfo;

  _configChanged(config: ServerInfo) {
    const jsPlugins = config.plugin?.js_resource_paths ?? [];
    const themes: string[] = config.default_theme ? [config.default_theme] : [];
    const instanceId = config.gerrit?.instance_id;
    getPluginLoader().loadPlugins([...themes, ...jsPlugins], instanceId);
  }

  override updated(changedProperties: PropertyValues<GrPluginHost>) {
    if (changedProperties.has('config') && this.config) {
      this._configChanged(this.config);
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-host': GrPluginHost;
  }
}
