/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginsModel} from '../../../models/plugins/plugins-model';
import {
  FlowsAutosubmitProvider,
  FlowsPluginApi,
  FlowsProvider,
} from '../../../api/flows';
import {Plugin} from './gr-public-js-api';

export class GrFlowsApi implements FlowsPluginApi {
  constructor(
    private readonly plugins: PluginsModel,
    private readonly plugin: Plugin
  ) {}

  register(provider: FlowsProvider): void {
    this.plugins.registerFlowsProvider({
      pluginName: this.plugin.getPluginName(),
      provider,
    });
  }

  registerAutosubmitProvider(provider: FlowsAutosubmitProvider): void {
    this.plugins.registerFlowsAutosubmitProvider({
      pluginName: this.plugin.getPluginName(),
      provider,
    });
  }
}
