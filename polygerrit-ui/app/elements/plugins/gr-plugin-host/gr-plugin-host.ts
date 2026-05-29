/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {ServerInfo} from '../../../types/common';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';

@customElement('gr-plugin-host')
export class GrPluginHost extends LitElement {
  @state()
  config?: ServerInfo;

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => {
        if (!config) return;
        const jsPlugins = config?.plugin?.js_resource_paths ?? [];
        const themes: string[] = config?.default_theme
          ? [config.default_theme]
          : [];
        const instanceId = config?.gerrit?.instance_id;
        this.getPluginLoader().loadPlugins(
          [...themes, ...jsPlugins],
          instanceId
        );
      }
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-host': GrPluginHost;
  }
}
